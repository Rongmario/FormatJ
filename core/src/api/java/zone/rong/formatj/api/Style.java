package zone.rong.formatj.api;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * An immutable set of formatting rules.
 *
 * <p>A style holds only the options that were explicitly set; anything else falls back to the
 * option's own default, so a style stays meaningful when the catalogue grows.
 */
public final class Style {

    private static final Style DEFAULTS = new Style(Map.of());

    private final Map<String, Object> values;

    Style(Map<String, Object> values) {
        this.values = Map.copyOf(values);
    }

    /** Every option at its built-in default. */
    public static Style defaults() {
        return DEFAULTS;
    }

    /** An empty builder; every unset option keeps its default. */
    public static StyleBuilder builder() {
        return new StyleBuilder();
    }

    /** A builder pre-populated with the named preset, ready for further tweaks. */
    public static StyleBuilder preset(Preset preset) {
        Objects.requireNonNull(preset, "preset");
        StyleBuilder builder = new StyleBuilder();
        preset.applyTo(builder);
        return builder;
    }

    /** The effective value of an option: what was set, or the option's default. */
    public <T> T get(Option<T> option) {
        Object value = values.get(option.key());
        return value == null ? option.defaultValue() : option.cast(value);
    }

    /** Whether this style sets the option explicitly rather than inheriting its default. */
    public boolean isSet(Option<?> option) {
        return values.containsKey(option.key());
    }

    /** A builder seeded with this style's explicit settings. */
    public StyleBuilder toBuilder() {
        return new StyleBuilder(values);
    }

    /** This style with {@code overrides}' explicit settings applied on top. */
    public Style mergedWith(Style overrides) {
        Map<String, Object> merged = new LinkedHashMap<>(values);
        merged.putAll(overrides.values);
        return new Style(merged);
    }

    /** Only the options this style sets explicitly, keyed by dotted option key. */
    public Map<String, Object> explicitValues() {
        return values;
    }

    /** Every option in the catalogue with its effective value, in catalogue order. */
    public Map<String, Object> resolvedValues() {
        Map<String, Object> resolved = new LinkedHashMap<>();
        for (Option<?> option : OptionRegistry.all()) {
            resolved.put(option.key(), get(option));
        }
        return resolved;
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof Style style && values.equals(style.values);
    }

    @Override
    public int hashCode() {
        return values.hashCode();
    }

    @Override
    public String toString() {
        return "Style" + values;
    }

}
