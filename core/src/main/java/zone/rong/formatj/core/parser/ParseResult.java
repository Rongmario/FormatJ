package zone.rong.formatj.core.parser;

import zone.rong.formatj.api.Diagnostic;
import zone.rong.formatj.core.cst.SyntaxNode;
import java.util.List;

/**
 * The outcome of parsing one file.
 *
 * @param root the concrete syntax tree, always lossless even when parsing was incomplete
 * @param diagnostics problems found while parsing
 * @param complete whether the parser understood the whole file; when false the tree contains
 *     {@code UNPARSED} regions and the formatter must leave those regions alone
 */
public record ParseResult(SyntaxNode root, List<Diagnostic> diagnostics, boolean complete) {

    public ParseResult {
        diagnostics = List.copyOf(diagnostics);
    }

    public boolean hasErrors() {
        return diagnostics.stream().anyMatch(d -> d.severity() == Diagnostic.Severity.ERROR);
    }

}
