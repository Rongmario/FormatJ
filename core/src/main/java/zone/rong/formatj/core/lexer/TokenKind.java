package zone.rong.formatj.core.lexer;

/** The lexical category of a {@link Token}. */
public enum TokenKind {

    /** A run of spaces, tabs and line terminators. */
    WHITESPACE,
    /** A {@code //} comment, excluding the line terminator that ends it. */
    LINE_COMMENT,
    /** A {@code /*} comment that is not Javadoc. */
    BLOCK_COMMENT,
    /** A {@code /**} comment. */
    JAVADOC_COMMENT,
    /** A reserved word such as {@code class}, including {@code true}, {@code false} and {@code null}. */
    KEYWORD,
    /** An identifier, which may be a contextual keyword such as {@code sealed} or {@code when}. */
    IDENTIFIER,
    /** An integer or floating point literal, with any prefix, separator and suffix. */
    NUMBER_LITERAL,
    /** A {@code 'c'} literal. */
    CHAR_LITERAL,
    /** A {@code "..."} literal. */
    STRING_LITERAL,
    /** A {@code """..."""} text block. */
    TEXT_BLOCK,
    /** An operator such as {@code +=} or {@code ->}. */
    OPERATOR,
    /** A separator such as {@code ;} or {@code (}. */
    SEPARATOR,
    /** Input the lexer could not classify. */
    ERROR,
    /** The synthetic token that ends every token list. */
    END_OF_FILE
    ;

    /** Whether tokens of this kind carry no program meaning and are attached to a real token. */
    public boolean isTrivia() {
        return this == WHITESPACE || isComment();
    }

    public boolean isComment() {
        return this == LINE_COMMENT || this == BLOCK_COMMENT || this == JAVADOC_COMMENT;
    }

    /** Whether tokens of this kind participate in the token-equivalence check after formatting. */
    public boolean isSignificant() {
        return !isTrivia() && this != END_OF_FILE;
    }

}
