package zone.rong.formatj.api;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Mutable builder for a {@link Style}.
 *
 * <p>Typed callers use {@link #set(Option, Object)} with a constant from one of the
 * {@code zone.rong.formatj.api.rules} classes. Tools that carry rules as untyped key-value pairs,
 * such as the CLI's {@code --set} flag, use {@link #setRaw(String, String)}.
 */
public final class StyleBuilder {

    private final Map<String, Object> values;

    StyleBuilder() {
        this.values = new LinkedHashMap<>();
    }

    StyleBuilder(Map<String, Object> seed) {
        this.values = new LinkedHashMap<>(seed);
    }

    /** Sets one option. */
    public <T> StyleBuilder set(Option<T> option, T value) {
        values.put(option.key(), option.cast(value));
        return this;
    }

    /** Clears one option, restoring its default. */
    public StyleBuilder unset(Option<?> option) {
        values.remove(option.key());
        return this;
    }

    /**
     * Sets one option from its dotted key and textual value, as used by config files and CLIs.
     *
     * <p>{@code inherit} clears an option with a parent, so it follows that parent again. A 1.x key
     * is translated to its 2.x equivalent with a logged warning.
     */
    public StyleBuilder setRaw(String key, String value) {
        LegacyKeys.Setting setting = LegacyKeys.translate(key, value);
        if (setting == null) {
            return this;
        }
        Option<?> option = OptionRegistry.require(setting.key());
        Object parsed = option.parse(setting.value());
        if (parsed == null) {
            values.remove(option.key());
        } else {
            values.put(option.key(), parsed);
        }
        return this;
    }

    /** Applies every explicit setting of {@code other} on top of this builder. */
    public StyleBuilder apply(Style other) {
        values.putAll(other.explicitValues());
        return this;
    }

    /** Snapshots the current settings into an immutable style. */
    public Style build() {
        return new Style(values);
    }

}
