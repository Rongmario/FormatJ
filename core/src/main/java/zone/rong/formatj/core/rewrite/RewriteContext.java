package zone.rong.formatj.core.rewrite;

import zone.rong.formatj.api.Option;
import zone.rong.formatj.api.Style;
import zone.rong.formatj.core.cst.GreenNode;
import zone.rong.formatj.core.cst.ProgramTokens;
import java.util.List;
import java.util.Map;

/**
 * What a {@link Rewrite} is given: the style to obey, the ledger to declare its edits in, and a way
 * to ask where a node's tokens sit in the original program.
 *
 * <p>Positions come from a map built once over the tree as it was before any rewriting, keyed by leaf
 * identity. Rewriting rebuilds only the ancestors of what it changes and reuses every untouched node,
 * so a leaf the rewrite is looking at is usually the same object the map knows about. Leaves the
 * rewrite stage itself created have no original position, which is exactly right: an edit is always
 * expressed against the tokens that were already there.
 */
public final class RewriteContext {

    private final Style style;
    private final EditLedger ledger;
    private final Map<GreenNode.Leaf, Integer> positions;

    RewriteContext(Style style, EditLedger ledger, GreenNode original) {
        this.style = style;
        this.ledger = ledger;
        this.positions = ProgramTokens.positions(original);
    }

    public Style style() {
        return style;
    }

    public <T> T rule(Option<T> option) {
        return style.get(option);
    }

    public void record(TokenEdit edit) {
        ledger.record(edit);
    }

    /**
     * The position of a node's first program token in the original token stream.
     *
     * @return the position, or -1 when the node has no token that was in the original source
     */
    public int firstPosition(GreenNode node) {
        for (GreenNode.Leaf leaf : ProgramTokens.leaves(node)) {
            Integer position = positions.get(leaf);
            if (position != null) {
                return position;
            }
        }
        return -1;
    }

    /**
     * One past the position of a node's last program token in the original token stream.
     *
     * <p>Tokens the rewrite stage added inside the node are skipped, so the answer stays a coordinate
     * in the original stream. Edits already recorded for those added tokens sort themselves out
     * against this one by their {@link TokenEdit.Bias}.
     *
     * @return the position, or -1 when the node has no token that was in the original source
     */
    public int endPosition(GreenNode node) {
        List<GreenNode.Leaf> leaves = ProgramTokens.leaves(node);
        for (int i = leaves.size() - 1; i >= 0; i--) {
            Integer position = positions.get(leaves.get(i));
            if (position != null) {
                return position + 1;
            }
        }
        return -1;
    }

}
