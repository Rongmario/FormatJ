package zone.rong.formatj.api;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Translates option keys from 1.x into their 2.x equivalents, warning once per key.
 *
 * <p>Drop this table in 3.0.
 */
final class LegacyKeys {

    static final Map<String, String> RENAMED = Map.ofEntries(
        Map.entry("arrays.c-style-brackets", "declarations.array-brackets"),
        Map.entry("blank-lines.before-record-compact-constructor", "blank-lines.before-compact-constructor"),
        Map.entry("lambdas.body-braces", "braces.lambda-body"),
        Map.entry("lambdas.keep-single-expression-inline", "wrapping.lambda-expression-on-arrow-line"),
        Map.entry("module.blank-lines-after-opening-brace", "blank-lines.after-module-opening-brace"),
        Map.entry("module.blank-lines-before-closing-brace", "blank-lines.before-module-closing-brace"),
        Map.entry("module.blank-lines-between-directive-groups", "blank-lines.between-module-directive-groups"),
        Map.entry("module.brace-placement", "braces.module-placement"),
        Map.entry("module.empty-body", "braces.empty-module-body"),
        Map.entry("module.exports-opens-target-list-wrapping", "wrapping.exports-opens-targets"),
        Map.entry("module.provides-implementation-list-wrapping", "wrapping.provides-implementations"),
        Map.entry("patterns.deconstruction-wrapping", "wrapping.deconstruction-patterns"),
        Map.entry("patterns.nested-indent", "indent.nested-pattern"),
        Map.entry("preservation.max-preserved-blank-lines", "blank-lines.max-consecutive"),
        Map.entry("records.component-wrapping", "wrapping.record-components"),
        Map.entry("records.space-before-with-block", "spacing.before-with-block"),
        Map.entry("records.with-style", "wrapping.with-block"),
        Map.entry("sealed.permits-on-new-line", "wrapping.permits-on-new-line"),
        Map.entry("sealed.permits-order", "declarations.permits-order"),
        Map.entry("sealed.permits-wrapping", "wrapping.permits-clause"),
        Map.entry("semicolons.remove-redundant", "declarations.remove-redundant-semicolons"),
        Map.entry("switch.arrow-body-on-new-line-when-long", "wrapping.arrow-case-body-on-new-line-when-long"),
        Map.entry("switch.arrow-case-braces", "braces.arrow-case-body"),
        Map.entry("switch.guard-on-same-line", "wrapping.case-guard-on-same-line"),
        Map.entry("switch.multi-label-wrapping", "wrapping.case-labels"),
        Map.entry("switch.null-default-on-one-line", "preservation.keep-case-null-default-inline"),
        Map.entry("wrapping.keep-simple-classes-on-one-line", "preservation.keep-simple-classes-inline"),
        Map.entry("wrapping.keep-simple-lambdas-on-one-line", "preservation.keep-simple-lambdas-inline"),
        Map.entry("wrapping.keep-simple-methods-on-one-line", "preservation.keep-simple-methods-inline"),
        Map.entry("wrapping.require-enum-constant-semicolon", "declarations.require-enum-semicolon")
    );

    private static final System.Logger LOGGER = System.getLogger("zone.rong.formatj");
    private static final Set<String> WARNED = ConcurrentHashMap.newKeySet();

    private LegacyKeys() { }

    /**
     * The 2.x setting a 1.x one maps to, the setting itself when the key is current, or null when
     * the 1.x setting asks for what 2.x already does.
     */
    static Setting translate(String key, String value) {
        String renamed = RENAMED.get(key);
        if (renamed != null) {
            return warn(key, value, new Setting(renamed, value));
        }
        boolean on = value.strip().equalsIgnoreCase("true");
        return switch (key) {
            case "records.single-line-empty-body" ->
                warn(key, value, new Setting("braces.empty-record-body", on ? "compact" : "inherit"));
            case "records.compact-constructor-blank-line" ->
                warn(key, value, new Setting("blank-lines.inside-compact-constructor", on ? "1" : "0"));

            // Both 1.x defaults were true, and a false fell back to a broader rule that 2.x keeps.
            case "preservation.keep-author-blank-lines" ->
                warn(key, value, on ? null : new Setting("blank-lines.max-consecutive", "0"));
            case "patterns.keep-simple-pattern-inline" ->
                warn(key, value, on ? null : new Setting("wrapping.instanceof", "wrap-if-long"));

            default -> new Setting(key, value);
        };
    }

    private static Setting warn(String key, String value, Setting replacement) {
        if (WARNED.add(key)) {
            String advice = replacement == null
                ? "is what 2.0 does by default, delete it"
                : "is now '" + replacement.key() + " = " + replacement.value() + "'";
            LOGGER.log(System.Logger.Level.WARNING, "Option '" + key + " = " + value + "' " + advice);
        }
        return replacement;
    }

    /** A current key and value. */
    record Setting(String key, String value) { }

}
