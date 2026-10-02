package zone.rong.formatj.core;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import zone.rong.formatj.core.lexer.JavaLexer;
import zone.rong.formatj.core.lexer.Token;
import zone.rong.formatj.core.lexer.TokenKind;
import zone.rong.formatj.core.lexer.UnicodeEscapes;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class JavaLexerTest {

    private static final String MODERN_SAMPLE = """
            package zone.rong.formatj.sample;

            import java.util.List;

            /** A record with a compact constructor. */
            public sealed interface Shape permits Circle, Square {

                record Circle(double radius) implements Shape {
                
                    Circle {
                        if (radius < 0) {
                            throw new IllegalArgumentException("negative");
                        }
                    }
                    
                }

                record Square(double side) implements Shape { }

                static String describe(Object value) {
                    return switch (value) {
                        case Circle(double r) when r > 10 -> "big circle";
                        case Circle c -> "circle";
                        case Square(double s) -> "square of " + s;
                        case null, default -> "unknown shape";
                    };
                }
            }
            """;

    // A text block cannot be written inside a text block, so this sample is a plain string.
    private static final String TEXT_BLOCK_SAMPLE = "String message = \"\"\"\n" + "        unknown\n" +
        "        shape\\s\"\"\";\n";

    private static String escape(String body) {
        return "\\" + body;
    }

    @Test
    void roundTripsModernSyntax() {
        List<Token> tokens = JavaLexer.tokenize(MODERN_SAMPLE);
        assertEquals(MODERN_SAMPLE, JavaLexer.toSource(tokens));
    }

    @Test
    void classifiesTextBlocksAsOneToken() {
        List<Token> tokens = JavaLexer.tokenize(TEXT_BLOCK_SAMPLE);
        assertEquals(TEXT_BLOCK_SAMPLE, JavaLexer.toSource(tokens));
        long textBlocks = tokens.stream().filter(t -> t.kind() == TokenKind.TEXT_BLOCK).count();
        assertEquals(1, textBlocks);
    }

    @Test
    void reportsNoErrorTokensForValidSource() {
        List<Token> tokens = JavaLexer.tokenize(MODERN_SAMPLE);
        assertTrue(
            tokens.stream().noneMatch(t -> t.kind() == TokenKind.ERROR),
            () -> "unexpected error token in " + tokens.stream().filter(t -> t.kind() == TokenKind.ERROR).toList()
        );
    }

    @ParameterizedTest
    @ValueSource(
        strings = {
            "",
            "class A {}",
            "int x = 0x1_FFp2f;",
            "int y = 0b1010_1010;",
            "double d = 1.5e-3;",
            "char c = '\\n';",
            "String s = \"a\\\"b\";",
            "// trailing comment",
            "/* unterminated",
            "/** javadoc */ class A {}",
            "var f = (a, b) -> a + b;",
            "x >>>= 2;",
            "a ? b : c;",
            "@Deprecated class A {}",
            "record R(int a, int b) {}",
            "\r\nclass A {}\r\n"
        }
    )
    void roundTripsEverySnippet(String source) {
        assertEquals(source, JavaLexer.toSource(JavaLexer.tokenize(source)));
    }

    @Test
    void aLoneUnderscoreIsTheReservedKeyword() {
        List<Token> tokens = JavaLexer.tokenize("_ -> __");
        List<Token> significant = tokens.stream().filter(token -> token.kind().isSignificant()).toList();

        assertEquals(TokenKind.KEYWORD, significant.get(0).kind());
        assertEquals("_", significant.get(0).text());
        assertEquals(TokenKind.OPERATOR, significant.get(1).kind());
        assertEquals("->", significant.get(1).text());
        assertEquals(TokenKind.IDENTIFIER, significant.get(2).kind());
        assertEquals("__", significant.get(2).text());
    }

    @Test
    void anEvenNumberOfBackslashesIsNotAnEscape() {
        // Two literal backslashes, not one escape: the second is preceded by an odd count (JLS 3.3)
        // and so is ineligible even though a 'u' and four hex digits follow it.
        String source = "\\" + "\\" + "u0041";
        List<Token> tokens = JavaLexer.tokenize(source);
        assertEquals(source, JavaLexer.toSource(tokens));
        List<Token> significant = tokens.stream().filter(t -> t.kind().isSignificant()).toList();
        assertEquals(TokenKind.ERROR, significant.get(0).kind());
        assertEquals(TokenKind.ERROR, significant.get(1).kind());
        assertEquals(TokenKind.IDENTIFIER, significant.get(2).kind());
        assertEquals("u0041", significant.get(2).text());
    }

    @Test
    void multipleUsAreAllowedInAnEscape() {
        String source = "\\" + "uuuu0041";
        List<Token> tokens = JavaLexer.tokenize(source);
        assertEquals(source, JavaLexer.toSource(tokens));
        Token identifier = tokens.stream().filter(t -> t.kind() == TokenKind.IDENTIFIER).findFirst().orElseThrow();
        assertEquals(source, identifier.text());
        assertEquals("A", identifier.decodedText());
    }

    @Test
    void aMalformedEscapeDoesNotCrashAndRoundTrips() {
        String source = escape("uuuu12XY");
        List<Token> tokens = JavaLexer.tokenize(source);
        assertEquals(source, JavaLexer.toSource(tokens));
        assertEquals(TokenKind.ERROR, tokens.getFirst().kind());
        assertEquals("\\", tokens.getFirst().text());
    }

    @Test
    void anEscapedKeywordIsClassifiedAndDecodedAsThatKeyword() {
        // i decodes to 'i', so this identifier reads "if".
        String source = "\\" + "u0069" + "f";
        Token token = JavaLexer.tokenize(source).getFirst();
        assertEquals(TokenKind.KEYWORD, token.kind());
        assertEquals(source, token.text());
        assertEquals("if", token.decodedText());
        assertTrue(token.is("if"));
    }

    @Test
    void tracksLineAndColumn() {
        List<Token> tokens = JavaLexer.tokenize("class A {\n    int x;\n}\n");
        Token intKeyword = tokens.stream()
            .filter(t -> t.kind() == TokenKind.KEYWORD && t.is("int"))
            .findFirst()
            .orElseThrow();
        assertEquals(2, intKeyword.line());
        assertEquals(5, intKeyword.column());
    }

    @Test
    void escapesCanOpenAndEndComments() {
        String lineSource = "/" + escape("u002f") + " comment" + escape("u000a") + "class B {}";
        List<Token> lineTokens = JavaLexer.tokenize(lineSource);
        assertEquals(lineSource, JavaLexer.toSource(lineTokens));
        Token lineComment = lineTokens.stream()
            .filter(token -> token.kind() == TokenKind.LINE_COMMENT)
            .findFirst()
            .orElseThrow();
        assertEquals("/" + escape("u002f") + " comment", lineComment.text());
        Token classKeyword = lineTokens.stream().filter(token -> token.is("class")).findFirst().orElseThrow();
        assertEquals(2, classKeyword.line());

        String blockSource = "/* comment *" + escape("u002f") + " class A {}";
        List<Token> blockTokens = JavaLexer.tokenize(blockSource);
        assertEquals(blockSource, JavaLexer.toSource(blockTokens));
        Token blockComment = blockTokens.getFirst();
        assertEquals(TokenKind.BLOCK_COMMENT, blockComment.kind());
        assertEquals("/* comment *" + escape("u002f"), blockComment.text());
        assertTrue(blockTokens.stream().anyMatch(token -> token.is("class")));
    }

    @Test
    void escapesCanSpellQuotesOperatorsAndSeparators() {
        String quoted = escape("u0022") + "text" + escape("u0022");
        Token string = JavaLexer.tokenize(quoted).getFirst();
        assertEquals(TokenKind.STRING_LITERAL, string.kind());
        assertEquals(quoted, string.text());
        assertEquals("\"text\"", string.decodedText());

        String source = "a " + escape("u002b") + escape("u003d") + " b" + escape("u003b");
        List<Token> tokens = JavaLexer.tokenize(source);
        assertEquals(source, JavaLexer.toSource(tokens));
        Token operator = tokens.stream().filter(token -> token.kind() == TokenKind.OPERATOR).findFirst().orElseThrow();
        assertEquals(escape("u002b") + escape("u003d"), operator.text());
        assertEquals("+=", operator.decodedText());
        assertEquals(source.indexOf(escape("u002b")), operator.start());
        assertEquals(operator.start() + operator.text().length(), operator.end());
        assertTrue(tokens.stream().anyMatch(token -> token.kind() == TokenKind.SEPARATOR && token.is(";")));
    }

    @Test
    void escapedQuotesCanDelimitATextBlock() {
        String quotes = escape("u0022").repeat(3);
        String source = quotes + escape("u000a") + "text" + escape("u000a") + quotes;
        Token textBlock = JavaLexer.tokenize(source).getFirst();

        assertEquals(TokenKind.TEXT_BLOCK, textBlock.kind());
        assertEquals(source, textBlock.text());
        assertEquals("\"\"\"\ntext\n\"\"\"", textBlock.decodedText());
        assertEquals(source, JavaLexer.toSource(JavaLexer.tokenize(source)));
    }

    @Test
    void escapedCrLfIsOneLineTerminator() {
        String source = "// comment" + escape("u000d") + escape("u000a") + "class A {}";
        List<Token> tokens = JavaLexer.tokenize(source);
        assertEquals(source, JavaLexer.toSource(tokens));
        Token whitespace = tokens.stream()
            .filter(token -> token.kind() == TokenKind.WHITESPACE)
            .findFirst()
            .orElseThrow();
        assertEquals(escape("u000d") + escape("u000a"), whitespace.text());
        assertEquals("\r\n", whitespace.decodedText());
        assertEquals(1, whitespace.lineTerminatorCount());
        Token classKeyword = tokens.stream().filter(token -> token.is("class")).findFirst().orElseThrow();
        assertEquals(2, classKeyword.line());
        assertEquals(1, classKeyword.column());
    }

    @Test
    void supplementaryIdentifierCharactersUseBothTranslatedSurrogates() {
        String source = escape("uD835") + escape("uDC82") + "Name";
        Token identifier = JavaLexer.tokenize(source).getFirst();
        assertEquals(TokenKind.IDENTIFIER, identifier.kind());
        assertEquals(source, identifier.text());
        assertEquals(new String(Character.toChars(0x1D482)) + "Name", identifier.decodedText());

        String literalSource = new String(Character.toChars(0x1D482)) + "Name";
        assertEquals(TokenKind.IDENTIFIER, JavaLexer.tokenize(literalSource).getFirst().kind());
    }

    @Test
    void anEscapeProducedBackslashAffectsFollowingEscapeEligibility() {
        String source = "\"" + escape("u005c") + escape("u005c") + escape("u006e") + "\"";
        Token string = JavaLexer.tokenize(source).getFirst();
        assertEquals(TokenKind.STRING_LITERAL, string.kind());
        assertEquals(source, string.text());
        assertEquals("\"\\\\n\"", string.decodedText());
    }

    @Test
    void eligibilityUsesTheTranslatedBackslashRun() {
        String separated = "\\" + "\\" + "u2122=" + escape("u2122");
        assertEquals("\\" + "\\" + "u2122=™", UnicodeEscapes.decode(separated));

        String threeBackslashes = "\\" + "\\" + escape("u006e");
        assertEquals("\\" + "\\" + "n", UnicodeEscapes.decode(threeBackslashes));
    }

    @Test
    void escapeTranslationDoesNotRecurse() {
        String source = escape("u005c") + "u005a";
        List<Token> tokens = JavaLexer.tokenize(source);
        assertEquals(source, JavaLexer.toSource(tokens));
        assertEquals("\\u005a", UnicodeEscapes.decode(source));
        assertEquals(TokenKind.ERROR, tokens.getFirst().kind());
        assertEquals(TokenKind.IDENTIFIER, tokens.get(1).kind());
        assertEquals("u005a", tokens.get(1).text());
    }

}
