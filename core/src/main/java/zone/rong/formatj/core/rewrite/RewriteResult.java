package zone.rong.formatj.core.rewrite;

import zone.rong.formatj.api.Diagnostic;
import zone.rong.formatj.core.cst.GreenNode;
import java.util.List;

/**
 * A rewritten tree and the complete account of what was done to it.
 *
 * @param root the tree to lay out, which no longer round-trips to the original source when
 *     {@code edits} is non-empty
 * @param edits every change to the program's tokens, in the order they were recorded
 * @param warnings what the rewrites want the user told, such as code they declined to touch
 */
public record RewriteResult(GreenNode root, List<TokenEdit> edits, List<Diagnostic> warnings) {

    public RewriteResult {
        edits = List.copyOf(edits);
        warnings = List.copyOf(warnings);
    }

    public RewriteResult(GreenNode root, List<TokenEdit> edits) {
        this(root, edits, List.of());
    }

    /** Whether the stage left the program's tokens alone. */
    public boolean unchanged() {
        return edits.isEmpty();
    }

}
