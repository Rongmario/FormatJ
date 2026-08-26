package zone.rong.formatj.core.text;

import java.util.ArrayList;
import java.util.List;

/**
 * The string a text block produces, and the ways of rewriting one that do not change it.
 *
 * <p>A text block is the one token whose layout is also its meaning. Every other token can be moved,
 * indented and re-indented freely because the characters of the token itself never change; a text
 * block's characters are its indentation, so "just re-indent it" is a claim about the program's
 * behaviour and has to be checked as one.
 *
 * <p>{@link #value} is that check's anchor: it is the string the language says the block denotes,
 * with incidental white space removed and escapes interpreted, as specified in JLS 3.10.6. Two text
 * blocks written differently but denoting the same string are the same token as far as the
 * formatter's verification is concerned, which is what lets the layout engine re-indent one and what
 * makes a rule that does change the string visible the moment it does.
 */
public final class TextBlocks {

    private TextBlocks() { }

    /** Whether a lexeme is a text block rather than an ordinary string literal. */
    public static boolean isTextBlock(String lexeme) {
        return lexeme.length() >= 6 && lexeme.startsWith("\"\"\"") && lexeme.endsWith("\"\"\"");
    }

    // --------------------------------------------------------------- reading

    /**
     * The characters between the line terminator that opens the block and the closing delimiter,
     * with line terminators normalised to {@code \n}.
     */
    public static String rawContent(String lexeme) {
        int index = 3;
        while (index < lexeme.length() && isLineWhitespace(lexeme.charAt(index))) {
            index++;
        }
        if (index < lexeme.length() && lexeme.charAt(index) == '\r') {
            index++;
        }
        if (index < lexeme.length() && lexeme.charAt(index) == '\n') {
            index++;
        }
        int end = Math.max(index, lexeme.length() - 3);
        return lexeme.substring(index, end).replace("\r\n", "\n").replace('\r', '\n');
    }

    /** What the author wrote between the opening delimiter and the line terminator after it. */
    public static String openingTail(String lexeme) {
        int index = 3;
        int start = index;
        while (index < lexeme.length() && isLineWhitespace(lexeme.charAt(index))) {
            index++;
        }
        return lexeme.substring(start, index);
    }

    /** The content lines, in order; the last is the one the closing delimiter sits on. */
    public static List<String> contentLines(String lexeme) {
        return List.of(rawContent(lexeme).split("\n", -1));
    }

    /**
     * The incidental indentation: the white space every significant line shares.
     *
     * <p>Blank lines do not count, because indentation is not visible on a line with nothing on it.
     * The last line always counts, even when it is blank, because that is the line the closing
     * delimiter is on and putting the delimiter further left is how an author says "and none of this
     * indentation is mine".
     */
    public static int incidentalIndent(List<String> lines) {
        int minimum = Integer.MAX_VALUE;
        for (int i = 0; i < lines.size(); i++) {
            String line = lines.get(i);
            boolean last = i == lines.size() - 1;
            if (isBlankLine(line) && !last) {
                continue;
            }
            minimum = Math.min(minimum, last && isBlankLine(line) ? line.length() : leadingWhitespace(line));
        }
        return minimum == Integer.MAX_VALUE ? 0 : minimum;
    }

    /** The content lines with the incidental indentation and every line's trailing space removed. */
    public static List<String> strippedLines(String lexeme) {
        List<String> lines = contentLines(lexeme);
        int indent = incidentalIndent(lines);
        List<String> stripped = new ArrayList<>(lines.size());
        for (String line : lines) {
            String withoutIndent = line.length() < indent ? "" : line.substring(indent);
            stripped.add(stripTrailing(withoutIndent));
        }
        return List.copyOf(stripped);
    }

    /**
     * The string the block denotes.
     *
     * <p>Incidental white space removed, then escapes interpreted, in that order — which is why a
     * space written as {@code \s} survives at the end of a line and the same space written as a space
     * does not.
     */
    public static String value(String lexeme) {
        return unescape(String.join("\n", strippedLines(lexeme)));
    }

    // -------------------------------------------------------------- rewriting

    /**
     * The block written with the closing delimiter on a line of its own.
     *
     * <p>The one change the formatter can make to a text block that a reader would call cosmetic and
     * the language would not: the value gains a line terminator it did not have. Declared as an edit
     * and checked against {@code text-blocks.closing-delimiter-on-own-line}'s law, like every other
     * change to a program's meaning.
     */
    public static String withClosingDelimiterOnOwnLine(String lexeme) {
        List<String> lines = contentLines(lexeme);
        if (lines.size() > 1 && isBlankLine(lines.getLast())) {
            return lexeme;
        }
        int indent = incidentalIndent(lines);
        List<String> moved = new ArrayList<>(lines);
        moved.add(" ".repeat(indent));
        return rebuild(lexeme, moved);
    }

    /** Whether the closing delimiter already sits on a line of its own. */
    public static boolean closingDelimiterOnOwnLine(String lexeme) {
        List<String> lines = contentLines(lexeme);
        return lines.size() > 1 && isBlankLine(lines.getLast());
    }

    /**
     * The block with the trailing spaces the author wrote made significant.
     *
     * <p>White space at the end of a line of a text block is thrown away by the language, so an
     * author who wrote it either meant nothing by it or meant something the language did not give
     * them. This rule takes the second reading and escapes the last of those spaces, which stops the
     * whole run being incidental. It is a change to the string, and it is declared as one.
     */
    public static String withEscapedTrailingSpaces(String lexeme) {
        List<String> lines = contentLines(lexeme);
        int indent = incidentalIndent(lines);
        List<String> escaped = new ArrayList<>(lines.size());
        boolean changed = false;
        for (int i = 0; i < lines.size(); i++) {
            String line = lines.get(i);
            boolean last = i == lines.size() - 1;
            if (last || isBlankLine(line) || line.length() <= indent) {
                escaped.add(line);
                continue;
            }
            String stripped = stripTrailing(line);
            if (stripped.length() == line.length()) {
                escaped.add(line);
                continue;
            }
            escaped.add(line.substring(0, line.length() - 1) + "\\s");
            changed = true;
        }
        return changed ? rebuild(lexeme, escaped) : lexeme;
    }

    /** Puts a set of content lines back into a text block, keeping the delimiters as they were. */
    private static String rebuild(String lexeme, List<String> lines) {
        return "\"\"\"" + openingTail(lexeme) + "\n" + String.join("\n", lines) + "\"\"\"";
    }

    // --------------------------------------------------------------- escapes

    /** Interprets the escape sequences of a text block, line continuations included. */
    public static String unescape(String text) {
        StringBuilder out = new StringBuilder(text.length());
        for (int i = 0; i < text.length(); i++) {
            char current = text.charAt(i);
            if (current != '\\' || i + 1 >= text.length()) {
                out.append(current);
                continue;
            }
            char next = text.charAt(++i);
            switch (next) {
                case 'b' -> out.append('\b');
                case 't' -> out.append('\t');
                case 'n' -> out.append('\n');
                case 'f' -> out.append('\f');
                case 'r' -> out.append('\r');
                case 's' -> out.append(' ');
                case '"' -> out.append('"');
                case '\'' -> out.append('\'');
                case '\\' -> out.append('\\');
                // A backslash at the end of a line joins it to the next, producing nothing itself.
                case '\n' -> { }
                default -> {
                    if (next >= '0' && next <= '7') {
                        i = octal(text, i, out);
                    } else {
                        out.append('\\').append(next);
                    }
                }
            }
        }
        return out.toString();
    }

    private static int octal(String text, int start, StringBuilder out) {
        int value = 0;
        int index = start;
        int digits = 0;
        int limit = text.charAt(start) <= '3' ? 3 : 2;
        while (index < text.length() && digits < limit && text.charAt(index) >= '0' && text.charAt(index) <= '7') {
            value = value * 8 + (text.charAt(index) - '0');
            index++;
            digits++;
        }
        out.append((char) value);
        return index - 1;
    }

    // ---------------------------------------------------------------- shared

    private static boolean isLineWhitespace(char current) {
        return current == ' ' || current == '\t' || current == '\f';
    }

    /** Whether the line contains only the white space Java treats as incidental in a text block. */
    private static boolean isBlankLine(String line) {
        for (int i = 0; i < line.length(); i++) {
            if (!isLineWhitespace(line.charAt(i))) {
                return false;
            }
        }
        return true;
    }

    private static int leadingWhitespace(String line) {
        int index = 0;
        while (index < line.length() && isLineWhitespace(line.charAt(index))) {
            index++;
        }
        return index;
    }

    private static String stripTrailing(String line) {
        int end = line.length();
        while (end > 0 && isLineWhitespace(line.charAt(end - 1))) {
            end--;
        }
        return line.substring(0, end);
    }

}
