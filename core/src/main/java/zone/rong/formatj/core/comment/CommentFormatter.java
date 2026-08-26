package zone.rong.formatj.core.comment;

import zone.rong.formatj.api.Option;
import zone.rong.formatj.api.Style;
import zone.rong.formatj.api.rules.CommentReflow;
import zone.rong.formatj.api.rules.CommentRules;
import zone.rong.formatj.api.rules.FileRules;
import zone.rong.formatj.api.rules.JavadocRules;
import zone.rong.formatj.api.rules.JavadocTagOrder;
import zone.rong.formatj.core.ir.Doc;
import zone.rong.formatj.core.lexer.Token;
import zone.rong.formatj.core.lexer.TokenKind;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Turns a comment into a document, under the rules that are allowed to rearrange it.
 *
 * <p>Everything here is layout, in the strict sense the formatter uses that word: it may put the
 * same words on different lines, and it may not do anything else. {@code ProsePreservation} checks
 * the result of every file against exactly that, so this class does not have to be trusted — a bug
 * in it costs the file its formatting rather than its comments.
 *
 * <h2>What is refused</h2>
 *
 * <ul>
 *   <li>Trailing comments are never re-wrapped. A comment that trails code has one line to live on,
 *       and a second line of it would land under the code rather than after it.
 *   <li>A comment holding a {@code {@code}}, {@code <pre>} or {@code @snippet} region keeps the lines
 *       it was written with. The region's own whitespace is content.
 *   <li>A run of {@code //} lines with no space after the slashes is left alone. That is what
 *       commented-out code looks like, and re-flowing it would run the statements together.
 *   <li>Anything carrying a formatter-off or formatter-on marker, which has to stay legible as the
 *       marker the reader wrote.
 * </ul>
 */
public final class CommentFormatter {

    private final Style style;

    public CommentFormatter(Style style) {
        this.style = style;
    }

    private <T> T rule(Option<T> option) {
        return style.get(option);
    }

    // ------------------------------------------------------------ entry points

    /**
     * A comment that trails code on its own line, which is never re-wrapped.
     *
     * @see #ownLine(List)
     */
    public Doc trailing(Token comment) {
        return verbatim(comment);
    }

    /**
     * A run of comments the author put on their own lines, laid out as one unit.
     *
     * <p>A run rather than a comment because that is the unit re-flowing works on: three {@code //}
     * lines are one paragraph, and refilling them one at a time would only ever make each line
     * shorter. The run is what the caller has already decided belongs together.
     */
    public Doc ownLine(List<Token> run) {
        if (run.size() == 1 && run.getFirst().kind() == TokenKind.JAVADOC_COMMENT) {
            return javadoc(run.getFirst());
        }
        if (run.size() == 1 && run.getFirst().kind() == TokenKind.BLOCK_COMMENT) {
            return blockComment(run.getFirst());
        }
        return lineComments(run);
    }

    /** Whether a run of line comments may be laid out as one unit rather than one at a time. */
    public boolean joinsLineComments() {
        return rule(CommentRules.REFLOW) == CommentReflow.REFLOW_TO_LINE_LENGTH;
    }

    // ------------------------------------------------------------ line comments

    private Doc lineComments(List<Token> run) {
        if (rule(CommentRules.REFLOW) != CommentReflow.REFLOW_TO_LINE_LENGTH || !reflowable(run)) {
            List<Doc> parts = new ArrayList<>();
            for (int i = 0; i < run.size(); i++) {
                if (i > 0) {
                    parts.add(Doc.hardLine());
                }
                parts.add(verbatim(run.get(i)));
            }
            return Doc.concat(parts);
        }

        String marker = slashes(run.getFirst().text());
        List<String> words = words(run);
        if (words.isEmpty()) {
            return verbatim(run.getFirst());
        }
        return Doc.align(Doc.concat(Doc.text(marker + " "), fill(words, marker + " ")));
    }

    /** The leading run of slashes, so that a {@code ///} documentation comment stays one. */
    private static String slashes(String text) {
        int index = 0;
        while (index < text.length() && text.charAt(index) == '/') {
            index++;
        }
        return text.substring(0, index);
    }

    // ----------------------------------------------------------- block comments

    private Doc blockComment(Token comment) {
        if (rule(CommentRules.REFLOW) != CommentReflow.REFLOW_TO_LINE_LENGTH
                || !reflowable(List.of(comment))
                || !rule(CommentRules.BLOCK_COMMENT_STAR_ALIGNMENT)) {
            return verbatim(comment);
        }
        List<String> words = words(List.of(comment));
        if (words.isEmpty()) {
            return verbatim(comment);
        }
        return Doc.align(
                Doc.concat(
                        Doc.text("/*"),
                        Doc.hardLine(),
                        Doc.text(" * "),
                        fill(words, " * "),
                        Doc.hardLine(),
                        Doc.text(" */")));
    }

    // ---------------------------------------------------------------- javadoc

    private Doc javadoc(Token comment) {
        Javadoc parsed = Javadoc.parse(comment.text());
        if (!restructures(parsed)) {
            return verbatim(comment);
        }
        if (parsed.singleLine() && parsed.tags().isEmpty() && rule(JavadocRules.KEEP_SINGLE_LINE)) {
            return verbatim(comment);
        }

        List<Doc> lines = new ArrayList<>(descriptionDocs(describe(parsed)));
        List<Javadoc.Tag> tags = ordered(parsed.tags());
        if (!tags.isEmpty() && !lines.isEmpty() && rule(JavadocRules.BLANK_LINE_BEFORE_TAGS)) {
            lines.add(Doc.hardLine());
            lines.add(Doc.text(" *"));
        }
        Map<String, Integer> columns = rule(JavadocRules.ALIGN_TAG_DESCRIPTIONS) ? descriptionColumns(tags) : Map.of();
        for (Javadoc.Tag tag : tags) {
            lines.addAll(tagLines(tag, columns.getOrDefault(tag.name(), 0)));
        }

        return Doc.align(Doc.concat(Doc.text("/**"), Doc.concat(lines), Doc.hardLine(), Doc.text(" */")));
    }

    /** Whether any rule that is on would rearrange this comment; if none would, it is left alone. */
    private boolean restructures(Javadoc parsed) {
        if (rule(JavadocRules.WRAP)
                || rule(JavadocRules.ADD_PARAGRAPH_TAGS)
                || rule(JavadocRules.ALIGN_TAG_DESCRIPTIONS)) {
            return true;
        }
        if (rule(JavadocRules.TAG_ORDER) != JavadocTagOrder.PRESERVE && !sorted(parsed.tags())) {
            return true;
        }
        return !parsed.tags().isEmpty()
                && !parsed.description().isEmpty()
                && parsed.blankBeforeTags() != rule(JavadocRules.BLANK_LINE_BEFORE_TAGS);
    }

    private static boolean sorted(List<Javadoc.Tag> tags) {
        for (int i = 1; i < tags.size(); i++) {
            if (Javadoc.canonicalRank(tags.get(i - 1)) > Javadoc.canonicalRank(tags.get(i))) {
                return false;
            }
        }
        return true;
    }

    /**
     * The tags in the order the style asks for.
     *
     * <p>A stable sort, which is not a detail: {@code @param} tags say which parameter they document
     * by name, but a reader takes their order from the signature, and two of them swapping places
     * would be a change to the documentation rather than to its layout.
     */
    private List<Javadoc.Tag> ordered(List<Javadoc.Tag> tags) {
        if (rule(JavadocRules.TAG_ORDER) == JavadocTagOrder.PRESERVE) {
            return tags;
        }
        List<Javadoc.Tag> sorted = new ArrayList<>(tags);
        sorted.sort(Comparator.comparingInt(Javadoc::canonicalRank));
        return sorted;
    }

    /**
     * The description, as lines under the star column.
     *
     * <p>Paragraphs are the unit when wrapping, because a paragraph is what a reader sees as one
     * body of text; a blank line, a piece of block markup or a code sample ends one, and each is
     * refilled on its own so that the shape the author gave the comment survives.
     */
    private List<Doc> descriptionDocs(List<String> lines) {
        List<Doc> parts = new ArrayList<>();
        if (!rule(JavadocRules.WRAP)) {
            for (String line : lines) {
                parts.add(Doc.hardLine());
                parts.add(starred(line));
            }
            return parts;
        }
        List<String> paragraph = new ArrayList<>();
        for (String line : lines) {
            boolean marker = line.strip().equalsIgnoreCase("<p>");
            if (line.isBlank() || marker) {
                parts.addAll(paragraphDocs(paragraph));
                paragraph.clear();
                parts.add(Doc.hardLine());
                parts.add(marker ? starred(line) : Doc.text(" *"));
                continue;
            }
            paragraph.add(line);
        }
        parts.addAll(paragraphDocs(paragraph));
        return parts;
    }

    private List<Doc> paragraphDocs(List<String> paragraph) {
        List<Doc> parts = new ArrayList<>();
        if (paragraph.isEmpty()) {
            return parts;
        }
        if (!reflowableLines(paragraph)) {
            for (String line : paragraph) {
                parts.add(Doc.hardLine());
                parts.add(starred(line));
            }
            return parts;
        }
        List<String> words = new ArrayList<>();
        for (Prose.Atom atom : Prose.atoms(String.join("\n", paragraph))) {
            words.add(atom.text());
        }
        if (words.isEmpty()) {
            return parts;
        }
        parts.add(Doc.hardLine());
        parts.add(Doc.concat(Doc.text(" * "), fill(words, " * ")));
        return parts;
    }

    /** The description, with paragraph markers written on its blank lines when the rule asks. */
    private List<String> describe(Javadoc parsed) {
        List<String> lines = parsed.description();
        if (!rule(JavadocRules.ADD_PARAGRAPH_TAGS)) {
            return lines;
        }
        List<String> marked = new ArrayList<>(lines.size());
        for (String line : lines) {
            marked.add(line.isBlank() ? " <p>" : line);
        }
        return marked;
    }

    /**
     * The column each kind of tag pads its description out to.
     *
     * <p>Per tag rather than over the whole comment. A run of {@code @param} tags is a table and
     * reads as one; a lone {@code @throws} naming a long exception is not part of that table, and
     * letting it set the column would push every parameter description halfway across the line to no
     * one's benefit.
     */
    private static Map<String, Integer> descriptionColumns(List<Javadoc.Tag> tags) {
        Map<String, Integer> columns = new LinkedHashMap<>();
        for (Javadoc.Tag tag : tags) {
            columns.merge(tag.name(), head(tag.lines().getFirst()).length(), Math::max);
        }
        return columns;
    }

    /**
     * The part of a tag's first line that names what it documents: the tag, and for the tags that
     * take one, the parameter or exception after it.
     */
    private static String head(String line) {
        String stripped = line.strip();
        int space = stripped.indexOf(' ');
        if (space < 0) {
            return stripped;
        }
        String tag = stripped.substring(0, space).toLowerCase(Locale.ROOT);
        if (!tag.equals("@param") && !tag.equals("@throws") && !tag.equals("@exception")) {
            return stripped.substring(0, space);
        }
        int second = stripped.indexOf(' ', space + 1);
        return second < 0 ? stripped : stripped.substring(0, second);
    }

    private List<Doc> tagLines(Javadoc.Tag tag, int column) {
        List<String> lines = tag.lines();
        String first = lines.getFirst().strip();
        String head = head(first);
        String rest = first.substring(head.length()).stripLeading();
        String padding = " ".repeat(Math.max(0, column - head.length()));
        String opening = " " + head + (rest.isEmpty() ? "" : padding + " " + rest);

        List<Doc> parts = new ArrayList<>();
        if (!rule(JavadocRules.WRAP) || !reflowableLines(lines)) {
            parts.add(Doc.hardLine());
            parts.add(starred(opening));
            for (String line : lines.subList(1, lines.size())) {
                parts.add(Doc.hardLine());
                parts.add(starred(line));
            }
            return parts;
        }

        List<String> words = new ArrayList<>();
        for (Prose.Atom atom : Prose.atoms(String.join("\n", lines))) {
            words.add(atom.text());
        }
        // The head and, where there is one, the name it documents are part of the tag, not its prose.
        int consumed = head.split(" ").length;
        List<String> body = words.subList(Math.min(consumed, words.size()), words.size());
        String indent = " * " + " ".repeat(Math.max(0, rule(JavadocRules.TAG_CONTINUATION_INDENT)));
        parts.add(Doc.hardLine());
        if (body.isEmpty()) {
            parts.add(starred(" " + head));
            return parts;
        }
        parts.add(Doc.concat(Doc.text(" * " + head + padding + " "), fill(body, indent)));
        return parts;
    }

    // ---------------------------------------------------------------- shared

    /** One line of a block comment, written under the star column. */
    private Doc starred(String line) {
        return Doc.text(stripTrailing(" *" + line));
    }

    /**
     * Words filled onto as many lines as they need.
     *
     * <p>{@link Doc.Fill} is what does the deciding, so the margin, the tab width and the column the
     * comment starts in are all the layout engine's business rather than this class's. The separator
     * carries the star or the slashes, which is the one thing a general-purpose fill does not know
     * about.
     */
    private Doc fill(List<String> words, String prefix) {
        Doc separator = Doc.ifBreak(Doc.concat(Doc.hardLine(), Doc.text(prefix)), Doc.text(" "));
        List<Doc> parts = new ArrayList<>(words.size() * 2);
        for (int i = 0; i < words.size(); i++) {
            if (i > 0) {
                parts.add(separator);
            }
            parts.add(Doc.text(words.get(i)));
        }
        return Doc.fill(parts);
    }

    /** Every word of a run of comments, in order. */
    private static List<String> words(List<Token> run) {
        List<String> words = new ArrayList<>();
        for (Token comment : run) {
            for (Prose.Atom atom : Prose.atoms(comment)) {
                words.add(atom.text());
            }
        }
        return words;
    }

    /** Whether a run of comments is prose rather than something whose lines carry meaning. */
    private boolean reflowable(List<Token> run) {
        for (Token comment : run) {
            for (Prose.Atom atom : Prose.atoms(comment)) {
                if (atom.verbatim()) {
                    return false;
                }
            }
            if (holdsMarker(comment.text())) {
                return false;
            }
            if (comment.kind() == TokenKind.LINE_COMMENT) {
                String content = Prose.content(comment);
                if (!content.isBlank() && !content.startsWith(" ")) {
                    // No space after the slashes: commented-out code, not a sentence.
                    return false;
                }
            }
        }
        return true;
    }

    private boolean reflowableLines(List<String> lines) {
        String joined = String.join("\n", lines);
        for (Prose.Atom atom : Prose.atoms(joined)) {
            if (atom.verbatim()) {
                return false;
            }
        }
        for (String line : lines) {
            String stripped = line.strip();
            if (stripped.startsWith("<") || stripped.startsWith("|") || holdsMarker(line)) {
                // Block markup and tables are laid out by their own lines, not by the margin.
                return false;
            }
        }
        return true;
    }

    private boolean holdsMarker(String text) {
        if (!rule(CommentRules.HONOUR_FORMATTER_OFF)) {
            return false;
        }
        String off = rule(CommentRules.OFF_MARKER);
        String on = rule(CommentRules.ON_MARKER);
        return !off.isBlank() && text.contains(off) || !on.isBlank() && text.contains(on);
    }

    // ------------------------------------------------------------- verbatim

    /** A comment re-indented but never re-worded, which is what every rule here defaults to. */
    public Doc verbatim(Token comment) {
        if (comment.kind() == TokenKind.LINE_COMMENT) {
            return Doc.text(stripTrailing(comment.text()));
        }
        String[] lines = comment.text().split("\r\n|\r|\n", -1);
        if (lines.length == 1) {
            return Doc.text(stripTrailing(comment.text()));
        }
        boolean alignStars = rule(CommentRules.BLOCK_COMMENT_STAR_ALIGNMENT);
        List<Doc> parts = new ArrayList<>();
        parts.add(Doc.text(stripTrailing(lines[0])));
        for (int i = 1; i < lines.length; i++) {
            String line = stripTrailing(lines[i]);
            String trimmed = line.strip();
            parts.add(Doc.hardLine());
            if (alignStars && trimmed.startsWith("*")) {
                parts.add(Doc.text(" " + trimmed));
            } else {
                parts.add(Doc.text(line.stripLeading().isEmpty() ? "" : line.strip()));
            }
        }
        return Doc.concat(parts);
    }

    private String stripTrailing(String text) {
        if (!rule(FileRules.TRIM_TRAILING_WHITESPACE)) {
            return text;
        }
        int end = text.length();
        while (end > 0 && (text.charAt(end - 1) == ' ' || text.charAt(end - 1) == '\t')) {
            end--;
        }
        return text.substring(0, end);
    }

}
