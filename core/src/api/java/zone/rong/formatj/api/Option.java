package zone.rong.formatj.api;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;

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

    /** The textual value that clears an option with a parent, so it follows that parent again. */
    public static final String INHERIT = "inherit";

    private final String key;
    private final Kind kind;
    private final Class<T> type;
    private final T defaultValue;
    private final Option<T> parent;
    private final int minValue;
    private final String description;

    private Option(String key, Kind kind, Class<T> type, T defaultValue, String description) {
        this(
            key,
            kind,
            type,
            Objects.requireNonNull(defaultValue, "defaultValue"),
            null,
            Integer.MIN_VALUE,
            description
        );
    }

    /** Exactly one of {@code defaultValue} and {@code parent} is set. */
    private Option(
        String key,
        Kind kind,
        Class<T> type,
        T defaultValue,
        Option<T> parent,
        int minValue,
        String description
    ) {
        this.key = Objects.requireNonNull(key, "key");
        this.kind = Objects.requireNonNull(kind, "kind");
        this.type = Objects.requireNonNull(type, "type");
        this.defaultValue = defaultValue;
        this.parent = parent;
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
        return new Option<>(key, Kind.INTEGER, Integer.class, defaultValue, null, minValue, description);
    }

    /** An integer option that takes {@code parent}'s value until it is set itself. */
    public static Option<Integer> ofInt(String key, Option<Integer> parent, String description) {
        return new Option<>(key, Kind.INTEGER, Integer.class, null, parent, parent.minValue, description);
    }

    public static Option<String> ofString(String key, String defaultValue, String description) {
        return new Option<>(key, Kind.STRING, String.class, defaultValue, description);
    }

    public static <E extends Enum<E>> Option<E> ofEnum(String key, E defaultValue, String description) {
        return new Option<>(key, Kind.ENUM, defaultValue.getDeclaringClass(), defaultValue, description);
    }

    /** An enum option that takes {@code parent}'s value until it is set itself. */
    public static <E extends Enum<E>> Option<E> ofEnum(String key, Option<E> parent, String description) {
        return new Option<>(key, Kind.ENUM, parent.type, null, parent, Integer.MIN_VALUE, description);
    }

    /**
     * An ordered list of groups, each group an ordered list of strings.
     *
     * <p>The TOML form takes either shape per entry, so {@code ["java", ["a", "b"]]} is one group of
     * {@code java} followed by one group of {@code a} and {@code b}. A bare string is the common case
     * and stays writable as one.
     */
    @SuppressWarnings({ "unchecked", "rawtypes" })
    public static Option<List<List<String>>> ofStringGroups(
        String key,
        List<List<String>> defaultValue,
        String description
    ) {
        Class<List<List<String>>> type = (Class) List.class;
        return new Option<>(key, Kind.STRING_GROUPS, type, copyGroups(defaultValue), description);
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

    /** The value used when nothing is set: the parent's default for an option that has one. */
    public T defaultValue() {
        return parent == null ? defaultValue : parent.defaultValue();
    }

    /** The option whose value this one takes until it is set itself. */
    public Optional<Option<T>> parent() {
        return Optional.ofNullable(parent);
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
        if (parent != null) {
            names.add(INHERIT);
        }
        for (Object constant : type.getEnumConstants()) {
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
                key + " expects a list of string groups, got " + value.getClass().getName()
            );
        }
        if (!type.isInstance(value)) {
            throw new IllegalArgumentException(
                key + " expects " + type.getSimpleName() + ", got " + value.getClass().getSimpleName()
            );
        }
        if (kind == Kind.INTEGER) {
            requireMin((Integer) value);
        }
        return type.cast(value);
    }

    /**
     * Parses the textual form used by config files and {@code --set key=value}.
     *
     * @return the value, or {@code null} for {@code inherit} on an option with a parent
     */
    public T parse(String raw) {
        Objects.requireNonNull(raw, "raw");
        String trimmed = raw.trim();
        if (parent != null && unquote(trimmed).equalsIgnoreCase(INHERIT)) {
            return null;
        }
        return switch (kind) {
            case BOOLEAN -> cast(parseBoolean(trimmed));
            case INTEGER -> cast(parseInt(trimmed));
            case STRING -> cast(unquote(trimmed));
            case ENUM -> cast(parseEnum(trimmed));
            case STRING_GROUPS -> cast(parseGroups(trimmed));
        };
    }

    /** Renders a value back to the textual form {@link #parse(String)} accepts. */
    public String render(T value) {
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

    @Override
    public String toString() {
        return key;
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
        String normalised = unquote(raw).trim().replace('-', '_').replace('.', '_').toUpperCase(Locale.ROOT);
        for (Object constant : type.getEnumConstants()) {
            if (((Enum<?>) constant).name().equals(normalised)) {
                return constant;
            }
        }
        throw new IllegalArgumentException(key + " expects one of " + allowedValues() + ", got '" + raw + "'");
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

    /** The value shapes an option can take. Determines how it is parsed and rendered. */
    public enum Kind {

        BOOLEAN,
        INTEGER,
        STRING,
        ENUM,
        STRING_GROUPS

    }

}
