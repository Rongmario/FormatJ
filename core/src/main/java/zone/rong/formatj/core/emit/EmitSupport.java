package zone.rong.formatj.core.emit;

import zone.rong.formatj.api.Option;
import zone.rong.formatj.api.Style;
import zone.rong.formatj.api.rules.AlignmentPolicy;
import zone.rong.formatj.api.rules.AlignmentRules;
import zone.rong.formatj.api.rules.AnnotationPlacement;
import zone.rong.formatj.api.rules.AnnotationRules;
import zone.rong.formatj.api.rules.BracePlacement;
import zone.rong.formatj.api.rules.BlankLineRules;
import zone.rong.formatj.api.rules.CommentRules;
import zone.rong.formatj.api.rules.FileRules;
import zone.rong.formatj.api.rules.IndentRules;
import zone.rong.formatj.api.rules.PreservationRules;
import zone.rong.formatj.api.rules.SpacingRules;
import zone.rong.formatj.api.rules.TextBlockIndentPolicy;
import zone.rong.formatj.api.rules.TextBlockRules;
import zone.rong.formatj.core.comment.CommentFormatter;
import zone.rong.formatj.core.cst.GreenNode;
import zone.rong.formatj.core.cst.SyntaxKind;
import zone.rong.formatj.core.cst.SyntaxToken;
import zone.rong.formatj.core.ir.AlignmentSite;
import zone.rong.formatj.core.ir.Doc;
import zone.rong.formatj.core.lexer.Token;
import zone.rong.formatj.core.lexer.TokenKind;
import zone.rong.formatj.core.text.TextBlocks;
import java.util.ArrayList;
import java.util.List;

/**
 * Shared machinery for the emitter: token and comment rendering, blank line policy, and the small
 * queries over the tree that every layout rule needs.
 */
abstract class EmitSupport {

    protected final Style style;
    private final CommentFormatter comments;

    EmitSupport(Style style) {
        this.style = style;
        this.comments = new CommentFormatter(style);
    }

    protected abstract Doc emit(GreenNode node);

    // ------------------------------------------------------------- options

    protected <T> T rule(Option<T> option) {
        return style.get(option);
    }

    protected int indentSize() {
        return rule(IndentRules.SIZE);
    }

    protected int continuation() {
        return rule(IndentRules.CONTINUATION);
    }

    // ------------------------------------------------------------ alignment

    /**
     * A zero-width mark for an alignment rule, or nothing at all when the rule is off.
     *
     * <p>The mark carries no width and no break, so a document with marks in it prints to exactly the
     * text it would have printed without them. What the rule turns on is only whether the printed text
     * is offered to {@link zone.rong.formatj.core.layout.ColumnAligner} to pad afterwards.
     *
     * <p>{@link AlignmentPolicy#ALIGN_ON_COLUMN} and {@link AlignmentPolicy#ALIGN_WHEN_MULTILINE} both
     * mean the same thing here, for the reason given on the enum: alignment is padding in front of
     * something, and padding only exists once a line has been broken.
     */
    protected Doc alignmentMark(AlignmentSite site) {
        Option<AlignmentPolicy> option =
                switch (site) {
                    case FIELD_NAME -> AlignmentRules.CONSECUTIVE_FIELDS;
                    case VARIABLE_NAME -> AlignmentRules.CONSECUTIVE_VARIABLES;
                    case ASSIGNMENT -> AlignmentRules.CONSECUTIVE_ASSIGNMENTS;
                    case ANNOTATION_VALUE -> AlignmentRules.ANNOTATION_VALUES;
                    case SWITCH_ARROW -> AlignmentRules.SWITCH_ARROWS;
                    case TRAILING_COMMENT -> AlignmentRules.TRAILING_COMMENTS;
                };
        return rule(option) == AlignmentPolicy.NONE ? Doc.EMPTY : Doc.mark(site);
    }

    /** Whether an alignment rule asks for a construct's own continuation lines to hang at its column. */
    protected boolean alignsOnColumn(Option<AlignmentPolicy> option) {
        return rule(option) != AlignmentPolicy.NONE;
    }

    // ------------------------------------------------------- author's lines

    /**
     * Whether a construct the author wrote on one line may stay on one line.
     *
     * <p>This is the seam every rule in the keep-on-one-line group goes through: the rule says
     * whether the style tolerates the shape at all, {@link AuthorLines} says whether the author
     * actually chose it, and the layout engine still decides whether it fits. Answering "yes" only
     * makes the single line <em>possible</em>; it never widens a line past the margin.
     */
    protected boolean keepsOnOneLine(GreenNode node, Option<Boolean> option) {
        return rule(option) && AuthorLines.onOneLine(node);
    }

    /**
     * Whether lines the author kept apart inside this node may be joined.
     *
     * <p>The dual reading of the same question, and what {@code preservation.never-join-lines} turns
     * on. It is consulted wherever the layout engine has an optional break to take, which is every
     * wrapping decision; where the emitter writes a fixed space there is no break to keep and the
     * author's own one is gone whatever this says.
     */
    protected boolean mayJoin(GreenNode node) {
        return !rule(PreservationRules.NEVER_JOIN_LINES) || AuthorLines.onOneLine(node);
    }

    /** A group that starts out broken when the author's own line breaks have to survive. */
    protected Doc authorGroup(GreenNode node, Doc content) {
        return mayJoin(node) ? Doc.group(content) : Doc.breakingGroup(content);
    }

    /** What separates a construct's header from its opening brace. */
    protected Doc braceLead(BracePlacement placement) {
        return switch (placement) {
            case END_OF_LINE -> space();
            case NEXT_LINE -> Doc.hardLine();
            case NEXT_LINE_INDENTED -> Doc.indent(indentSize(), Doc.hardLine());
        };
    }

    /** What separates a statement from its terminating semicolon. */
    protected Doc semicolonLead() {
        return spaceIf(rule(SpacingRules.BEFORE_SEMICOLON));
    }

    // --------------------------------------------------------- tree queries

    protected static boolean isLeaf(GreenNode node) {
        return node instanceof GreenNode.Leaf;
    }

    /** Whether the node is a single token with the given text. */
    protected static boolean is(GreenNode node, String lexeme) {
        return node instanceof GreenNode.Leaf leaf && leaf.lexeme().equals(lexeme);
    }

    protected static boolean isAny(GreenNode node, String... lexemes) {
        for (String lexeme : lexemes) {
            if (is(node, lexeme)) {
                return true;
            }
        }
        return false;
    }

    protected static boolean isKind(GreenNode node, SyntaxKind kind) {
        return node.kind() == kind;
    }

    /** The token text of a leaf, or an empty string for a branch. */
    protected static String lexeme(GreenNode node) {
        return node instanceof GreenNode.Leaf leaf ? leaf.lexeme() : "";
    }

    /** The first token of a node, which carries its leading trivia. */
    protected static SyntaxToken firstToken(GreenNode node) {
        GreenNode current = node;
        while (current instanceof GreenNode.Branch branch) {
            if (branch.children().isEmpty()) {
                return null;
            }
            current = branch.children().getFirst();
        }
        return ((GreenNode.Leaf) current).token();
    }

    /** Children with the given kind, in order. */
    protected static List<GreenNode> childrenOfKind(GreenNode node, SyntaxKind kind) {
        List<GreenNode> matches = new ArrayList<>();
        for (GreenNode child : node.children()) {
            if (child.kind() == kind) {
                matches.add(child);
            }
        }
        return matches;
    }

    /** The number of statements a block holds, ignoring its braces. */
    protected static int statementCount(GreenNode block) {
        int count = 0;
        for (GreenNode child : block.children()) {
            if (!isLeaf(child)) {
                count++;
            }
        }
        return count;
    }

    // --------------------------------------------------------- blank lines

    /** Blank lines to leave before {@code node}, honouring the author within the configured cap. */
    protected int blankLinesBefore(GreenNode node, int minimum) {
        if (!rule(PreservationRules.KEEP_AUTHOR_BLANK_LINES)) {
            return minimum;
        }
        SyntaxToken token = firstToken(node);
        int author = token == null ? 0 : token.blankLinesBefore();
        int cap = Math.min(rule(PreservationRules.MAX_PRESERVED_BLANK_LINES), rule(BlankLineRules.MAX_CONSECUTIVE));
        return Math.max(minimum, Math.min(author, cap));
    }

    /** One line break, plus {@code blankLines} empty lines. */
    protected static Doc lineBreaks(int blankLines) {
        List<Doc> parts = new ArrayList<>(blankLines + 1);
        for (int i = 0; i <= blankLines; i++) {
            parts.add(Doc.hardLine());
        }
        return Doc.concat(parts);
    }

    /** A separator before {@code node} of at least {@code minimum} blank lines. */
    protected Doc separatorBefore(GreenNode node, int minimum) {
        return lineBreaks(blankLinesBefore(node, minimum));
    }

    // ------------------------------------------------------------- tokens

    /** Renders one token with the comments attached to it. */
    protected Doc leaf(GreenNode.Leaf node) {
        return leaf(node, true);
    }

    /** Renders one token, optionally without the comments that lead it. */
    protected Doc leaf(GreenNode.Leaf node, boolean includeLeadingComments) {
        SyntaxToken token = node.token();
        List<Doc> parts = new ArrayList<>();
        if (includeLeadingComments) {
            parts.add(leadingTrivia(token));
        }
        if (token.token().kind() != TokenKind.END_OF_FILE) {
            parts.add(tokenText(token.token()));
        }
        boolean first = true;
        for (Token comment : token.trailingComments()) {
            // Only the first comment of a line has a column of its own to share with its neighbours.
            Doc mark = first ? alignmentMark(AlignmentSite.TRAILING_COMMENT) : Doc.EMPTY;
            first = false;
            parts.add(Doc.lineSuffix(Doc.concat(mark, trailingSpacing(), comments.trailing(comment))));
        }
        return Doc.concat(parts);
    }

    private Doc trailingSpacing() {
        return Doc.text(" ".repeat(Math.max(1, rule(CommentRules.TRAILING_COMMENT_MIN_SPACES))));
    }

    /**
     * The comments leading a node, separated as the author had them but with no break after the last.
     *
     * <p>Used where the comments belong inside a construct but the token they are attached to sits
     * outside it, such as a comment on the last line of an otherwise empty block.
     */
    protected Doc commentsBefore(GreenNode node) {
        SyntaxToken token = firstToken(node);
        if (token == null) {
            return Doc.EMPTY;
        }
        List<Token> attached = token.leadingComments();
        List<Doc> parts = new ArrayList<>();
        for (int i = 0; i < attached.size(); i++) {
            if (i > 0) {
                parts.add(Doc.hardLine());
            }
            parts.add(comments.ownLine(List.of(attached.get(i))));
        }
        return Doc.concat(parts);
    }

    protected static boolean hasLeadingComments(GreenNode node) {
        SyntaxToken token = firstToken(node);
        return token != null && !token.leadingComments().isEmpty();
    }

    /** Comments that come before a token, each followed by whatever separated it from what follows. */
    private Doc leadingTrivia(SyntaxToken token) {
        List<Token> leading = token.leading();
        List<Doc> parts = new ArrayList<>();
        for (int i = 0; i < leading.size(); i++) {
            Token trivia = leading.get(i);
            if (!trivia.kind().isComment()) {
                continue;
            }
            // A paragraph of // lines is one unit: refilling them one at a time could only ever make
            // each line shorter, never move a word from the end of one onto the next.
            int last = i;
            List<Token> run = new ArrayList<>();
            run.add(trivia);
            while (comments.joinsLineComments()
                    && trivia.kind() == TokenKind.LINE_COMMENT
                    && newlinesAfter(leading, last) == 1) {
                int next = nextComment(leading, last);
                if (next < 0 || leading.get(next).kind() != TokenKind.LINE_COMMENT) {
                    break;
                }
                run.add(leading.get(next));
                last = next;
            }
            parts.add(comments.ownLine(run));
            i = last;

            int newlines = newlinesAfter(leading, last);
            if (newlines == 0 && leading.get(last).kind() != TokenKind.LINE_COMMENT) {
                // A block comment the author kept inline stays inline.
                parts.add(Doc.text(" "));
            } else {
                int cap =
                        Math.min(
                                rule(PreservationRules.MAX_PRESERVED_BLANK_LINES),
                                rule(BlankLineRules.MAX_CONSECUTIVE));
                parts.add(lineBreaks(Math.min(Math.max(0, newlines - 1), cap)));
            }
        }
        return Doc.concat(parts);
    }

    /** The index of the next comment in a trivia list, or -1 when there is none. */
    private static int nextComment(List<Token> leading, int index) {
        for (int i = index + 1; i < leading.size(); i++) {
            if (leading.get(i).kind().isComment()) {
                return i;
            }
        }
        return -1;
    }

    /** Line breaks between the trivia at {@code index} and the next comment or the token itself. */
    private static int newlinesAfter(List<Token> leading, int index) {
        int newlines = 0;
        for (int i = index + 1; i < leading.size(); i++) {
            Token trivia = leading.get(i);
            if (trivia.kind().isComment()) {
                break;
            }
            newlines += countNewlines(trivia.text());
        }
        return newlines;
    }

    private static int countNewlines(String text) {
        int count = 0;
        for (int i = 0; i < text.length(); i++) {
            char current = text.charAt(i);
            if (current == '\n') {
                count++;
            } else if (current == '\r' && (i + 1 >= text.length() || text.charAt(i + 1) != '\n')) {
                count++;
            }
        }
        return count;
    }

    /** A comment, re-indented but never re-worded. */
    protected Doc commentDoc(Token comment) {
        return comments.verbatim(comment);
    }

    /** Token text; multi-line tokens such as text blocks are emitted exactly as written. */
    protected Doc tokenText(Token token) {
        String text = token.text();
        if (token.kind() == TokenKind.TEXT_BLOCK
                && rule(TextBlockRules.INDENT_POLICY) != TextBlockIndentPolicy.PRESERVE
                && TextBlocks.isTextBlock(text)) {
            return textBlock(text);
        }
        if (text.indexOf('\n') < 0 && text.indexOf('\r') < 0) {
            return Doc.text(text);
        }
        // A text block carries its own line structure, so it must break every group around it.
        return Doc.concat(Doc.breakParent(), Doc.text(text));
    }

    /**
     * A text block re-indented by the layout engine.
     *
     * <p>Safe to do here, and only here, because the amount of indentation a text block carries is
     * incidental: the language strips whatever every line of it shares. Shifting all of them by the
     * same amount therefore denotes the same string, which is why re-indenting is layout and the
     * other two {@code text-blocks.*} rules — which do change the string — are rewrites.
     *
     * <p>The two policies differ only in what "the same amount" is measured from.
     * {@code reindent-to-block} lets the ordinary indentation of the enclosing block carry the block,
     * so it moves with the code it belongs to. {@code minimal} pins it to the column the opening
     * delimiter landed in, which is the least indentation the content can be given without the
     * incidental whitespace becoming somebody's problem.
     */
    private Doc textBlock(String text) {
        List<String> lines = TextBlocks.strippedLines(text);
        List<Doc> parts = new ArrayList<>();
        parts.add(Doc.breakParent());
        parts.add(Doc.text("\"\"\"" + TextBlocks.openingTail(text)));
        for (int i = 0; i < lines.size(); i++) {
            parts.add(Doc.hardLine());
            String line = lines.get(i);
            parts.add(Doc.text(i == lines.size() - 1 ? line + "\"\"\"" : line));
        }
        Doc block = Doc.concat(parts);
        return rule(TextBlockRules.INDENT_POLICY) == TextBlockIndentPolicy.MINIMAL ? Doc.align(block) : block;
    }

    /** Drops the trailing spaces of a comment or verbatim line, unless the file rule keeps them. */
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

    // ------------------------------------------------------- formatter off

    /**
     * Where a node's leading comments turn formatting off.
     *
     * <p>The escape hatch is deliberately coarse: formatting stops at a whole member or statement and
     * resumes at another one. A marker in the middle of an expression would leave the region with no
     * well-defined boundaries in the tree, and reproducing half a construct verbatim is how a
     * formatter starts emitting code that no longer parses.
     *
     * @return the index in the node's leading trivia of the off marker, or {@code -1}
     */
    protected int formatterOffIndex(GreenNode node) {
        return markerIndex(node, rule(CommentRules.OFF_MARKER));
    }

    /** Whether a node's leading comments turn formatting back on. */
    protected boolean turnsFormattingOn(GreenNode node) {
        return markerIndex(node, rule(CommentRules.ON_MARKER)) >= 0;
    }

    private int markerIndex(GreenNode node, String marker) {
        if (!rule(CommentRules.HONOUR_FORMATTER_OFF) || marker.isBlank()) {
            return -1;
        }
        SyntaxToken token = firstToken(node);
        if (token == null) {
            return -1;
        }
        List<Token> leading = token.leading();
        for (int i = 0; i < leading.size(); i++) {
            Token trivia = leading.get(i);
            if (trivia.kind().isComment() && trivia.text().contains(marker)) {
                return i;
            }
        }
        return -1;
    }

    /**
     * A run of nodes reproduced exactly, from the off marker to the end of the last one.
     *
     * <p>Comments that came before the marker are still the formatter's to place, so they are emitted
     * normally and only what follows the marker is copied through.
     *
     * @param run the nodes the region covers, the first of which carries the marker
     * @param offIndex index of the marker in the first node's leading trivia
     */
    protected Doc formatterOffRegion(List<GreenNode> run, int offIndex) {
        GreenNode first = run.getFirst();
        SyntaxToken token = firstToken(first);
        int prefix = 0;
        for (int i = 0; i < offIndex; i++) {
            prefix += token.leading().get(i).length();
        }
        StringBuilder out = new StringBuilder();
        out.append(first.text(), prefix, first.text().length());
        for (int i = 1; i < run.size(); i++) {
            out.append(run.get(i).text());
        }
        List<Doc> parts = new ArrayList<>();
        for (int i = 0; i < offIndex; i++) {
            Token trivia = token.leading().get(i);
            if (trivia.kind().isComment()) {
                parts.add(commentDoc(trivia));
                parts.add(Doc.hardLine());
            }
        }
        // Text with its own line structure has to break every group around it, as a text block does.
        parts.add(Doc.breakParent());
        parts.add(Doc.text(stripTrailing(out.toString())));
        return Doc.concat(parts);
    }

    // ------------------------------------------------------------ verbatim

    /** A region reproduced exactly, used for unparsed and formatter-off code. */
    protected Doc verbatim(GreenNode node) {
        String text = node.text().strip();
        String[] lines = text.split("\r\n|\r|\n", -1);
        List<Doc> parts = new ArrayList<>();
        for (int i = 0; i < lines.length; i++) {
            if (i > 0) {
                parts.add(Doc.hardLine());
            }
            parts.add(Doc.text(stripTrailing(lines[i])));
        }
        return Doc.concat(parts);
    }

    /** What separates an annotation on a declaration from what follows it. */
    protected Doc annotationSeparator(GreenNode next, List<GreenNode> siblings) {
        return annotationSeparator(next, rule(AnnotationRules.DECLARATION_PLACEMENT), siblings);
    }

    /** What separates an annotation from what follows it, per the given placement rule. */
    protected Doc annotationSeparator(GreenNode next, AnnotationPlacement placement, List<GreenNode> siblings) {
        if (rule(AnnotationRules.SINGLE_MARKER_INLINE) && isLoneMarkerAnnotation(siblings)) {
            // One bare @Override reads as part of the signature, not as a line of its own.
            return space();
        }
        return switch (placement) {
            case NEW_LINE -> Doc.hardLine();
            case SAME_LINE -> space();
            case SAME_LINE_WHEN_SHORT -> Doc.line();
            case PRESERVE -> startsNewLine(next) ? Doc.hardLine() : space();
        };
    }

    /**
     * Whether the only annotation among these siblings is a marker.
     *
     * <p>Modifier lists are looked through, since that is where a declaration's annotations usually
     * sit; a second annotation anywhere means the set is no longer a lone marker.
     */
    protected static boolean isLoneMarkerAnnotation(List<GreenNode> siblings) {
        GreenNode only = null;
        int count = 0;
        for (GreenNode sibling : siblings) {
            if (sibling.kind() == SyntaxKind.ANNOTATION) {
                count++;
                only = sibling;
            } else if (sibling.kind() == SyntaxKind.MODIFIERS) {
                for (GreenNode modifier : sibling.children()) {
                    if (modifier.kind() == SyntaxKind.ANNOTATION) {
                        count++;
                        only = modifier;
                    }
                }
            }
        }
        if (count != 1) {
            return false;
        }
        for (GreenNode part : only.children()) {
            if (part.kind() == SyntaxKind.ANNOTATION_ARGUMENTS) {
                return false;
            }
        }
        return true;
    }

    protected static boolean startsNewLine(GreenNode node) {
        SyntaxToken token = firstToken(node);
        return token != null && token.startsNewLine();
    }

    protected static Doc space() {
        return Doc.text(" ");
    }

    protected static Doc spaceIf(boolean condition) {
        return condition ? space() : Doc.EMPTY;
    }

    protected Doc joinAll(List<GreenNode> nodes, Doc separator) {
        List<Doc> parts = new ArrayList<>(nodes.size());
        for (GreenNode node : nodes) {
            parts.add(emit(node));
        }
        return Doc.join(separator, parts);
    }

}
