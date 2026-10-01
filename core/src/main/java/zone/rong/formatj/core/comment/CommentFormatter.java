package zone.rong.formatj.core.comment;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import zone.rong.formatj.api.Option;
import zone.rong.formatj.api.Style;
import zone.rong.formatj.api.rules.CommentReflow;
import zone.rong.formatj.api.rules.CommentRules;
import zone.rong.formatj.api.rules.FileRules;
import zone.rong.formatj.api.rules.JavadocClosingTagForm;
import zone.rong.formatj.api.rules.JavadocOpeningTagPosition;
import zone.rong.formatj.api.rules.JavadocRules;
import zone.rong.formatj.api.rules.JavadocTagOrder;
import zone.rong.formatj.core.ir.Doc;
import zone.rong.formatj.core.lexer.Token;
import zone.rong.formatj.core.lexer.TokenKind;

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
 *   <li>Markdown blocks with structural syntax keep their lines exactly as written. Only plain
 *       paragraphs may be refilled.
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
        if (run.stream().anyMatch(Token::hasUnicodeEscape)) {
            return verbatimRun(run);
        }
        if (run.size() == 1 && run.getFirst().kind() == TokenKind.JAVADOC_COMMENT) {
            return javadoc(run.getFirst());
        }
        if (run.size() == 1 && run.getFirst().kind() == TokenKind.BLOCK_COMMENT) {
            return blockComment(run.getFirst());
        }
        if (Prose.isMarkdownComment(run.getFirst())) {
            return markdownJavadoc(run);
        }
        return lineComments(run);
    }

    /** Whether two adjacent line comments belong to the same layout unit. */
    public boolean joinsLineComments(Token first, Token next) {
        if (first.kind() != TokenKind.LINE_COMMENT || next.kind() != TokenKind.LINE_COMMENT) {
            return false;
        }
        boolean markdown = Prose.isMarkdownComment(first);
        if (markdown != Prose.isMarkdownComment(next)) {
            return false;
        }
        return markdown || rule(CommentRules.REFLOW) == CommentReflow.REFLOW_TO_LINE_LENGTH;
    }

    // ------------------------------------------------------------ line comments

    private Doc lineComments(List<Token> run) {
        if (rule(CommentRules.REFLOW) != CommentReflow.REFLOW_TO_LINE_LENGTH || !reflowable(run)) {
            return verbatimRun(run);
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
        if (rule(CommentRules.REFLOW) != CommentReflow.REFLOW_TO_LINE_LENGTH ||
            !comment.hasLineTerminator() ||
            !reflowable(List.of(comment)) ||
            !rule(CommentRules.BLOCK_COMMENT_STAR_ALIGNMENT)) {
            return verbatim(comment);
        }
        List<Doc> parts = new ArrayList<>();
        parts.add(Doc.text("/*"));
        // Blank lines separate paragraphs, which are refilled one at a time.
        List<String> paragraph = new ArrayList<>();
        for (String line : Prose.blockContentLines(comment.text())) {
            if (!line.isBlank()) {
                paragraph.add(line);
                continue;
            }
            addParagraph(parts, paragraph);
            paragraph.clear();
        }
        addParagraph(parts, paragraph);
        if (parts.size() == 1) {
            return verbatim(comment);
        }
        parts.add(Doc.hardLine());
        parts.add(Doc.text(" */"));
        return Doc.align(Doc.concat(parts));
    }

    private void addParagraph(List<Doc> parts, List<String> paragraph) {
        if (paragraph.isEmpty()) {
            return;
        }
        if (parts.size() > 1) {
            parts.add(Doc.hardLine());
            parts.add(Doc.text(" *"));
        }
        List<String> words = new ArrayList<>();
        for (Prose.Atom atom : Prose.atoms(String.join("\n", paragraph))) {
            words.add(atom.text());
        }
        parts.add(Doc.hardLine());
        parts.add(Doc.concat(Doc.text(" * "), fill(words, " * ")));
    }

    // ---------------------------------------------------------------- javadoc

    private Doc javadoc(Token comment) {
        Javadoc parsed = Javadoc.parse(comment.text());
        // Javadoc keeps the indentation of a line without a leading star, so such a line can be
        // neither starred nor re-indented without changing what the documentation shows.
        if (!restructures(parsed) || hasStarlessLine(comment.text())) {
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

    private Doc markdownJavadoc(List<Token> run) {
        Javadoc parsed = Javadoc.parseMarkdown(run);
        if (!parsed.safeToFormat() || !restructuresMarkdown(parsed)) {
            return verbatimRun(run);
        }

        List<Doc> lines = new ArrayList<>();
        for (Javadoc.Block block : parsed.descriptionBlocks()) {
            addMarkdownBlock(lines, block);
        }

        List<Javadoc.Tag> tags = ordered(parsed.tags());
        if (!tags.isEmpty() && !lines.isEmpty() && rule(JavadocRules.BLANK_LINE_BEFORE_TAGS)) {
            addMarkdownLine(lines, "");
        }
        Map<String, Integer> columns = rule(JavadocRules.ALIGN_TAG_DESCRIPTIONS) ? descriptionColumns(tags) : Map.of();
        for (Javadoc.Tag tag : tags) {
            addMarkdownTag(lines, tag, columns.getOrDefault(tag.name(), 0));
        }
        if (lines.isEmpty()) {
            return verbatimRun(run);
        }
        return Doc.align(Doc.concat(lines));
    }

    private boolean restructuresMarkdown(Javadoc parsed) {
        if (rule(JavadocRules.WRAP) || rule(JavadocRules.ALIGN_TAG_DESCRIPTIONS)) {
            return true;
        }
        if (rule(JavadocRules.TAG_ORDER) != JavadocTagOrder.PRESERVE && !sorted(parsed.tags())) {
            return true;
        }
        return !parsed.tags().isEmpty() &&
            !parsed.description().isEmpty() &&
            parsed.blankBeforeTags() != rule(JavadocRules.BLANK_LINE_BEFORE_TAGS);
    }

    private void addMarkdownBlock(List<Doc> output, Javadoc.Block block) {
        if (block.kind() == Javadoc.BlockKind.PARAGRAPH && rule(JavadocRules.WRAP)) {
            String prefix = leadingWhitespace(block.lines().getFirst());
            addMarkdownDoc(output, markdownParagraph(block, prefix, prefix));
            return;
        }
        for (String line : block.lines()) {
            addMarkdownLine(output, line);
        }
    }

    private void addMarkdownTag(List<Doc> output, Javadoc.Tag tag, int column) {
        String first = tag.lines().getFirst();
        String prefix = leadingWhitespace(first);
        String rest = first.strip().substring(tag.head().length()).stripLeading();
        String padding = " ".repeat(Math.max(0, column - tag.head().length()));

        if (!rule(JavadocRules.WRAP)) {
            String opening = rule(JavadocRules.ALIGN_TAG_DESCRIPTIONS) && !rest.isEmpty()
                ? prefix + tag.head() + padding + " " + rest
                : first;
            addMarkdownLine(output, opening);
            for (String line : tag.lines().subList(1, tag.lines().size())) {
                addMarkdownLine(output, line);
            }
            return;
        }

        List<Javadoc.Block> blocks = tag.description();
        int nextBlock = 0;
        if (!blocks.isEmpty() && blocks.getFirst().kind() == Javadoc.BlockKind.PARAGRAPH) {
            List<String> words = blockWords(blocks.getFirst());
            if (words.isEmpty()) {
                addMarkdownLine(output, prefix + tag.head());
            } else {
                String continuation = prefix + " ".repeat(Math.max(0, rule(JavadocRules.TAG_CONTINUATION_INDENT)));
                addMarkdownDoc(
                    output,
                    Doc.concat(markdownText(prefix + tag.head() + padding + " "), fill(words, "///" + continuation))
                );
            }
            nextBlock = 1;
        } else if (!blocks.isEmpty() && tag.descriptionStartsOnTagLine()) {
            Javadoc.Block firstBlock = blocks.getFirst();
            String firstBody = firstBlock.lines().getFirst().stripLeading();
            addMarkdownLine(output, prefix + tag.head() + padding + " " + firstBody);
            for (String line : firstBlock.lines().subList(1, firstBlock.lines().size())) {
                addMarkdownLine(output, line);
            }
            nextBlock = 1;
        } else {
            addMarkdownLine(output, prefix + tag.head());
        }

        for (Javadoc.Block block : blocks.subList(nextBlock, blocks.size())) {
            addMarkdownBlock(output, block);
        }
    }

    private Doc markdownParagraph(Javadoc.Block block, String firstPrefix, String continuationPrefix) {
        List<String> words = blockWords(block);
        if (words.isEmpty()) {
            return markdownText(firstPrefix);
        }
        return Doc.concat(markdownText(firstPrefix), fill(words, "///" + continuationPrefix));
    }

    private static List<String> blockWords(Javadoc.Block block) {
        List<String> words = new ArrayList<>();
        for (Prose.Atom atom : Prose.atoms(String.join("\n", block.lines()))) {
            words.add(atom.text());
        }
        return words;
    }

    private static String leadingWhitespace(String line) {
        int index = 0;
        while (index < line.length() && Character.isWhitespace(line.charAt(index))) {
            index++;
        }
        return line.substring(0, index);
    }

    private static Doc markdownText(String content) {
        return Doc.textPreservingTrailingWhitespace("///" + content);
    }

    private static void addMarkdownLine(List<Doc> output, String content) {
        addMarkdownDoc(output, markdownText(content));
    }

    private static void addMarkdownDoc(List<Doc> output, Doc line) {
        if (!output.isEmpty()) {
            output.add(Doc.hardLine());
        }
        output.add(line);
    }

    private Doc verbatimRun(List<Token> run) {
        List<Doc> parts = new ArrayList<>();
        for (int i = 0; i < run.size(); i++) {
            if (i > 0) {
                parts.add(Doc.hardLine());
            }
            parts.add(verbatim(run.get(i)));
        }
        return Doc.concat(parts);
    }

    /** Whether any rule that is on would rearrange this comment; if none would, it is left alone. */
    private boolean restructures(Javadoc parsed) {
        if (rule(JavadocRules.WRAP) ||
            rule(JavadocRules.ADD_PARAGRAPH_TAGS) ||
            rule(JavadocRules.ALIGN_TAG_DESCRIPTIONS) ||
            rule(JavadocRules.CLOSING_TAG_FORM) != JavadocClosingTagForm.PRESERVE ||
            rule(JavadocRules.OPENING_TAG_POSITION) != JavadocOpeningTagPosition.PRESERVE) {
            return true;
        }
        if (rule(JavadocRules.TAG_ORDER) != JavadocTagOrder.PRESERVE && !sorted(parsed.tags())) {
            return true;
        }
        return !parsed.tags().isEmpty() &&
            !parsed.description().isEmpty() &&
            parsed.blankBeforeTags() != rule(JavadocRules.BLANK_LINE_BEFORE_TAGS);
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
        // A blank line inside a <pre> or {@code} region is part of the sample, not a paragraph break.
        List<int[]> verbatim = Prose.verbatimRanges(String.join("\n", lines));
        int offset = 0;
        for (String line : lines) {
            boolean insideVerbatim = Prose.isInside(verbatim, offset - 1);
            offset += line.length() + 1;
            if (insideVerbatim) {
                paragraph.add(line);
                continue;
            }
            boolean marker = isMarkerLine(line);
            if (line.isBlank() || marker) {
                parts.addAll(paragraphDocs(paragraph));
                paragraph.clear();
                parts.add(Doc.hardLine());
                parts.add(marker ? starred(normaliseMarkerLine(line)) : Doc.text(" *"));
                continue;
            }
            String leading = leadingMarker(line);
            if (leading != null) {
                // A same-line `<p> text` opens a new paragraph; the marker stays with its text
                // so wrapping keeps them on one line.
                parts.addAll(paragraphDocs(paragraph));
                paragraph.clear();
                paragraph.add(line);
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
        List<String> lines = new ArrayList<>(parsed.description());
        if (rule(JavadocRules.ADD_PARAGRAPH_TAGS)) {
            List<int[]> verbatim = Prose.verbatimRanges(String.join("\n", lines));
            int offset = 0;
            for (int i = 0; i < lines.size(); i++) {
                // A blank line in a code sample is part of the sample, and a block element such as
                // <pre> or <ul> is a paragraph of its own already.
                if (lines.get(i).isBlank() && !Prose.isInside(verbatim, offset) && !opensBlock(lines, i + 1)) {
                    offset += lines.get(i).length() + 1;
                    lines.set(i, " <p>");
                    continue;
                }
                offset += lines.get(i).length() + 1;
            }
        }
        if (rule(JavadocRules.CLOSING_TAG_FORM) == JavadocClosingTagForm.SLASH_FIRST) {
            lines = normaliseClosingForm(lines);
        }
        if (rule(JavadocRules.OPENING_TAG_POSITION) != JavadocOpeningTagPosition.PRESERVE) {
            lines = normalisePosition(lines, rule(JavadocRules.OPENING_TAG_POSITION));
        }
        return List.copyOf(lines);
    }

    private static final Pattern BLOCK_ELEMENT = Pattern.compile(
        "<(pre|ul|ol|dl|table|blockquote|div|h[1-6]|hr|p)\\b.*",
        Pattern.CASE_INSENSITIVE
    );

    private static boolean opensBlock(List<String> lines, int from) {
        for (int i = from; i < lines.size(); i++) {
            String stripped = lines.get(i).strip();
            if (!stripped.isEmpty()) {
                return BLOCK_ELEMENT.matcher(stripped).matches();
            }
        }
        return true;
    }

    // ------------------------------------------------- paragraph markers (<p> family)

    /**
     * Standalone compact paragraph markers: {@code <p>}, {@code </p>} and {@code <p/>} in any
     * case, bounded by whitespace or line ends. Forms with spaces inside the brackets
     * ({@code <p />}) are left alone: splitting them would cut one word into two and fail the
     * prose check.
     */
    private static final Pattern PARAGRAPH_MARKER = Pattern.compile(
        "(^|\\s)(</p>|<p/>|<p>)(\\s|$)",
        Pattern.CASE_INSENSITIVE
    );

    private static boolean isParagraphMarkerToken(String token) {
        return token.equalsIgnoreCase("<p>") || token.equalsIgnoreCase("</p>") || token.equalsIgnoreCase("<p/>");
    }

    private static boolean isCloserToken(String token) {
        return token.equalsIgnoreCase("</p>") || token.equalsIgnoreCase("<p/>");
    }

    /** Whether the whole line is one paragraph marker, e.g. {@code " <p>"}. */
    private static boolean isMarkerLine(String line) {
        return isParagraphMarkerToken(line.strip());
    }

    /**
     * The marker a line opens with when it leads with one followed by text, e.g. {@code "<p>"}
     * for {@code " <p> Second"}, or null for marker-only, blank and ordinary lines.
     */
    private static String leadingMarker(String line) {
        String stripped = line.strip();
        int space = stripped.indexOf(' ');
        if (space < 0) {
            space = stripped.indexOf('\t');
        }
        if (space < 0) {
            return null;
        }
        String first = stripped.substring(0, space);
        if (!isParagraphMarkerToken(first) || stripped.substring(space).isBlank()) {
            return null;
        }
        return first;
    }

    /** A marker-only line in canonical spacing, so {@code "<P/>"} still prints as {@code " * <p/>"}. */
    private static String normaliseMarkerLine(String line) {
        String stripped = line.strip();
        if (!isParagraphMarkerToken(stripped)) {
            return line;
        }
        return " " + stripped;
    }

    /** The line with one leading paragraph marker removed, for block-markup detection. */
    private static String withoutLeadingMarker(String line) {
        String leading = leadingMarker(line);
        if (leading == null) {
            return line.strip();
        }
        return line.strip().substring(leading.length()).strip();
    }

    /**
     * Rewrites standalone closers slash-first: {@code <p/>} (any case) becomes {@code </p>},
     * and {@code </P>} becomes {@code </p>}. Openers are untouched. Markers inside
     * {@code <pre>}, {@code {@code}} or {@code @snippet} regions are content and are skipped.
     */
    private static List<String> normaliseClosingForm(List<String> lines) {
        List<int[]> verbatim = Prose.verbatimRanges(String.join("\n", lines));
        List<String> out = new ArrayList<>(lines.size());
        int offset = 0;
        for (String line : lines) {
            StringBuilder rebuilt = null;
            Matcher matcher = PARAGRAPH_MARKER.matcher(line);
            while (matcher.find()) {
                String token = matcher.group(2);
                if (!isCloserToken(token) || Prose.isInside(verbatim, offset + matcher.start(2))) {
                    continue;
                }
                if (rebuilt == null) {
                    rebuilt = new StringBuilder(line);
                }
                rebuilt.replace(matcher.start(2), matcher.end(2), "</p>");
            }
            out.add(rebuilt == null ? line : rebuilt.toString());
            offset += line.length() + 1;
        }
        return out;
    }

    /**
     * Moves standalone paragraph markers to the configured position. Markers inside verbatim
     * regions are left where they are.
     *
     * <p>Both positions expand through the same split: every standalone marker outside verbatim
     * gets its own entry, text around it becomes neighbouring entries. {@code NEW_LINE} stops
     * there; {@code SAME_LINE} then merges each marker-only entry with the text entry
     * immediately after it. Expand-then-merge is idempotent, which is what keeps formatting a
     * fixed point.
     */
    private static List<String> normalisePosition(List<String> lines, JavadocOpeningTagPosition position) {
        List<int[]> verbatim = Prose.verbatimRanges(String.join("\n", lines));
        List<String> split = new ArrayList<>(lines.size());
        int offset = 0;
        for (String line : lines) {
            List<String> parts = splitMarkers(line, offset, verbatim);
            if (parts == null) {
                split.add(line);
            } else {
                split.addAll(parts);
            }
            offset += line.length() + 1;
        }
        if (position != JavadocOpeningTagPosition.SAME_LINE) {
            return split;
        }
        List<String> merged = new ArrayList<>(split.size());
        for (int i = 0; i < split.size(); i++) {
            String line = split.get(i);
            if (isMarkerLine(line) && i + 1 < split.size()) {
                String next = split.get(i + 1);
                if (!next.isBlank() && !isMarkerLine(next)) {
                    merged.add(" " + line.strip() + " " + next.strip());
                    i++;
                    continue;
                }
            }
            merged.add(line);
        }
        return merged;
    }

    /**
     * Splits a line around its standalone markers outside verbatim, or null when there is
     * nothing to split. Text fragments keep their leading space so {@code " *" + line} still
     * prints under the star; markers are canonicalised to {@code " <marker>"}.
     */
    private static List<String> splitMarkers(String line, int offset, List<int[]> verbatim) {
        Matcher matcher = PARAGRAPH_MARKER.matcher(line);
        List<int[]> hits = new ArrayList<>();
        List<String> tokens = new ArrayList<>();
        while (matcher.find()) {
            if (Prose.isInside(verbatim, offset + matcher.start(2))) {
                continue;
            }
            hits.add(new int[] { matcher.start(2), matcher.end(2) });
            tokens.add(matcher.group(2));
        }
        if (hits.isEmpty()) {
            return null;
        }
        List<String> parts = new ArrayList<>(hits.size() * 2 + 1);
        int cursor = 0;
        for (int i = 0; i < hits.size(); i++) {
            String before = line.substring(cursor, hits.get(i)[0]);
            if (!before.isBlank()) {
                parts.add(stripTrailingOnly(before));
            }
            parts.add(" " + tokens.get(i));
            cursor = hits.get(i)[1];
        }
        String after = line.substring(cursor);
        if (!after.isBlank()) {
            parts.add(ensureLeadingSpace(after));
        }
        if (parts.isEmpty()) {
            return null;
        }
        // A line that was already exactly one marker needs no rewrite.
        if (parts.size() == 1 && isMarkerLine(parts.getFirst())) {
            return null;
        }
        return parts;
    }

    private static String stripTrailingOnly(String text) {
        int end = text.length();
        while (end > 0 && (text.charAt(end - 1) == ' ' || text.charAt(end - 1) == '\t')) {
            end--;
        }
        return text.substring(0, end);
    }

    private static String ensureLeadingSpace(String text) {
        String strippedTrailing = stripTrailingOnly(text);
        String stripped = strippedTrailing.stripLeading();
        if (stripped.isEmpty()) {
            return "";
        }
        return strippedTrailing.startsWith(" ") || strippedTrailing.startsWith("\t")
            ? strippedTrailing
            : " " + stripped;
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
            columns.merge(tag.name(), tag.head().length(), Math::max);
        }
        return columns;
    }

    /**
     * The part of a tag's first line that names what it documents: the tag, and for the tags that
     * take one, the parameter or exception after it.
     */
    private static String head(String line) {
        return Javadoc.tagHead(line);
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
        int consumed = head.split("\\s+").length;
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
            String stripped = withoutLeadingMarker(line);
            if (stripped.startsWith("<") || stripped.startsWith("|") || holdsMarker(line)) {
                // Block markup and tables are laid out by their own lines, not by the margin.
                // A leading paragraph marker is not markup: `<p> text` still refills.
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
            return Prose.isMarkdownComment(comment)
                ? Doc.textPreservingTrailingWhitespace(comment.text())
                : Doc.text(stripTrailing(comment.text()));
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
            } else if (trimmed.isEmpty() || trimmed.startsWith("*")) {
                parts.add(Doc.text(trimmed));
            } else {
                // Without a star, the indentation is the content: a code sample in a Javadoc keeps it.
                parts.add(Doc.lineIndent(0, Doc.text(line)));
            }
        }
        return Doc.concat(parts);
    }

    private static boolean hasStarlessLine(String text) {
        String[] lines = text.split("\r\n|\r|\n", -1);
        for (int i = 1; i < lines.length; i++) {
            String trimmed = lines[i].strip();
            if (!trimmed.isEmpty() && !trimmed.startsWith("*")) {
                return true;
            }
        }
        return false;
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
