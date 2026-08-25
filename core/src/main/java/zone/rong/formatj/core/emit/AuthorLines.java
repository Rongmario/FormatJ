package zone.rong.formatj.core.emit;

import zone.rong.formatj.core.cst.GreenNode;
import zone.rong.formatj.core.cst.SyntaxToken;
import zone.rong.formatj.core.lexer.Token;

/**
 * Whether the author wrote a construct on one line.
 *
 * <p>A group of rules in the catalogue are all variations on one question: the author put this on a
 * single line, so may the formatter leave it there — or, read the other way round, the author split
 * this over several lines, so may the formatter join them? {@code keep-simple-blocks-inline},
 * {@code keep-simple-methods-on-one-line}, {@code keep-simple-lambdas-on-one-line},
 * {@code keep-simple-classes-on-one-line}, {@code keep-simple-pattern-inline},
 * {@code null-default-on-one-line}, {@code keep-line-break-after-open-paren},
 * {@code never-join-lines} and {@code wrapping.throws-clause = preserve} differ only in which
 * construct they ask about and which option turns them on. The question itself is answered here,
 * once, so that they cannot disagree about what "on one line" means.
 *
 * <p>The answer is about line terminators and nothing else. A node is on one line when no line
 * terminator falls between its first significant token and its last character: comments the author
 * kept inline count as part of that line, a comment that ended a line does not, and a token that
 * carries its own line structure — a text block — never does. Trivia before the first token is
 * excluded, because that whitespace separates the node from what came before it rather than saying
 * anything about the node.
 *
 * <p>Nothing here asks whether a construct is <em>simple</em>, only whether it was written on one
 * line. How long a line may be is already the layout engine's decision, made against
 * {@code wrapping.max-line-length} with the whole surrounding line in hand, and second-guessing it
 * here with a statement count or a character budget would give two answers to one question.
 */
final class AuthorLines {

    private AuthorLines() { }

    /** Whether the author wrote every token of this node on one line. */
    static boolean onOneLine(GreenNode node) {
        return scan(node, new boolean[] {true});
    }

    /** Whether the author put a line break anywhere inside this node. */
    static boolean brokeInside(GreenNode node) {
        return !onOneLine(node);
    }

    /**
     * Whether the author broke the line immediately after this node's first token.
     *
     * <p>The opening delimiter of a list is the node's first token, so this is what
     * {@code preservation.keep-line-break-after-open-paren} asks about.
     */
    static boolean brokeAfterFirstToken(GreenNode node) {
        SyntaxToken second = tokenAt(node, 1);
        return second != null && breaksBefore(second);
    }

    /**
     * @param first holds whether the next leaf reached is the node's first, whose leading trivia sits
     *     outside the node and is therefore not part of the question
     */
    private static boolean scan(GreenNode node, boolean[] first) {
        if (node instanceof GreenNode.Leaf leaf) {
            SyntaxToken token = leaf.token();
            if (!first[0] && breaksBefore(token)) {
                return false;
            }
            first[0] = false;
            if (hasNewline(token.text())) {
                return false;
            }
            for (Token trivia : token.trailing()) {
                if (hasNewline(trivia.text())) {
                    return false;
                }
            }
            return true;
        }
        for (GreenNode child : node.children()) {
            if (!scan(child, first)) {
                return false;
            }
        }
        return true;
    }

    /** Whether anything attached before this token ended a line, comments included. */
    private static boolean breaksBefore(SyntaxToken token) {
        for (Token trivia : token.leading()) {
            if (hasNewline(trivia.text())) {
                return true;
            }
        }
        return false;
    }

    /** The token at {@code index} in this node's token order, or {@code null} if there is none. */
    private static SyntaxToken tokenAt(GreenNode node, int index) {
        return tokenAt(node, new int[] {index});
    }

    private static SyntaxToken tokenAt(GreenNode node, int[] remaining) {
        if (node instanceof GreenNode.Leaf leaf) {
            return remaining[0]-- == 0 ? leaf.token() : null;
        }
        for (GreenNode child : node.children()) {
            SyntaxToken found = tokenAt(child, remaining);
            if (found != null) {
                return found;
            }
        }
        return null;
    }

    private static boolean hasNewline(String text) {
        return text.indexOf('\n') >= 0 || text.indexOf('\r') >= 0;
    }

}
