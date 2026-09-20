package zone.rong.formatj.core.comment;

import zone.rong.formatj.core.lexer.Token;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

/** A traditional or Markdown documentation comment split into its description and block tags. */
public final class Javadoc {

    /** The source form of the documentation comment. */
    public enum Form {

        TRADITIONAL,
        MARKDOWN

    }

    /** How a Markdown block may be laid out. */
    public enum BlockKind {

        BLANK,
        PARAGRAPH,
        PRESERVED

    }

    /** One Markdown block whose line boundaries stay separate from neighbouring blocks. */
    public record Block(BlockKind kind, List<String> lines) {

        public Block {
            lines = List.copyOf(lines);
        }

    }

    /** One block tag and the lines and Markdown blocks belonging to it. */
    public record Tag(
            String name,
            String head,
            List<String> lines,
            List<Block> description,
            boolean descriptionStartsOnTagLine) {

        public Tag {
            lines = List.copyOf(lines);
            description = List.copyOf(description);
        }

    }

    /** The conventional order for block tags. Unknown tags stay after the known tags. */
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

    private static final Pattern ATX_HEADING = Pattern.compile("#{1,6}(?:\\s|$).*");
    private static final Pattern SETEXT_HEADING = Pattern.compile("(?:=+|-+)\\s*");
    private static final Pattern LIST_ITEM = Pattern.compile("(?:[-+*]|\\d+[.)])\\s+.*");
    private static final Pattern LINK_DEFINITION = Pattern.compile("\\[[^]]+]:\\s*\\S+.*");
    private static final Pattern THEMATIC_BREAK = Pattern.compile("(?:(?:\\*\\s*){3,}|(?:-\\s*){3,}|(?:_\\s*){3,})");

    private final Form form;
    private final List<String> description;
    private final List<Block> descriptionBlocks;
    private final List<Tag> tags;
    private final boolean singleLine;
    private final boolean blankBeforeTags;
    private final boolean safeToFormat;
    private final int markdownIndent;

    private Javadoc(
            Form form,
            List<String> description,
            List<Block> descriptionBlocks,
            List<Tag> tags,
            boolean singleLine,
            boolean blankBeforeTags,
            boolean safeToFormat,
            int markdownIndent) {
        this.form = form;
        this.description = List.copyOf(description);
        this.descriptionBlocks = List.copyOf(descriptionBlocks);
        this.tags = List.copyOf(tags);
        this.singleLine = singleLine;
        this.blankBeforeTags = blankBeforeTags;
        this.safeToFormat = safeToFormat;
        this.markdownIndent = markdownIndent;
    }

    public Form form() {
        return form;
    }

    /** Whether the author left a blank line between the description and the first block tag. */
    public boolean blankBeforeTags() {
        return blankBeforeTags;
    }

    /** The description lines with leading and trailing blank lines removed. */
    public List<String> description() {
        return description;
    }

    /** The Markdown description blocks, or an empty list for a traditional comment. */
    public List<Block> descriptionBlocks() {
        return descriptionBlocks;
    }

    /** The block tags in source order. */
    public List<Tag> tags() {
        return tags;
    }

    /** Whether the author wrote the whole comment on one line. */
    public boolean singleLine() {
        return singleLine;
    }

    /** Whether all Markdown delimiters needed for safe structural edits were recognized. */
    public boolean safeToFormat() {
        return safeToFormat;
    }

    /** The common indentation removed by the JDK from Markdown content. */
    public int markdownIndent() {
        return markdownIndent;
    }

    /** Where this tag sits in the conventional order. */
    public static int canonicalRank(Tag tag) {
        int rank = CANONICAL.indexOf(tag.name());
        return rank < 0 ? CANONICAL.size() : rank;
    }

    // --------------------------------------------------------------- parse

    /** Reads a traditional documentation comment. */
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
            String name = !Prose.isInside(verbatim, offset) ? tagName(line) : null;
            offset += (i < content.size() ? content.get(i).length() : 0) + 1;
            if (name != null) {
                if (currentTag == null) {
                    blankBeforeTags = !description.isEmpty() && description.getLast().isBlank();
                } else {
                    tags.add(traditionalTag(currentName, currentTag));
                }
                currentName = name;
                currentTag = new ArrayList<>();
                currentTag.add(line);
            } else if (currentTag != null) {
                currentTag.add(line);
            } else {
                description.add(line);
            }
        }
        if (currentTag != null) {
            tags.add(traditionalTag(currentName, currentTag));
        }

        trimBlankEnds(description);
        return new Javadoc(Form.TRADITIONAL, description, List.of(), tags, lines.size() == 1, blankBeforeTags, true, 0);
    }

    /** Reads one physically contiguous Markdown documentation-comment run. */
    public static Javadoc parseMarkdown(List<Token> comments) {
        List<String> lines = new ArrayList<>(comments.size());
        for (Token comment : comments) {
            if (!Prose.isMarkdownComment(comment)) {
                throw new IllegalArgumentException("Markdown runs may contain only /// comments");
            }
            lines.add(comment.text().substring(3));
        }

        int indent = commonIndent(lines);
        MarkdownScan scan = scanMarkdown(lines, indent);
        List<String> description = new ArrayList<>();
        List<Tag> tags = new ArrayList<>();
        List<String> currentTag = null;
        String currentName = null;
        boolean blankBeforeTags = false;

        for (int i = 0; i < lines.size(); i++) {
            String line = lines.get(i);
            String name = scan.literalAtLineStart().get(i) ? null : tagName(line);
            if (name != null) {
                if (currentTag == null) {
                    blankBeforeTags = !description.isEmpty() && description.getLast().isBlank();
                } else {
                    tags.add(markdownTag(currentName, currentTag, indent));
                }
                currentName = name;
                currentTag = new ArrayList<>();
                currentTag.add(line);
            } else if (currentTag != null) {
                currentTag.add(line);
            } else {
                description.add(line);
            }
        }
        if (currentTag != null) {
            tags.add(markdownTag(currentName, currentTag, indent));
        }

        trimBlankEnds(description);
        return new Javadoc(
                Form.MARKDOWN,
                description,
                markdownBlocks(description, indent),
                tags,
                comments.size() == 1,
                blankBeforeTags,
                scan.safe(),
                indent);
    }

    private static Tag traditionalTag(String name, List<String> sourceLines) {
        List<String> lines = new ArrayList<>(sourceLines);
        trimBlankEnds(lines);
        String head = tagHead(lines.getFirst());
        List<String> body = tagBody(lines, head, 1);
        return new Tag(name, head, lines, paragraphBlocks(body), !firstTagBody(lines, head).isEmpty());
    }

    private static Tag markdownTag(String name, List<String> sourceLines, int indent) {
        List<String> lines = new ArrayList<>(sourceLines);
        trimBlankEnds(lines);
        String head = tagHead(lines.getFirst());
        String firstBody = firstTagBody(lines, head);
        List<String> body = tagBody(lines, head, indent);
        return new Tag(name, head, lines, markdownBlocks(body, indent), !firstBody.isEmpty());
    }

    private static List<String> tagBody(List<String> lines, String head, int indent) {
        List<String> body = new ArrayList<>();
        String first = firstTagBody(lines, head);
        if (!first.isEmpty()) {
            body.add(" ".repeat(indent) + first);
        }
        body.addAll(lines.subList(1, lines.size()));
        trimBlankEnds(body);
        return body;
    }

    private static String firstTagBody(List<String> lines, String head) {
        String stripped = lines.getFirst().strip();
        return stripped.substring(Math.min(head.length(), stripped.length())).stripLeading();
    }

    /** The tag and optional argument that precede its description. */
    public static String tagHead(String line) {
        String stripped = line.strip();
        int space = firstWhitespace(stripped, 0);
        if (space < 0) {
            return stripped;
        }
        String tag = stripped.substring(0, space).toLowerCase(Locale.ROOT);
        if (!tag.equals("@param") && !tag.equals("@throws") && !tag.equals("@exception")) {
            return stripped.substring(0, space);
        }
        int argument = skipWhitespace(stripped, space);
        int end = firstWhitespace(stripped, argument);
        return end < 0 ? stripped : stripped.substring(0, end);
    }

    private static int firstWhitespace(String text, int start) {
        for (int i = start; i < text.length(); i++) {
            if (Character.isWhitespace(text.charAt(i))) {
                return i;
            }
        }
        return -1;
    }

    private static int skipWhitespace(String text, int start) {
        int index = start;
        while (index < text.length() && Character.isWhitespace(text.charAt(index))) {
            index++;
        }
        return index;
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

    // ------------------------------------------------------ Markdown blocks

    private static List<Block> markdownBlocks(List<String> lines, int indent) {
        List<Block> blocks = new ArrayList<>();
        for (int i = 0; i < lines.size();) {
            if (lines.get(i).isBlank()) {
                blocks.add(new Block(BlockKind.BLANK, List.of(lines.get(i))));
                i++;
                continue;
            }

            Fence fence = openingFence(removeIndent(lines.get(i), indent));
            if (fence != null) {
                int end = i + 1;
                while (end < lines.size() && !closingFence(removeIndent(lines.get(end), indent), fence)) {
                    end++;
                }
                end = Math.min(lines.size(), end + 1);
                blocks.add(new Block(BlockKind.PRESERVED, lines.subList(i, end)));
                i = end;
                continue;
            }

            int end = i + 1;
            while (end < lines.size()
                    && !lines.get(end).isBlank()
                    && openingFence(removeIndent(lines.get(end), indent)) == null) {
                end++;
            }
            List<String> blockLines = lines.subList(i, end);
            BlockKind kind = plainParagraph(blockLines, indent) ? BlockKind.PARAGRAPH : BlockKind.PRESERVED;
            blocks.add(new Block(kind, blockLines));
            i = end;
        }
        return List.copyOf(blocks);
    }

    private static List<Block> paragraphBlocks(List<String> lines) {
        return lines.isEmpty() ? List.of() : List.of(new Block(BlockKind.PARAGRAPH, lines));
    }

    private static boolean plainParagraph(List<String> lines, int indent) {
        for (int lineIndex = 0; lineIndex < lines.size(); lineIndex++) {
            String sourceLine = lines.get(lineIndex);
            String line = removeIndent(sourceLine, indent);
            if (line.isBlank()
                    || lineIndex == 0 && Character.isWhitespace(line.charAt(0))
                    || ATX_HEADING.matcher(line).matches()
                    || SETEXT_HEADING.matcher(line).matches()
                    || LIST_ITEM.matcher(line).matches()
                    || LINK_DEFINITION.matcher(line).matches()
                    || THEMATIC_BREAK.matcher(line).matches()
                    || line.startsWith(">")
                    || line.startsWith(":")
                    || line.startsWith("!")
                    || line.startsWith("$")
                    || line.startsWith("+")
                    || line.indexOf('|') >= 0
                    || line.endsWith("  ")
                    || line.endsWith("\\")) {
                return false;
            }
            for (int i = 0; i < line.length(); i++) {
                if ("`[]<>\\*_~{}$".indexOf(line.charAt(i)) >= 0) {
                    return false;
                }
            }
        }
        return true;
    }

    // ---------------------------------------------------- Markdown literals

    private record MarkdownScan(List<Boolean> literalAtLineStart, boolean safe) { }

    private record Fence(char marker, int length) { }

    private static MarkdownScan scanMarkdown(List<String> lines, int indent) {
        List<Boolean> literal = new ArrayList<>(lines.size());
        Fence openFence = null;
        boolean indentedCode = false;
        int codeSpan = 0;
        int inlineDepth = 0;

        for (String sourceLine : lines) {
            String line = removeIndent(sourceLine, indent);
            if (openFence != null) {
                literal.add(true);
                if (closingFence(line, openFence)) {
                    openFence = null;
                }
                continue;
            }
            if (indentedCode) {
                if (line.isBlank() || indentation(line) >= 4) {
                    literal.add(true);
                    continue;
                }
                indentedCode = false;
            }
            Fence fence = openingFence(line);
            if (fence != null) {
                literal.add(true);
                openFence = fence;
                continue;
            }
            if (!line.isBlank() && indentation(line) >= 4) {
                literal.add(true);
                indentedCode = true;
                continue;
            }

            literal.add(codeSpan > 0 || inlineDepth > 0);
            for (int i = 0; i < line.length();) {
                if (inlineDepth > 0) {
                    char current = line.charAt(i++);
                    if (current == '{') {
                        inlineDepth++;
                    } else if (current == '}') {
                        inlineDepth--;
                    }
                    continue;
                }
                if (line.charAt(i) == '`') {
                    int end = i + 1;
                    while (end < line.length() && line.charAt(end) == '`') {
                        end++;
                    }
                    int run = end - i;
                    if (codeSpan == 0) {
                        codeSpan = run;
                    } else if (codeSpan == run) {
                        codeSpan = 0;
                    }
                    i = end;
                    continue;
                }
                if (codeSpan == 0
                        && line.charAt(i) == '{'
                        && i + 2 < line.length()
                        && line.charAt(i + 1) == '@'
                        && Character.isLetter(line.charAt(i + 2))) {
                    inlineDepth = 1;
                    i += 2;
                    continue;
                }
                i++;
            }
        }
        return new MarkdownScan(List.copyOf(literal), openFence == null && codeSpan == 0 && inlineDepth == 0);
    }

    private static Fence openingFence(String line) {
        int start = indentation(line);
        if (start > 3 || start >= line.length()) {
            return null;
        }
        char marker = line.charAt(start);
        if (marker != '`' && marker != '~') {
            return null;
        }
        int end = start;
        while (end < line.length() && line.charAt(end) == marker) {
            end++;
        }
        if (end - start < 3 || marker == '`' && line.indexOf('`', end) >= 0) {
            return null;
        }
        return new Fence(marker, end - start);
    }

    private static boolean closingFence(String line, Fence fence) {
        int start = indentation(line);
        if (start > 3 || start >= line.length() || line.charAt(start) != fence.marker()) {
            return false;
        }
        int end = start;
        while (end < line.length() && line.charAt(end) == fence.marker()) {
            end++;
        }
        return end - start >= fence.length() && line.substring(end).isBlank();
    }

    private static int indentation(String line) {
        int index = 0;
        while (index < line.length() && (line.charAt(index) == ' ' || line.charAt(index) == '\t')) {
            index++;
        }
        return index;
    }

    private static int commonIndent(List<String> lines) {
        int indent = Integer.MAX_VALUE;
        for (String line : lines) {
            if (!line.isBlank()) {
                indent = Math.min(indent, indentation(line));
            }
        }
        return indent == Integer.MAX_VALUE ? 0 : indent;
    }

    /** Removes up to {@code count} leading whitespace characters from one Markdown line. */
    public static String removeIndent(String line, int count) {
        int index = 0;
        while (index < line.length() && index < count && Character.isWhitespace(line.charAt(index))) {
            index++;
        }
        return line.substring(index);
    }

    // ---------------------------------------------------------- traditional

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

        String[] split = inner.split("\\r\\n|\\r|\\n", -1);
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
