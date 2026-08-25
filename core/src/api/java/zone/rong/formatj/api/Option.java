package zone.rong.formatj.api;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

/**
 * A single typed, documented, defaulted formatting rule.
 *
 * <p>Every rule FormatJ knows about is an {@code Option} constant declared in one of the classes in
 * {@code zone.rong.formatj.api.rules}. Creating an option registers it with {@link OptionRegistry}, which
 * is what allows the same catalogue to drive the builder API, the TOML config file, the CLI's
 * {@code --set} flag, the Gradle DSL and the Maven plugin parameters without any reflection.
 *
 * @param <T> the type of the option's value
 */
public final class Option<T> {

    /** The value shapes an option can take. Determines how it is parsed and rendered. */
    public enum Kind {

        BOOLEAN,
        INTEGER,
        STRING,
        ENUM,
        STRING_LIST

    }

    private final String key;
    private final Kind kind;
    private final Class<T> type;
    private final T defaultValue;
    private final String description;

    private Option(String key, Kind kind, Class<T> type, T defaultValue, String description) {
        this.key = Objects.requireNonNull(key, "key");
        this.kind = Objects.requireNonNull(kind, "kind");
        this.type = Objects.requireNonNull(type, "type");
        this.defaultValue = Objects.requireNonNull(defaultValue, "defaultValue");
        this.description = Objects.requireNonNull(description, "description");
        OptionRegistry.register(this);
    }

    public static Option<Boolean> ofBoolean(String key, boolean defaultValue, String description) {
        return new Option<>(key, Kind.BOOLEAN, Boolean.class, defaultValue, description);
    }

    public static Option<Integer> ofInt(String key, int defaultValue, String description) {
        return new Option<>(key, Kind.INTEGER, Integer.class, defaultValue, description);
    }

    public static Option<String> ofString(String key, String defaultValue, String description) {
        return new Option<>(key, Kind.STRING, String.class, defaultValue, description);
    }

    public static <E extends Enum<E>> Option<E> ofEnum(String key, E defaultValue, String description) {
        return new Option<>(key, Kind.ENUM, defaultValue.getDeclaringClass(), defaultValue, description);
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    public static Option<List<String>> ofStringList(String key, List<String> defaultValue, String description) {
        Class<List<String>> type = (Class) List.class;
        return new Option<>(key, Kind.STRING_LIST, type, List.copyOf(defaultValue), description);
    }

    /** The dotted key used in {@code formatj.toml}, e.g. {@code wrapping.max-line-length}. */
    public String key() {
        return key;
    }

    public Kind kind() {
        return kind;
    }

    public Class<T> type() {
        return type;
    }

    public T defaultValue() {
        return defaultValue;
    }

    /** One-line human readable explanation, rendered as a comment by {@code --dump-config}. */
    public String description() {
        return description;
    }

    /** The legal values of an enum option, in declaration order; empty for every other kind. */
    public List<String> allowedValues() {
        if (kind != Kind.ENUM) {
            return List.of();
        }
        List<String> names = new ArrayList<>();
        for (T constant : type.getEnumConstants()) {
            names.add(renderEnum((Enum<?>) constant));
        }
        return List.copyOf(names);
    }

    /** Narrows an untyped value to this option's type, rejecting anything that does not fit. */
    public T cast(Object value) {
        Objects.requireNonNull(value, () -> "value for " + key);
        if (kind == Kind.STRING_LIST) {
            if (value instanceof List<?> list) {
                List<String> copy = new ArrayList<>(list.size());
                for (Object element : list) {
                    copy.add(String.valueOf(element));
                }
                return type.cast(List.copyOf(copy));
            }
            throw new IllegalArgumentException(key + " expects a list of strings, got " + value.getClass().getName());
        }
        if (!type.isInstance(value)) {
            throw new IllegalArgumentException(
                    key + " expects " + type.getSimpleName() + ", got " + value.getClass().getSimpleName());
        }
        return type.cast(value);
    }

    /** Parses the textual form used by config files and {@code --set key=value}. */
    public T parse(String raw) {
        Objects.requireNonNull(raw, "raw");
        String trimmed = raw.trim();
        return switch (kind) {
            case BOOLEAN -> cast(parseBoolean(trimmed));
            case INTEGER -> cast(parseInt(trimmed));
            case STRING -> cast(trimmed);
            case ENUM -> cast(parseEnum(trimmed));
            case STRING_LIST -> cast(parseList(trimmed));
        };
    }

    /** Renders a value back to the textual form {@link #parse(String)} accepts. */
    public String render(T value) {
        if (value instanceof Enum<?> constant) {
            return renderEnum(constant);
        }
        if (value instanceof List<?> list) {
            List<String> quoted = new ArrayList<>(list.size());
            for (Object element : list) {
                quoted.add("\"" + element + "\"");
            }
            return "[" + String.join(", ", quoted) + "]";
        }
        if (value instanceof String string) {
            return "\"" + string + "\"";
        }
        return String.valueOf(value);
    }

    private Boolean parseBoolean(String raw) {
        if (raw.equalsIgnoreCase("true")) {
            return Boolean.TRUE;
        }
        if (raw.equalsIgnoreCase("false")) {
            return Boolean.FALSE;
        }
        throw new IllegalArgumentException(key + " expects true or false, got '" + raw + "'");
    }

    private Integer parseInt(String raw) {
        try {
            return Integer.valueOf(raw);
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException(key + " expects an integer, got '" + raw + "'", e);
        }
    }

    private Object parseEnum(String raw) {
        String normalised = raw.trim().replace('-', '_').replace('.', '_').toUpperCase(Locale.ROOT);
        for (T constant : type.getEnumConstants()) {
            if (((Enum<?>) constant).name().equals(normalised)) {
                return constant;
            }
        }
        throw new IllegalArgumentException(key + " expects one of " + allowedValues() + ", got '" + raw + "'");
    }

    private List<String> parseList(String raw) {
        String body = raw;
        if (body.startsWith("[") && body.endsWith("]")) {
            body = body.substring(1, body.length() - 1);
        }
        List<String> values = new ArrayList<>();
        for (String piece : body.split(",")) {
            String element = piece.trim();
            if (element.isEmpty()) {
                continue;
            }
            if (element.length() >= 2 && element.startsWith("\"") && element.endsWith("\"")) {
                element = element.substring(1, element.length() - 1);
            }
            values.add(element);
        }
        return List.copyOf(values);
    }

    private static String renderEnum(Enum<?> constant) {
        return constant.name().toLowerCase(Locale.ROOT).replace('_', '-');
    }

    @Override
    public String toString() {
        return key;
    }

}
