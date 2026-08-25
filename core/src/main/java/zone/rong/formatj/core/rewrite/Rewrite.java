package zone.rong.formatj.core.rewrite;

import zone.rong.formatj.core.cst.GreenNode;

/**
 * One rule that may add or remove code.
 *
 * <p>A rewrite is handed each node of the tree, innermost first, and returns either that same node
 * or a replacement for it. Every replacement that changes the program's tokens must be declared to
 * {@link RewriteContext#record}; an undeclared change fails verification and costs the file its
 * rewrites, so declaring is not optional politeness.
 *
 * <p>Rewrites must be idempotent. Running one over its own output has to produce no further edits,
 * because formatting is required to be a fixed point and a rule that keeps changing its mind would
 * break that for every file it touches.
 */
public interface Rewrite {

    /** The rule key this rewrite implements, for diagnostics. */
    String name();

    /** Whether the current style asks for this rewrite at all; false skips the traversal entirely. */
    boolean enabled(RewriteContext context);

    /** Returns {@code node} unchanged, or a replacement whose every token change has been recorded. */
    GreenNode rewrite(GreenNode node, RewriteContext context);

}
