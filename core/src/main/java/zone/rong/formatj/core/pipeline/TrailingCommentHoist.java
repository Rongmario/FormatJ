package zone.rong.formatj.core.pipeline;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.function.Predicate;

import zone.rong.formatj.core.cst.GreenNode;
import zone.rong.formatj.core.cst.SyntaxKind;
import zone.rong.formatj.core.cst.SyntaxToken;
import zone.rong.formatj.core.lexer.Token;
import zone.rong.formatj.core.lexer.TokenKind;

/**
 * Moves a trailing line comment that runs past the margin onto its own line above the statement or
 * member it trails, where comment re-flowing can wrap it.
 *
 * <p>It works on the tree of text that has already been laid out, because the column a trailing
 * comment ends at is only known there. A comment moves when it trails the first line of the
 * statement or the statement's last token, and only when it is the first comment inside that
 * statement, so no comment ever changes places with another.
 */
final class TrailingCommentHoist {

    private static final Set<SyntaxKind> STATEMENTS = EnumSet.of(
        SyntaxKind.BLOCK,
        SyntaxKind.LOCAL_VARIABLE_DECLARATION,
        SyntaxKind.LOCAL_TYPE_DECLARATION,
        SyntaxKind.EXPRESSION_STATEMENT,
        SyntaxKind.IF_STATEMENT,
        SyntaxKind.FOR_STATEMENT,
        SyntaxKind.ENHANCED_FOR_STATEMENT,
        SyntaxKind.WHILE_STATEMENT,
        SyntaxKind.DO_STATEMENT,
        SyntaxKind.SWITCH_STATEMENT,
        SyntaxKind.TRY_STATEMENT,
        SyntaxKind.RETURN_STATEMENT,
        SyntaxKind.THROW_STATEMENT,
        SyntaxKind.BREAK_STATEMENT,
        SyntaxKind.CONTINUE_STATEMENT,
        SyntaxKind.YIELD_STATEMENT,
        SyntaxKind.SYNCHRONIZED_STATEMENT,
        SyntaxKind.LABELED_STATEMENT,
        SyntaxKind.ASSERT_STATEMENT,
        SyntaxKind.EMPTY_STATEMENT
    );

    private TrailingCommentHoist() { }

    /**
     * @param maxWidth columns a line may occupy
     * @param reflows whether a comment is prose that re-flowing would wrap
     * @return the same node when nothing moved
     */
    static GreenNode apply(GreenNode node, int maxWidth, Predicate<Token> reflows) {
        if (node instanceof GreenNode.Leaf || node.kind().isVerbatim()) {
            return node;
        }
        List<GreenNode> children = new ArrayList<>(node.children());
        boolean changed = false;
        for (int i = 0; i < children.size(); i++) {
            GreenNode child = apply(children.get(i), maxWidth, reflows);
            changed |= child != children.get(i);
            children.set(i, child);
        }
        GreenNode rebuilt = changed ? GreenNode.branch(node.kind(), children) : node;
        return hosts(node.kind()) ? hoist(rebuilt, maxWidth, reflows) : rebuilt;
    }

    private static boolean hosts(SyntaxKind kind) {
        return STATEMENTS.contains(kind) || kind.isMember();
    }

    private static GreenNode hoist(GreenNode node, int maxWidth, Predicate<Token> reflows) {
        List<GreenNode.Leaf> leaves = new ArrayList<>();
        if (!collect(node, leaves) || leaves.isEmpty()) {
            return node;
        }
        SyntaxToken first = leaves.getFirst().token();
        List<Token> above = first.leadingComments();
        // A line comment already above the statement is its own paragraph; joining it would merge two.
        if (!startsLine(first) || !above.isEmpty() && above.getLast().kind() == TokenKind.LINE_COMMENT) {
            return node;
        }
        for (int i = 0; i < leaves.size(); i++) {
            GreenNode.Leaf leaf = leaves.get(i);
            SyntaxToken token = leaf.token();
            if (i > 0 && !token.leadingComments().isEmpty()) {
                return node;
            }
            List<Token> trailing = token.trailingComments();
            if (trailing.isEmpty()) {
                continue;
            }
            Token comment = trailing.getFirst();
            boolean onFirstLine = token.token().line() == first.token().line();
            boolean closes = i == leaves.size() - 1 && !token.token().is("}") && innermost(node);
            if (trailing.size() > 1 ||
                !(onFirstLine || closes) ||
                comment.column() - 1 + comment.text().stripTrailing().length() <= maxWidth ||
                !reflows.test(comment)) {
                return node;
            }
            GreenNode.Leaf bare = GreenNode.leaf(new SyntaxToken(token.leading(), token.token(), List.of()));
            return withLeadingComment(replace(node, leaf, bare), comment);
        }
        return node;
    }

    /** Every token of the node in order, or false when part of it is reproduced as written. */
    private static boolean collect(GreenNode node, List<GreenNode.Leaf> leaves) {
        if (node instanceof GreenNode.Leaf leaf) {
            leaves.add(leaf);
            return true;
        }
        if (node.kind().isVerbatim()) {
            return false;
        }
        for (GreenNode child : node.children()) {
            if (!collect(child, leaves)) {
                return false;
            }
        }
        return true;
    }

    private static boolean startsLine(SyntaxToken token) {
        int leadingWidth = token.width() - token.token().length() -
            token.trailing().stream().mapToInt(Token::length).sum();
        return token.startsNewLine() || token.token().start() == leadingWidth;
    }

    /** Whether no statement nested in this one ends at the same token. */
    private static boolean innermost(GreenNode node) {
        GreenNode last = node.children().getLast();
        while (!(last instanceof GreenNode.Leaf)) {
            if (hosts(last.kind()) || last.children().isEmpty()) {
                return false;
            }
            last = last.children().getLast();
        }
        return true;
    }

    private static GreenNode replace(GreenNode node, GreenNode.Leaf target, GreenNode.Leaf replacement) {
        if (node == target) {
            return replacement;
        }
        if (node instanceof GreenNode.Leaf) {
            return node;
        }
        List<GreenNode> children = new ArrayList<>(node.children());
        for (int i = 0; i < children.size(); i++) {
            GreenNode child = replace(children.get(i), target, replacement);
            if (child != children.get(i)) {
                children.set(i, child);
                return GreenNode.branch(node.kind(), children);
            }
        }
        return node;
    }

    private static GreenNode withLeadingComment(GreenNode node, Token comment) {
        if (node instanceof GreenNode.Leaf leaf) {
            SyntaxToken token = leaf.token();
            List<Token> leading = new ArrayList<>(token.leading());
            leading.add(comment);
            leading.add(Token.synthetic(TokenKind.WHITESPACE, "\n"));
            return GreenNode.leaf(new SyntaxToken(leading, token.token(), token.trailing()));
        }
        List<GreenNode> children = new ArrayList<>(node.children());
        children.set(0, withLeadingComment(children.getFirst(), comment));
        return GreenNode.branch(node.kind(), children);
    }

}
