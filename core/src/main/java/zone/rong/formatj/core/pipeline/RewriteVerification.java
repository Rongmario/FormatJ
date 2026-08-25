package zone.rong.formatj.core.pipeline;

import zone.rong.formatj.api.rules.BraceRules;
import zone.rong.formatj.api.rules.ImportRules;
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
            if (edit.authority() == BraceRules.IF_ELSE
                    || edit.authority() == BraceRules.FOR_LOOP
                    || edit.authority() == BraceRules.WHILE_LOOP) {
                String problem = checkBraceLaw(edit);
                if (problem != null) {
                    return problem;
                }
            }
            if (edit.authority() == ImportRules.ORDER) {
                String problem = checkImportLaw(edit, formatted);
                if (problem != null) {
                    return problem;
                }
            }
        }
        return checkBracesBalance(edits);
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

    /** A brace rule may add and remove braces, and nothing else. */
    private static String checkBraceLaw(TokenEdit edit) {
        for (String token : edit.inserted()) {
            if (!token.equals("{") && !token.equals("}")) {
                return edit.authority().key() + " may only insert braces but inserted '" + token + "'";
            }
        }
        for (String token : edit.removed()) {
            if (!token.equals("{") && !token.equals("}")) {
                return edit.authority().key() + " may only remove braces but removed '" + token + "'";
            }
        }
        return null;
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
