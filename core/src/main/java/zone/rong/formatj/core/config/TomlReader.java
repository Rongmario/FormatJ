package zone.rong.formatj.core.config;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * A reader for the subset of TOML a style file needs: tables, dotted keys, strings, integers,
 * booleans and arrays, which may run over several lines.
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
        String[] lines = document.split("\r\n|\r|\n", -1);
        String table = "";
        for (int index = 0; index < lines.length; index++) {
            int lineNumber = index + 1;
            String line = stripComment(lines[index]).trim();
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

            // An array may span lines. Join them so the value parsers still see one line; elements
            // keep their commas, and a comment on a continuation line is already gone.
            int depth = arrayDepth(value, 0);
            if (depth > 0) {
                StringBuilder joined = new StringBuilder(value);
                while (depth > 0) {
                    if (++index >= lines.length) {
                        throw new TomlException("Unterminated array for '" + key + "'", lineNumber);
                    }
                    String continuation = stripComment(lines[index]).trim();
                    joined.append(continuation);
                    depth = arrayDepth(continuation, depth);
                }
                value = joined.toString();
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

    /** Bracket depth once this line has been scanned, ignoring brackets inside a quoted string. */
    private static int arrayDepth(String line, int depth) {
        boolean inString = false;
        for (int i = 0; i < line.length(); i++) {
            char current = line.charAt(i);
            if (current == '"' && (i == 0 || line.charAt(i - 1) != '\\')) {
                inString = !inString;
            } else if (!inString && current == '[') {
                depth++;
            } else if (!inString && current == ']') {
                depth--;
            }
        }
        return depth;
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
