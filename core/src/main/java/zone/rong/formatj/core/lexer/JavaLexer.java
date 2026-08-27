package zone.rong.formatj.core.lexer;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * A lossless Java lexer: every character of the input lands in exactly one token, whitespace and
 * comments included.
 *
 * <p>Losslessness is the property the whole formatter rests on. Because the token list can always be
 * concatenated back into the original source, the pipeline can verify that formatting changed only
 * layout and never the program, and can fall back to the untouched source whenever it cannot.
 *
 * <p>Unicode escapes ({@code \\u0041} standing in for a source character) are not yet decoded; they
 * are lexed as the characters they are written with, which is lossless but means an identifier
 * spelled with an escape is not recognised as such.
 */
public final class JavaLexer {

    /**
     * Reserved words, including {@code true}/{@code false}/{@code null} and {@code _} (reserved since
     * Java 9). Java 22 reuses {@code _} as an unnamed variable; the lexer still emits it as a keyword.
     */
    private static final Set<String> KEYWORDS =
            Set.of(
                    "abstract",
                    "assert",
                    "boolean",
                    "break",
                    "byte",
                    "case",
                    "catch",
                    "char",
                    "class",
                    "const",
                    "continue",
                    "default",
                    "do",
                    "double",
                    "else",
                    "enum",
                    "extends",
                    "final",
                    "finally",
                    "float",
                    "for",
                    "goto",
                    "if",
                    "implements",
                    "import",
                    "instanceof",
                    "int",
                    "interface",
                    "long",
                    "native",
                    "new",
                    "package",
                    "private",
                    "protected",
                    "public",
                    "return",
                    "short",
                    "static",
                    "strictfp",
                    "super",
                    "switch",
                    "synchronized",
                    "this",
                    "throw",
                    "throws",
                    "transient",
                    "try",
                    "void",
                    "volatile",
                    "while",
                    "_",
                    "true",
                    "false",
                    "null");

    /**
     * Multi-character operators, longest first so that longest-match works by scanning this list in
     * order.
     *
     * <p>Nothing starting with {@code >} beyond the single character is listed. Closing a generic type
     * would otherwise lex as one {@code >>} token that the parser cannot split without losing the
     * exact source text, so {@code >>}, {@code >=} and the rest are recognised by the parser from
     * adjacent {@code >} tokens instead.
     */
    private static final List<String> OPERATORS =
            List.of(
                    "<<=",
                    "...",
                    "->",
                    "::",
                    "++",
                    "--",
                    "&&",
                    "||",
                    "==",
                    "!=",
                    "<=",
                    "+=",
                    "-=",
                    "*=",
                    "/=",
                    "%=",
                    "&=",
                    "|=",
                    "^=",
                    "<<",
                    "+",
                    "-",
                    "*",
                    "/",
                    "%",
                    "=",
                    "<",
                    ">",
                    "!",
                    "~",
                    "?",
                    ":",
                    "&",
                    "|",
                    "^");

    private static final Set<Character> SEPARATORS = Set.of('(', ')', '{', '}', '[', ']', ';', ',', '.', '@');

    private final String source;
    private int offset;
    private int line = 1;
    private int column = 1;

    private JavaLexer(String source) {
        this.source = source;
    }

    /** Lexes the whole source, ending with an {@link TokenKind#END_OF_FILE} token. */
    public static List<Token> tokenize(String source) {
        return new JavaLexer(source).run();
    }

    /** Concatenates a token list back into source text. */
    public static String toSource(List<Token> tokens) {
        StringBuilder text = new StringBuilder();
        for (Token token : tokens) {
            text.append(token.text());
        }
        return text.toString();
    }

    private List<Token> run() {
        List<Token> tokens = new ArrayList<>();
        while (offset < source.length()) {
            tokens.add(nextToken());
        }
        tokens.add(new Token(TokenKind.END_OF_FILE, "", offset, line, column));
        return List.copyOf(tokens);
    }

    private Token nextToken() {
        char first = source.charAt(offset);
        if (isWhitespace(first)) {
            return whitespace();
        }
        if (first == '/' && peekIs(1, '/')) {
            return lineComment();
        }
        if (first == '/' && peekIs(1, '*')) {
            return blockComment();
        }
        if (first == '"' && peekIs(1, '"') && peekIs(2, '"')) {
            return textBlock();
        }
        if (first == '"') {
            return stringLiteral();
        }
        if (first == '\'') {
            return charLiteral();
        }
        if (Character.isDigit(first)
                || (first == '.' && offset + 1 < source.length() && Character.isDigit(source.charAt(offset + 1)))) {
            return numberLiteral();
        }
        if (Character.isJavaIdentifierStart(first)) {
            return identifierOrKeyword();
        }
        if (source.startsWith("...", offset)) {
            // Checked before separators: '.' is a separator, but a varargs ellipsis is one operator.
            return emit(TokenKind.OPERATOR, offset + 3);
        }
        if (SEPARATORS.contains(first)) {
            return emit(TokenKind.SEPARATOR, offset + 1);
        }
        for (String operator : OPERATORS) {
            if (source.startsWith(operator, offset)) {
                return emit(TokenKind.OPERATOR, offset + operator.length());
            }
        }
        return emit(TokenKind.ERROR, offset + 1);
    }

    private Token whitespace() {
        int end = offset;
        while (end < source.length() && isWhitespace(source.charAt(end))) {
            end++;
        }
        return emit(TokenKind.WHITESPACE, end);
    }

    private Token lineComment() {
        int end = offset + 2;
        while (end < source.length() && source.charAt(end) != '\n' && source.charAt(end) != '\r') {
            end++;
        }
        return emit(TokenKind.LINE_COMMENT, end);
    }

    private Token blockComment() {
        boolean javadoc = peekIs(2, '*') && !peekIs(3, '/');
        int end = offset + 2;
        while (end < source.length()
                && !(source.charAt(end) == '*' && end + 1 < source.length() && source.charAt(end + 1) == '/')) {
            end++;
        }
        // An unterminated comment runs to end of file; the parser reports it, the lexer stays lossless.
        end = Math.min(end + 2, source.length());
        return emit(javadoc ? TokenKind.JAVADOC_COMMENT : TokenKind.BLOCK_COMMENT, end);
    }

    private Token textBlock() {
        int end = offset + 3;
        while (end < source.length()) {
            if (source.charAt(end) == '\\') {
                end = Math.min(end + 2, source.length());
                continue;
            }
            if (source.startsWith("\"\"\"", end)) {
                end += 3;
                return emit(TokenKind.TEXT_BLOCK, end);
            }
            end++;
        }
        return emit(TokenKind.ERROR, source.length());
    }

    private Token stringLiteral() {
        int end = offset + 1;
        while (end < source.length()) {
            char current = source.charAt(end);
            if (current == '\\') {
                end = Math.min(end + 2, source.length());
                continue;
            }
            if (current == '"') {
                return emit(TokenKind.STRING_LITERAL, end + 1);
            }
            if (current == '\n' || current == '\r') {
                break;
            }
            end++;
        }
        return emit(TokenKind.ERROR, end);
    }

    private Token charLiteral() {
        int end = offset + 1;
        while (end < source.length()) {
            char current = source.charAt(end);
            if (current == '\\') {
                end = Math.min(end + 2, source.length());
                continue;
            }
            if (current == '\'') {
                return emit(TokenKind.CHAR_LITERAL, end + 1);
            }
            if (current == '\n' || current == '\r') {
                break;
            }
            end++;
        }
        return emit(TokenKind.ERROR, end);
    }

    private Token numberLiteral() {
        int end = offset;
        boolean hexadecimal = source.startsWith("0x", end) || source.startsWith("0X", end);
        boolean binary = source.startsWith("0b", end) || source.startsWith("0B", end);
        if (hexadecimal || binary) {
            end += 2;
        }
        while (end < source.length()) {
            char current = source.charAt(end);
            if (Character.isLetterOrDigit(current) || current == '_' || current == '.') {
                // An exponent sign is part of the literal, but only right after e or p.
                if ((current == 'e' || current == 'E') && !hexadecimal && isSign(end + 1)) {
                    end += 2;
                    continue;
                }
                if ((current == 'p' || current == 'P') && hexadecimal && isSign(end + 1)) {
                    end += 2;
                    continue;
                }
                end++;
                continue;
            }
            break;
        }
        return emit(TokenKind.NUMBER_LITERAL, end);
    }

    private Token identifierOrKeyword() {
        int end = offset;
        while (end < source.length() && Character.isJavaIdentifierPart(source.charAt(end))) {
            end++;
        }
        String text = source.substring(offset, end);
        return emit(KEYWORDS.contains(text) ? TokenKind.KEYWORD : TokenKind.IDENTIFIER, end);
    }

    private boolean isSign(int at) {
        return at < source.length() && (source.charAt(at) == '+' || source.charAt(at) == '-');
    }

    private boolean peekIs(int ahead, char expected) {
        int at = offset + ahead;
        return at < source.length() && source.charAt(at) == expected;
    }

    private static boolean isWhitespace(char c) {
        return c == ' ' || c == '\t' || c == '\n' || c == '\r' || c == '\f';
    }

    private Token emit(TokenKind kind, int end) {
        String text = source.substring(offset, end);
        Token token = new Token(kind, text, offset, line, column);
        advance(text);
        return token;
    }

    private void advance(String text) {
        for (int i = 0; i < text.length(); i++) {
            char current = text.charAt(i);
            if (current == '\n') {
                line++;
                column = 1;
            } else if (current == '\r') {
                // A CRLF pair counts as one line break.
                if (i + 1 < text.length() && text.charAt(i + 1) == '\n') {
                    continue;
                }
                line++;
                column = 1;
            } else {
                column++;
            }
        }
        offset += text.length();
    }

}
