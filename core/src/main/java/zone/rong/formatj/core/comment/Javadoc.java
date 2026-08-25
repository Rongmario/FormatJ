package zone.rong.formatj.core.comment;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * A Javadoc comment taken apart into the two things the {@code javadoc.*} rules have opinions about:
 * the description, and the block tags after it.
 *
 * <p>Lines are held as they read once the marker has gone — leading whitespace and the leading star
 * run removed, and nothing else. The space that usually follows the star is left on, so re-emitting a
 * line as {@code " *" + line} reproduces the source character for character. That is what lets the
 * rules that only rearrange structure leave everything they did not touch exactly as it was.
 *
 * <p>An {@code @} at the start of a line is a block tag only when it is not inside a verbatim region;
 * a line of a {@code <pre>} block that begins with an annotation is part of the sample. {@link Prose}
 * decides where those regions are, so the wrapper and the tag reader cannot disagree.
 */
public final class Javadoc {

    /**
     * One block tag and the lines belonging to it.
     *
     * @param name the tag including its {@code @}, lowercased for ordering purposes only
     * @param lines the tag's own line and every continuation line under it
     */
    public record Tag(String name, List<String> lines) {

        public Tag {
            lines = List.copyOf(lines);
        }

    }

    /**
     * The order the javadoc convention puts block tags in. Tags not named here keep to the end, in
     * the order the author had them, because inventing a position for a tag nobody standardised is
     * how a formatter starts losing arguments with its users.
     */
    private static final List<String> CANONICAL =
            List.of(
                    "@author",
                    "@version",
                    "@param",
                    "@return",
                    "@throws",
                    "@exception",
                    "@see",
                    "@since",
                    "@serial",
                    "@serialfield",
                    "@serialdata",
                    "@deprecated");

    private final List<String> description;
    private final List<Tag> tags;
    private final boolean singleLine;
    private final boolean blankBeforeTags;

    private Javadoc(List<String> description, List<Tag> tags, boolean singleLine, boolean blankBeforeTags) {
        this.description = List.copyOf(description);
        this.tags = List.copyOf(tags);
        this.singleLine = singleLine;
        this.blankBeforeTags = blankBeforeTags;
    }

    /** Whether the author left a blank line between the description and the first block tag. */
    public boolean blankBeforeTags() {
        return blankBeforeTags;
    }

    /** The description lines, in order, with trailing blank lines already dropped. */
    public List<String> description() {
        return description;
    }

    /** The block tags, in the order the author wrote them. */
    public List<Tag> tags() {
        return tags;
    }

    /** Whether the author wrote the whole comment on one line. */
    public boolean singleLine() {
        return singleLine;
    }

    /** Where this tag sits in the conventional order; unknown tags sort after every known one. */
    public static int canonicalRank(Tag tag) {
        int rank = CANONICAL.indexOf(tag.name());
        return rank < 0 ? CANONICAL.size() : rank;
    }

    // --------------------------------------------------------------- parse

    /** Reads a {@code /**} comment. */
    public static Javadoc parse(String text) {
        List<String> lines = displayLines(text);
        List<String> content = Prose.blockContentLines(text);
        List<int[]> verbatim = Prose.verbatimRanges(String.join("\n", content));

        List<String> description = new ArrayList<>();
        List<Tag> tags = new ArrayList<>();
        List<String> currentTag = null;
        String currentName = null;
        boolean blankBeforeTags = false;
        int offset = 0;

        for (int i = 0; i < lines.size(); i++) {
            String line = lines.get(i);
            boolean starts = !Prose.isInside(verbatim, offset) && tagName(line) != null;
            offset += (i < content.size() ? content.get(i).length() : 0) + 1;
            if (starts) {
                if (currentTag == null) {
                    blankBeforeTags = !description.isEmpty() && description.getLast().isBlank();
                }
                if (currentTag != null) {
                    tags.add(new Tag(currentName, currentTag));
                }
                currentName = tagName(line);
                currentTag = new ArrayList<>();
                currentTag.add(line);
                continue;
            }
            if (currentTag != null) {
                currentTag.add(line);
            } else {
                description.add(line);
            }
        }
        if (currentTag != null) {
            tags.add(new Tag(currentName, currentTag));
        }

        trimBlankEnds(description);
        List<Tag> trimmed = new ArrayList<>(tags.size());
        for (Tag tag : tags) {
            List<String> tagLines = new ArrayList<>(tag.lines());
            trimBlankEnds(tagLines);
            trimmed.add(new Tag(tag.name(), tagLines));
        }
        return new Javadoc(description, trimmed, lines.size() == 1, blankBeforeTags);
    }

    /** The tag a line opens, or null when it opens none. */
    private static String tagName(String line) {
        String stripped = line.strip();
        if (stripped.length() < 2 || stripped.charAt(0) != '@' || !Character.isLetter(stripped.charAt(1))) {
            return null;
        }
        int end = 1;
        while (end < stripped.length() && Character.isLetter(stripped.charAt(end))) {
            end++;
        }
        return stripped.substring(0, end).toLowerCase(Locale.ROOT);
    }

    /**
     * The lines of the comment with the markers gone but the space after the star kept.
     *
     * <p>Kept because {@code " *" + line} then reproduces the original, so a rule that only reorders
     * tags does not silently re-space every line it walked past on the way.
     */
    private static List<String> displayLines(String text) {
        String inner = text;
        if (inner.startsWith("/*")) {
            inner = inner.substring(2);
        }
        if (inner.endsWith("*/") && inner.length() >= 2) {
            inner = inner.substring(0, inner.length() - 2);
        }
        int start = 0;
        while (start < inner.length() && inner.charAt(start) == '*') {
            start++;
        }
        inner = inner.substring(start);

        String[] split = inner.split("\r\n|\r|\n", -1);
        List<String> lines = new ArrayList<>(split.length);
        lines.add(stripTrailing(split[0]));
        for (int i = 1; i < split.length; i++) {
            String line = split[i].stripLeading();
            int stars = 0;
            while (stars < line.length() && line.charAt(stars) == '*') {
                stars++;
            }
            lines.add(stripTrailing(line.substring(stars)));
        }
        if (lines.size() > 1 && lines.getLast().isBlank()) {
            lines.removeLast();
        }
        return lines;
    }

    private static void trimBlankEnds(List<String> lines) {
        while (!lines.isEmpty() && lines.getLast().isBlank()) {
            lines.removeLast();
        }
        while (!lines.isEmpty() && lines.getFirst().isBlank()) {
            lines.removeFirst();
        }
    }

    private static String stripTrailing(String text) {
        int end = text.length();
        while (end > 0 && (text.charAt(end - 1) == ' ' || text.charAt(end - 1) == '\t')) {
            end--;
        }
        return text.substring(0, end);
    }

}
