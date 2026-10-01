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
        STRING_GROUPS,
        INHERITABLE_INTEGER,
        INHERITABLE_ENUM

    }

    private final String key;
    private final Kind kind;
    private final Class<T> type;
    private final Class<?> valueType;
    private final T defaultValue;
    private final int minValue;
    private final String description;

    private Option(String key, Kind kind, Class<T> type, T defaultValue, String description) {
        this(key, kind, type, type, defaultValue, Integer.MIN_VALUE, description);
    }

    private Option(String key, Kind kind, Class<T> type, Class<?> valueType, T defaultValue, String description) {
        this(key, kind, type, valueType, defaultValue, Integer.MIN_VALUE, description);
    }

    /** {@code minValue} only matters for {@link Kind#INTEGER} and {@link Kind#INHERITABLE_INTEGER}. */
    private Option(
            String key,
            Kind kind,
            Class<T> type,
            Class<?> valueType,
            T defaultValue,
            int minValue,
            String description) {
        this.key = Objects.requireNonNull(key, "key");
        this.kind = Objects.requireNonNull(kind, "kind");
        this.type = Objects.requireNonNull(type, "type");
        this.valueType = Objects.requireNonNull(valueType, "valueType");
        this.defaultValue = Objects.requireNonNull(defaultValue, "defaultValue");
        this.minValue = minValue;
        this.description = Objects.requireNonNull(description, "description");
        OptionRegistry.register(this);
    }

    public static Option<Boolean> ofBoolean(String key, boolean defaultValue, String description) {
        return new Option<>(key, Kind.BOOLEAN, Boolean.class, defaultValue, description);
    }

    public static Option<Integer> ofInt(String key, int defaultValue, String description) {
        return ofInt(key, defaultValue, 0, description);
    }

    /** An integer option that rejects a value below {@code minValue}. */
    public static Option<Integer> ofInt(String key, int defaultValue, int minValue, String description) {
        return new Option<>(key, Kind.INTEGER, Integer.class, Integer.class, defaultValue, minValue, description);
    }

    public static Option<String> ofString(String key, String defaultValue, String description) {
        return new Option<>(key, Kind.STRING, String.class, defaultValue, description);
    }

    public static <E extends Enum<E>> Option<E> ofEnum(String key, E defaultValue, String description) {
        return new Option<>(key, Kind.ENUM, defaultValue.getDeclaringClass(), defaultValue, description);
    }

    /** An integer option whose {@code inherit} value defers to another rule. */
    @SuppressWarnings({"unchecked", "rawtypes"})
    public static Option<Inheritable<Integer>> ofInheritableInt(String key, String description) {
        Class<Inheritable<Integer>> type = (Class) Inheritable.class;
        return new Option<>(key, Kind.INHERITABLE_INTEGER, type, Integer.class, Inheritable.inherit(), 0, description);
    }

    /** An enum option whose {@code inherit} value defers to another rule. */
    @SuppressWarnings({"unchecked", "rawtypes"})
    public static <E extends Enum<E>> Option<Inheritable<E>> ofInheritableEnum(
            String key,
            Class<E> valueType,
            String description) {
        Class<Inheritable<E>> type = (Class) Inheritable.class;
        return new Option<>(key, Kind.INHERITABLE_ENUM, type, valueType, Inheritable.inherit(), description);
    }

    /**
     * An ordered list of groups, each group an ordered list of strings.
     *
     * <p>The TOML form takes either shape per entry, so {@code ["java", ["a", "b"]]} is one group of
     * {@code java} followed by one group of {@code a} and {@code b}. A bare string is the common case
     * and stays writable as one.
     */
    @SuppressWarnings({"unchecked", "rawtypes"})
    public static Option<List<List<String>>> ofStringGroups(
            String key,
            List<List<String>> defaultValue,
            String description) {
        Class<List<List<String>>> type = (Class) List.class;
        return new Option<>(key, Kind.STRING_GROUPS, type, copyGroups(defaultValue), description);
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
        if (kind != Kind.ENUM && kind != Kind.INHERITABLE_ENUM) {
            return List.of();
        }
        List<String> names = new ArrayList<>();
        if (kind == Kind.INHERITABLE_ENUM) {
            names.add("inherit");
        }
        for (Object constant : valueType.getEnumConstants()) {
            names.add(renderEnum((Enum<?>) constant));
        }
        return List.copyOf(names);
    }

    /** Narrows an untyped value to this option's type, rejecting anything that does not fit. */
    public T cast(Object value) {
        Objects.requireNonNull(value, () -> "value for " + key);
        if (kind == Kind.STRING_GROUPS) {
            if (value instanceof List<?> list) {
                return type.cast(copyGroups(list));
            }
            throw new IllegalArgumentException(
                    key + " expects a list of string groups, got " + value.getClass().getName());
        }
        if (kind == Kind.INHERITABLE_INTEGER || kind == Kind.INHERITABLE_ENUM) {
            if (!(value instanceof Inheritable<?> inheritable)) {
                throw new IllegalArgumentException(
                        key + " expects Inheritable, got " + value.getClass().getSimpleName());
            }
            Object override = inheritable.override();
            if (override != null && !valueType.isInstance(override)) {
                throw new IllegalArgumentException(
                        key + " expects an inherited " + valueType.getSimpleName() + ", got "
                                + override.getClass().getSimpleName());
            }
            if (kind == Kind.INHERITABLE_INTEGER && override != null) {
                requireMin((Integer) override);
            }
            return type.cast(value);
        }
        if (!type.isInstance(value)) {
            throw new IllegalArgumentException(
                    key + " expects " + type.getSimpleName() + ", got " + value.getClass().getSimpleName());
        }
        if (kind == Kind.INTEGER) {
            requireMin((Integer) value);
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
            case STRING -> cast(unquote(trimmed));
            case ENUM -> cast(parseEnum(trimmed));
            case STRING_GROUPS -> cast(parseGroups(trimmed));
            case INHERITABLE_INTEGER -> cast(parseInheritableInteger(trimmed));
            case INHERITABLE_ENUM -> cast(parseInheritableEnum(trimmed));
        };
    }

    /** Renders a value back to the textual form {@link #parse(String)} accepts. */
    public String render(T value) {
        if (value instanceof Inheritable<?> inheritable) {
            if (inheritable.inherits()) {
                return "inherit";
            }
            Object override = inheritable.override();
            return override instanceof Enum<?> constant ? renderEnum(constant) : String.valueOf(override);
        }
        if (value instanceof Enum<?> constant) {
            return renderEnum(constant);
        }
        if (value instanceof List<?> groups) {
            List<String> rendered = new ArrayList<>(groups.size());
            for (Object group : groups) {
                // A group of one renders bare, so a config that never groups reads as a plain list.
                List<?> prefixes = group instanceof List<?> nested ? nested : List.of(group);
                if (prefixes.size() == 1) {
                    rendered.add(quote(String.valueOf(prefixes.getFirst())));
                    continue;
                }
                List<String> quoted = new ArrayList<>(prefixes.size());
                for (Object prefix : prefixes) {
                    quoted.add(quote(String.valueOf(prefix)));
                }
                rendered.add("[" + String.join(", ", quoted) + "]");
            }
            return "[" + String.join(", ", rendered) + "]";
        }
        if (value instanceof String string) {
            return quote(string);
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
        // TOML allows '_' as a digit group separator, e.g. 1_000.
        String digits = raw.indexOf('_') >= 0 ? raw.replace("_", "") : raw;
        int value;
        try {
            value = Integer.parseInt(digits);
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException(key + " expects an integer, got '" + raw + "'", e);
        }
        return requireMin(value);
    }

    private int requireMin(int value) {
        if (value < minValue) {
            throw new IllegalArgumentException(key + " must be at least " + minValue + ", got " + value);
        }
        return value;
    }

    private Object parseEnum(String raw) {
        return parseEnum(raw, valueType);
    }

    private Object parseEnum(String raw, Class<?> enumType) {
        String normalised = raw.trim().replace('-', '_').replace('.', '_').toUpperCase(Locale.ROOT);
        for (Object constant : enumType.getEnumConstants()) {
            if (((Enum<?>) constant).name().equals(normalised)) {
                return constant;
            }
        }
        throw new IllegalArgumentException(key + " expects one of " + allowedValues() + ", got '" + raw + "'");
    }

    private Inheritable<Integer> parseInheritableInteger(String raw) {
        return isInherit(raw) ? Inheritable.inherit() : Inheritable.of(parseInt(raw));
    }

    private Inheritable<?> parseInheritableEnum(String raw) {
        return isInherit(raw) ? Inheritable.inherit() : Inheritable.of(parseEnum(raw, valueType));
    }

    private static boolean isInherit(String raw) {
        return raw.equalsIgnoreCase("inherit");
    }

    /** Splits the outer array, where an element is either a bare string or a nested array. */
    private List<List<String>> parseGroups(String raw) {
        List<List<String>> groups = new ArrayList<>();
        for (String element : split(raw, true)) {
            if (element.startsWith("[")) {
                groups.add(parseList(element));
            } else {
                groups.add(List.of(unquote(element)));
            }
        }
        return List.copyOf(groups);
    }

    private List<String> parseList(String raw) {
        List<String> values = new ArrayList<>();
        for (String element : split(raw, false)) {
            values.add(unquote(element));
        }
        return List.copyOf(values);
    }

    /**
     * The comma-separated elements of one array, stripped of its brackets and of blank elements so
     * that TOML's trailing comma costs nothing.
     *
     * @param nesting whether a nested array counts as a single element rather than as its own commas
     */
    private List<String> split(String raw, boolean nesting) {
        String body = raw.trim();
        if (body.startsWith("[") && body.endsWith("]")) {
            body = body.substring(1, body.length() - 1);
        }
        List<String> elements = new ArrayList<>();
        StringBuilder element = new StringBuilder();
        boolean quoted = false;
        int depth = 0;
        for (int i = 0; i < body.length(); i++) {
            char c = body.charAt(i);
            if (quoted && c == '\\' && i + 1 < body.length()) {
                element.append(c).append(body.charAt(++i));
            } else if (c == '"') {
                quoted = !quoted;
                element.append(c);
            } else if (nesting && !quoted && (c == '[' || c == ']')) {
                depth += c == '[' ? 1 : -1;
                element.append(c);
            } else if (c == ',' && !quoted && depth == 0) {
                addElement(elements, element);
                element.setLength(0);
            } else {
                element.append(c);
            }
        }
        if (quoted) {
            throw new IllegalArgumentException(key + " has an unterminated quote: '" + raw + "'");
        }
        if (depth != 0) {
            throw new IllegalArgumentException(key + " has an unbalanced bracket: '" + raw + "'");
        }
        addElement(elements, element);
        return elements;
    }

    /** Deep copy of a groups value, promoting a bare string element to a group of one. */
    private static List<List<String>> copyGroups(List<?> groups) {
        List<List<String>> copy = new ArrayList<>(groups.size());
        for (Object group : groups) {
            List<?> prefixes = group instanceof List<?> nested ? nested : List.of(group);
            List<String> strings = new ArrayList<>(prefixes.size());
            for (Object prefix : prefixes) {
                strings.add(String.valueOf(prefix));
            }
            copy.add(List.copyOf(strings));
        }
        return List.copyOf(copy);
    }

    private static void addElement(List<String> elements, StringBuilder element) {
        String piece = element.toString().trim();
        if (piece.isEmpty()) {
            return;
        }
        elements.add(piece);
    }

    /** Wraps a value in double quotes, escaping backslashes and quotes so {@link #unquote} inverts it. */
    private static String quote(String value) {
        StringBuilder out = new StringBuilder(value.length() + 2).append('"');
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            if (c == '"' || c == '\\') {
                out.append('\\');
            }
            out.append(c);
        }
        return out.append('"').toString();
    }

    /** Strips one layer of surrounding double quotes and their escapes; bare text is returned as is. */
    private static String unquote(String raw) {
        if (raw.length() < 2 || !raw.startsWith("\"") || !raw.endsWith("\"")) {
            return raw;
        }
        String body = raw.substring(1, raw.length() - 1);
        StringBuilder out = new StringBuilder(body.length());
        for (int i = 0; i < body.length(); i++) {
            char c = body.charAt(i);
            if (c == '\\' && i + 1 < body.length()) {
                c = body.charAt(++i);
            }
            out.append(c);
        }
        return out.toString();
    }

    private static String renderEnum(Enum<?> constant) {
        return constant.name().toLowerCase(Locale.ROOT).replace('_', '-');
    }

    @Override
    public String toString() {
        return key;
    }

}
