package zone.rong.formatj.core.layout;

import zone.rong.formatj.core.ir.Doc;
import zone.rong.formatj.core.ir.DocBreaks;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.Iterator;
import java.util.List;

/**
 * Renders a {@link Doc} to text, choosing which optional breaks to take.
 *
 * <p>The algorithm is the usual one: walk the document with an explicit stack, and when a group is
 * reached, look ahead to see whether it fits flat on what remains of the line. If it does, print it
 * flat; if it does not, break it and reconsider each nested group on its own. Line suffixes are held
 * back until the next line break, which is how a trailing comment stays trailing.
 */
public final class DocPrinter {

    private final int maxWidth;
    private final boolean useTabs;
    private final int tabWidth;
    private final String lineSeparator;
    private final boolean trimTrailingWhitespace;
    private final boolean indentBlankLines;

    /**
     * @param maxWidth columns a line may occupy before groups must break
     * @param useTabs whether indentation is written with tab characters
     * @param tabWidth columns one tab occupies, used both to write and to measure indentation
     * @param lineSeparator the terminator written at every line break
     * @param trimTrailingWhitespace whether a line's trailing spaces are dropped as it is closed
     * @param indentBlankLines whether a line with nothing on it still carries its indentation. The
     *     two settings meet on exactly those lines: trimming would strip the indentation away again,
     *     so this exempts a blank line from it and leaves every other line trimmed as before.
     */
    public DocPrinter(
            int maxWidth,
            boolean useTabs,
            int tabWidth,
            String lineSeparator,
            boolean trimTrailingWhitespace,
            boolean indentBlankLines) {
        this.maxWidth = maxWidth;
        this.useTabs = useTabs;
        this.tabWidth = tabWidth;
        this.lineSeparator = lineSeparator;
        this.trimTrailingWhitespace = trimTrailingWhitespace;
        this.indentBlankLines = indentBlankLines;
    }

    /** A printer indenting with spaces and ending lines with a newline. */
    public static DocPrinter ofSpaces(int maxWidth) {
        return new DocPrinter(maxWidth, false, 4, "\n", true, false);
    }

    /** Indentation text for a column count, honouring the tab settings. */
    private String indentation(int columns) {
        if (!useTabs) {
            return " ".repeat(columns);
        }
        return "\t".repeat(columns / tabWidth) + " ".repeat(columns % tabWidth);
    }

    private enum Mode {

        FLAT,
        BREAK

    }

    private record Command(int indent, Mode mode, Doc doc) { }

    public String print(Doc document) {
        Doc prepared = DocBreaks.propagate(document);
        StringBuilder out = new StringBuilder();
        List<Doc> lineSuffixes = new ArrayList<>();
        Deque<Command> commands = new ArrayDeque<>();
        commands.push(new Command(0, Mode.BREAK, prepared));
        int column = 0;

        while (!commands.isEmpty()) {
            Command command = commands.pop();
            Doc doc = command.doc();
            int indent = command.indent();
            Mode mode = command.mode();

            switch (doc) {
                case Doc.Text text -> {
                    out.append(text.value());
                    column += text.value().length();
                }
                case Doc.Concat concat -> pushReversed(commands, concat.parts(), indent, mode);
                case Doc.Indent nested -> commands.push(new Command(indent + nested.columns(), mode, nested.content()));
                case Doc.Align align -> commands.push(new Command(column, mode, align.content()));
                case Doc.Group group -> {
                    boolean flat = !group.shouldBreak()
                                    && mode == Mode.BREAK
                                    && fits(group.content(), maxWidth - column, commands)
                            || mode == Mode.FLAT && !group.shouldBreak();
                    commands.push(new Command(indent, flat ? Mode.FLAT : Mode.BREAK, group.content()));
                }
                case Doc.Fill fill -> printFill(commands, fill.parts(), indent, mode, maxWidth - column);
                case Doc.IfBreak ifBreak ->
                        commands.push(
                                new Command(indent, mode, mode == Mode.BREAK ? ifBreak.broken() : ifBreak.flat()));
                case Doc.LineSuffix suffix -> lineSuffixes.add(suffix.content());
                case Doc.BreakParent ignored -> {
                    // Handled by DocBreaks.propagate before printing starts.
                }
                case Doc.Break lineBreak -> {
                    if (mode == Mode.FLAT && lineBreak.kind() != Doc.BreakKind.HARD) {
                        if (lineBreak.kind() == Doc.BreakKind.LINE) {
                            out.append(' ');
                            column++;
                        }
                        break;
                    }
                    if (!lineSuffixes.isEmpty()) {
                        commands.push(new Command(indent, mode, lineBreak));
                        for (int i = lineSuffixes.size() - 1; i >= 0; i--) {
                            commands.push(new Command(indent, mode, lineSuffixes.get(i)));
                        }
                        lineSuffixes.clear();
                        break;
                    }
                    if (trimTrailingWhitespace && !(indentBlankLines && lineIsAllWhitespace(out))) {
                        trimTrailingSpaces(out);
                    }
                    out.append(lineSeparator).append(indentation(indent));
                    column = indent;
                }
            }
        }

        for (Doc suffix : lineSuffixes) {
            out.append(
                    new DocPrinter(
                                    maxWidth,
                                    useTabs,
                                    tabWidth,
                                    lineSeparator,
                                    trimTrailingWhitespace,
                                    indentBlankLines)
                            .print(suffix));
        }
        return out.toString();
    }

    /**
     * Fills as many parts onto the current line as fit.
     *
     * <p>Parts alternate content and separator. Each step decides only about the part in hand and the
     * one after it, then pushes the remainder back as another fill, which is what lets a list wrap
     * mid-line instead of collapsing all at once the way a broken group does.
     */
    private void printFill(Deque<Command> commands, List<Doc> parts, int indent, Mode mode, int remaining) {
        if (parts.isEmpty()) {
            return;
        }
        Doc content = parts.getFirst();
        boolean contentFits = fits(content, remaining, commands);
        if (parts.size() == 1) {
            commands.push(new Command(indent, contentFits ? Mode.FLAT : Mode.BREAK, content));
            return;
        }
        Doc separator = parts.get(1);
        if (parts.size() == 2) {
            Mode chosen = contentFits ? Mode.FLAT : Mode.BREAK;
            commands.push(new Command(indent, chosen, separator));
            commands.push(new Command(indent, chosen, content));
            return;
        }
        List<Doc> rest = parts.subList(2, parts.size());
        Doc pair = Doc.concat(List.of(content, separator, parts.get(2)));
        boolean pairFits = fits(pair, remaining, commands);

        commands.push(new Command(indent, mode, Doc.fill(rest)));
        if (pairFits) {
            commands.push(new Command(indent, Mode.FLAT, separator));
            commands.push(new Command(indent, Mode.FLAT, content));
        } else if (contentFits) {
            commands.push(new Command(indent, Mode.BREAK, separator));
            commands.push(new Command(indent, Mode.FLAT, content));
        } else {
            commands.push(new Command(indent, Mode.BREAK, separator));
            commands.push(new Command(indent, Mode.BREAK, content));
        }
    }

    private static void pushReversed(Deque<Command> commands, List<Doc> parts, int indent, Mode mode) {
        for (int i = parts.size() - 1; i >= 0; i--) {
            commands.push(new Command(indent, mode, parts.get(i)));
        }
    }

    /**
     * Whether {@code doc}, printed flat, fits in what is left of the line.
     *
     * <p>What follows the document on the same line counts too: a group that fits on its own can
     * still push the closing bracket and semicolon after it past the margin, and deciding without
     * looking at them is how formatters end up emitting lines one or two characters too long.
     */
    private boolean fits(Doc doc, int remaining, Deque<Command> rest) {
        if (remaining < 0) {
            return false;
        }
        Deque<Command> queue = new ArrayDeque<>();
        queue.push(new Command(0, Mode.FLAT, doc));
        Iterator<Command> following = rest.iterator();
        int left = remaining;

        while (left >= 0) {
            if (queue.isEmpty()) {
                if (!following.hasNext()) {
                    return true;
                }
                queue.push(following.next());
                continue;
            }
            Command command = queue.pop();
            Mode mode = command.mode();
            switch (command.doc()) {
                case Doc.Text text -> left -= text.value().length();
                case Doc.Concat concat -> pushReversed(queue, concat.parts(), 0, mode);
                case Doc.Fill fill -> pushReversed(queue, fill.parts(), 0, mode);
                case Doc.Group group ->
                        queue.push(new Command(0, group.shouldBreak() ? Mode.BREAK : mode, group.content()));
                case Doc.Indent indent -> queue.push(new Command(0, mode, indent.content()));
                case Doc.Align align -> queue.push(new Command(0, mode, align.content()));
                case Doc.IfBreak ifBreak ->
                        queue.push(new Command(0, mode, mode == Mode.BREAK ? ifBreak.broken() : ifBreak.flat()));
                case Doc.LineSuffix ignored -> {
                    // Suffixes are printed at the next break, not on this line.
                }
                case Doc.BreakParent ignored -> {
                    // Break propagation happens before printing.
                }
                case Doc.Break lineBreak -> {
                    if (mode == Mode.BREAK || lineBreak.kind() == Doc.BreakKind.HARD) {
                        // The line ends here, so everything measured so far did fit.
                        return mode == Mode.BREAK;
                    }
                    if (lineBreak.kind() == Doc.BreakKind.LINE) {
                        left--;
                    }
                }
            }
        }
        return false;
    }

    /** Whether nothing but whitespace has been written since the last line separator. */
    private boolean lineIsAllWhitespace(StringBuilder out) {
        int start = out.lastIndexOf(lineSeparator);
        for (int i = start < 0 ? 0 : start + lineSeparator.length(); i < out.length(); i++) {
            char current = out.charAt(i);
            if (current != ' ' && current != '\t') {
                return false;
            }
        }
        return true;
    }

    private static void trimTrailingSpaces(StringBuilder out) {
        int end = out.length();
        while (end > 0 && (out.charAt(end - 1) == ' ' || out.charAt(end - 1) == '\t')) {
            end--;
        }
        out.setLength(end);
    }

}
