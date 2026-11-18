package zone.rong.formatj.api;

import org.junit.jupiter.api.Test;
import zone.rong.formatj.api.rules.BlankLineRules;
import zone.rong.formatj.api.rules.BracePlacement;
import zone.rong.formatj.api.rules.BracePolicy;
import zone.rong.formatj.api.rules.BraceRules;
import zone.rong.formatj.api.rules.ChainPolicy;
import zone.rong.formatj.api.rules.EmptyBodyStyle;
import zone.rong.formatj.api.rules.IndentRules;
import zone.rong.formatj.api.rules.ModifierOrder;
import zone.rong.formatj.api.rules.ModifierRules;
import zone.rong.formatj.api.rules.WrapPolicy;
import zone.rong.formatj.api.rules.WrappingRules;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

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
            .set(IndentRules.SIZE, 4)
            .set(IndentRules.USE_TABS, false)
            .set(IndentRules.CONTINUATION, 8)
            .set(WrappingRules.MAX_LINE_LENGTH, 120)
            .set(WrappingRules.CHAINED_CALLS, ChainPolicy.BREAK_ALL_IF_MULTILINE)
            .set(BraceRules.MODULE_PLACEMENT, BracePlacement.NEXT_LINE)
            .set(WrappingRules.EXPORTS_OPENS_TARGETS, WrapPolicy.CHOP_DOWN_ALWAYS)
            .set(ModifierRules.ORDER, ModifierOrder.CANONICAL)
            .set(BraceRules.ARROW_CASE_BODY, BracePolicy.WHEN_MULTI_STATEMENT)
            .build();

        assertEquals(120, style.get(WrappingRules.MAX_LINE_LENGTH));
        assertEquals(ChainPolicy.BREAK_ALL_IF_MULTILINE, style.get(WrappingRules.CHAINED_CALLS));
        assertEquals(BracePlacement.NEXT_LINE, style.get(BraceRules.MODULE_PLACEMENT));
        assertEquals(WrapPolicy.CHOP_DOWN_ALWAYS, style.get(WrappingRules.EXPORTS_OPENS_TARGETS));
        assertEquals(ModifierOrder.CANONICAL, style.get(ModifierRules.ORDER));
        assertEquals(BracePolicy.WHEN_MULTI_STATEMENT, style.get(BraceRules.ARROW_CASE_BODY));
        assertTrue(style.isSet(IndentRules.CONTINUATION));
    }

    @Test
    void googleDiffersFromTheFormatJDefaultsWhereItsGuideDoes() {
        Style google = Preset.GOOGLE.style();
        assertEquals(2, google.get(IndentRules.SIZE));
        assertEquals(4, google.get(IndentRules.CONTINUATION));
        assertEquals(100, google.get(WrappingRules.MAX_LINE_LENGTH));
        assertFalse(google.isSet(BraceRules.MODULE_PLACEMENT));
        assertEquals(2, google.get(IndentRules.ARRAY_INITIALIZER));
        assertEquals(ModifierOrder.PRESERVE, google.get(ModifierRules.ORDER));
    }

    @Test
    void mergingLetsAProjectStyleOverrideAPreset() {
        Style overrides = Style.builder().set(IndentRules.SIZE, 8).build();
        Style merged = Preset.GOOGLE.style().mergedWith(overrides);
        assertEquals(8, merged.get(IndentRules.SIZE));
        assertEquals(100, merged.get(WrappingRules.MAX_LINE_LENGTH));
    }

    @Test
    void rawSettingIsTypedAndValidated() {
        Style style = Style.builder()
            .setRaw("indent.size", "2")
            .setRaw("wrapping.chained-calls", "break-when-too-long")
            .setRaw("braces.module-placement", "next-line")
            .setRaw("modifiers.order", "canonical")
            .build();
        assertEquals(2, style.get(IndentRules.SIZE));
        assertEquals(ChainPolicy.BREAK_WHEN_TOO_LONG, style.get(WrappingRules.CHAINED_CALLS));
        assertEquals(BracePlacement.NEXT_LINE, style.get(BraceRules.MODULE_PLACEMENT));
        assertEquals(ModifierOrder.CANONICAL, style.get(ModifierRules.ORDER));
        assertThrows(IllegalArgumentException.class, () -> Style.builder().setRaw("indent.size", "wide"));
        assertThrows(IllegalArgumentException.class, () -> Style.builder().setRaw("wrapping.chained-calls", "nope"));
    }

    @Test
    void legacyKeysTranslateToTheirReplacements() {
        LegacyKeys.RENAMED.forEach((old, current) -> {
            assertTrue(OptionRegistry.find(old).isEmpty(), old);
            assertTrue(OptionRegistry.find(current).isPresent(), current);
        });

        Style style = Style.builder()
            .setRaw("sealed.permits-wrapping", "chop-down-always")
            .setRaw("records.single-line-empty-body", "true")
            .setRaw("records.compact-constructor-blank-line", "true")
            .setRaw("preservation.keep-author-blank-lines", "false")
            .setRaw("patterns.keep-simple-pattern-inline", "true")
            .build();
        assertEquals(WrapPolicy.CHOP_DOWN_ALWAYS, style.get(WrappingRules.PERMITS_CLAUSE));
        assertEquals(EmptyBodyStyle.COMPACT, style.get(BraceRules.EMPTY_RECORD_BODY));
        assertEquals(1, style.get(BlankLineRules.INSIDE_COMPACT_CONSTRUCTOR));
        assertEquals(0, style.get(BlankLineRules.MAX_CONSECUTIVE));
        assertFalse(style.isSet(WrappingRules.INSTANCEOF));
    }

    @Test
    void aChildFollowsItsParentUntilSetAndInheritClearsIt() {
        Style style = Style.builder()
            .set(IndentRules.CONTINUATION, 8)
            .set(BraceRules.CLASS_PLACEMENT, BracePlacement.NEXT_LINE)
            .build();
        assertEquals(8, style.get(IndentRules.CHAINED_CALL));
        assertEquals(BracePlacement.NEXT_LINE, style.get(BraceRules.MODULE_PLACEMENT));

        Style reset = style.toBuilder()
            .set(IndentRules.CHAINED_CALL, 2)
            .setRaw("indent.chained-call", "inherit")
            .build();
        assertFalse(reset.isSet(IndentRules.CHAINED_CALL));
        assertEquals(8, reset.get(IndentRules.CHAINED_CALL));
        assertThrows(IllegalArgumentException.class, () -> Style.builder().setRaw("indent.size", "inherit"));
    }

    @Test
    void toBuilderPreservesExplicitSettingsOnly() {
        Style style = Style.builder().set(IndentRules.SIZE, 2).build();
        Style copy = style.toBuilder().build();
        assertEquals(style, copy);
        assertEquals(1, copy.explicitValues().size());
    }

}
