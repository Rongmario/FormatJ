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
import zone.rong.formatj.core.cst.GreenNode;
import zone.rong.formatj.core.cst.SyntaxNode;
import zone.rong.formatj.core.emit.DocEmitter;
import zone.rong.formatj.core.layout.DocPrinter;
import zone.rong.formatj.core.lexer.JavaLexer;
import zone.rong.formatj.core.lexer.Token;
import zone.rong.formatj.core.parser.JavaParser;
import zone.rong.formatj.core.parser.ParseResult;
import zone.rong.formatj.core.rewrite.Rewrite;
import zone.rong.formatj.core.rewrite.RewriteResult;
import zone.rong.formatj.core.rewrite.RewriteStage;
import java.util.ArrayList;
import java.util.List;

/**
 * The formatting pipeline: lex, parse, rewrite, emit, lay out, verify.
 *
 * <p>Verification is not optional decoration. Formatting must be a fixed point: formatting twice
 * changes nothing the second time, and when it does the original source is returned with a
 * diagnostic.
 *
 * <p>What the output is checked against depends on whether any rule that adds or removes code is
 * turned on. With none of them on, the program's tokens must come out exactly as they went in. With
 * one of them on, the rewrite stage declares every token it added or removed, and the output must be
 * the original with precisely those edits applied and nothing else; see {@link RewriteVerification}.
 * Either way the optional semicolon after a no-argument enum constant list is a style choice and is
 * allowed to appear or disappear.
 *
 * <p>When a rewrite fails verification the file is not abandoned. The pipeline runs again with
 * rewriting switched off and returns that result with a warning, so an unsound rule costs the file
 * its rewrites rather than all of its formatting.
 */
public final class DefaultFormatter implements Formatter {

    private final Style style;
    private final LanguageLevel languageLevel;
    private final boolean previewFeatures;
    private final boolean verify;
    private final List<Rewrite> rewrites;

    public DefaultFormatter(Style style, LanguageLevel languageLevel, boolean previewFeatures, boolean verify) {
        this(style, languageLevel, previewFeatures, verify, RewriteStage.defaults());
    }

    /**
     * A formatter running a chosen set of rewrites.
     *
     * <p>Only verification's own tests need this: they have to feed the pipeline a rewrite that
     * misbehaves on purpose, which is the one thing no shipped rewrite will do.
     */
    DefaultFormatter(
            Style style,
            LanguageLevel languageLevel,
            boolean previewFeatures,
            boolean verify,
            List<Rewrite> rewrites) {
        this.style = style;
        this.languageLevel = languageLevel;
        this.previewFeatures = previewFeatures;
        this.verify = verify;
        this.rewrites = List.copyOf(rewrites);
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

        Attempt attempt = attempt(parsed, true);
        if (attempt.failed() && attempt.rewrote()) {
            Attempt withoutRewrites = attempt(parsed, false);
            if (withoutRewrites.failed()) {
                return FormatResult.failed(source, List.of(Diagnostic.error(withoutRewrites.problem())));
            }
            diagnostics.add(
                    Diagnostic.warning(
                            "Rules that add or remove code were skipped for this file: " + attempt.problem()));
            return FormatResult.formatted(source, withoutRewrites.text()).withDiagnostics(diagnostics);
        }
        if (attempt.failed()) {
            return FormatResult.failed(source, List.of(Diagnostic.error(attempt.problem())));
        }

        return FormatResult.formatted(source, attempt.text()).withDiagnostics(diagnostics);
    }

    /**
     * One run of rewrite, emit, lay out and verify.
     *
     * @param allowed whether rules that add or remove code may run
     */
    private Attempt attempt(ParseResult parsed, boolean allowed) {
        GreenNode original = parsed.root().green();
        RewriteResult rewritten =
                allowed ? RewriteStage.apply(original, style, this.rewrites) : new RewriteResult(original, List.of());

        // Not "did it declare an edit" but "did it touch anything": a rewrite that changed the tree
        // and declared nothing is the worst case, and the one most in need of the fallback.
        boolean rewrote = !rewritten.unchanged() || rewritten.root() != original;

        if (verify && rewrote) {
            String problem = RewriteVerification.verifyRewrite(original, rewritten.root());
            if (problem != null) {
                return Attempt.failure(problem, true);
            }
            // A rule that keeps changing its mind would cost every file it touches the fixed point.
            if (!RewriteStage.apply(rewritten.root(), style, this.rewrites).unchanged()) {
                return Attempt.failure("rewriting did not settle after one pass", true);
            }
        }

        String formatted = layout(SyntaxNode.root(rewritten.root()));
        if (!verify) {
            return Attempt.success(formatted, rewrote);
        }

        ParseResult formattedTree = JavaParser.parse(formatted, languageLevel, previewFeatures);
        if (formattedTree.hasErrors() || !formattedTree.complete()) {
            return Attempt.failure("Formatting produced source that no longer parses", rewrote);
        }

        String problem =
                rewrote
                        ? RewriteVerification.verifyOutput(original, formattedTree.root().green(), rewritten.edits())
                        : TokenEquivalence.firstDifference(original, formattedTree.root().green());
        if (problem != null) {
            return Attempt.failure("Formatting would change the program: " + problem, rewrote);
        }

        GreenNode second =
                allowed
                        ? RewriteStage.apply(formattedTree.root().green(), style, this.rewrites).root()
                        : formattedTree.root().green();
        String twice = layout(SyntaxNode.root(second));
        if (!twice.equals(formatted)) {
            return Attempt.failure("Formatting is not stable; file left unchanged", rewrote);
        }

        return Attempt.success(formatted, rewrote);
    }

    /**
     * The outcome of one run.
     *
     * @param rewrote whether the run actually changed the program, and so whether dropping the
     *     rewrites is a fallback worth trying
     */
    private record Attempt(String text, String problem, boolean rewrote) {

        static Attempt success(String text, boolean rewrote) {
            return new Attempt(text, null, rewrote);
        }

        static Attempt failure(String problem, boolean rewrote) {
            return new Attempt(null, problem, rewrote);
        }

        boolean failed() {
            return problem != null;
        }

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
                lineSeparator(),
                style.get(FileRules.TRIM_TRAILING_WHITESPACE));
    }

    private String lineSeparator() {
        return switch (style.get(FileRules.LINE_ENDING)) {
            case CRLF -> "\r\n";
            case SYSTEM -> System.lineSeparator();
            case LF, PRESERVE -> "\n";
        };
    }

}
