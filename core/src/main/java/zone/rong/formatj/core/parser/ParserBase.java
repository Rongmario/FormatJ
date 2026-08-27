package zone.rong.formatj.core.parser;

import zone.rong.formatj.api.Diagnostic;
import zone.rong.formatj.api.LanguageLevel;
import zone.rong.formatj.core.cst.GreenNode;
import zone.rong.formatj.core.cst.SyntaxKind;
import zone.rong.formatj.core.cst.SyntaxToken;
import zone.rong.formatj.core.lexer.Token;
import zone.rong.formatj.core.lexer.TokenKind;
import java.util.ArrayList;
import java.util.List;

/**
 * Token cursor, trivia attachment and error recovery shared by every layer of the parser.
 *
 * <p>The cursor walks the full token list, whitespace and comments included, so that no character
 * can be dropped. Callers only ever see significant tokens; trivia is attached on the way past.
 */
abstract class ParserBase {

    /** Thrown when a construct does not parse. Caught at a recovery point, never by a caller. */
    static final class ParseFailure extends RuntimeException {

        private final Token token;

        ParseFailure(String message, Token token) {
            super(message, null, false, false);
            this.token = token;
        }

        Token token() {
            return token;
        }

    }

    protected final LanguageLevel languageLevel;
    protected final boolean previewFeatures;

    private final List<Token> tokens;
    private final List<Diagnostic> diagnostics = new ArrayList<>();
    private int index;

    ParserBase(List<Token> tokens, LanguageLevel languageLevel, boolean previewFeatures) {
        this.tokens = tokens;
        this.languageLevel = languageLevel;
        this.previewFeatures = previewFeatures;
    }

    List<Diagnostic> diagnostics() {
        return List.copyOf(diagnostics);
    }

    // ---------------------------------------------------------------- cursor

    /** Position in the raw token list, for rollback during error recovery. */
    protected int mark() {
        return index;
    }

    protected void reset(int mark) {
        index = mark;
    }

    protected boolean atEnd() {
        return peek().kind() == TokenKind.END_OF_FILE;
    }

    /** The next significant token. */
    protected Token peek() {
        return peek(0);
    }

    /** The significant token {@code ahead} positions after the next one. */
    protected Token peek(int ahead) {
        int position = index;
        int seen = 0;
        while (position < tokens.size()) {
            Token token = tokens.get(position);
            if (!token.isTrivia()) {
                if (seen == ahead) {
                    return token;
                }
                seen++;
            }
            position++;
        }
        return tokens.getLast();
    }

    protected boolean at(String lexeme) {
        return peek().is(lexeme);
    }

    protected boolean at(TokenKind kind) {
        return peek().kind() == kind;
    }

    protected boolean atAny(String... lexemes) {
        for (String lexeme : lexemes) {
            if (at(lexeme)) {
                return true;
            }
        }
        return false;
    }

    protected boolean atIdentifier() {
        return peek().kind() == TokenKind.IDENTIFIER;
    }

    /**
     * Whether the token {@code ahead} is the unnamed variable {@code _}.
     *
     * <p>{@code _} is a reserved keyword (Java 9). Java 22 reuses that keyword as an unnamed
     * parameter, local, or pattern binding; it is never reclassified as an identifier.
     */
    protected boolean atUnnamed(int ahead) {
        Token token = peek(ahead);
        return languageLevel.isAtLeast(LanguageLevel.JAVA_22) && token.kind() == TokenKind.KEYWORD && token.is("_");
    }

    protected boolean atUnnamed() {
        return atUnnamed(0);
    }

    /** An identifier, or {@code _} where unnamed variables are allowed. */
    protected boolean atName() {
        return atIdentifier() || atUnnamed();
    }

    /** Whether the next token is the given contextual keyword, e.g. {@code sealed} or {@code when}. */
    protected boolean atContextual(String word) {
        return peek().kind() == TokenKind.IDENTIFIER && peek().is(word);
    }

    /** Consumes the next significant token, attaching its trivia. */
    protected GreenNode advance() {
        List<Token> leading = takeLeading();
        if (index >= tokens.size()) {
            throw new ParseFailure("Unexpected end of file", tokens.getLast());
        }
        Token token = tokens.get(index++);
        List<Token> trailing = takeTrailing();
        return GreenNode.leaf(new SyntaxToken(leading, token, trailing));
    }

    /** Consumes the next token, which must be {@code lexeme}. */
    protected GreenNode expect(String lexeme) {
        if (!at(lexeme)) {
            throw new ParseFailure("Expected '" + lexeme + "' but found '" + describe(peek()) + "'", peek());
        }
        return advance();
    }

    /** Consumes the next token, which must be of {@code kind}. */
    protected GreenNode expect(TokenKind kind, String what) {
        if (peek().kind() != kind) {
            throw new ParseFailure("Expected " + what + " but found '" + describe(peek()) + "'", peek());
        }
        return advance();
    }

    /** Consumes the next token if it is {@code lexeme}, adding it to {@code children}. */
    protected boolean consumeIf(List<GreenNode> children, String lexeme) {
        if (!at(lexeme)) {
            return false;
        }
        children.add(advance());
        return true;
    }

    protected GreenNode identifier() {
        return expect(TokenKind.IDENTIFIER, "an identifier");
    }

    /** An identifier, or the unnamed variable {@code _}. */
    protected GreenNode name() {
        if (atUnnamed()) {
            return advance();
        }
        return identifier();
    }

    /** Fails the current construct. */
    protected ParseFailure fail(String message) {
        return new ParseFailure(message + " but found '" + describe(peek()) + "'", peek());
    }

    protected void report(Diagnostic diagnostic) {
        diagnostics.add(diagnostic);
    }

    private static String describe(Token token) {
        return token.kind() == TokenKind.END_OF_FILE ? "end of file" : token.text();
    }

    // --------------------------------------------------------------- trivia

    private List<Token> takeLeading() {
        List<Token> leading = new ArrayList<>();
        while (index < tokens.size() && tokens.get(index).isTrivia()) {
            leading.add(tokens.get(index++));
        }
        return leading;
    }

    /**
     * Trivia that stays on the line of the token just consumed.
     *
     * <p>Whitespace is only taken when a comment follows it on the same line; otherwise it belongs to
     * the next token, where it carries the blank line count the author wrote.
     */
    private List<Token> takeTrailing() {
        List<Token> trailing = new ArrayList<>();
        List<Token> pending = new ArrayList<>();
        int committed = index;
        while (index < tokens.size()) {
            Token token = tokens.get(index);
            if (token.kind() == TokenKind.WHITESPACE) {
                if (containsNewline(token.text())) {
                    break;
                }
                pending.add(token);
                index++;
                continue;
            }
            if (!token.kind().isComment()) {
                break;
            }
            trailing.addAll(pending);
            pending.clear();
            trailing.add(token);
            index++;
            committed = index;
            if (token.kind() == TokenKind.LINE_COMMENT) {
                break;
            }
        }
        index = committed;
        return trailing;
    }

    private static boolean containsNewline(String text) {
        return text.indexOf('\n') >= 0 || text.indexOf('\r') >= 0;
    }

    // ------------------------------------------------------------- recovery

    /**
     * Wraps the raw tokens from {@code from} up to the current position in an unparsed node.
     *
     * <p>Everything inside comes back out of the formatter exactly as it went in, which is how an
     * unsupported or malformed construct costs the user formatting rather than correctness.
     */
    protected GreenNode unparsedFrom(int from) {
        List<GreenNode> raw = new ArrayList<>(index - from);
        for (int position = from; position < index; position++) {
            raw.add(GreenNode.leaf(SyntaxToken.of(tokens.get(position))));
        }
        return GreenNode.branch(SyntaxKind.UNPARSED, raw);
    }

    /**
     * Skips forward to a point where parsing can sensibly resume: the token after the next semicolon
     * at the current brace depth, or the closing brace of the construct being skipped.
     */
    protected void skipToRecoveryPoint() {
        int depth = 0;
        while (index < tokens.size()) {
            Token token = tokens.get(index);
            if (token.kind() == TokenKind.END_OF_FILE) {
                return;
            }
            index++;
            if (token.isTrivia()) {
                continue;
            }
            if (token.is("{") || token.is("(") || token.is("[")) {
                depth++;
            } else if (token.is(")") || token.is("]")) {
                depth = Math.max(0, depth - 1);
            } else if (token.is("}")) {
                if (depth == 0) {
                    // The brace closes the construct we were inside; leave it for the caller.
                    index--;
                    return;
                }
                depth--;
                if (depth == 0) {
                    return;
                }
            } else if (token.is(";") && depth == 0) {
                return;
            }
        }
    }

    protected static GreenNode branch(SyntaxKind kind, List<GreenNode> children) {
        return GreenNode.branch(kind, children);
    }

}
