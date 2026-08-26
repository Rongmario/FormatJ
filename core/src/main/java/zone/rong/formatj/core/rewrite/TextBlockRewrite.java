package zone.rong.formatj.core.rewrite;

import zone.rong.formatj.api.Option;
import zone.rong.formatj.api.rules.TextBlockRules;
import zone.rong.formatj.core.cst.GreenNode;
import zone.rong.formatj.core.cst.SyntaxToken;
import zone.rong.formatj.core.lexer.Token;
import zone.rong.formatj.core.lexer.TokenKind;
import zone.rong.formatj.core.text.TextBlocks;
import java.util.ArrayList;
import java.util.List;

/**
 * The two text block rules that change the string the program produces.
 *
 * <p>A text block looks like a layout problem and is not one. Its indentation is thrown away by the
 * language, so re-indenting it says nothing about the program and belongs to the layout engine; but
 * where its closing delimiter sits and whether its trailing spaces survive are both readable in the
 * string it denotes, so those belong here, declared token by token like every other change to a
 * program's meaning.
 *
 * <p>That is the split. {@code text-blocks.indent-policy} is not implemented in this class because
 * it is not a rewrite: see {@code EmitSupport}. The two that are here each change the value, and the
 * law they are held to — {@code RewriteVerification.checkTextBlockLaw} — allows a change to a line's
 * trailing white space and a change to the final line terminator, and nothing else. A rule that lost
 * a word of the content would fail it however it described itself.
 *
 * <h2>What is declined</h2>
 *
 * <ul>
 *   <li>A block the lexer did not read as a complete text block, which nothing here can reason about.
 *   <li>A block whose closing delimiter is already on its own line, and a block with no trailing
 *       spaces to make significant: both are already what the rule asks for, and a rewrite that
 *       records an edit changing nothing would cost the file its fixed point.
 * </ul>
 */
public final class TextBlockRewrite implements Rewrite {

    @Override
    public String name() {
        return "text-blocks";
    }

    @Override
    public boolean enabled(RewriteContext context) {
        return context.rule(TextBlockRules.CLOSING_DELIMITER_ON_OWN_LINE)
                || context.rule(TextBlockRules.ESCAPE_TRAILING_SPACES);
    }

    @Override
    public GreenNode rewrite(GreenNode node, RewriteContext context) {
        List<GreenNode> children = node.children();
        List<GreenNode> rewritten = null;
        for (int i = 0; i < children.size(); i++) {
            GreenNode replacement = rewriteLeaf(children.get(i), context);
            if (replacement != children.get(i)) {
                if (rewritten == null) {
                    rewritten = new ArrayList<>(children);
                }
                rewritten.set(i, replacement);
            }
        }
        return rewritten == null ? node : GreenNode.branch(node.kind(), rewritten);
    }

    /**
     * One token, rewritten if it is a text block the rules have something to say about.
     *
     * <p>Asked of a node's children rather than of the node itself, because the traversal hands a
     * rewrite every branch and no leaf: a leaf has nothing inside it to settle first. A text block is
     * the one construct that lives entirely in a single token, so it is reached from its parent.
     */
    private GreenNode rewriteLeaf(GreenNode child, RewriteContext context) {
        if (!(child instanceof GreenNode.Leaf leaf)) {
            return child;
        }
        SyntaxToken syntax = leaf.token();
        Token token = syntax.token();
        if (token.kind() != TokenKind.TEXT_BLOCK || !TextBlocks.isTextBlock(token.text())) {
            return child;
        }

        String original = token.text();
        String rewritten = original;
        Option<?> authority = null;

        if (context.rule(TextBlockRules.CLOSING_DELIMITER_ON_OWN_LINE)) {
            String moved = TextBlocks.withClosingDelimiterOnOwnLine(rewritten);
            if (!moved.equals(rewritten)) {
                authority = TextBlockRules.CLOSING_DELIMITER_ON_OWN_LINE;
                rewritten = moved;
            }
        }
        if (context.rule(TextBlockRules.ESCAPE_TRAILING_SPACES)) {
            String escaped = TextBlocks.withEscapedTrailingSpaces(rewritten);
            if (!escaped.equals(rewritten)) {
                authority = authority == null ? TextBlockRules.ESCAPE_TRAILING_SPACES : authority;
                rewritten = escaped;
            }
        }
        if (authority == null) {
            return child;
        }

        int position = context.firstPosition(child);
        if (position < 0) {
            return child;
        }
        context.record(
                new TokenEdit(
                        authority,
                        "a text block written the way the text block rules ask for",
                        position,
                        List.of(original),
                        List.of(rewritten),
                        TokenEdit.Bias.INNERMOST_FIRST));
        return GreenNode.leaf(
                new SyntaxToken(syntax.leading(), Token.synthetic(TokenKind.TEXT_BLOCK, rewritten), syntax.trailing()));
    }

}
