package zone.rong.formatj.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import zone.rong.formatj.core.lexer.JavaLexer;
import zone.rong.formatj.core.lexer.Token;
import zone.rong.formatj.core.lexer.TokenKind;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

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
    private static final String TEXT_BLOCK_SAMPLE = "String message = \"\"\"\n" + "        unknown\n"
            + "        shape\\s\"\"\";\n";

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
                () -> "unexpected error token in " + tokens.stream().filter(t -> t.kind() == TokenKind.ERROR).toList());
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
            })
    void roundTripsEverySnippet(String source) {
        assertEquals(source, JavaLexer.toSource(JavaLexer.tokenize(source)));
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

}
