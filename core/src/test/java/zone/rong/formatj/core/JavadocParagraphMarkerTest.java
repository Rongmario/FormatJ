package zone.rong.formatj.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import zone.rong.formatj.api.FormatRequest;
import zone.rong.formatj.api.FormatResult;
import zone.rong.formatj.api.LanguageLevel;
import zone.rong.formatj.api.Style;
import zone.rong.formatj.api.StyleBuilder;
import zone.rong.formatj.api.rules.JavadocClosingTagForm;
import zone.rong.formatj.api.rules.JavadocOpeningTagPosition;
import zone.rong.formatj.api.rules.JavadocRules;
import zone.rong.formatj.api.rules.WrappingRules;
import java.util.function.Consumer;
import org.junit.jupiter.api.Test;

class JavadocParagraphMarkerTest {

    private static String format(Consumer<StyleBuilder> rules, String body) {
        StyleBuilder builder = Style.builder();
        rules.accept(builder);
        FormatResult result = FormatJ.newFormatter()
                .style(builder.build())
                .languageLevel(LanguageLevel.LATEST)
                .build()
                .format(FormatRequest.of("class T {\n\n" + body + "\n}\n").withName("T.java"));
        assertFalse(result.hasErrors(), () -> result.diagnostics().toString());
        return result.text();
    }

    @Test
    void closingFormPreservesByDefault() {
        String formatted =
                format(
                        style -> { },
                        "    /**\n     * First.\n     * <p/>\n     * Second.\n     */\n    void f() { }\n");
        assertTrue(formatted.contains("     * <p/>\n"), formatted);
    }

    @Test
    void closingFormSlashFirstConvertsSelfClosingButLeavesOpeners() {
        String formatted =
                format(
                        style -> style.set(JavadocRules.CLOSING_TAG_FORM, JavadocClosingTagForm.SLASH_FIRST),
                        "    /**\n     * First.\n     * <p/>\n     * Second.\n     * <p>\n     * Third.\n     */\n    void f() { }\n");
        assertTrue(formatted.contains("     * </p>\n"), formatted);
        assertTrue(formatted.contains("     * <p>\n"), formatted);
        assertFalse(formatted.contains("<p/>"), formatted);
    }

    @Test
    void closingFormLeavesPreContentAlone() {
        String formatted =
                format(
                        style -> style.set(JavadocRules.CLOSING_TAG_FORM, JavadocClosingTagForm.SLASH_FIRST),
                        "    /**\n     * <pre>\n     * <p/>\n     * </pre>\n     */\n    void f() { }\n");
        assertTrue(formatted.contains("     * <p/>\n"), formatted);
    }

    @Test
    void openingPositionNewLineSplitsLeadingMarker() {
        String formatted =
                format(
                        style -> style.set(JavadocRules.OPENING_TAG_POSITION, JavadocOpeningTagPosition.NEW_LINE),
                        "    /**\n     * First.\n     * <p> Second.\n     */\n    void f() { }\n");
        assertTrue(formatted.contains("     * <p>\n     * Second.\n"), formatted);
    }

    @Test
    void openingPositionNewLineSplitsInlineMarker() {
        String formatted =
                format(
                        style -> style.set(JavadocRules.OPENING_TAG_POSITION, JavadocOpeningTagPosition.NEW_LINE),
                        "    /**\n     * First <p> Second.\n     */\n    void f() { }\n");
        assertTrue(formatted.contains("     * First\n     * <p>\n     * Second.\n"), formatted);
    }

    @Test
    void openingPositionSameLineJoinsMarkerWithNext() {
        String formatted =
                format(
                        style -> style.set(JavadocRules.OPENING_TAG_POSITION, JavadocOpeningTagPosition.SAME_LINE),
                        "    /**\n     * First.\n     * <p>\n     * Second.\n     */\n    void f() { }\n");
        assertTrue(formatted.contains("     * <p> Second.\n"), formatted);
    }

    @Test
    void openingPositionSameLineKeepsMarkerWithTextWhenWrapping() {
        String formatted =
                format(
                        style -> style.set(JavadocRules.OPENING_TAG_POSITION, JavadocOpeningTagPosition.SAME_LINE)
                                .set(JavadocRules.WRAP, true),
                        "    /**\n     * First.\n     * <p>\n     * Second.\n     */\n    void f() { }\n");
        assertTrue(formatted.contains("     * <p> Second.\n"), formatted);
    }

    @Test
    void newLinePlusClosingFormIsAFixedPointWhenWrapping() {
        assertFixedPoint(style -> style.set(JavadocRules.OPENING_TAG_POSITION, JavadocOpeningTagPosition.NEW_LINE)
                .set(JavadocRules.CLOSING_TAG_FORM, JavadocClosingTagForm.SLASH_FIRST)
                .set(JavadocRules.WRAP, true)
                .set(WrappingRules.MAX_LINE_LENGTH, 60));
    }

    @Test
    void sameLinePlusClosingFormIsAFixedPointWhenWrapping() {
        assertFixedPoint(style -> style.set(JavadocRules.OPENING_TAG_POSITION, JavadocOpeningTagPosition.SAME_LINE)
                .set(JavadocRules.CLOSING_TAG_FORM, JavadocClosingTagForm.SLASH_FIRST)
                .set(JavadocRules.WRAP, true)
                .set(WrappingRules.MAX_LINE_LENGTH, 60));
    }

    private static void assertFixedPoint(Consumer<StyleBuilder> rules) {
        String body = "    /**\n     * First paragraph long enough to be refilled to the margin.\n"
                + "     * <p> Second paragraph also long enough to need refilling at the margin.\n"
                + "     * <p/> Third paragraph marked with a self-closing tag.\n     */\n    void f() { }\n";
        String once = format(rules, body);
        StyleBuilder builder = Style.builder();
        rules.accept(builder);
        FormatResult twice = FormatJ.newFormatter()
                .style(builder.build())
                .languageLevel(LanguageLevel.LATEST)
                .build()
                .format(FormatRequest.of(once).withName("T.java"));
        assertFalse(twice.hasErrors(), () -> twice.diagnostics().toString());
        assertEquals(once, twice.text());
    }

}
