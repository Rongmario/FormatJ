package zone.rong.formatj.core.pipeline;

import zone.rong.formatj.api.LanguageLevel;
import zone.rong.formatj.api.Style;
import zone.rong.formatj.core.cst.GreenNode;
import zone.rong.formatj.core.cst.SyntaxNode;
import zone.rong.formatj.core.lexer.JavaLexer;
import zone.rong.formatj.core.lexer.Token;
import zone.rong.formatj.core.parser.JavaParser;
import zone.rong.formatj.core.parser.ParseResult;
import zone.rong.formatj.core.rewrite.RewriteResult;
import zone.rong.formatj.core.rewrite.RewriteStage;
import java.io.IOException;
import java.io.PrintStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.PathMatcher;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

/**
 * Times the formatter pipeline stages separately over a set of sources.
 *
 * <p>Measurement only: it calls the same lexer, parser, rewrite, layout, reparse, and verification
 * code the formatter uses. It does not change formatting behaviour.
 */
public final class StageTimer {

    private StageTimer() { }

    /** Nanoseconds spent in each named stage, summed over every file. */
    public record Times(
            long lexNanos,
            long parseNanos,
            long rewriteNanos,
            long layoutNanos,
            long reparseNanos,
            long verifyNanos,
            long secondLayoutNanos,
            int files) {

        public String report() {
            return "lex: " + lexNanos + " ns\n" + "parse: " + parseNanos + " ns\n" + "rewrite: " + rewriteNanos
                    + " ns\n" + "layout: " + layoutNanos + " ns\n" + "reparse: " + reparseNanos + " ns\n" + "verify: "
                    + verifyNanos + " ns\n" + "second-layout: " + secondLayoutNanos + " ns\n" + "files: " + files
                    + "\n";
        }

        Times plus(Times other) {
            return new Times(
                    lexNanos + other.lexNanos,
                    parseNanos + other.parseNanos,
                    rewriteNanos + other.rewriteNanos,
                    layoutNanos + other.layoutNanos,
                    reparseNanos + other.reparseNanos,
                    verifyNanos + other.verifyNanos,
                    secondLayoutNanos + other.secondLayoutNanos,
                    files + other.files);
        }

        Times dividedBy(int divisor) {
            return new Times(
                    lexNanos / divisor,
                    parseNanos / divisor,
                    rewriteNanos / divisor,
                    layoutNanos / divisor,
                    reparseNanos / divisor,
                    verifyNanos / divisor,
                    secondLayoutNanos / divisor,
                    files / divisor);
        }

    }

    public static Times measure(Iterable<String> sources) {
        DefaultFormatter formatter = new DefaultFormatter(Style.defaults(), LanguageLevel.LATEST, false, true);
        Times total = empty();
        for (String source : sources) {
            total = total.plus(measure(source, formatter));
        }
        return total;
    }

    static Times benchmark(List<String> sources) {
        for (int i = 0; i < 2; i++) {
            measure(sources);
        }
        Times total = empty();
        for (int i = 0; i < 5; i++) {
            total = total.plus(measure(sources));
        }
        return total.dividedBy(5);
    }

    public static Times measure(String source) {
        return measure(source, new DefaultFormatter(Style.defaults(), LanguageLevel.LATEST, false, true));
    }

    static Times measure(String source, DefaultFormatter formatter) {
        long t0 = System.nanoTime();
        List<Token> tokens = JavaLexer.tokenize(source);
        long t1 = System.nanoTime();
        ParseResult parsed = JavaParser.parse(tokens, formatter.languageLevel(), false);
        long t2 = System.nanoTime();
        GreenNode original = parsed.root().green();
        RewriteResult rewritten = RewriteStage.apply(original, formatter.style());
        long t3 = System.nanoTime();
        long verifyNanos = 0;
        long verifyStart = System.nanoTime();
        String rewriteProblem = RewriteVerification.verifyRewrite(original, rewritten.root());
        if (rewriteProblem != null) {
            throw new IllegalStateException(rewriteProblem);
        }
        if (!RewriteStage.apply(rewritten.root(), formatter.style()).unchanged()) {
            throw new IllegalStateException("rewriting did not settle after one pass");
        }
        verifyNanos += System.nanoTime() - verifyStart;

        long layoutStart = System.nanoTime();
        String formatted = formatter.layout(SyntaxNode.root(rewritten.root()));
        long t4 = System.nanoTime();
        ParseResult formattedTree = JavaParser.parse(formatted, formatter.languageLevel(), false);
        long t5 = System.nanoTime();

        verifyStart = System.nanoTime();
        if (formattedTree.hasErrors() || !formattedTree.complete()) {
            throw new IllegalStateException("formatted source did not parse completely");
        }
        boolean changed = !rewritten.unchanged() || rewritten.root() != original;
        String outputProblem =
                changed
                ? RewriteVerification.verifyOutput(original, formattedTree.root().green(), rewritten.edits())
                : TokenEquivalence.firstDifference(original, formattedTree.root().green());
        if (outputProblem != null) {
            throw new IllegalStateException(outputProblem);
        }
        String proseProblem =
                ProsePreservation.firstDifference(rewritten.root(), formattedTree.root().green(), formatter.style());
        if (proseProblem != null) {
            throw new IllegalStateException(proseProblem);
        }
        GreenNode second = RewriteStage.apply(formattedTree.root().green(), formatter.style()).root();
        verifyNanos += System.nanoTime() - verifyStart;

        long secondLayoutStart = System.nanoTime();
        String twice = formatter.layout(SyntaxNode.root(second));
        long t6 = System.nanoTime();
        if (!twice.equals(formatted)) {
            throw new IllegalStateException("formatting was not stable");
        }
        return new Times(t1 - t0, t2 - t1, t3 - t2, t4 - layoutStart, t5 - t4, verifyNanos, t6 - secondLayoutStart, 1);
    }

    public static void writeReport(Times times, PrintStream out) {
        out.print(times.report());
    }

    public static void main(String[] arguments) throws IOException {
        Path root = Path.of(arguments.length == 0 ? "." : arguments[0]).toAbsolutePath().normalize();
        List<String> excludes = new ArrayList<>();
        for (int i = 1; i < arguments.length; i++) {
            excludes.add(arguments[i]);
        }
        if (excludes.isEmpty()) {
            excludes.add("**/build/**");
            excludes.add("**/src/test/resources/**");
        }
        List<String> sources = load(root, excludes);
        writeReport(benchmark(sources), System.out);
    }

    private static List<String> load(Path root, List<String> excludes) throws IOException {
        List<PathMatcher> matchers = new ArrayList<>();
        for (String glob : excludes) {
            matchers.add(FileSystems.getDefault().getPathMatcher("glob:" + glob));
        }
        try (Stream<Path> files = Files.walk(root)) {
            return files.filter(Files::isRegularFile)
                    .filter(path -> path.toString().endsWith(".java"))
                    .filter(path -> {
                        String relative = root.relativize(path).toString().replace('\\', '/');
                        for (PathMatcher matcher : matchers) {
                            if (matcher.matches(Path.of(relative)) || matcher.matches(path)) {
                                return false;
                            }
                            if (relative.contains("/build/") || relative.contains("/src/test/resources/")) {
                                return false;
                            }
                        }
                        return true;
                    })
                    .sorted()
                    .map(path -> {
                        try {
                            return Files.readString(path, StandardCharsets.UTF_8);
                        } catch (IOException e) {
                            throw new UncheckedIOException(e);
                        }
                    })
                    .toList();
        }
    }

    private static Times empty() {
        return new Times(0, 0, 0, 0, 0, 0, 0, 0);
    }

}
