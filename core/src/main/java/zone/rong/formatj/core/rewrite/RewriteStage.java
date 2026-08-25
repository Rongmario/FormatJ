package zone.rong.formatj.core.rewrite;

import zone.rong.formatj.api.Style;
import zone.rong.formatj.core.cst.GreenNode;
import java.util.ArrayList;
import java.util.List;

/**
 * The stage between parsing and emitting, where rules that add or remove code are applied.
 *
 * <p>Everything below this stage is lossless: the tree the parser produced concatenates back to the
 * file it came from. Everything above it is layout, which cannot change the program. This stage is
 * the one place allowed to change the program, and it pays for that with
 * {@code RewriteVerification}: the tree it returns comes with a declared account of every token it
 * added or removed, and the output is checked against that account rather than merely inspected for
 * plausibility.
 *
 * <h2>Order</h2>
 *
 * <p>Rewrites run in the fixed order declared here, once each, rather than being iterated to a
 * fixed point. Imports come first because they work on the file as a whole and nothing else has an
 * opinion about them. A fixed point would hide rules that disagree with each other; running once in a stated
 * order means two rules that fight produce a stable, explainable result. The order matters as more
 * rules land: a rule that turns colon cases into arrow cases creates bodies that the brace rules have
 * an opinion about, so it runs before them. Text blocks come last because nothing else can produce
 * one and they can produce nothing else.
 *
 * <p>Within a rewrite, nodes are visited innermost first, so a construct's own children are settled
 * before it is asked about itself.
 *
 * <h2>What is never touched</h2>
 *
 * <p>Regions the parser could not read, and regions between formatter-off markers, are emitted
 * exactly as written. This stage refuses to descend into them, so an individual rewrite does not have
 * to remember to check.
 */
public final class RewriteStage {

    private static final List<Rewrite> REWRITES =
            List.of(
                    new ImportRewrite(),
                    new SealedRewrite(),
                    new SwitchCaseRewrite(),
                    new SwitchRewrite(),
                    new LambdaRewrite(),
                    new BraceRewrite(),
                    new TextBlockRewrite());

    private RewriteStage() { }

    /** Every rewrite the formatter ships, in the order they run. */
    public static List<Rewrite> defaults() {
        return REWRITES;
    }

    /** Applies the enabled rewrites to a tree, returning it and the edits that were declared. */
    public static RewriteResult apply(GreenNode root, Style style) {
        return apply(root, style, REWRITES);
    }

    /**
     * Applies a given set of rewrites.
     *
     * <p>Exists so that verification can be exercised against a deliberately faulty rewrite. Nothing
     * else should be choosing its own set: the order rewrites run in is part of the formatter's
     * behaviour, not a caller's decision.
     */
    public static RewriteResult apply(GreenNode root, Style style, List<Rewrite> rewrites) {
        EditLedger ledger = new EditLedger();
        RewriteContext context = new RewriteContext(style, ledger, root);

        GreenNode rewritten = root;
        for (Rewrite rewrite : rewrites) {
            if (rewrite.enabled(context)) {
                rewritten = visit(rewritten, rewrite, context);
            }
        }
        return new RewriteResult(rewritten, ledger.edits());
    }

    private static GreenNode visit(GreenNode node, Rewrite rewrite, RewriteContext context) {
        if (node instanceof GreenNode.Leaf || node.kind().isVerbatim()) {
            return node;
        }

        List<GreenNode> children = node.children();
        List<GreenNode> rewrittenChildren = null;
        for (int i = 0; i < children.size(); i++) {
            GreenNode child = children.get(i);
            GreenNode rewrittenChild = visit(child, rewrite, context);
            if (rewrittenChild != child && rewrittenChildren == null) {
                rewrittenChildren = new ArrayList<>(children);
            }
            if (rewrittenChildren != null) {
                rewrittenChildren.set(i, rewrittenChild);
            }
        }

        GreenNode rebuilt = rewrittenChildren == null ? node : GreenNode.branch(node.kind(), rewrittenChildren);
        return rewrite.rewrite(rebuilt, context);
    }

}
