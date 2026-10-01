package zone.rong.formatj.core.rewrite;

import zone.rong.formatj.api.rules.LiteralRules;
import zone.rong.formatj.api.rules.LongSuffix;
import zone.rong.formatj.core.cst.GreenNode;
import zone.rong.formatj.core.cst.SyntaxToken;
import zone.rong.formatj.core.lexer.Token;
import zone.rong.formatj.core.lexer.TokenKind;
import java.util.ArrayList;
import java.util.List;

/**
 * Respells numeric literals. The value never changes, only the case of some of its characters.
 *
 * <p>Literals live in a single token, so like text blocks they are reached from their parent. A
 * literal containing a Unicode escape is left alone, since its token text is not what the program
 * reads.
 */
public final class LiteralRewrite implements Rewrite {

    @Override
    public String name() {
        return "literals";
    }

    @Override
    public boolean enabled(RewriteContext context) {
        return context.rule(LiteralRules.LONG_SUFFIX) == LongSuffix.UPPER;
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

    private GreenNode rewriteLeaf(GreenNode child, RewriteContext context) {
        if (!(child instanceof GreenNode.Leaf leaf)) {
            return child;
        }
        SyntaxToken syntax = leaf.token();
        Token token = syntax.token();
        if (token.kind() != TokenKind.NUMBER_LITERAL || token.hasUnicodeEscape()) {
            return child;
        }

        String original = token.text();
        String rewritten = original;
        if (context.rule(LiteralRules.LONG_SUFFIX) == LongSuffix.UPPER && rewritten.endsWith("l")) {
            rewritten = rewritten.substring(0, rewritten.length() - 1) + "L";
        }
        int position = context.firstPosition(child);
        if (rewritten.equals(original) || position < 0) {
            return child;
        }

        context.record(new TokenEdit(
                LiteralRules.LONG_SUFFIX,
                "a numeric literal spelled the way the literal rules ask for",
                position,
                List.of(original),
                List.of(rewritten),
                TokenEdit.Bias.INNERMOST_FIRST));
        return GreenNode.leaf(new SyntaxToken(
                syntax.leading(),
                Token.synthetic(TokenKind.NUMBER_LITERAL, rewritten),
                syntax.trailing()));
    }

}
