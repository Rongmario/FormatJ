package zone.rong.formatj.core.pipeline;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import zone.rong.formatj.api.Option;
import zone.rong.formatj.api.rules.ArrayRules;
import zone.rong.formatj.api.rules.BraceRules;
import zone.rong.formatj.api.rules.ImportRules;
import zone.rong.formatj.api.rules.LambdaRules;
import zone.rong.formatj.api.rules.LiteralRules;
import zone.rong.formatj.api.rules.MemberRules;
import zone.rong.formatj.api.rules.ModifierRules;
import zone.rong.formatj.api.rules.SealedRules;
import zone.rong.formatj.api.rules.SemicolonRules;
import zone.rong.formatj.api.rules.SwitchRules;
import zone.rong.formatj.api.rules.TextBlockRules;
import zone.rong.formatj.core.cst.GreenNode;
import zone.rong.formatj.core.cst.MemberGroup;
import zone.rong.formatj.core.cst.ProgramTokens;
import zone.rong.formatj.core.cst.SyntaxKind;
import zone.rong.formatj.core.cst.SyntaxToken;
import zone.rong.formatj.core.imports.ImportEntry;
import zone.rong.formatj.core.imports.ImportUsage;
import zone.rong.formatj.core.lexer.Token;
import zone.rong.formatj.core.lexer.UnicodeEscapes;
import zone.rong.formatj.core.rewrite.TokenEdit;
import zone.rong.formatj.core.text.TextBlocks;

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
        String lawProblem = checkEditLaws(before, edits, formatted);
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
     * or which another edit already removed, is caught before anything is emitted. Reordering edits
     * apply last, to what the other edits produced, so a splice inside a moved stretch moves with it.
     */
    static List<String> replay(List<String> original, List<TokenEdit> edits) {
        boolean[] deleted = new boolean[original.size()];
        for (TokenEdit edit : edits) {
            for (int i = 0; i < edit.removed().size(); i++) {
                int index = edit.position() + i;
                if (index >= original.size()) {
                    throw new IllegalStateException(
                        edit.authority().key() + " claims to delete past the end of the file"
                    );
                }
                if (!original.get(index).equals(edit.removed().get(i))) {
                    throw new IllegalStateException(
                        edit.authority().key() + " claims to delete '" + edit.removed().get(i) + "' at token " + index +
                            " but the source has '" + original.get(index) + "'"
                    );
                }
                if (deleted[index]) {
                    throw new IllegalStateException(
                        "two edits both delete token " + index + " ('" + original.get(index) + "')"
                    );
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
                        edit.authority().key() + " claims to insert past the end of the file"
                    );
                }
                insertions.add(new Sequenced(edit, i));
            }
        }
        insertions.sort(ORDER);

        List<List<String>> slots = new ArrayList<>(original.size() + 1);
        int next = 0;
        for (int position = 0; position <= original.size(); position++) {
            List<String> slot = new ArrayList<>();
            while (next < insertions.size() && insertions.get(next).edit().position() == position) {
                slot.addAll(insertions.get(next).edit().inserted());
                next++;
            }
            if (position < original.size() && !deleted[position]) {
                slot.add(original.get(position));
            }
            slots.add(slot);
        }
        reorder(slots, edits);

        List<String> expected = new ArrayList<>(original.size() + insertions.size());
        slots.forEach(expected::addAll);
        return List.copyOf(expected);
    }

    /**
     * Applies the reordering edits, innermost first.
     *
     * <p>A stretch is moved as a whole, so a reordering inside one leaves the stretch where it was
     * and the reordering around it sees the same slots in a different arrangement. The slots between
     * two stretches belong to tokens a splice deleted, so they hold nothing and are dropped.
     */
    private static void reorder(List<List<String>> slots, List<TokenEdit> edits) {
        List<TokenEdit> moves = edits.stream()
            .filter(edit -> !edit.order().isEmpty())
            .sorted(Comparator.comparingInt(RewriteVerification::runLength))
            .toList();
        for (TokenEdit move : moves) {
            int start = move.position();
            int end = start + runLength(move);
            List<TokenEdit.Span> sorted = move.order()
                .stream()
                .sorted(Comparator.comparingInt(TokenEdit.Span::start))
                .toList();
            for (int i = 1; i < sorted.size(); i++) {
                if (sorted.get(i).start() < sorted.get(i - 1).end()) {
                    throw new IllegalStateException(move.authority().key() + " reordered overlapping stretches");
                }
            }
            if (end > slots.size()) {
                throw new IllegalStateException(move.authority().key() + " reorders past the end of the file");
            }
            List<List<String>> moved = new ArrayList<>();
            for (TokenEdit.Span span : move.order()) {
                moved.addAll(List.copyOf(slots.subList(span.start(), span.end())));
            }
            List<List<String>> region = slots.subList(start, end);
            int kept = moved.size();
            for (int i = 0; i < region.size(); i++) {
                region.set(i, i < kept ? moved.get(i) : List.of());
            }
        }
    }

    /** From the first token a reordering touches to one past the last. */
    private static int runLength(TokenEdit move) {
        return move.order().stream().mapToInt(TokenEdit.Span::end).max().orElse(0) - move.position();
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
    private static String checkEditLaws(GreenNode before, List<TokenEdit> edits, GreenNode formatted) {
        for (TokenEdit edit : edits) {
            String problem = checkEditLaw(edit, before, formatted);
            if (problem != null) {
                return problem;
            }
        }
        return checkBracesBalance(edits);
    }

    /** The law of the one rule this edit claims to be. */
    private static String checkEditLaw(TokenEdit edit, GreenNode before, GreenNode formatted) {
        Option<?> authority = edit.authority();
        if (authority == BraceRules.IF_ELSE ||
            authority == BraceRules.FOR_LOOP ||
            authority == BraceRules.WHILE_LOOP ||
            authority == SwitchRules.ARROW_CASE_BRACES) {
            return checkBraceLaw(edit);
        }
        if (authority == ImportRules.ORDER) {
            return checkImportLaw(edit, formatted);
        }
        if (authority == SealedRules.PERMITS_ORDER) {
            return checkPermitsLaw(edit);
        }
        if (authority == ModifierRules.ORDER) {
            return checkModifierLaw(edit, before);
        }
        if (authority == ModifierRules.REMOVE_REDUNDANT) {
            return checkRedundantModifierLaw(edit);
        }
        if (authority == ArrayRules.C_STYLE_BRACKETS) {
            return checkOnly(edit, "array brackets", "[", "]");
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
        if (authority == SwitchRules.CASE_STYLE) {
            return checkCaseStyleLaw(edit);
        }
        if (authority == TextBlockRules.CLOSING_DELIMITER_ON_OWN_LINE ||
            authority == TextBlockRules.ESCAPE_TRAILING_SPACES) {
            return checkTextBlockLaw(edit);
        }
        if (authority == LiteralRules.LONG_SUFFIX || authority == LiteralRules.HEX_DIGITS) {
            return checkLiteralLaw(edit);
        }
        if (authority == SemicolonRules.REMOVE_REDUNDANT) {
            return checkSemicolonLaw(edit, before);
        }
        if (authority == MemberRules.ORDER) {
            return checkMemberOrderLaw(edit, before);
        }
        return null;
    }

    /**
     * The member order rule may rearrange the members of one type body into IntelliJ's group order,
     * keeping members of one group in the order they were in.
     *
     * <p>The edit names stretches of the original tokens, so nothing can be added or dropped by it.
     * What this checks is that the stretches are exactly the members of one body, that whatever lies
     * between them is a stray semicolon another edit deleted, and that the order they are written in
     * is the stable sort by group, which is re-derived from the original tree.
     */
    private static String checkMemberOrderLaw(TokenEdit edit, GreenNode before) {
        String problem = MemberRules.ORDER.key() + " may only sort the members of one type body";
        if (!edit.removed().isEmpty() || !edit.inserted().isEmpty()) {
            return problem;
        }
        Map<GreenNode.Leaf, Integer> positions = ProgramTokens.positions(before);
        MemberBody body = bodyAt(before, edit.position(), positions);
        if (body == null) {
            return problem;
        }

        Map<TokenEdit.Span, Integer> originalIndex = new LinkedHashMap<>();
        for (GreenNode member : body.members()) {
            List<GreenNode.Leaf> leaves = ProgramTokens.leaves(member);
            originalIndex.put(
                new TokenEdit.Span(positions.get(leaves.getFirst()), positions.get(leaves.getLast()) + 1),
                originalIndex.size()
            );
        }
        if (edit.order().size() != originalIndex.size() || !originalIndex.keySet().containsAll(edit.order())) {
            return problem;
        }

        List<String> tokens = ProgramTokens.lexemes(before);
        List<TokenEdit.Span> spans = List.copyOf(originalIndex.keySet());
        for (int i = 1; i < spans.size(); i++) {
            for (int gap = spans.get(i - 1).end(); gap < spans.get(i).start(); gap++) {
                if (!tokens.get(gap).equals(";")) {
                    return problem;
                }
            }
        }

        int previousGroup = 0;
        int previousIndex = -1;
        for (TokenEdit.Span span : edit.order()) {
            int index = originalIndex.get(span);
            int group = MemberGroup.of(body.members().get(index), body.interfaceBody());
            if (group < previousGroup || group == previousGroup && index < previousIndex) {
                return MemberRules.ORDER.key() + " did not produce the stable order by member group";
            }
            previousGroup = group;
            previousIndex = index;
        }
        return null;
    }

    /** The orderable members of a type body, and whether that body belongs to an interface. */
    private record MemberBody(List<GreenNode> members, boolean interfaceBody) { }

    /** The body whose first orderable member starts at {@code position}, or null. */
    private static MemberBody bodyAt(GreenNode node, int position, Map<GreenNode.Leaf, Integer> positions) {
        boolean interfaceBody = node.kind() == SyntaxKind.INTERFACE_DECLARATION ||
            node.kind() == SyntaxKind.ANNOTATION_TYPE_DECLARATION;
        for (GreenNode child : node.children()) {
            if (child.kind() == SyntaxKind.CLASS_BODY) {
                List<GreenNode> members = child.children()
                    .subList(1, child.children().size() - 1)
                    .stream()
                    .filter(member -> member.kind() != SyntaxKind.ENUM_CONSTANTS &&
                        member.kind() != SyntaxKind.EMPTY_STATEMENT)
                    .toList();
                if (!members.isEmpty() &&
                    positions.get(ProgramTokens.leaves(members.getFirst()).getFirst()) == position) {
                    return new MemberBody(members, interfaceBody);
                }
            }
            MemberBody found = bodyAt(child, position, positions);
            if (found != null) {
                return found;
            }
        }
        return null;
    }

    /** The semicolon rule may delete one {@code ;} that the original tree reads as an empty declaration. */
    private static String checkSemicolonLaw(TokenEdit edit, GreenNode before) {
        if (edit.removed().equals(List.of(";")) &&
            edit.inserted().isEmpty() &&
            isEmptyDeclaration(before, edit.position(), ProgramTokens.positions(before))) {
            return null;
        }
        return edit.authority().key() + " may only delete a stray semicolon between members or after a type";
    }

    private static boolean isEmptyDeclaration(GreenNode node, int position, Map<GreenNode.Leaf, Integer> positions) {
        boolean container = node.kind() == SyntaxKind.CLASS_BODY || node.kind() == SyntaxKind.COMPILATION_UNIT;
        for (GreenNode child : node.children()) {
            if (container &&
                child.kind() == SyntaxKind.EMPTY_STATEMENT &&
                positions.get(ProgramTokens.leaves(child).getFirst()) == position) {
                return true;
            }
            if (isEmptyDeclaration(child, position, positions)) {
                return true;
            }
        }
        return false;
    }

    /**
     * A literal rule may change the case of one literal's characters, and only the ones it owns: the
     * long suffix, and the hex digits before any {@code p} exponent.
     */
    private static String checkLiteralLaw(TokenEdit edit) {
        if (edit.removed().size() != 1 || edit.inserted().size() != 1) {
            return edit.authority().key() + " may only rewrite one literal";
        }
        String before = edit.removed().getFirst();
        String after = edit.inserted().getFirst();
        if (before.length() != after.length()) {
            return edit.authority().key() + " changed the length of " + before;
        }
        for (int i = 0; i < before.length(); i++) {
            char was = before.charAt(i);
            char now = after.charAt(i);
            boolean suffix = edit.authority() == LiteralRules.LONG_SUFFIX &&
                i == before.length() - 1 &&
                was == 'l' &&
                now == 'L';
            boolean digit = before.regionMatches(true, 0, "0x", 0, 2) &&
                i >= 2 &&
                before.substring(0, i).toLowerCase(Locale.ROOT).indexOf('p') < 0 &&
                Character.toLowerCase(was) == Character.toLowerCase(now) &&
                Character.toLowerCase(was) >= 'a' &&
                Character.toLowerCase(was) <= 'f';
            if (was != now && !suffix && !digit) {
                return edit.authority().key() + " changed '" + was + "' in " + before;
            }
        }
        return null;
    }

    /**
     * A text block rule may change where the string ends and nothing else.
     *
     * <p>The two rules that rewrite a text block both change the string it denotes, which is the
     * reason they are rewrites rather than layout, and the reason the law is stated over the value
     * rather than over the characters. What they are allowed to change is the white space at the end
     * of a line and the line terminator at the end of the block — the two places the language throws
     * something away that the author may have meant. Everything else must survive: comparing both
     * values with those two allowances normalised out leaves a comparison in which a lost line, a
     * changed word or a mangled escape is still a difference.
     *
     * <p>Stated once for both rules because they act on one token, so they arrive as one edit. Which
     * of the two an edit names says which rule was reached for first; it cannot buy the edit any
     * latitude the other would not also have had.
     */
    private static String checkTextBlockLaw(TokenEdit edit) {
        if (edit.removed().size() != 1 ||
            edit.inserted().size() != 1 ||
            !TextBlocks.isTextBlock(edit.removed().getFirst()) ||
            !TextBlocks.isTextBlock(edit.inserted().getFirst())) {
            return edit.authority().key() + " may only rewrite one whole text block";
        }
        String before = endings(TextBlocks.value(edit.removed().getFirst()));
        String after = endings(TextBlocks.value(edit.inserted().getFirst()));
        if (before.equals(after)) {
            return null;
        }
        return edit.authority().key() + " changed what the text block says, not only where it ends";
    }

    /** A value with the two differences a text block rule may make normalised away. */
    private static String endings(String value) {
        String[] lines = value.split("\n", -1);
        StringBuilder out = new StringBuilder(value.length());
        for (int i = 0; i < lines.length; i++) {
            if (i > 0) {
                out.append('\n');
            }
            int end = lines[i].length();
            while (end > 0 &&
                (lines[i].charAt(end - 1) == ' ' ||
                    lines[i].charAt(end - 1) == '\t' ||
                    lines[i].charAt(end - 1) == '\f')) {
                end--;
            }
            out.append(lines[i], 0, end);
        }
        while (!out.isEmpty() && out.charAt(out.length() - 1) == '\n') {
            out.setLength(out.length() - 1);
        }
        return out.toString();
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
        if (ImportUsage.namesMentioned(formatted).contains(entry.simpleName()) ||
            ImportUsage.mentionedInComments(formatted, entry.simpleName())) {
            return ImportRules.ORDER.key() + " removed " + entry.text() + " but the file still mentions " +
                entry.simpleName();
        }
        return null;
    }

    /** Splits a run of tokens into import declarations, or null when it is not made of them. */
    private static List<ImportEntry> declarations(List<String> tokens) {
        List<ImportEntry> declarations = new ArrayList<>();
        List<String> current = new ArrayList<>();
        for (String token : tokens) {
            current.add(token);
            if (!decoded(token).equals(";")) {
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

    /** A modifier rule may permute one declaration's modifiers and nothing else. */
    private static String checkModifierLaw(TokenEdit edit, GreenNode beforeTree) {
        List<ModifierElement> before = modifierSpan(edit.removed());
        List<ModifierElement> after = modifierSpan(edit.inserted());
        if (before == null || after == null || !isDeclaredModifierSpan(beforeTree, edit)) {
            return ModifierRules.ORDER.key() + " may only rewrite one declared modifier span";
        }
        if (before.size() != after.size()) {
            return ModifierRules.ORDER.key() + " inserted or deleted a modifier";
        }

        List<List<String>> beforeModifiers = new ArrayList<>();
        List<List<String>> afterModifiers = new ArrayList<>();
        for (int i = 0; i < before.size(); i++) {
            ModifierElement was = before.get(i);
            ModifierElement now = after.get(i);
            if (was.annotation() != now.annotation()) {
                return ModifierRules.ORDER.key() + " moved a modifier across an annotation";
            }
            if (was.annotation()) {
                if (!was.tokens().equals(now.tokens())) {
                    return ModifierRules.ORDER.key() + " changed or reordered annotations";
                }
            } else {
                beforeModifiers.add(was.tokens());
                afterModifiers.add(now.tokens());
            }
        }

        String difference = sameTokenBag(beforeModifiers, afterModifiers);
        return difference == null
            ? null
            : ModifierRules.ORDER.key() + " did more than permute modifiers: " + difference;
    }

    /** The redundant-modifier rule may delete one modifier at a time and insert nothing. */
    private static String checkRedundantModifierLaw(TokenEdit edit) {
        if (!edit.inserted().isEmpty() || edit.removed().size() != 1) {
            return ModifierRules.REMOVE_REDUNDANT.key() + " may only delete a single modifier";
        }
        return checkOnly(edit, "modifiers", "public", "abstract", "static", "final", "private");
    }

    private static final Set<String> MODIFIERS = Set.of(
        "public",
        "protected",
        "private",
        "abstract",
        "default",
        "static",
        "final",
        "transient",
        "volatile",
        "synchronized",
        "native",
        "strictfp",
        "sealed"
    );

    private static boolean isDeclaredModifierSpan(GreenNode tree, TokenEdit edit) {
        Map<GreenNode.Leaf, Integer> positions = ProgramTokens.positions(tree);
        return containsModifierSpan(tree, edit, positions);
    }

    private static boolean containsModifierSpan(
        GreenNode node,
        TokenEdit edit,
        Map<GreenNode.Leaf, Integer> positions
    ) {
        if (isModifierDeclaration(node.kind()) && !node.children().isEmpty()) {
            GreenNode modifiers = node.children().getFirst();
            List<GreenNode.Leaf> leaves = ProgramTokens.leaves(modifiers);
            if (modifiers.kind() == SyntaxKind.MODIFIERS &&
                !leaves.isEmpty() &&
                positions.get(leaves.getFirst()) == edit.position() &&
                ProgramTokens.lexemes(modifiers).equals(edit.removed())) {
                return true;
            }
        }
        for (GreenNode child : node.children()) {
            if (containsModifierSpan(child, edit, positions)) {
                return true;
            }
        }
        return false;
    }

    private static boolean isModifierDeclaration(SyntaxKind kind) {
        return switch (kind) {
            case CLASS_DECLARATION, INTERFACE_DECLARATION, ENUM_DECLARATION, RECORD_DECLARATION,
                ANNOTATION_TYPE_DECLARATION, FIELD_DECLARATION, METHOD_DECLARATION, CONSTRUCTOR_DECLARATION,
                COMPACT_CONSTRUCTOR_DECLARATION, ANNOTATION_ELEMENT_DECLARATION -> true;
            default -> false;
        };
    }

    /** Splits one modifier branch into modifier units and fixed annotation slots. */
    private static List<ModifierElement> modifierSpan(List<String> tokens) {
        List<ModifierElement> elements = new ArrayList<>();
        for (int i = 0; i < tokens.size();) {
            String token = decoded(tokens.get(i));
            if (token.equals("@")) {
                int end = annotationEnd(tokens, i);
                if (end < 0) {
                    return null;
                }
                elements.add(new ModifierElement(true, tokens.subList(i, end)));
                i = end;
                continue;
            }
            if (token.equals("non") &&
                i + 2 < tokens.size() &&
                decoded(tokens.get(i + 1)).equals("-") &&
                decoded(tokens.get(i + 2)).equals("sealed")) {
                elements.add(new ModifierElement(false, tokens.subList(i, i + 3)));
                i += 3;
                continue;
            }
            if (!MODIFIERS.contains(token)) {
                return null;
            }
            elements.add(new ModifierElement(false, List.of(tokens.get(i))));
            i++;
        }
        return elements.stream().anyMatch(element -> !element.annotation()) ? List.copyOf(elements) : null;
    }

    private static int annotationEnd(List<String> tokens, int start) {
        int next = start + 1;
        if (next >= tokens.size() || !identifier(decoded(tokens.get(next)))) {
            return -1;
        }
        next++;
        while (next < tokens.size() && decoded(tokens.get(next)).equals(".")) {
            if (next + 1 >= tokens.size() || !identifier(decoded(tokens.get(next + 1)))) {
                return -1;
            }
            next += 2;
        }
        if (next >= tokens.size() || !decoded(tokens.get(next)).equals("(")) {
            return next;
        }

        int depth = 0;
        do {
            String token = decoded(tokens.get(next++));
            if (token.equals("(")) {
                depth++;
            } else if (token.equals(")") && --depth < 0) {
                return -1;
            }
        } while (next < tokens.size() && depth > 0);
        return depth == 0 ? next : -1;
    }

    private static boolean identifier(String text) {
        if (text.isEmpty() || !Character.isJavaIdentifierStart(text.codePointAt(0))) {
            return false;
        }
        for (int offset = Character.charCount(text.codePointAt(0)); offset < text.length();) {
            int codePoint = text.codePointAt(offset);
            if (!Character.isJavaIdentifierPart(codePoint)) {
                return false;
            }
            offset += Character.charCount(codePoint);
        }
        return true;
    }

    private static String decoded(String token) {
        return UnicodeEscapes.decode(token);
    }

    private static String sameTokenBag(List<List<String>> before, List<List<String>> after) {
        Map<List<String>, Integer> counts = new LinkedHashMap<>();
        for (List<String> modifier : before) {
            counts.merge(modifier, 1, Integer::sum);
        }
        for (List<String> modifier : after) {
            Integer count = counts.get(modifier);
            if (count == null || count == 0) {
                return "produced " + modifier + ", which was not there";
            }
            counts.put(modifier, count - 1);
        }
        for (Map.Entry<List<String>, Integer> entry : counts.entrySet()) {
            if (entry.getValue() > 0) {
                return "dropped " + entry.getKey();
            }
        }
        return null;
    }

    private record ModifierElement(boolean annotation, List<String> tokens) {

        private ModifierElement {
            tokens = List.copyOf(tokens);
        }

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
        for (String raw : tokens) {
            String token = decoded(raw);
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
        if (removed.equals(List.of("{")) ||
            removed.equals(List.of("{", "return")) ||
            removed.equals(List.of(";", "}"))) {
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

    /**
     * A case style rule may spell a label and its terminator differently, and nothing else.
     *
     * <p>Deliberately a weaker law than the others here, and said so out loud. Whether a colon case
     * may become an arrow case is a question about fall-through and about the scope a colon switch
     * shares, and neither is readable in the tokens that changed; the check that makes this rule safe
     * is the precondition {@code SwitchCaseRewrite} applies before it changes anything, not this.
     * What this does is keep the rule inside its own vocabulary, so an edit that reached for a piece
     * of the case body while claiming to be restyling a label still fails.
     */
    private static String checkCaseStyleLaw(TokenEdit edit) {
        return checkOnly(
            edit,
            "case labels and their terminators",
            ":",
            "->",
            "case",
            ",",
            "break",
            ";",
            "yield",
            "{",
            "}"
        );
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
            if (!TokenEquivalence.sameToken(expected.get(i), actual.get(i))) {
                return "expected '" + expected.get(i) + "' at position " + i + " but found '" + actual.get(i) + "'";
            }
        }
        if (expected.size() != actual.size()) {
            return "expected " + expected.size() + " entries but found " + actual.size();
        }
        return null;
    }

}
