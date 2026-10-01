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
 * <p>Lexing reads the character stream after Unicode escape translation, as required by JLS 3.2 and
 * 3.3. Each translated character retains its raw source span, so {@link Token#text()} and token
 * offsets still describe the source exactly as written.
 */
public final class JavaLexer {

    /**
     * Reserved words, including {@code true}/{@code false}/{@code null} and {@code _} (reserved since
     * Java 9). Java 22 reuses {@code _} as an unnamed variable; the lexer still emits it as a keyword.
     */
    private static final Set<String> KEYWORDS = Set.of(
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
    private static final List<String> OPERATORS = List.of(
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
    private final TranslatedSource input;

    private int offset;
    private int line = 1;
    private int column = 1;

    private JavaLexer(String source) {
        this.source = source;
        this.input = TranslatedSource.translate(source);
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
        while (offset < input.length()) {
            tokens.add(nextToken());
        }
        tokens.add(new Token(TokenKind.END_OF_FILE, "", source.length(), line, column));
        return List.copyOf(tokens);
    }

    private Token nextToken() {
        char first = input.charAt(offset);
        if (offset == 0 && first == '\uFEFF') {
            // A UTF-8 BOM only ever appears as the file's first character; treat it as trivia so
            // it round-trips like any other leading whitespace instead of becoming an error token.
            return emit(TokenKind.WHITESPACE, offset + 1);
        }
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
        if (isDigit(first) || (first == '.' && offset + 1 < input.length() && isDigit(input.charAt(offset + 1)))) {
            return numberLiteral();
        }
        if (Character.isJavaIdentifierStart(input.codePointAt(offset))) {
            return identifierOrKeyword();
        }
        if (input.startsWith("...", offset)) {
            // Checked before separators: '.' is a separator, but a varargs ellipsis is one operator.
            return emit(TokenKind.OPERATOR, offset + 3);
        }
        if (SEPARATORS.contains(first)) {
            return emit(TokenKind.SEPARATOR, offset + 1);
        }
        for (String operator : OPERATORS) {
            if (input.startsWith(operator, offset)) {
                return emit(TokenKind.OPERATOR, offset + operator.length());
            }
        }
        return emit(TokenKind.ERROR, offset + 1);
    }

    private Token whitespace() {
        int end = offset;
        while (end < input.length() && isWhitespace(input.charAt(end))) {
            end++;
        }
        return emit(TokenKind.WHITESPACE, end);
    }

    private Token lineComment() {
        int end = offset + 2;
        while (end < input.length() && input.charAt(end) != '\n' && input.charAt(end) != '\r') {
            end++;
        }
        return emit(TokenKind.LINE_COMMENT, end);
    }

    private Token blockComment() {
        boolean javadoc = peekIs(2, '*') && !peekIs(3, '/');
        int end = offset + 2;
        while (end < input.length()
                && !(input.charAt(end) == '*' && end + 1 < input.length() && input.charAt(end + 1) == '/')) {
            end++;
        }
        // An unterminated comment runs to end of file; the parser reports it, the lexer stays lossless.
        end = Math.min(end + 2, input.length());
        return emit(javadoc ? TokenKind.JAVADOC_COMMENT : TokenKind.BLOCK_COMMENT, end);
    }

    private Token textBlock() {
        int end = offset + 3;
        while (end < input.length()) {
            if (input.charAt(end) == '\\') {
                end = Math.min(end + 2, input.length());
                continue;
            }
            if (input.startsWith("\"\"\"", end)) {
                end += 3;
                return emit(TokenKind.TEXT_BLOCK, end);
            }
            end++;
        }
        return emit(TokenKind.ERROR, input.length());
    }

    private Token stringLiteral() {
        int end = offset + 1;
        while (end < input.length()) {
            char current = input.charAt(end);
            if (current == '\\') {
                end = Math.min(end + 2, input.length());
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
        while (end < input.length()) {
            char current = input.charAt(end);
            if (current == '\\') {
                end = Math.min(end + 2, input.length());
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
        boolean hexadecimal = input.startsWith("0x", end) || input.startsWith("0X", end);
        boolean binary = input.startsWith("0b", end) || input.startsWith("0B", end);
        if (hexadecimal || binary) {
            end += 2;
        }
        while (end < input.length()) {
            char current = input.charAt(end);
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
        while (end < input.length()) {
            int codePoint = input.codePointAt(end);
            if (!Character.isJavaIdentifierPart(codePoint)) {
                break;
            }
            end += Character.charCount(codePoint);
        }
        String decoded = input.text(offset, end);
        return emit(KEYWORDS.contains(decoded) ? TokenKind.KEYWORD : TokenKind.IDENTIFIER, end);
    }

    private boolean isSign(int at) {
        return at < input.length() && (input.charAt(at) == '+' || input.charAt(at) == '-');
    }

    private boolean peekIs(int ahead, char expected) {
        int at = offset + ahead;
        return at < input.length() && input.charAt(at) == expected;
    }

    private static boolean isDigit(char c) {
        return c >= '0' && c <= '9';
    }

    private static boolean isWhitespace(char c) {
        return c == ' ' || c == '\t' || c == '\n' || c == '\r' || c == '\f';
    }

    private Token emit(TokenKind kind, int end) {
        int rawStart = input.rawOffset(offset);
        String text = input.rawText(offset, end);
        Token token = new Token(kind, text, rawStart, line, column);
        advance(end);
        return token;
    }

    private void advance(int end) {
        for (int i = offset; i < end; i++) {
            char current = input.charAt(i);
            if (current == '\n') {
                line++;
                column = 1;
            } else if (current == '\r') {
                line++;
                column = 1;
                if (i + 1 < end && input.charAt(i + 1) == '\n') {
                    i++;
                }
            } else {
                column++;
            }
        }
        offset = end;
    }

}
