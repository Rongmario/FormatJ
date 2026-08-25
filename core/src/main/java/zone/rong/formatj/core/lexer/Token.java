package zone.rong.formatj.core.lexer;

/**
 * One lexeme, including whitespace and comments.
 *
 * <p>The lexer emits every character of the input exactly once, so concatenating {@link #text()}
 * over a token list reproduces the source byte for byte. Every later stage depends on that.
 *
 * @param kind lexical category
 * @param text the exact characters of the lexeme
 * @param start offset of the first character, zero-based
 * @param line one-based line the lexeme starts on
 * @param column one-based column the lexeme starts at, counting characters
 */
public record Token(TokenKind kind, String text, int start, int line, int column) {

    /** Offset just past the last character of the lexeme. */
    public int end() {
        return start + text.length();
    }

    public int length() {
        return text.length();
    }

    public boolean isTrivia() {
        return kind.isTrivia();
    }

    public boolean is(TokenKind other) {
        return kind == other;
    }

    /** Whether this token is the given punctuation or keyword, e.g. {@code is("{")}. */
    public boolean is(String lexeme) {
        return text.equals(lexeme);
    }

    @Override
    public String toString() {
        return kind + "(" + text.replace("\n", "\\n") + ")@" + line + ":" + column;
    }

}
