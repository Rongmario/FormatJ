package zone.rong.formatj.core.pipeline;

import zone.rong.formatj.api.rules.BraceRules;
import zone.rong.formatj.core.cst.GreenNode;
import zone.rong.formatj.core.cst.ProgramTokens;
import zone.rong.formatj.core.cst.SyntaxToken;
import zone.rong.formatj.core.lexer.Token;
import zone.rong.formatj.core.rewrite.TokenEdit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

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
        String lawProblem = checkEditLaws(edits);
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
    private static String checkEditLaws(List<TokenEdit> edits) {
        for (TokenEdit edit : edits) {
            if (edit.authority() == BraceRules.IF_ELSE
                    || edit.authority() == BraceRules.FOR_LOOP
                    || edit.authority() == BraceRules.WHILE_LOOP) {
                String problem = checkBraceLaw(edit);
                if (problem != null) {
                    return problem;
                }
            }
        }
        return checkBracesBalance(edits);
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
     */
    private static String checkCommentsSurvived(GreenNode before, GreenNode after) {
        String difference = firstDifference(comments(before), comments(after));
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
