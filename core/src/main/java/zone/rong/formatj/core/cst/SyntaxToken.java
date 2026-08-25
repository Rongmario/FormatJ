package zone.rong.formatj.core.cst;

import zone.rong.formatj.core.lexer.Token;
import java.util.List;

/**
 * A significant token together with the trivia attached to it.
 *
 * <p>Whitespace and comments are not part of the grammar, so the parser cannot leave them in the
 * child lists of syntax nodes and still produce a tree the emitter can lay out freely. Instead every
 * piece of trivia is attached to exactly one token: trailing trivia is what follows the token on its
 * own line, leading trivia is everything else, attached to the token that comes after it.
 *
 * <p>That assignment is total and disjoint, which is what keeps the tree lossless.
 *
 * @param leading trivia before the token, in source order
 * @param token the significant token itself
 * @param trailing trivia after the token on the same line
 */
public record SyntaxToken(List<Token> leading, Token token, List<Token> trailing) {

    public SyntaxToken {
        leading = List.copyOf(leading);
        trailing = List.copyOf(trailing);
    }

    public static SyntaxToken of(Token token) {
        return new SyntaxToken(List.of(), token, List.of());
    }

    /** The token's own text, without trivia. */
    public String text() {
        return token.text();
    }

    /** Characters covered including trivia. */
    public int width() {
        int width = token.length();
        for (Token trivia : leading) {
            width += trivia.length();
        }
        for (Token trivia : trailing) {
            width += trivia.length();
        }
        return width;
    }

    /** Appends the exact source text, trivia included. */
    public void appendTo(StringBuilder out) {
        for (Token trivia : leading) {
            out.append(trivia.text());
        }
        out.append(token.text());
        for (Token trivia : trailing) {
            out.append(trivia.text());
        }
    }

    /** Comments attached before this token, in source order. */
    public List<Token> leadingComments() {
        return leading.stream().filter(trivia -> trivia.kind().isComment()).toList();
    }

    /** Comments attached after this token on the same line. */
    public List<Token> trailingComments() {
        return trailing.stream().filter(trivia -> trivia.kind().isComment()).toList();
    }

    /**
     * Blank lines the author left before this token.
     *
     * <p>Counted before the first attached comment rather than before the token itself: a blank line
     * above a documented member belongs to the member, and the comment is part of it.
     */
    public int blankLinesBefore() {
        int newlines = 0;
        for (Token trivia : leading) {
            if (trivia.kind().isComment()) {
                break;
            }
            newlines += countNewlines(trivia.text());
        }
        return Math.max(0, newlines - 1);
    }

    /** Whether the author started a new line before this token. */
    public boolean startsNewLine() {
        for (Token trivia : leading) {
            if (!trivia.kind().isComment() && countNewlines(trivia.text()) > 0) {
                return true;
            }
        }
        return false;
    }

    /** Whether any comment is attached to this token. */
    public boolean hasComments() {
        return !leadingComments().isEmpty() || !trailingComments().isEmpty();
    }

    private static int countNewlines(String text) {
        int count = 0;
        for (int i = 0; i < text.length(); i++) {
            char current = text.charAt(i);
            if (current == '\n') {
                count++;
            } else if (current == '\r' && (i + 1 >= text.length() || text.charAt(i + 1) != '\n')) {
                count++;
            }
        }
        return count;
    }

    @Override
    public String toString() {
        return token.toString();
    }

}
