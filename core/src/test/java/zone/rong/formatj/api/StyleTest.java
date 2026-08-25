package zone.rong.formatj.api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import zone.rong.formatj.api.rules.BracePolicy;
import zone.rong.formatj.api.rules.ChainPolicy;
import zone.rong.formatj.api.rules.IndentRules;
import zone.rong.formatj.api.rules.SwitchRules;
import zone.rong.formatj.api.rules.WrappingRules;
import org.junit.jupiter.api.Test;

class StyleTest {

    @Test
    void unsetOptionsFallBackToTheirDefaults() {
        Style style = Style.builder().build();
        assertEquals(4, style.get(IndentRules.SIZE));
        assertFalse(style.isSet(IndentRules.SIZE));
    }

    @Test
    void theBuilderReadsLikeTheDocumentedExample() {
        Style style = Style.preset(Preset.FORMATJ)
                .indent(indent -> indent.size(4).useTabs(false).continuation(8))
                .wrapping(wrapping -> wrapping.maxLineLength(120).chainedCalls(ChainPolicy.BREAK_ALL_IF_MULTILINE))
                .switches(switches -> switches.arrowCaseBraces(BracePolicy.WHEN_MULTI_STATEMENT))
                .build();

        assertEquals(120, style.get(WrappingRules.MAX_LINE_LENGTH));
        assertEquals(ChainPolicy.BREAK_ALL_IF_MULTILINE, style.get(WrappingRules.CHAINED_CALLS));
        assertEquals(BracePolicy.WHEN_MULTI_STATEMENT, style.get(SwitchRules.ARROW_CASE_BRACES));
        assertTrue(style.isSet(IndentRules.CONTINUATION));
    }

    @Test
    void googleDiffersFromTheFormatJDefaultsWhereItsGuideDoes() {
        Style google = Preset.GOOGLE.style();
        assertEquals(2, google.get(IndentRules.SIZE));
        assertEquals(4, google.get(IndentRules.CONTINUATION));
        assertEquals(100, google.get(WrappingRules.MAX_LINE_LENGTH));
    }

    @Test
    void mergingLetsAProjectStyleOverrideAPreset() {
        Style overrides = Style.builder().indent(indent -> indent.size(8)).build();
        Style merged = Preset.GOOGLE.style().mergedWith(overrides);
        assertEquals(8, merged.get(IndentRules.SIZE));
        assertEquals(100, merged.get(WrappingRules.MAX_LINE_LENGTH));
    }

    @Test
    void rawSettingIsTypedAndValidated() {
        Style style = Style.builder()
                .setRaw("indent.size", "2")
                .setRaw("wrapping.chained-calls", "break-when-too-long")
                .build();
        assertEquals(2, style.get(IndentRules.SIZE));
        assertEquals(ChainPolicy.BREAK_WHEN_TOO_LONG, style.get(WrappingRules.CHAINED_CALLS));
        assertThrows(IllegalArgumentException.class, () -> Style.builder().setRaw("indent.size", "wide"));
        assertThrows(IllegalArgumentException.class, () -> Style.builder().setRaw("wrapping.chained-calls", "nope"));
    }

    @Test
    void toBuilderPreservesExplicitSettingsOnly() {
        Style style = Style.builder().indent(indent -> indent.size(2)).build();
        Style copy = style.toBuilder().build();
        assertEquals(style, copy);
        assertEquals(1, copy.explicitValues().size());
    }

}
