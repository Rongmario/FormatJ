package zone.rong.formatj.core.config;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * A reader for the subset of TOML a style file needs: tables, dotted keys, strings, integers,
 * booleans, inline tables and arrays, which may run over several lines.
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
        Set<String> tables = new HashSet<>();
        String withoutBom = document.startsWith("\uFEFF") ? document.substring(1) : document;
        String[] lines = withoutBom.split("\r\n|\r|\n", -1);
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
                if (!tables.add(table)) {
                    throw new TomlException("Duplicate table '" + table + "'", lineNumber);
                }
                continue;
            }
            int equals = indexOfAssignment(line);
            if (equals < 0) {
                throw new TomlException("Expected 'key = value'", lineNumber);
            }
            String key = unquote(line.substring(0, equals).trim(), lineNumber);
            String value = line.substring(equals + 1).trim();
            if (key.isEmpty()) {
                throw new TomlException("Missing key", lineNumber);
            }
            if (value.isEmpty()) {
                throw new TomlException("Missing value for '" + key + "'", lineNumber);
            }
            String qualified = table.isEmpty() ? key : table + "." + key;

            if (value.startsWith("{")) {
                if (!value.endsWith("}")) {
                    throw new TomlException("Unterminated inline table for '" + key + "'", lineNumber);
                }
                putInlineTable(values, qualified, value.substring(1, value.length() - 1), lineNumber);
                continue;
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

            if (values.put(qualified, unquote(value, lineNumber)) != null) {
                throw new TomlException("Duplicate key '" + qualified + "'", lineNumber);
            }
        }
        return values;
    }

    /** Assigns each {@code key = value} pair of an inline table body under {@code prefix}. */
    private static void putInlineTable(Map<String, String> values, String prefix, String body, int lineNumber) {
        for (String pair : splitTopLevel(body, ',')) {
            String trimmed = pair.trim();
            if (trimmed.isEmpty()) {
                continue;
            }
            int equals = indexOfAssignment(trimmed);
            if (equals < 0) {
                throw new TomlException("Expected 'key = value' in inline table for '" + prefix + "'", lineNumber);
            }
            String subKey = unquote(trimmed.substring(0, equals).trim(), lineNumber);
            String subValue = trimmed.substring(equals + 1).trim();
            String qualified = prefix + "." + subKey;
            if (values.put(qualified, unquote(subValue, lineNumber)) != null) {
                throw new TomlException("Duplicate key '" + qualified + "'", lineNumber);
            }
        }
    }

    /** Strips a trailing comment. A '#' inside a basic or literal string does not start one. */
    private static String stripComment(String line) {
        boolean[] quoted = quotedPositions(line);
        for (int i = 0; i < line.length(); i++) {
            if (!quoted[i] && line.charAt(i) == '#') {
                return line.substring(0, i);
            }
        }
        return line;
    }

    /** Bracket depth once this line has been scanned, ignoring brackets inside a quoted string. */
    private static int arrayDepth(String line, int depth) {
        boolean[] quoted = quotedPositions(line);
        for (int i = 0; i < line.length(); i++) {
            if (quoted[i]) {
                continue;
            }
            char current = line.charAt(i);
            if (current == '[') {
                depth++;
            } else if (current == ']') {
                depth--;
            }
        }
        return depth;
    }

    /** Index of the assignment '=', ignoring one inside a quoted key or value. */
    private static int indexOfAssignment(String line) {
        boolean[] quoted = quotedPositions(line);
        for (int i = 0; i < line.length(); i++) {
            if (!quoted[i] && line.charAt(i) == '=') {
                return i;
            }
        }
        return -1;
    }

    /** Splits {@code body} on a top-level {@code separator}, skipping one nested inside brackets or a string. */
    private static List<String> splitTopLevel(String body, char separator) {
        boolean[] quoted = quotedPositions(body);
        List<String> parts = new ArrayList<>();
        int start = 0;
        int depth = 0;
        for (int i = 0; i < body.length(); i++) {
            if (quoted[i]) {
                continue;
            }
            char c = body.charAt(i);
            if (c == '[') {
                depth++;
            } else if (c == ']') {
                depth--;
            } else if (c == separator && depth == 0) {
                parts.add(body.substring(start, i));
                start = i + 1;
            }
        }
        parts.add(body.substring(start));
        return parts;
    }

    /**
     * Marks which characters of {@code line} fall inside a basic ({@code "..."}) or literal
     * ({@code '...'}) string, so callers can skip a '#', bracket or '=' that appears inside quoted
     * text. A backslash inside a basic string escapes whatever follows it, including another
     * backslash, so {@code \\"} closes the string rather than escaping the quote.
     */
    private static boolean[] quotedPositions(String line) {
        boolean[] quoted = new boolean[line.length()];
        boolean inBasic = false;
        boolean inLiteral = false;
        for (int i = 0; i < line.length(); i++) {
            char c = line.charAt(i);
            if (inBasic) {
                quoted[i] = true;
                if (c == '\\' && i + 1 < line.length()) {
                    quoted[++i] = true;
                } else if (c == '"') {
                    inBasic = false;
                }
            } else if (inLiteral) {
                quoted[i] = true;
                if (c == '\'') {
                    inLiteral = false;
                }
            } else if (c == '"') {
                inBasic = true;
                quoted[i] = true;
            } else if (c == '\'') {
                inLiteral = true;
                quoted[i] = true;
            }
        }
        return quoted;
    }

    /**
     * Removes a scalar's surrounding quotes. A basic string's escapes are decoded; a literal
     * string's content is kept exactly as written, since TOML gives it no escapes at all.
     */
    private static String unquote(String value, int lineNumber) {
        if (value.length() >= 2 && value.startsWith("'") && value.endsWith("'")) {
            return value.substring(1, value.length() - 1);
        }
        if (value.length() >= 2 && value.startsWith("\"") && value.endsWith("\"")) {
            return decodeBasicString(value.substring(1, value.length() - 1), lineNumber);
        }
        return value;
    }

    /** Decodes TOML basic-string escapes: quote, backslash, the control-char shorthands, and unicode escapes. */
    private static String decodeBasicString(String body, int lineNumber) {
        StringBuilder out = new StringBuilder(body.length());
        for (int i = 0; i < body.length(); i++) {
            char c = body.charAt(i);
            if (c != '\\') {
                out.append(c);
                continue;
            }
            if (i + 1 >= body.length()) {
                throw new TomlException("Dangling escape in string", lineNumber);
            }
            char escape = body.charAt(++i);
            switch (escape) {
                case '"' -> out.append('"');
                case '\\' -> out.append('\\');
                case 'b' -> out.append('\b');
                case 't' -> out.append('\t');
                case 'n' -> out.append('\n');
                case 'f' -> out.append('\f');
                case 'r' -> out.append('\r');
                case 'u' -> i = appendUnicodeEscape(out, body, i, 4, lineNumber);
                case 'U' -> i = appendUnicodeEscape(out, body, i, 8, lineNumber);
                default -> throw new TomlException("Unknown escape '\\" + escape + "'", lineNumber);
            }
        }
        return out.toString();
    }

    /** Appends the code point of a short ('u') or long ('U') unicode escape, returning its last hex digit's index. */
    private static int appendUnicodeEscape(StringBuilder out, String body, int i, int digits, int lineNumber) {
        char form = body.charAt(i);
        if (i + digits >= body.length()) {
            throw new TomlException("Incomplete unicode escape", lineNumber);
        }
        String hex = body.substring(i + 1, i + 1 + digits);
        try {
            out.appendCodePoint(Integer.parseInt(hex, 16));
        } catch (NumberFormatException e) {
            throw new TomlException("Invalid unicode escape '" + "\\" + form + hex + "'", lineNumber);
        }
        return i + digits;
    }

    /** A malformed style file. */
    public static final class TomlException extends RuntimeException {

        private final String rawMessage;
        private final int line;

        TomlException(String message, int line) {
            super(message + " (line " + line + ")");
            this.rawMessage = message;
            this.line = line;
        }

        private TomlException(String rawMessage, int line, Path file) {
            super(file + ": " + rawMessage + " (line " + line + ")");
            this.rawMessage = rawMessage;
            this.line = line;
        }

        public int line() {
            return line;
        }

        /** This failure, naming the file it came from; {@link #read} only ever sees the document text. */
        public TomlException forFile(Path file) {
            return new TomlException(rawMessage, line, file);
        }

    }

}
