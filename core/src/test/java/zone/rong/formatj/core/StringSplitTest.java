package zone.rong.formatj.core;

import org.junit.jupiter.api.Test;
import zone.rong.formatj.api.FormatRequest;
import zone.rong.formatj.api.FormatResult;
import zone.rong.formatj.api.Preset;
import zone.rong.formatj.api.Style;
import zone.rong.formatj.api.rules.WrappingRules;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class StringSplitTest {

    private static final String LONG = "This is a very long string literal that keeps going and going well past the " +
        "one hundred and twenty column limit";

    private static String format(String source, Style style) {
        FormatResult result = FormatJ.newFormatter().style(style).build().format(FormatRequest.of(source));
        assertFalse(result.hasErrors(), () -> result.diagnostics().toString());
        assertTrue(result.diagnostics().isEmpty(), () -> result.diagnostics().toString());
        return result.text();
    }

    private static String method(String body) {
        return "class A {\n\n    void f() {\n        " + body + "\n    }\n\n}\n";
    }

    @Test
    void aLongLiteralSplitsAfterASpace() {
        String formatted = format(method("String a = \"" + LONG + "\";"), Style.defaults());

        assertEquals(
            method(
                "String a = \"This is a very long string literal that keeps going and going well past the one " +
                    "hundred and \" +\n            \"twenty column limit\";"
            ),
            formatted
        );
        assertEquals(formatted, format(formatted, Style.defaults()));
    }

    @Test
    void piecesFollowTheOperatorPosition() {
        Style google = Style.preset(Preset.GOOGLE).build();
        String formatted = format("class A {\n  String a = \"" + LONG + "\";\n}\n", google);

        assertEquals(
            "class A {\n  String a = \"This is a very long string literal that keeps going and going well past " +
                "the one \"\n      + \"hundred and twenty column limit\";\n}\n",
            formatted
        );
        assertTrue(formatted.lines().allMatch(line -> line.length() <= 100), formatted);
    }

    @Test
    void aLiteralThatJoinsARunExtendsIt() {
        String formatted = format(method("String a = x + \"" + LONG + "\" + y;"), Style.defaults());

        assertTrue(formatted.lines().allMatch(line -> line.length() <= 120), formatted);
        assertEquals(formatted, format(formatted, Style.defaults()));
    }

    @Test
    void aNewlineEscapeIsASplitPoint() {
        String text = "first line of a snippet that is fairly long and goes on\\nsecond line of a snippet that is " +
            "long too\\n";
        String formatted = format(method("String a = \"" + text + "\";"), Style.defaults());

        assertTrue(formatted.contains("goes on\\n\" +"), formatted);
    }

    @Test
    void aLiteralWherePlusWouldBindToItsNeighbourStaysWhole() {
        String receiver = method("int n = \"" + LONG + "\".length();");
        String cast = method("Object o = (Object) \"" + LONG + "\";");

        assertTrue(format(receiver, Style.defaults()).contains("\"" + LONG + "\""));
        assertTrue(format(cast, Style.defaults()).contains("\"" + LONG + "\""));
    }

    @Test
    void piecesFitAndNeverShareALineInAnyContext() {
        String w = '"' + LONG + " and then some more words to make it longer still\"";
        String source = """
            @Ann(value = W, other = W)
            class A {
                static final String F = W;
                String g = c ? W : W;
                String[] h = { W, "short", W };

                enum E {
                    X(W), Y(W, 1);
                    E(String s) { }
                    E(String s, int i) { }
                }

                String f(int k) {
                    log(1, W, 2, W);
                    outer(inner(W), 2);
                    String s = "prefix " + name + W + suffix;
                    s += W;
                    builder.append(W).append(W).append(x);
                    run(NAME, () -> {
                        log(2, W);
                        done();
                    });
                    run(NAME, new Runnable() {
                        public void run() {
                            log(W);
                            done();
                        }
                    });
                    Object o = switch (k) {
                        case 1 -> W;
                        default -> {
                            yield W;
                        }
                    };
                    assert k > 0 : W;
                    log("a\\nb\\nTAIL);
                    return c ? W : W;
                }
            }
            """.replace("TAIL", w.substring(1)).replace("W", w);

        for (Preset preset : Preset.values()) {
            Style style = Style.preset(preset).build();
            int max = style.get(WrappingRules.MAX_LINE_LENGTH);
            String formatted = format(source, style);

            assertTrue(formatted.lines().allMatch(line -> line.length() <= max), formatted);
            assertTrue(formatted.lines().noneMatch(line -> line.matches(".*\"\\s*\\+\\s*\".*")), formatted);
            assertEquals(formatted, format(formatted, style));
        }
    }

    @Test
    void theRuleCanBeTurnedOff() {
        Style style = Style.builder().set(WrappingRules.SPLIT_LONG_STRINGS, false).build();

        assertTrue(format(method("String a = \"" + LONG + "\";"), style).contains("\"" + LONG + "\""));
    }

}
