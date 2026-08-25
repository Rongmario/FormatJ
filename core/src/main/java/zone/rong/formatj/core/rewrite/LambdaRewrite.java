package zone.rong.formatj.core.rewrite;

import zone.rong.formatj.api.rules.BracePolicy;
import zone.rong.formatj.api.rules.LambdaParameterStyle;
import zone.rong.formatj.api.rules.LambdaRules;
import zone.rong.formatj.core.cst.GreenNode;
import zone.rong.formatj.core.cst.SyntaxKind;
import java.util.ArrayList;
import java.util.List;

/**
 * The two lambda rules that change tokens: the parentheses round a single parameter, and the braces
 * round the body.
 *
 * <h2>Parentheses</h2>
 *
 * <p>Adding them is always safe. Removing them is offered only for the one shape the language allows
 * to go bare — exactly one parameter, written as a name with no type, no {@code final} and no
 * annotation — so {@code ()}, {@code (a, b)}, {@code (int x)} and {@code (var x)} keep theirs.
 *
 * <h2>Braces</h2>
 *
 * <p>Here it is the other way round from {@link BraceRewrite}: taking the braces off is the safe
 * direction and putting them on is not. A block body says which of the two lambda shapes the target
 * type wanted — {@code x -> { return e; }} is value-compatible and {@code x -> { e(); }} is
 * void-compatible — so the expression body it collapses to is the one that compiles. Going the other
 * way, {@code x -> e} could need either {@code { return e; }} or {@code { e; }}, and which one is a
 * question about the functional interface being implemented rather than about the text. A formatter
 * does not have the target type, so {@link BracePolicy#ALWAYS} is declined for an expression body
 * rather than guessed at: braces the author did not ask for are a smaller harm than a file that no
 * longer compiles.
 */
public final class LambdaRewrite implements Rewrite {

    @Override
    public String name() {
        return "lambdas";
    }

    @Override
    public boolean enabled(RewriteContext context) {
        return context.rule(LambdaRules.PARAMETER_STYLE) != LambdaParameterStyle.PRESERVE
                || context.rule(LambdaRules.BODY_BRACES) != BracePolicy.PRESERVE;
    }

    @Override
    public GreenNode rewrite(GreenNode node, RewriteContext context) {
        if (node.kind() != SyntaxKind.LAMBDA_EXPRESSION || node.children().size() != 3) {
            return node;
        }
        List<GreenNode> children = new ArrayList<>(node.children());
        boolean changed = false;

        GreenNode parameters = rewriteParameters(children.get(0), context);
        if (parameters != children.get(0)) {
            children.set(0, parameters);
            changed = true;
        }

        GreenNode body = rewriteBody(children.get(2), context);
        if (body != children.get(2)) {
            children.set(2, body);
            changed = true;
        }

        return changed ? GreenNode.branch(node.kind(), children) : node;
    }

    // ---------------------------------------------------------- parentheses

    private GreenNode rewriteParameters(GreenNode parameters, RewriteContext context) {
        LambdaParameterStyle style = context.rule(LambdaRules.PARAMETER_STYLE);
        if (style == LambdaParameterStyle.PRESERVE || parameters.kind() != SyntaxKind.LAMBDA_PARAMETERS) {
            return parameters;
        }
        return style == LambdaParameterStyle.ALWAYS_PARENTHESISE
                ? parenthesise(parameters, context)
                : unparenthesise(parameters, context);
    }

    /** Wraps a bare name in parentheses. */
    private GreenNode parenthesise(GreenNode parameters, RewriteContext context) {
        if (parameters.children().size() != 1) {
            return parameters;
        }
        int start = context.firstPosition(parameters);
        int end = context.endPosition(parameters);
        if (start < 0 || end < 0) {
            return parameters;
        }

        context.record(
                TokenEdit.insert(
                        LambdaRules.PARAMETER_STYLE,
                        "parentheses added around a lambda parameter",
                        start,
                        TokenEdit.Bias.OUTERMOST_FIRST,
                        "("));
        context.record(
                TokenEdit.insert(
                        LambdaRules.PARAMETER_STYLE,
                        "parentheses added around a lambda parameter",
                        end,
                        TokenEdit.Bias.INNERMOST_FIRST,
                        ")"));

        List<GreenNode> children = new ArrayList<>();
        children.add(Synthetic.separator("("));
        children.add(GreenNode.branch(SyntaxKind.PARAMETER, List.of(parameters.children().getFirst())));
        children.add(Synthetic.separator(")"));
        return GreenNode.branch(SyntaxKind.LAMBDA_PARAMETERS, children);
    }

    /** Takes the parentheses off {@code (x)}, and off nothing else. */
    private GreenNode unparenthesise(GreenNode parameters, RewriteContext context) {
        List<GreenNode> children = parameters.children();
        if (children.size() != 3) {
            return parameters;
        }
        GreenNode open = children.getFirst();
        GreenNode close = children.getLast();
        GreenNode parameter = children.get(1);
        if (parameter.kind() != SyntaxKind.PARAMETER || parameter.children().size() != 1) {
            return parameters;
        }
        GreenNode name = parameter.children().getFirst();
        if (!(name instanceof GreenNode.Leaf)) {
            return parameters;
        }
        if (Synthetic.carriesComments(open) || Synthetic.carriesComments(close)) {
            return parameters;
        }

        int openPosition = context.firstPosition(open);
        int closePosition = context.firstPosition(close);
        if (openPosition < 0 || closePosition < 0) {
            return parameters;
        }

        context.record(
                TokenEdit.delete(
                        LambdaRules.PARAMETER_STYLE,
                        "parentheses removed from a single lambda parameter",
                        openPosition,
                        "("));
        context.record(
                TokenEdit.delete(
                        LambdaRules.PARAMETER_STYLE,
                        "parentheses removed from a single lambda parameter",
                        closePosition,
                        ")"));
        return GreenNode.branch(SyntaxKind.LAMBDA_PARAMETERS, List.of(name));
    }

    // ---------------------------------------------------------------- braces

    private GreenNode rewriteBody(GreenNode body, RewriteContext context) {
        BracePolicy policy = context.rule(LambdaRules.BODY_BRACES);
        if (policy == BracePolicy.PRESERVE || policy == BracePolicy.ALWAYS) {
            // ALWAYS would have to choose between { return e; } and { e; }, which the target type
            // decides and the tokens do not say. See the class comment.
            return body;
        }
        if (body.kind() != SyntaxKind.BLOCK) {
            return body;
        }
        List<GreenNode> children = body.children();
        if (children.size() != 3) {
            // Empty, or more than one statement: either way there is no expression to collapse to.
            return body;
        }
        GreenNode open = children.getFirst();
        GreenNode close = children.getLast();
        GreenNode only = children.get(1);

        GreenNode expression = collapsible(only);
        if (expression == null) {
            return body;
        }

        List<GreenNode> statement = only.children();
        GreenNode semicolon = statement.getLast();
        GreenNode keyword = only.kind() == SyntaxKind.RETURN_STATEMENT ? statement.getFirst() : null;

        // A brace, a return or a semicolon that carries a comment has nowhere to rehome it once it is
        // gone, so the whole collapse is declined rather than done at the comment's expense.
        if (Synthetic.carriesComments(open)
                || Synthetic.carriesComments(close)
                || Synthetic.carriesComments(semicolon)
                || Synthetic.carriesComments(keyword)) {
            return body;
        }

        int openPosition = context.firstPosition(open);
        int semicolonPosition = context.firstPosition(semicolon);
        if (openPosition < 0 || semicolonPosition < 0 || context.firstPosition(close) != semicolonPosition + 1) {
            return body;
        }

        String reason = "braces removed from a single-expression lambda body";
        if (keyword != null) {
            if (context.firstPosition(keyword) != openPosition + 1) {
                return body;
            }
            context.record(
                    new TokenEdit(
                            LambdaRules.BODY_BRACES,
                            reason,
                            openPosition,
                            List.of("{", "return"),
                            List.of(),
                            TokenEdit.Bias.INNERMOST_FIRST));
        } else {
            context.record(TokenEdit.delete(LambdaRules.BODY_BRACES, reason, openPosition, "{"));
        }
        context.record(TokenEdit.delete(LambdaRules.BODY_BRACES, reason, semicolonPosition, ";", "}"));
        return expression;
    }

    /**
     * The expression a one-statement block can be written as, or null when it cannot be.
     *
     * <p>{@code return e;} and a lone statement expression are the two shapes an expression body can
     * take. {@code return;} has no value to become an expression, and everything else — a
     * declaration, an {@code if}, a loop — is not an expression at all.
     */
    private static GreenNode collapsible(GreenNode statement) {
        List<GreenNode> children = statement.children();
        if (statement.kind() == SyntaxKind.RETURN_STATEMENT && children.size() == 3) {
            return children.get(1);
        }
        if (statement.kind() == SyntaxKind.EXPRESSION_STATEMENT && children.size() == 2) {
            return children.getFirst();
        }
        return null;
    }

}
