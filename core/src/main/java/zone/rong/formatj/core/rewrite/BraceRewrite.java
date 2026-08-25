package zone.rong.formatj.core.rewrite;

import zone.rong.formatj.api.Option;
import zone.rong.formatj.api.rules.BraceRules;
import zone.rong.formatj.api.rules.BracePolicy;
import zone.rong.formatj.core.cst.GreenNode;
import zone.rong.formatj.core.cst.SyntaxKind;
import zone.rong.formatj.core.cst.SyntaxToken;
import zone.rong.formatj.core.lexer.Token;
import zone.rong.formatj.core.lexer.TokenKind;
import java.util.ArrayList;
import java.util.List;

/**
 * Puts braces around the body of a control statement, or takes them off.
 *
 * <p>The emitter already lays out both shapes, a {@code BLOCK} body and a bare statement, so this
 * rewrite only has to decide which of the two it is handed. Nothing in the emitter changes.
 *
 * <h2>When braces are refused</h2>
 *
 * <p>Adding braces is always safe. Removing them is not, and the cases below are refused rather than
 * risked, because a formatter that produces code which does not compile is worse than one that
 * leaves a brace the author did not want:
 *
 * <ul>
 *   <li>a declaration cannot be the unbraced body of a control statement, so the braces stay;
 *   <li>an {@code if} with an {@code else} keeps the braces around a body containing an {@code if},
 *       because removing them lets the {@code else} reattach to the inner {@code if};
 *   <li>braces carrying comments stay, because there is no unambiguous home for the comment once the
 *       brace it was attached to is gone.
 * </ul>
 */
public final class BraceRewrite implements Rewrite {

    @Override
    public String name() {
        return "braces";
    }

    @Override
    public boolean enabled(RewriteContext context) {
        return context.rule(BraceRules.IF_ELSE) != BracePolicy.PRESERVE
                || context.rule(BraceRules.FOR_LOOP) != BracePolicy.PRESERVE
                || context.rule(BraceRules.WHILE_LOOP) != BracePolicy.PRESERVE;
    }

    @Override
    public GreenNode rewrite(GreenNode node, RewriteContext context) {
        Option<BracePolicy> authority = authorityFor(node.kind());
        if (authority == null) {
            return node;
        }
        BracePolicy policy = context.rule(authority);
        if (policy == BracePolicy.PRESERVE) {
            return node;
        }

        int index = bodyIndex(node);
        List<GreenNode> children = node.children();
        if (index < 0 || index >= children.size()) {
            return node;
        }
        GreenNode body = children.get(index);

        // "else if" is a chain, not a body; bracing it would produce else { if (...) ... }.
        if (node.kind() == SyntaxKind.ELSE_CLAUSE && body.kind() == SyntaxKind.IF_STATEMENT) {
            return node;
        }

        GreenNode replacement = rewriteBody(node, body, policy, authority, context);
        if (replacement == body) {
            return node;
        }
        List<GreenNode> rewritten = new ArrayList<>(children);
        rewritten.set(index, replacement);
        return GreenNode.branch(node.kind(), rewritten);
    }

    private GreenNode rewriteBody(
            GreenNode statement,
            GreenNode body,
            BracePolicy policy,
            Option<BracePolicy> authority,
            RewriteContext context) {
        boolean braced = body.kind() == SyntaxKind.BLOCK;
        boolean wanted =
                switch (policy) {
                    case ALWAYS -> true;
                    case NEVER -> false;
                    case WHEN_MULTI_STATEMENT -> braced ? statementsIn(body) != 1 : false;
                    case PRESERVE -> braced;
                };

        if (wanted == braced) {
            return body;
        }
        return wanted ? wrap(body, authority, context) : unwrap(statement, body, authority, context);
    }

    /** Puts a synthetic block around a bare statement. */
    private GreenNode wrap(GreenNode body, Option<BracePolicy> authority, RewriteContext context) {
        int start = context.firstPosition(body);
        int end = context.endPosition(body);
        if (start < 0 || end < 0) {
            // A body with no token from the original source cannot be described as an edit against it.
            return body;
        }

        context.record(
                TokenEdit.insert(
                        authority,
                        "braces added around a control statement body",
                        start,
                        TokenEdit.Bias.OUTERMOST_FIRST,
                        "{"));
        context.record(
                TokenEdit.insert(
                        authority,
                        "braces added around a control statement body",
                        end,
                        TokenEdit.Bias.INNERMOST_FIRST,
                        "}"));

        return GreenNode.branch(SyntaxKind.BLOCK, List.of(brace("{"), body, brace("}")));
    }

    /** Takes a single-statement block down to the statement itself, where that is safe. */
    private GreenNode unwrap(
            GreenNode statement,
            GreenNode body,
            Option<BracePolicy> authority,
            RewriteContext context) {
        List<GreenNode> children = body.children();
        if (children.size() != 3) {
            return body;
        }
        GreenNode open = children.getFirst();
        GreenNode close = children.getLast();
        GreenNode only = children.get(1);

        if (!(open instanceof GreenNode.Leaf openLeaf) || !(close instanceof GreenNode.Leaf closeLeaf)) {
            return body;
        }
        if (only.kind() == SyntaxKind.LOCAL_VARIABLE_DECLARATION || only.kind() == SyntaxKind.LOCAL_TYPE_DECLARATION) {
            return body;
        }
        if (openLeaf.token().hasComments() || closeLeaf.token().hasComments()) {
            return body;
        }
        if (danglingElse(statement, only)) {
            return body;
        }

        int openPosition = context.firstPosition(open);
        int closePosition = context.firstPosition(close);
        if (openPosition < 0 || closePosition < 0) {
            return body;
        }

        context.record(TokenEdit.delete(authority, "braces removed from a single-statement body", openPosition, "{"));
        context.record(TokenEdit.delete(authority, "braces removed from a single-statement body", closePosition, "}"));
        return only;
    }

    /**
     * Whether unbracing this body would let a following {@code else} bind to the wrong {@code if}.
     *
     * <p>Conservative on purpose: any {@code if} inside the body of an {@code if} that has an
     * {@code else} keeps its braces, without working out whether that particular inner {@code if}
     * would actually capture the {@code else}.
     */
    private boolean danglingElse(GreenNode statement, GreenNode only) {
        return statement.kind() == SyntaxKind.IF_STATEMENT
                && statement.children().size() > 5
                && only.kind() == SyntaxKind.IF_STATEMENT;
    }

    private static int statementsIn(GreenNode block) {
        return Math.max(0, block.children().size() - 2);
    }

    private static GreenNode brace(String lexeme) {
        return GreenNode.leaf(SyntaxToken.of(Token.synthetic(TokenKind.SEPARATOR, lexeme)));
    }

    /** Which rule governs the body of this statement, or null when it has no body to brace. */
    private static Option<BracePolicy> authorityFor(SyntaxKind kind) {
        return switch (kind) {
            case IF_STATEMENT, ELSE_CLAUSE -> BraceRules.IF_ELSE;
            case FOR_STATEMENT, ENHANCED_FOR_STATEMENT -> BraceRules.FOR_LOOP;
            case WHILE_STATEMENT, DO_STATEMENT -> BraceRules.WHILE_LOOP;
            default -> null;
        };
    }

    /**
     * Where the body sits among a statement's children.
     *
     * <p>A {@code for} is the odd one out: its header holds a variable number of children, so the
     * body is found from the end rather than counted from the start.
     */
    private static int bodyIndex(GreenNode node) {
        return switch (node.kind()) {
            case IF_STATEMENT, WHILE_STATEMENT, ENHANCED_FOR_STATEMENT -> 4;
            case ELSE_CLAUSE, DO_STATEMENT -> 1;
            case FOR_STATEMENT -> node.children().size() - 1;
            default -> -1;
        };
    }

}
