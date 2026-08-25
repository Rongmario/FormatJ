package zone.rong.formatj.core.pipeline;

import zone.rong.formatj.api.Option;
import zone.rong.formatj.api.rules.BraceRules;
import zone.rong.formatj.api.rules.ImportRules;
import zone.rong.formatj.api.rules.LambdaRules;
import zone.rong.formatj.api.rules.SealedRules;
import zone.rong.formatj.api.rules.SwitchRules;
import zone.rong.formatj.core.cst.GreenNode;
import zone.rong.formatj.core.cst.ProgramTokens;
import zone.rong.formatj.core.cst.SyntaxToken;
import zone.rong.formatj.core.imports.ImportEntry;
import zone.rong.formatj.core.imports.ImportUsage;
import zone.rong.formatj.core.lexer.Token;
import zone.rong.formatj.core.rewrite.TokenEdit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Checks the output of a run that was allowed to change the program.
 *
 * <p>The formatter's ordinary guarantee is that the program's tokens come out exactly as they went
 * in. The rewrite stage exists to break that guarantee in specific, declared places, so it needs a
 * check of its own; without one, "the tokens changed" stops being evidence of a bug and the
 * formatter loses the only thing that made its output trustworthy.
 *
 * <p>The replacement is not a weaker check but a differently anchored one. The rewrite stage declares
 * every edit it made. Replaying those edits against the original token stream produces the token
 * stream the output is required to have, and the output is compared against that, one token at a
 * time. Nothing is waved through: an edit made but not declared, an edit declared but not made, and
 * damage anywhere the edits did not claim to touch all fail the same comparison.
 *
 * <p>Three further checks sit alongside it. Each edit is measured against the law of the rule that
 * claims to authorise it, so an edit cannot get itself accepted by describing itself as something
 * else. Comments must survive: a rewrite that deletes a token carrying comments has to rehome them,
 * never drop them. And rewriting must settle, because formatting is required to be a fixed point.
 */
public final class RewriteVerification {

    private RewriteVerification() { }

    /**
     * Verifies the tree the rewrite stage produced, before it is laid out.
     *
     * @return a description of the first problem, or null when the rewrite kept its side of the deal
     */
    public static String verifyRewrite(GreenNode before, GreenNode after) {
        return checkCommentsSurvived(before, after);
    }

    /**
     * Verifies that the formatted output is the original with exactly {@code edits} applied.
     *
     * @param before the tree as parsed, before any rewriting
     * @param formatted the tree obtained by re-parsing the formatter's output
     * @return a description of the first problem, or null when the output is exactly what was declared
     */
    public static String verifyOutput(GreenNode before, GreenNode formatted, List<TokenEdit> edits) {
        String lawProblem = checkEditLaws(edits, formatted);
        if (lawProblem != null) {
            return lawProblem;
        }

        List<String> expected;
        try {
            expected = replay(ProgramTokens.lexemes(before), edits);
        } catch (IllegalStateException problem) {
            return problem.getMessage();
        }

        String difference = firstDifference(expected, ProgramTokens.lexemes(formatted));
        return difference == null ? null : "output does not match the declared edits: " + difference;
    }

    // ------------------------------------------------------------------ replay

    /**
     * The token stream the declared edits say the output must have.
     *
     * <p>Deletions are resolved first, so an edit that claims to remove a token which is not there,
     * or which another edit already removed, is caught before anything is emitted.
     */
    static List<String> replay(List<String> original, List<TokenEdit> edits) {
        boolean[] deleted = new boolean[original.size()];
        for (TokenEdit edit : edits) {
            for (int i = 0; i < edit.removed().size(); i++) {
                int index = edit.position() + i;
                if (index >= original.size()) {
                    throw new IllegalStateException(
                            edit.authority().key() + " claims to delete past the end of the file");
                }
                if (!original.get(index).equals(edit.removed().get(i))) {
                    throw new IllegalStateException(
                            edit.authority().key() + " claims to delete '" + edit.removed().get(i) + "' at token "
                                    + index + " but the source has '" + original.get(index) + "'");
                }
                if (deleted[index]) {
                    throw new IllegalStateException(
                            "two edits both delete token " + index + " ('" + original.get(index) + "')");
                }
                deleted[index] = true;
            }
        }

        List<Sequenced> insertions = new ArrayList<>();
        for (int i = 0; i < edits.size(); i++) {
            TokenEdit edit = edits.get(i);
            if (!edit.inserted().isEmpty()) {
                if (edit.position() > original.size()) {
                    throw new IllegalStateException(
                            edit.authority().key() + " claims to insert past the end of the file");
                }
                insertions.add(new Sequenced(edit, i));
            }
        }
        insertions.sort(ORDER);

        List<String> expected = new ArrayList<>(original.size() + insertions.size());
        int next = 0;
        for (int position = 0; position <= original.size(); position++) {
            while (next < insertions.size() && insertions.get(next).edit().position() == position) {
                expected.addAll(insertions.get(next).edit().inserted());
                next++;
            }
            if (position < original.size() && !deleted[position]) {
                expected.add(original.get(position));
            }
        }
        return List.copyOf(expected);
    }

    /** An edit and where it sat in the ledger, which is the tiebreak for edits at one position. */
    private record Sequenced(TokenEdit edit, int sequence) { }

    /**
     * Position first; then, for edits landing on the same token, the bias each edit declared.
     *
     * <p>Rewriting runs innermost-first, so ledger order is inner before outer. A closing delimiter
     * wants that order and an opening delimiter wants the reverse, which is the whole reason a bias
     * exists.
     */
    private static final Comparator<Sequenced> ORDER = Comparator
            .comparingInt((Sequenced entry) -> entry.edit().position())
            .thenComparingInt(entry -> entry.edit().bias() == TokenEdit.Bias.OUTERMOST_FIRST ? 0 : 1)
            .thenComparingInt(entry -> entry.edit().bias() == TokenEdit.Bias.OUTERMOST_FIRST
                    ? -entry.sequence()
                    : entry.sequence());

    // -------------------------------------------------------------- edit laws

    /**
     * Each edit against the law of the rule that authorises it.
     *
     * <p>Replay alone would accept any self-consistent ledger, including one describing an edit the
     * rule has no business making. The law is what ties an edit back to the rule the user actually
     * turned on.
     */
    private static String checkEditLaws(List<TokenEdit> edits, GreenNode formatted) {
        for (TokenEdit edit : edits) {
            String problem = checkEditLaw(edit, formatted);
            if (problem != null) {
                return problem;
            }
        }
        return checkBracesBalance(edits);
    }

    /** The law of the one rule this edit claims to be. */
    private static String checkEditLaw(TokenEdit edit, GreenNode formatted) {
        Option<?> authority = edit.authority();
        if (authority == BraceRules.IF_ELSE
                || authority == BraceRules.FOR_LOOP
                || authority == BraceRules.WHILE_LOOP
                || authority == SwitchRules.ARROW_CASE_BRACES) {
            return checkBraceLaw(edit);
        }
        if (authority == ImportRules.ORDER) {
            return checkImportLaw(edit, formatted);
        }
        if (authority == SealedRules.PERMITS_ORDER) {
            return checkPermitsLaw(edit);
        }
        if (authority == LambdaRules.PARAMETER_STYLE) {
            return checkOnly(edit, "parentheses", "(", ")");
        }
        if (authority == LambdaRules.BODY_BRACES) {
            return checkLambdaBraceLaw(edit);
        }
        if (authority == SwitchRules.YIELD_STYLE) {
            return checkYieldLaw(edit);
        }
        return null;
    }

    /**
     * The import rules may rearrange the declarations that were there, and delete ones nothing refers
     * to. They may not invent one, and they may not delete one the file still needs.
     *
     * <p>The second half is not taken on trust. The verifier splits the edit's own tokens into
     * declarations and, for each one that did not come back, re-derives from the formatted output
     * whether the name is still mentioned. It never asks the rewrite what it concluded.
     */
    private static String checkImportLaw(TokenEdit edit, GreenNode formatted) {
        List<ImportEntry> before = declarations(edit.removed());
        List<ImportEntry> after = declarations(edit.inserted());
        if (before == null || after == null) {
            return ImportRules.ORDER.key() + " may only rewrite whole import declarations";
        }

        Map<String, Integer> remaining = new LinkedHashMap<>();
        for (ImportEntry entry : before) {
            remaining.merge(entry.text(), 1, Integer::sum);
        }
        for (ImportEntry entry : after) {
            Integer count = remaining.get(entry.text());
            if (count == null || count == 0) {
                return ImportRules.ORDER.key() + " produced an import that was not there: " + entry.text();
            }
            remaining.put(entry.text(), count - 1);
        }

        for (ImportEntry entry : before) {
            Integer count = remaining.get(entry.text());
            if (count == null || count == 0) {
                continue;
            }
            String problem = checkRemovable(entry, formatted);
            if (problem != null) {
                return problem;
            }
        }
        return null;
    }

    private static String checkRemovable(ImportEntry entry, GreenNode formatted) {
        if (!entry.isRemovable()) {
            return ImportRules.ORDER.key() + " removed an import whose use cannot be seen: " + entry.text();
        }
        if (ImportUsage.namesMentioned(formatted).contains(entry.simpleName())
                || ImportUsage.mentionedInComments(formatted, entry.simpleName())) {
            return ImportRules.ORDER.key() + " removed " + entry.text() + " but the file still mentions "
                    + entry.simpleName();
        }
        return null;
    }

    /** Splits a run of tokens into import declarations, or null when it is not made of them. */
    private static List<ImportEntry> declarations(List<String> tokens) {
        List<ImportEntry> declarations = new ArrayList<>();
        List<String> current = new ArrayList<>();
        for (String token : tokens) {
            current.add(token);
            if (!token.equals(";")) {
                continue;
            }
            ImportEntry entry = ImportEntry.ofLexemes(current);
            if (entry == null) {
                return null;
            }
            declarations.add(entry);
            current = new ArrayList<>();
        }
        return current.isEmpty() ? declarations : null;
    }

    /**
     * A permits clause may be rearranged and nothing else.
     *
     * <p>The same law as {@code imports.order}, on a list whose elements are separated rather than
     * terminated: the run comes back holding the same declarations in a different order. It is the
     * stricter half of the import law, without the removal clause — a permitted subclass that went
     * missing would stop the file compiling and one that appeared would permit something the author
     * never wrote, so unlike an unused import there is no case in which dropping or inventing one is
     * allowed.
     */
    private static String checkPermitsLaw(TokenEdit edit) {
        List<String> before = separated(edit.removed());
        List<String> after = separated(edit.inserted());
        if (before == null || after == null) {
            return SealedRules.PERMITS_ORDER.key() + " may only rewrite a whole permits clause";
        }
        String difference = sameBag(before, after);
        return difference == null
                ? null
                : SealedRules.PERMITS_ORDER.key() + " did more than reorder the clause: " + difference;
    }

    /**
     * Splits a comma-separated run into its elements, or null when it is not one.
     *
     * <p>Nesting is counted, so a type argument's own commas stay inside the element they belong to.
     */
    private static List<String> separated(List<String> tokens) {
        List<String> elements = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        int depth = 0;
        for (String token : tokens) {
            if (token.equals("<")) {
                depth++;
            } else if (token.equals(">")) {
                depth--;
            }
            if (depth < 0) {
                return null;
            }
            if (token.equals(",") && depth == 0) {
                if (current.isEmpty()) {
                    return null;
                }
                elements.add(current.toString());
                current.setLength(0);
                continue;
            }
            current.append(token);
        }
        if (depth != 0 || current.isEmpty()) {
            return null;
        }
        elements.add(current.toString());
        return elements;
    }

    /** Whether two lists hold the same things, in any order. */
    private static String sameBag(List<String> before, List<String> after) {
        Map<String, Integer> counts = new LinkedHashMap<>();
        for (String element : before) {
            counts.merge(element, 1, Integer::sum);
        }
        for (String element : after) {
            Integer count = counts.get(element);
            if (count == null || count == 0) {
                return "produced " + element + ", which was not there";
            }
            counts.put(element, count - 1);
        }
        for (Map.Entry<String, Integer> entry : counts.entrySet()) {
            if (entry.getValue() > 0) {
                return "dropped " + entry.getKey();
            }
        }
        return null;
    }

    /**
     * A lambda body rule may only take a body apart, never put one together.
     *
     * <p>Collapsing {@code x -> { return e; }} to {@code x -> e} removes a brace, possibly a
     * {@code return}, and the statement's semicolon, in two contiguous pieces. Insisting on exactly
     * those pieces is what stops the rule reaching for anything else, and the empty insert list is
     * what holds the rewrite to its own account of why the other direction is not offered: which
     * braced form an expression body wants is a question about the target type, not about the tokens.
     */
    private static String checkLambdaBraceLaw(TokenEdit edit) {
        if (!edit.inserted().isEmpty()) {
            return LambdaRules.BODY_BRACES.key() + " may only remove braces but inserted " + edit.inserted();
        }
        List<String> removed = edit.removed();
        if (removed.equals(List.of("{"))
                || removed.equals(List.of("{", "return"))
                || removed.equals(List.of(";", "}"))) {
            return null;
        }
        return LambdaRules.BODY_BRACES.key() + " removed " + removed + ", which is not a lambda body's braces";
    }

    /**
     * The braces of an arrow case body in an expression switch, and the {@code yield} that comes with
     * them.
     *
     * <p>They are one edit rather than two because they are one decision: an expression switch's
     * arrow body is a value, so a block round it has to yield that value and an expression body has
     * to be that value. The law therefore names the pair, and a rule that added a brace without the
     * {@code yield} — leaving a block that falls off its end without producing anything — fails it.
     */
    private static String checkYieldLaw(TokenEdit edit) {
        List<String> changed = edit.inserted().isEmpty() ? edit.removed() : edit.inserted();
        if (!edit.removed().isEmpty() && !edit.inserted().isEmpty()) {
            return SwitchRules.YIELD_STYLE.key() + " may add or remove a yield block, not replace one";
        }
        if (changed.equals(List.of("{", "yield")) || changed.equals(List.of("}"))) {
            return null;
        }
        return SwitchRules.YIELD_STYLE.key() + " changed " + changed + ", which is not a yield block";
    }

    /** A rule that may touch the named tokens and no others. */
    private static String checkOnly(TokenEdit edit, String what, String... allowed) {
        List<String> permitted = List.of(allowed);
        for (String token : edit.inserted()) {
            if (!permitted.contains(token)) {
                return edit.authority().key() + " may only insert " + what + " but inserted '" + token + "'";
            }
        }
        for (String token : edit.removed()) {
            if (!permitted.contains(token)) {
                return edit.authority().key() + " may only remove " + what + " but removed '" + token + "'";
            }
        }
        return null;
    }

    /** A brace rule may add and remove braces, and nothing else. */
    private static String checkBraceLaw(TokenEdit edit) {
        return checkOnly(edit, "braces", "{", "}");
    }

    /** Braces come in pairs: an edit that opens without closing would not compile. */
    private static String checkBracesBalance(List<TokenEdit> edits) {
        int opened = 0;
        int closed = 0;
        for (TokenEdit edit : edits) {
            for (String token : edit.inserted()) {
                if (token.equals("{")) {
                    opened++;
                } else if (token.equals("}")) {
                    closed++;
                }
            }
            for (String token : edit.removed()) {
                if (token.equals("{")) {
                    opened--;
                } else if (token.equals("}")) {
                    closed--;
                }
            }
        }
        return opened == closed
                ? null
                : "braces were not balanced: " + opened + " opened against " + closed + " closed";
    }

    // ------------------------------------------------------------- comments

    /**
     * Comments must come through a rewrite intact.
     *
     * <p>Comments are not significant tokens, so the token comparison is blind to them: a rewrite
     * could delete a brace and take the comment attached to it with no other check noticing. When a
     * rewrite removes a token that carried comments, those comments belong on whatever token survives
     * next to it.
     *
     * <p>Compared as a bag rather than a sequence. Reordering imports reorders the comments riding on
     * them, which is the point of the exercise, so insisting on the original order would fail a
     * correct rewrite. Losing one, gaining one or altering one still fails.
     */
    private static String checkCommentsSurvived(GreenNode before, GreenNode after) {
        List<String> was = new ArrayList<>(comments(before));
        List<String> now = new ArrayList<>(comments(after));
        was.sort(null);
        now.sort(null);
        String difference = firstDifference(was, now);
        return difference == null ? null : "a comment was lost or altered: " + difference;
    }

    private static List<String> comments(GreenNode node) {
        List<String> comments = new ArrayList<>();
        collectComments(node, comments);
        return comments;
    }

    private static void collectComments(GreenNode node, List<String> comments) {
        if (node instanceof GreenNode.Leaf leaf) {
            SyntaxToken token = leaf.token();
            for (Token comment : token.leadingComments()) {
                comments.add(comment.text());
            }
            for (Token comment : token.trailingComments()) {
                comments.add(comment.text());
            }
            return;
        }
        for (GreenNode child : node.children()) {
            collectComments(child, comments);
        }
    }

    // ---------------------------------------------------------------- shared

    private static String firstDifference(List<String> expected, List<String> actual) {
        int shared = Math.min(expected.size(), actual.size());
        for (int i = 0; i < shared; i++) {
            if (!expected.get(i).equals(actual.get(i))) {
                return "expected '" + expected.get(i) + "' at position " + i + " but found '" + actual.get(i) + "'";
            }
        }
        if (expected.size() != actual.size()) {
            return "expected " + expected.size() + " entries but found " + actual.size();
        }
        return null;
    }

}
