package zone.rong.formatj.core.config;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * A reader for the subset of TOML a style file needs: tables, dotted keys, strings, integers,
 * booleans and single-line arrays.
 *
 * <p>Hand-written on purpose. A formatter that pulls in a config library inherits that library's
 * version conflicts, and version friction is one of the things this project exists to avoid.
 */
public final class TomlReader {

    private TomlReader() { }

    /**
     * Flattens a TOML document to dotted key to raw value text.
     *
     * <p>Values are returned in their source form so that {@code Option.parse} can apply the typing;
     * the reader deliberately does not guess types of its own.
     *
     * @throws TomlException if the document is malformed
     */
    public static Map<String, String> read(String document) {
        Map<String, String> values = new LinkedHashMap<>();
        String table = "";
        int lineNumber = 0;
        for (String rawLine : document.split("\r\n|\r|\n", -1)) {
            lineNumber++;
            String line = stripComment(rawLine).trim();
            if (line.isEmpty()) {
                continue;
            }
            if (line.startsWith("[")) {
                if (!line.endsWith("]")) {
                    throw new TomlException("Unterminated table header", lineNumber);
                }
                table = line.substring(1, line.length() - 1).trim();
                if (table.isEmpty()) {
                    throw new TomlException("Empty table header", lineNumber);
                }
                continue;
            }
            int equals = indexOfAssignment(line);
            if (equals < 0) {
                throw new TomlException("Expected 'key = value'", lineNumber);
            }
            String key = line.substring(0, equals).trim();
            String value = line.substring(equals + 1).trim();
            if (key.isEmpty()) {
                throw new TomlException("Missing key", lineNumber);
            }
            if (value.isEmpty()) {
                throw new TomlException("Missing value for '" + key + "'", lineNumber);
            }
            String qualified = table.isEmpty() ? key : table + "." + key;
            if (values.put(qualified, unquote(value)) != null) {
                throw new TomlException("Duplicate key '" + qualified + "'", lineNumber);
            }
        }
        return values;
    }

    /** Strips a trailing comment, ignoring '#' inside a quoted string. */
    private static String stripComment(String line) {
        boolean inString = false;
        for (int i = 0; i < line.length(); i++) {
            char current = line.charAt(i);
            if (current == '"' && (i == 0 || line.charAt(i - 1) != '\\')) {
                inString = !inString;
            } else if (current == '#' && !inString) {
                return line.substring(0, i);
            }
        }
        return line;
    }

    /** Index of the assignment '=', ignoring one inside a quoted key or value. */
    private static int indexOfAssignment(String line) {
        boolean inString = false;
        for (int i = 0; i < line.length(); i++) {
            char current = line.charAt(i);
            if (current == '"' && (i == 0 || line.charAt(i - 1) != '\\')) {
                inString = !inString;
            } else if (current == '=' && !inString) {
                return i;
            }
        }
        return -1;
    }

    /** Removes surrounding quotes from a scalar string, leaving arrays and numbers as written. */
    private static String unquote(String value) {
        if (value.length() >= 2 && value.startsWith("\"") && value.endsWith("\"")) {
            return value.substring(1, value.length() - 1);
        }
        return value;
    }

    /** A malformed style file. */
    public static final class TomlException extends RuntimeException {

        private final int line;

        TomlException(String message, int line) {
            super(message + " (line " + line + ")");
            this.line = line;
        }

        public int line() {
            return line;
        }

    }

}
