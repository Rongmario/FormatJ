package zone.rong.formatj.core.rewrite;

import zone.rong.formatj.api.rules.BracePolicy;
import zone.rong.formatj.api.rules.SwitchRules;
import zone.rong.formatj.api.rules.YieldStyle;
import zone.rong.formatj.core.cst.GreenNode;
import zone.rong.formatj.core.cst.SyntaxKind;
import java.util.ArrayList;
import java.util.List;

/**
 * The braces round an arrow case body, and the {@code yield} inside them.
 *
 * <p>Both rules are decided here rather than at the case itself, because the answer depends on
 * something a {@code SWITCH_CASE} cannot see: whether the switch it belongs to produces a value. A
 * statement switch's arrow body is a statement, so its braces are braces and nothing else. An
 * expression switch's arrow body is a value, so the braces bring a {@code yield} with them — the two
 * are one decision, not two, and it belongs to {@code switch.yield-style}.
 *
 * <p>That is the split, and it is what keeps one token from having two rules with an opinion about
 * it: {@code switch.arrow-case-braces} governs statement switches, {@code switch.yield-style}
 * governs expression switches, and neither is consulted about the other's cases.
 *
 * <h2>What is declined</h2>
 *
 * <ul>
 *   <li>{@code arrow-case-braces = never} and {@code = when-multi-statement} ask the same thing of an
 *       arrow case and get the same answer. An arrow body may only be an expression, a {@code throw}
 *       or a block, so a block holding more than one statement has no unbraced form to go to under
 *       either value; the difference between the two shows up on {@code if} and loop bodies, not
 *       here.
 *   <li>{@code yield-style = always-block} leaves a {@code throw} body alone. A {@code throw}
 *       produces no value, so there is no expression for a {@code yield} to be written round.
 *   <li>Braces carrying a comment stay, as everywhere else: the comment would have nowhere to go.
 * </ul>
 */
public final class SwitchRewrite implements Rewrite {

    @Override
    public String name() {
        return "switch";
    }

    @Override
    public boolean enabled(RewriteContext context) {
        return context.rule(SwitchRules.ARROW_CASE_BRACES) != BracePolicy.PRESERVE
                || context.rule(SwitchRules.YIELD_STYLE) != YieldStyle.PRESERVE;
    }

    @Override
    public GreenNode rewrite(GreenNode node, RewriteContext context) {
        boolean value = node.kind() == SyntaxKind.SWITCH_EXPRESSION;
        if (!value && node.kind() != SyntaxKind.SWITCH_STATEMENT) {
            return node;
        }
        List<GreenNode> children = node.children();
        int blockIndex = children.size() - 1;
        GreenNode block = children.get(blockIndex);
        if (block.kind() != SyntaxKind.SWITCH_BLOCK) {
            return node;
        }

        List<GreenNode> cases = new ArrayList<>(block.children());
        boolean changed = false;
        for (int i = 0; i < cases.size(); i++) {
            GreenNode rewritten = rewriteCase(cases.get(i), value, context);
            if (rewritten != cases.get(i)) {
                cases.set(i, rewritten);
                changed = true;
            }
        }
        if (!changed) {
            return node;
        }

        List<GreenNode> rebuilt = new ArrayList<>(children);
        rebuilt.set(blockIndex, GreenNode.branch(block.kind(), cases));
        return GreenNode.branch(node.kind(), rebuilt);
    }

    private GreenNode rewriteCase(GreenNode switchCase, boolean value, RewriteContext context) {
        if (switchCase.kind() != SyntaxKind.SWITCH_CASE || switchCase.children().size() != 3) {
            return switchCase;
        }
        List<GreenNode> children = switchCase.children();
        if (!(children.get(1) instanceof GreenNode.Leaf arrow) || !arrow.lexeme().equals("->")) {
            return switchCase;
        }

        GreenNode body = children.get(2);
        GreenNode replacement = value ? rewriteValueBody(body, context) : rewriteStatementBody(body, context);
        if (replacement == body) {
            return switchCase;
        }
        List<GreenNode> rebuilt = new ArrayList<>(children);
        rebuilt.set(2, replacement);
        return GreenNode.branch(switchCase.kind(), rebuilt);
    }

    // ------------------------------------------------- statement switch braces

    private GreenNode rewriteStatementBody(GreenNode body, RewriteContext context) {
        BracePolicy policy = context.rule(SwitchRules.ARROW_CASE_BRACES);
        if (policy == BracePolicy.PRESERVE) {
            return body;
        }
        if (policy == BracePolicy.ALWAYS) {
            return body.kind() == SyntaxKind.BLOCK ? body : brace(body, context);
        }
        return body.kind() == SyntaxKind.BLOCK ? unbrace(body, context) : body;
    }

    /** Puts a synthetic block round a bare arrow body. */
    private GreenNode brace(GreenNode body, RewriteContext context) {
        int start = context.firstPosition(body);
        int end = context.endPosition(body);
        if (start < 0 || end < 0) {
            return body;
        }
        String reason = "braces added around an arrow case body";
        context.record(
                TokenEdit.insert(
                        SwitchRules.ARROW_CASE_BRACES, reason, start, TokenEdit.Bias.OUTERMOST_FIRST, "{"));
        context.record(
                TokenEdit.insert(
                        SwitchRules.ARROW_CASE_BRACES, reason, end, TokenEdit.Bias.INNERMOST_FIRST, "}"));
        return GreenNode.branch(SyntaxKind.BLOCK, List.of(Synthetic.separator("{"), body, Synthetic.separator("}")));
    }

    /** Takes a one-statement block down to the statement, where the statement may stand alone. */
    private GreenNode unbrace(GreenNode body, RewriteContext context) {
        List<GreenNode> children = body.children();
        if (children.size() != 3) {
            return body;
        }
        GreenNode open = children.getFirst();
        GreenNode close = children.getLast();
        GreenNode only = children.get(1);

        // The only two statements an arrow body may be without braces.
        if (only.kind() != SyntaxKind.EXPRESSION_STATEMENT && only.kind() != SyntaxKind.THROW_STATEMENT) {
            return body;
        }
        if (Synthetic.carriesComments(open) || Synthetic.carriesComments(close)) {
            return body;
        }

        int openPosition = context.firstPosition(open);
        int closePosition = context.firstPosition(close);
        if (openPosition < 0 || closePosition < 0) {
            return body;
        }
        String reason = "braces removed from a single-statement arrow case body";
        context.record(TokenEdit.delete(SwitchRules.ARROW_CASE_BRACES, reason, openPosition, "{"));
        context.record(TokenEdit.delete(SwitchRules.ARROW_CASE_BRACES, reason, closePosition, "}"));
        return only;
    }

    // ------------------------------------------------ expression switch yields

    private GreenNode rewriteValueBody(GreenNode body, RewriteContext context) {
        return switch (context.rule(SwitchRules.YIELD_STYLE)) {
            case PRESERVE -> body;
            case EXPRESSION_WHEN_POSSIBLE -> toExpression(body, context);
            case ALWAYS_BLOCK -> toBlock(body, context);
        };
    }

    /** {@code -> { yield e; }} becomes {@code -> e;}. */
    private GreenNode toExpression(GreenNode body, RewriteContext context) {
        if (body.kind() != SyntaxKind.BLOCK || body.children().size() != 3) {
            return body;
        }
        GreenNode open = body.children().getFirst();
        GreenNode close = body.children().getLast();
        GreenNode only = body.children().get(1);
        if (only.kind() != SyntaxKind.YIELD_STATEMENT || only.children().size() != 3) {
            return body;
        }
        GreenNode keyword = only.children().getFirst();
        GreenNode expression = only.children().get(1);
        GreenNode semicolon = only.children().getLast();

        if (Synthetic.carriesComments(open)
                || Synthetic.carriesComments(close)
                || Synthetic.carriesComments(keyword)) {
            return body;
        }

        int openPosition = context.firstPosition(open);
        int closePosition = context.firstPosition(close);
        if (openPosition < 0
                || closePosition < 0
                || context.firstPosition(keyword) != openPosition + 1
                || context.firstPosition(semicolon) != closePosition - 1) {
            return body;
        }

        String reason = "a lone yield written as an expression body";
        context.record(
                new TokenEdit(
                        SwitchRules.YIELD_STYLE,
                        reason,
                        openPosition,
                        List.of("{", "yield"),
                        List.of(),
                        TokenEdit.Bias.INNERMOST_FIRST));
        context.record(TokenEdit.delete(SwitchRules.YIELD_STYLE, reason, closePosition, "}"));
        return GreenNode.branch(SyntaxKind.EXPRESSION_STATEMENT, List.of(expression, semicolon));
    }

    /** {@code -> e;} becomes {@code -> { yield e; }}. */
    private GreenNode toBlock(GreenNode body, RewriteContext context) {
        if (body.kind() != SyntaxKind.EXPRESSION_STATEMENT || body.children().size() != 2) {
            return body;
        }
        int start = context.firstPosition(body);
        int end = context.endPosition(body);
        if (start < 0 || end < 0) {
            return body;
        }

        String reason = "an expression body written as a block with a yield";
        context.record(
                new TokenEdit(
                        SwitchRules.YIELD_STYLE,
                        reason,
                        start,
                        List.of(),
                        List.of("{", "yield"),
                        TokenEdit.Bias.OUTERMOST_FIRST));
        context.record(
                TokenEdit.insert(SwitchRules.YIELD_STYLE, reason, end, TokenEdit.Bias.INNERMOST_FIRST, "}"));

        GreenNode yield =
                GreenNode.branch(
                        SyntaxKind.YIELD_STATEMENT,
                        List.of(
                                Synthetic.contextualKeyword("yield"),
                                body.children().getFirst(),
                                body.children().getLast()));
        return GreenNode.branch(
                SyntaxKind.BLOCK, List.of(Synthetic.separator("{"), yield, Synthetic.separator("}")));
    }

}
