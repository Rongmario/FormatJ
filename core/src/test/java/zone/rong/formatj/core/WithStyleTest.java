package zone.rong.formatj.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import zone.rong.formatj.api.Style;
import zone.rong.formatj.api.rules.RecordRules;
import zone.rong.formatj.api.rules.RecordWithStyle;
import org.junit.jupiter.api.Test;

/**
 * {@code records.with-style}, which is layout rather than a rewrite.
 *
 * <p>The three values choose between a with-block on one line and the same block spread over
 * several. Those are the same tokens either way, so the rule has nothing to declare to the rewrite
 * stage and no edit law to be measured against; it belongs to the emitter with the rest of the
 * one-line questions.
 */
class WithStyleTest {

    private static final String INLINE = "class T {\n\n    void run() {\n        var q = p with { x = 1; };\n"
            + "    }\n\n}\n";

    private static final String SPREAD = "class T {\n\n    void run() {\n        var q = p with {\n"
            + "            x = 1;\n        };\n    }\n\n}\n";

    private static String format(String source, RecordWithStyle style) {
        return FormatJ.newFormatter()
                .style(Style.builder().set(RecordRules.WITH_STYLE, style).build())
                .previewFeatures(true)
                .build()
                .format(source);
    }

    @Test
    void inlineWhenShortJoinsABlockTheAuthorSpread() {
        String formatted = format(SPREAD, RecordWithStyle.INLINE_WHEN_SHORT);
        assertTrue(formatted.contains("p with { x = 1; }"), formatted);
    }

    @Test
    void alwaysBlockSpreadsABlockTheAuthorWroteInline() {
        String formatted = format(INLINE, RecordWithStyle.ALWAYS_BLOCK);
        assertTrue(formatted.contains("p with {\n            x = 1;\n        }"), formatted);
    }

    @Test
    void preserveKeepsWhicheverTheAuthorWrote() {
        assertTrue(format(INLINE, RecordWithStyle.PRESERVE).contains("p with { x = 1; }"));
        assertTrue(format(SPREAD, RecordWithStyle.PRESERVE).contains("p with {\n"));
    }

    @Test
    void aBlockTooLongForItsLineBreaksWhateverTheRuleAsks() {
        String source = "class T {\n\n    void run() {\n        var q = p with { someRatherLongComponentName = "
                + "aRatherLongExpressionIndeed + andAnotherOneOfSimilarLength + andAYetLongerThirdOne; };"
                + "\n    }\n\n}\n";
        String formatted = format(source, RecordWithStyle.INLINE_WHEN_SHORT);
        assertTrue(formatted.contains("p with {\n"), formatted);
    }

    @Test
    void eachValueSettlesAfterOnePass() {
        for (RecordWithStyle style : RecordWithStyle.values()) {
            for (String source : new String[] {INLINE, SPREAD}) {
                String once = format(source, style);
                assertEquals(once, format(once, style), style.name());
            }
        }
    }

}
