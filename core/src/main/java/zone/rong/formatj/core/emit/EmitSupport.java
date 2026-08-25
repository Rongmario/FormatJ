package zone.rong.formatj.core.emit;

import zone.rong.formatj.api.Option;
import zone.rong.formatj.api.Style;
import zone.rong.formatj.api.rules.AnnotationRules;
import zone.rong.formatj.api.rules.BlankLineRules;
import zone.rong.formatj.api.rules.CommentRules;
import zone.rong.formatj.api.rules.FileRules;
import zone.rong.formatj.api.rules.IndentRules;
import zone.rong.formatj.api.rules.PreservationRules;
import zone.rong.formatj.core.cst.GreenNode;
import zone.rong.formatj.core.cst.SyntaxKind;
import zone.rong.formatj.core.cst.SyntaxToken;
import zone.rong.formatj.core.ir.Doc;
import zone.rong.formatj.core.lexer.Token;
import zone.rong.formatj.core.lexer.TokenKind;
import java.util.ArrayList;
import java.util.List;

/**
 * Shared machinery for the emitter: token and comment rendering, blank line policy, and the small
 * queries over the tree that every layout rule needs.
 */
abstract class EmitSupport {

    protected final Style style;

    EmitSupport(Style style) {
        this.style = style;
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
        for (Token comment : token.trailingComments()) {
            parts.add(Doc.lineSuffix(Doc.concat(trailingSpacing(), commentDoc(comment))));
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
        List<Token> comments = token.leadingComments();
        List<Doc> parts = new ArrayList<>();
        for (int i = 0; i < comments.size(); i++) {
            if (i > 0) {
                parts.add(Doc.hardLine());
            }
            parts.add(commentDoc(comments.get(i)));
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
            parts.add(commentDoc(trivia));
            int newlines = newlinesAfter(leading, i);
            if (newlines == 0 && trivia.kind() != TokenKind.LINE_COMMENT) {
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

    /** Token text; multi-line tokens such as text blocks are emitted exactly as written. */
    protected Doc tokenText(Token token) {
        String text = token.text();
        if (text.indexOf('\n') < 0 && text.indexOf('\r') < 0) {
            return Doc.text(text);
        }
        // A text block carries its own line structure, so it must break every group around it.
        return Doc.concat(Doc.breakParent(), Doc.text(text));
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

    /** What separates an annotation from what follows it, per the annotation placement rules. */
    protected Doc annotationSeparator(GreenNode next) {
        return switch (rule(AnnotationRules.DECLARATION_PLACEMENT)) {
            case NEW_LINE -> Doc.hardLine();
            case SAME_LINE -> space();
            case SAME_LINE_WHEN_SHORT -> Doc.line();
            case PRESERVE -> startsNewLine(next) ? Doc.hardLine() : space();
        };
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
