package zone.rong.formatj.core.pipeline;

import zone.rong.formatj.core.cst.GreenNode;
import zone.rong.formatj.core.cst.SyntaxKind;
import zone.rong.formatj.core.lexer.JavaLexer;
import zone.rong.formatj.core.lexer.Token;
import java.util.ArrayList;
import java.util.List;

/**
 * Checks that formatting changed layout only.
 *
 * <p>Two sources are equivalent when their significant tokens, ignoring whitespace, comments, and
 * the optional semicolon after a no-argument enum constant list, match one for one. That semicolon
 * is a style choice, not a program change.
 */
public final class TokenEquivalence {

    private TokenEquivalence() { }

    /** Whether the two sources differ only in whitespace and comments. */
    public static boolean equivalent(String before, String after) {
        return firstDifference(before, after) == null;
    }

    /**
     * The first significant token that differs, or null when the sources are equivalent.
     *
     * @return a human readable description of the difference, for use in a diagnostic
     */
    public static String firstDifference(String before, String after) {
        return firstDifference(lexemes(significantTokens(before)), lexemes(significantTokens(after)));
    }

    /**
     * Tree-aware comparison: the terminator of an enum constant list may appear on one side only
     * when nothing follows the constants. A {@code ;} that introduces members still has to match.
     */
    public static String firstDifference(GreenNode before, GreenNode after) {
        return firstDifference(programTokens(before), programTokens(after));
    }

    private static String firstDifference(List<String> left, List<String> right) {
        int shared = Math.min(left.size(), right.size());
        for (int i = 0; i < shared; i++) {
            if (!left.get(i).equals(right.get(i))) {
                return "token " + (i + 1) + " changed from '" + left.get(i) + "' to '" + right.get(i) + "'";
            }
        }
        if (left.size() != right.size()) {
            return "token count changed from " + left.size() + " to " + right.size();
        }
        return null;
    }

    /** Every token that carries program meaning, in order. */
    public static List<Token> significantTokens(String source) {
        List<Token> significant = new ArrayList<>();
        for (Token token : JavaLexer.tokenize(source)) {
            if (token.kind().isSignificant()) {
                significant.add(token);
            }
        }
        return List.copyOf(significant);
    }

    private static List<String> lexemes(List<Token> tokens) {
        List<String> lexemes = new ArrayList<>(tokens.size());
        for (Token token : tokens) {
            lexemes.add(token.text());
        }
        return lexemes;
    }

    static List<String> programTokens(GreenNode node) {
        List<String> tokens = new ArrayList<>();
        collect(node, tokens);
        return tokens;
    }

    private static void collect(GreenNode node, List<String> tokens) {
        if (node instanceof GreenNode.Leaf leaf) {
            if (leaf.token().token().kind().isSignificant()) {
                tokens.add(leaf.lexeme());
            }
            return;
        }
        if (node.kind() == SyntaxKind.CLASS_BODY) {
            List<GreenNode> children = node.children();
            for (int i = 0; i < children.size(); i++) {
                GreenNode child = children.get(i);
                if (child.kind() == SyntaxKind.ENUM_CONSTANTS) {
                    collectEnumConstants(child, tokens, membersFollow(children, i));
                } else {
                    collect(child, tokens);
                }
            }
            return;
        }
        for (GreenNode child : node.children()) {
            collect(child, tokens);
        }
    }

    private static void collectEnumConstants(GreenNode node, List<String> tokens, boolean membersFollow) {
        List<GreenNode> children = node.children();
        boolean skipTerminator = !membersFollow
                && !children.isEmpty()
                && isSemicolon(children.getLast());
        int last = children.size() - 1;
        for (int i = 0; i < children.size(); i++) {
            if (skipTerminator && i == last) {
                continue;
            }
            collect(children.get(i), tokens);
        }
    }

    private static boolean membersFollow(List<GreenNode> body, int enumConstantsIndex) {
        for (int i = enumConstantsIndex + 1; i < body.size(); i++) {
            if (body.get(i).kind().isMember()) {
                return true;
            }
        }
        return false;
    }

    private static boolean isSemicolon(GreenNode node) {
        return node instanceof GreenNode.Leaf leaf && leaf.lexeme().equals(";");
    }

}
