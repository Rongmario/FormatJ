package zone.rong.formatj.core.comment;

import zone.rong.formatj.core.lexer.Token;
import zone.rong.formatj.core.lexer.TokenKind;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * What a comment says, separated from how it is written.
 *
 * <p>Re-wrapping prose is the one thing the layout engine does that the token check cannot see:
 * comments are not significant tokens, so a rule that dropped half a sentence would pass every other
 * check the formatter has. The prose of a comment is therefore given a definition here, once, and
 * both sides of the argument read it: {@link CommentFormatter} may only rearrange the atoms this
 * class finds, and {@code ProsePreservation} fails the file when the atoms come out different.
 *
 * <p>An atom is either a word — a run of non-whitespace, which re-wrapping may move to another line
 * but may not alter — or a verbatim region, which re-wrapping may not touch at all. The verbatim
 * regions are the ones where whitespace is content rather than layout:
 *
 * <ul>
 *   <li>{@code {@code ...}} and {@code {@literal ...}}, to the brace that closes them,
 *   <li>{@code <pre>} to {@code </pre>},
 *   <li>{@code @snippet}, inline or as a block tag.
 * </ul>
 *
 * <p>Treating those as single atoms does double duty. The wrapper cannot break one across lines
 * because it is one atom, and the verifier compares one against the other character for character.
 * Neither has to be told separately not to reflow a code sample.
 */
public final class Prose {

    /**
     * One indivisible piece of a comment.
     *
     * @param verbatim whether the text is content rather than prose, and so must survive unaltered
     * @param text the characters themselves, line breaks included when verbatim
     */
    public record Atom(boolean verbatim, String text) {

        /** A word: a run of non-whitespace that may be moved to another line but not changed. */
        public static Atom word(String text) {
            return new Atom(false, text);
        }

        /** A region whose own whitespace is content, which nothing may reformat. */
        public static Atom verbatim(String text) {
            return new Atom(true, text);
        }

        /** Whether this atom is a paragraph marker, which is layout rather than prose. */
        public boolean isParagraphMarker() {
            if (verbatim) {
                return false;
            }
            String lower = text.toLowerCase(Locale.ROOT);
            return lower.equals("<p>") || lower.equals("</p>") || lower.equals("<p/>");
        }

    }

    private Prose() { }

    // ------------------------------------------------------------- content

    /**
     * The lines of a comment with its markers removed.
     *
     * <p>What comes back is what the comment says: no {@code //}, no {@code /*}, no leading star, and
     * for a block comment no more than one space after the star. Everything else is left alone, so a
     * line indented inside a {@code <pre>} block keeps its indentation relative to the star.
     */
    public static List<String> contentLines(Token comment) {
        if (comment.kind() == TokenKind.LINE_COMMENT) {
            return List.of(stripLeadingSlashes(comment.text()));
        }
        return blockContentLines(comment.text());
    }

    /** The content of a comment as one string, lines joined with {@code \n}. */
    public static String content(Token comment) {
        return String.join("\n", contentLines(comment));
    }

    private static String stripLeadingSlashes(String text) {
        int index = 0;
        while (index < text.length() && text.charAt(index) == '/') {
            index++;
        }
        return text.substring(index);
    }

    /** The content lines of a {@code /*} or {@code /**} comment. */
    public static List<String> blockContentLines(String text) {
        String inner = text;
        if (inner.startsWith("/*")) {
            inner = inner.substring(2);
        }
        if (inner.endsWith("*/") && inner.length() >= 2) {
            inner = inner.substring(0, inner.length() - 2);
        }
        // The third star of /** and any decorative ones after it belong to the marker, not the text.
        int start = 0;
        while (start < inner.length() && inner.charAt(start) == '*') {
            start++;
        }
        inner = inner.substring(start);

        String[] lines = inner.split("\r\n|\r|\n", -1);
        List<String> content = new ArrayList<>(lines.length);
        content.add(lines[0]);
        for (int i = 1; i < lines.length; i++) {
            content.add(stripStar(lines[i]));
        }
        return List.copyOf(content);
    }

    /** One continuation line of a block comment, without its leading whitespace and star. */
    private static String stripStar(String line) {
        String stripped = line.stripLeading();
        if (!stripped.startsWith("*")) {
            return stripped.isEmpty() ? "" : line.strip();
        }
        int index = 0;
        while (index < stripped.length() && stripped.charAt(index) == '*') {
            index++;
        }
        // Exactly one space, so that indentation inside a <pre> block is preserved relative to it.
        if (index < stripped.length() && stripped.charAt(index) == ' ') {
            index++;
        }
        return stripped.substring(index);
    }

    // --------------------------------------------------------------- atoms

    /** The atoms of a comment, in order. */
    public static List<Atom> atoms(Token comment) {
        return atoms(content(comment));
    }

    /**
     * The atoms of a comment's content.
     *
     * <p>Scanned rather than parsed. A verbatim opener wins wherever it starts, and everything
     * between it and its close is one atom however many words and line breaks it holds; outside one,
     * every run of non-whitespace is a word and whitespace is nothing at all.
     */
    public static List<Atom> atoms(String content) {
        List<Atom> atoms = new ArrayList<>();
        StringBuilder word = new StringBuilder();
        int index = 0;
        boolean lineStart = true;

        while (index < content.length()) {
            int end = verbatimEnd(content, index, lineStart);
            if (end > index) {
                flush(word, atoms);
                atoms.add(Atom.verbatim(content.substring(index, end)));
                lineStart = false;
                index = end;
                continue;
            }
            char current = content.charAt(index);
            if (Character.isWhitespace(current)) {
                flush(word, atoms);
                lineStart = current == '\n';
                index++;
                continue;
            }
            word.append(current);
            lineStart = false;
            index++;
        }
        flush(word, atoms);
        return List.copyOf(atoms);
    }

    /**
     * The character ranges of {@code content} that are verbatim, in order.
     *
     * <p>Used by {@link Javadoc} to tell a block tag from an {@code @} that happens to sit at the
     * start of a line inside a code sample. Sharing the scan is the point: a region the wrapper must
     * not touch and a region a tag cannot start in are the same region.
     */
    public static List<int[]> verbatimRanges(String content) {
        List<int[]> ranges = new ArrayList<>();
        int index = 0;
        boolean lineStart = true;
        while (index < content.length()) {
            int end = verbatimEnd(content, index, lineStart);
            if (end > index) {
                ranges.add(new int[] {index, end});
                lineStart = false;
                index = end;
                continue;
            }
            lineStart = content.charAt(index) == '\n';
            index++;
        }
        return List.copyOf(ranges);
    }

    /** Whether {@code offset} falls inside one of {@code ranges}. */
    public static boolean isInside(List<int[]> ranges, int offset) {
        for (int[] range : ranges) {
            if (offset >= range[0] && offset < range[1]) {
                return true;
            }
        }
        return false;
    }

    private static void flush(StringBuilder word, List<Atom> atoms) {
        if (!word.isEmpty()) {
            atoms.add(Atom.word(word.toString()));
            word.setLength(0);
        }
    }

    /**
     * Where the verbatim region starting at {@code index} ends, or {@code index} when none starts
     * there.
     *
     * <p>An unclosed region runs to the end of the comment. That is deliberate: a comment holding an
     * unterminated {@code <pre>} is one nobody should be re-wrapping, and treating the remainder as
     * content is the reading that changes the least.
     */
    private static int verbatimEnd(String content, int index, boolean lineStart) {
        if (startsWithIgnoreCase(content, index, "<pre>")) {
            int close = indexOfIgnoreCase(content, "</pre>", index);
            return close < 0 ? content.length() : close + "</pre>".length();
        }
        for (String tag : new String[] {"{@code", "{@literal", "{@snippet"}) {
            if (content.startsWith(tag, index) && endsTag(content, index + tag.length())) {
                return matchingBrace(content, index);
            }
        }
        if (lineStart && content.startsWith("@snippet", index) && endsTag(content, index + "@snippet".length())) {
            return blockTagEnd(content, index);
        }
        return index;
    }

    /** Whether an inline tag's name ends here, rather than {@code {@codex} being read as {@code}. */
    private static boolean endsTag(String content, int index) {
        return index >= content.length()
                || Character.isWhitespace(content.charAt(index))
                || content.charAt(index) == '}';
    }

    private static int matchingBrace(String content, int open) {
        int depth = 0;
        for (int i = open; i < content.length(); i++) {
            char current = content.charAt(i);
            if (current == '{') {
                depth++;
            } else if (current == '}') {
                depth--;
                if (depth == 0) {
                    return i + 1;
                }
            }
        }
        return content.length();
    }

    /** A block tag runs until the line that starts the next one, or to the end of the comment. */
    private static int blockTagEnd(String content, int start) {
        int index = content.indexOf('\n', start);
        while (index >= 0) {
            int lineStart = index + 1;
            int lineEnd = content.indexOf('\n', lineStart);
            String line = content.substring(lineStart, lineEnd < 0 ? content.length() : lineEnd).strip();
            if (line.startsWith("@")) {
                return index;
            }
            index = lineEnd;
        }
        return content.length();
    }

    private static boolean startsWithIgnoreCase(String content, int index, String prefix) {
        return content.regionMatches(true, index, prefix, 0, prefix.length());
    }

    private static int indexOfIgnoreCase(String content, String needle, int from) {
        for (int i = from; i + needle.length() <= content.length(); i++) {
            if (content.regionMatches(true, i, needle, 0, needle.length())) {
                return i;
            }
        }
        return -1;
    }

}
