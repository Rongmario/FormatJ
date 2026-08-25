package zone.rong.formatj.core.rewrite;

import java.util.ArrayList;
import java.util.List;

/**
 * The edits a run of the rewrite stage declared, in the order they were recorded.
 *
 * <p>Recording order carries meaning: rewriting runs innermost-first, so it is the tiebreak the
 * verifier needs for two edits landing on the same token. See {@link TokenEdit.Bias}.
 */
public final class EditLedger {

    private final List<TokenEdit> edits = new ArrayList<>();

    public void record(TokenEdit edit) {
        edits.add(edit);
    }

    public List<TokenEdit> edits() {
        return List.copyOf(edits);
    }

    public boolean isEmpty() {
        return edits.isEmpty();
    }

    public int size() {
        return edits.size();
    }

}
