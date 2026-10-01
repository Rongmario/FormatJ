package zone.rong.formatj.core.rewrite;

import zone.rong.formatj.core.cst.GreenNode;
import zone.rong.formatj.core.cst.SyntaxToken;
import zone.rong.formatj.core.lexer.Token;
import zone.rong.formatj.core.lexer.TokenKind;

/**
 * The tokens a rewrite makes up, and the question every rewrite has to ask before deleting one.
 *
 * <p>A token the rewrite stage created has no position in the original stream, which is the point:
 * an edit is always expressed against the tokens that were already there, and a made-up token is
 * what the edit says to insert rather than a coordinate it can refer to.
 */
final class Synthetic {

    private Synthetic() { }

    /** A separator such as a brace or a parenthesis. */
    static GreenNode separator(String lexeme) {
        return leaf(TokenKind.SEPARATOR, lexeme);
    }

    /** An operator such as {@code ->}. */
    static GreenNode operator(String lexeme) {
        return leaf(TokenKind.OPERATOR, lexeme);
    }

    /** A reserved word such as {@code break}. */
    static GreenNode keyword(String lexeme) {
        return leaf(TokenKind.KEYWORD, lexeme);
    }

    /** A contextual keyword, which the lexer reads as an identifier. */
    static GreenNode contextualKeyword(String lexeme) {
        return leaf(TokenKind.IDENTIFIER, lexeme);
    }

    private static GreenNode leaf(TokenKind kind, String lexeme) {
        return GreenNode.leaf(SyntaxToken.of(Token.synthetic(kind, lexeme)));
    }

    /**
     * Whether deleting this node would take a comment with it.
     *
     * <p>Asked of the individual tokens a rewrite is about to remove. A comment riding on one of them
     * has no unambiguous home once it is gone, so the rewrite declines rather than rehoming it
     * somewhere the author did not put it. Null is not carrying anything, which lets a caller ask
     * about a token that may or may not be part of the shape it is looking at.
     */
    static boolean carriesComments(GreenNode node) {
        if (node == null) {
            return false;
        }
        if (node instanceof GreenNode.Leaf leaf) {
            return leaf.token().hasComments();
        }
        for (GreenNode child : node.children()) {
            if (carriesComments(child)) {
                return true;
            }
        }
        return false;
    }

}
