package zone.rong.formatj.core.pipeline;

import zone.rong.formatj.api.Diagnostic;
import zone.rong.formatj.api.FormatRequest;
import zone.rong.formatj.api.FormatResult;
import zone.rong.formatj.api.Formatter;
import zone.rong.formatj.api.LanguageLevel;
import zone.rong.formatj.api.Style;
import zone.rong.formatj.api.rules.FileRules;
import zone.rong.formatj.api.rules.IndentRules;
import zone.rong.formatj.api.rules.WrappingRules;
import zone.rong.formatj.core.cst.SyntaxNode;
import zone.rong.formatj.core.emit.DocEmitter;
import zone.rong.formatj.core.layout.DocPrinter;
import zone.rong.formatj.core.lexer.JavaLexer;
import zone.rong.formatj.core.lexer.Token;
import zone.rong.formatj.core.parser.JavaParser;
import zone.rong.formatj.core.parser.ParseResult;
import java.util.ArrayList;
import java.util.List;

/**
 * The formatting pipeline: lex, parse, emit, lay out, verify.
 *
 * <p>Verification is not optional decoration. Formatting must be a fixed point (formatting twice
 * changes nothing the second time) and must preserve the program's tokens; when either check fails
 * the original source is returned with a diagnostic. The optional semicolon after a no-argument
 * enum constant list is a style choice and is allowed to appear or disappear.
 */
public final class DefaultFormatter implements Formatter {

    private final Style style;
    private final LanguageLevel languageLevel;
    private final boolean previewFeatures;
    private final boolean verify;

    public DefaultFormatter(Style style, LanguageLevel languageLevel, boolean previewFeatures, boolean verify) {
        this.style = style;
        this.languageLevel = languageLevel;
        this.previewFeatures = previewFeatures;
        this.verify = verify;
    }

    @Override
    public Style style() {
        return style;
    }

    @Override
    public LanguageLevel languageLevel() {
        return languageLevel;
    }

    @Override
    public FormatResult format(FormatRequest request) {
        String source = request.source();
        List<Diagnostic> diagnostics = new ArrayList<>();

        List<Token> tokens = JavaLexer.tokenize(source);
        if (!JavaLexer.toSource(tokens).equals(source)) {
            // A lexer that loses characters would make every later stage unsafe.
            return FormatResult.failed(
                    source,
                    List.of(Diagnostic.error("Lexer did not round-trip the source; file left unchanged")));
        }

        ParseResult parsed = JavaParser.parse(tokens, languageLevel, previewFeatures);
        diagnostics.addAll(parsed.diagnostics());
        if (parsed.hasErrors()) {
            return FormatResult.failed(source, diagnostics);
        }
        if (!parsed.complete()) {
            diagnostics.add(Diagnostic.info("Parser does not yet cover this file; source left unchanged"));
            return FormatResult.unchanged(source).withDiagnostics(diagnostics);
        }

        String formatted = layout(parsed.root());

        if (verify) {
            ParseResult formattedTree = JavaParser.parse(formatted, languageLevel, previewFeatures);
            String problem = TokenEquivalence.firstDifference(parsed.root().green(), formattedTree.root().green());
            if (problem != null) {
                return FormatResult.failed(
                        source,
                        List.of(Diagnostic.error("Formatting would change the program: " + problem)));
            }
            String twice = layout(formattedTree.root());
            if (!twice.equals(formatted)) {
                return FormatResult.failed(
                        source,
                        List.of(Diagnostic.error("Formatting is not stable; file left unchanged")));
            }
        }

        return FormatResult.formatted(source, formatted).withDiagnostics(diagnostics);
    }

    /** Lays out a parsed file and normalises how it ends. */
    private String layout(SyntaxNode root) {
        String text = printer().print(new DocEmitter(style).emit(root));
        String separator = lineSeparator();
        String trimmed = stripTrailingBlankLines(text);
        return style.get(FileRules.FINAL_NEWLINE) ? trimmed + separator : trimmed;
    }

    private static String stripTrailingBlankLines(String text) {
        int end = text.length();
        while (end > 0 && Character.isWhitespace(text.charAt(end - 1))) {
            end--;
        }
        return text.substring(0, end);
    }

    private DocPrinter printer() {
        return new DocPrinter(
                style.get(WrappingRules.MAX_LINE_LENGTH),
                style.get(IndentRules.USE_TABS),
                style.get(FileRules.TAB_WIDTH),
                lineSeparator());
    }

    private String lineSeparator() {
        return switch (style.get(FileRules.LINE_ENDING)) {
            case CRLF -> "\r\n";
            case SYSTEM -> System.lineSeparator();
            case LF, PRESERVE -> "\n";
        };
    }

}
