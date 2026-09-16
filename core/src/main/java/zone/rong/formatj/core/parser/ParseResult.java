package zone.rong.formatj.core.parser;

import zone.rong.formatj.api.Diagnostic;
import zone.rong.formatj.core.cst.GreenNode;
import zone.rong.formatj.core.cst.SyntaxKind;
import zone.rong.formatj.core.cst.SyntaxNode;
import java.util.List;

/**
 * The outcome of parsing one file.
 *
 * @param root the concrete syntax tree, always lossless even when parsing was incomplete
 * @param diagnostics problems found while parsing
 */
public record ParseResult(SyntaxNode root, List<Diagnostic> diagnostics) {

    public ParseResult {
        diagnostics = List.copyOf(diagnostics);
    }

    public boolean hasErrors() {
        return diagnostics.stream().anyMatch(d -> d.severity() == Diagnostic.Severity.ERROR);
    }

    /**
     * Whether the parser understood the whole file. Derived from the tree itself rather than
     * threaded through every parse method, so a failure deep in a member or statement can't be
     * missed the way a hand-maintained flag was.
     */
    public boolean complete() {
        return !containsUnparsed(root.green());
    }

    private static boolean containsUnparsed(GreenNode node) {
        if (node.kind() == SyntaxKind.UNPARSED) {
            return true;
        }
        for (GreenNode child : node.children()) {
            if (containsUnparsed(child)) {
                return true;
            }
        }
        return false;
    }

}
