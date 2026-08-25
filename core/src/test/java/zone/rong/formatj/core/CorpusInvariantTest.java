package zone.rong.formatj.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import zone.rong.formatj.api.Diagnostic;
import zone.rong.formatj.api.FormatRequest;
import zone.rong.formatj.api.FormatResult;
import zone.rong.formatj.api.Formatter;
import zone.rong.formatj.api.LanguageLevel;
import zone.rong.formatj.api.Style;
import zone.rong.formatj.api.rules.BracePolicy;
import zone.rong.formatj.api.rules.BraceRules;
import zone.rong.formatj.api.rules.ImportRules;
import zone.rong.formatj.api.rules.SortOrder;
import zone.rong.formatj.core.cst.SyntaxKind;
import zone.rong.formatj.core.cst.SyntaxNode;
import zone.rong.formatj.core.lexer.JavaLexer;
import zone.rong.formatj.core.parser.JavaParser;
import zone.rong.formatj.core.parser.ParseResult;
import zone.rong.formatj.core.pipeline.TokenEquivalence;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.TestFactory;

/**
 * Runs the invariants over a real corpus: FormatJ's own sources. No expected output is needed, which
 * is what lets the corpus grow without anyone maintaining golden files for it.
 */
class CorpusInvariantTest {

    private static List<String> unparsedRegions(SyntaxNode node) {
        List<String> regions = new ArrayList<>();
        collect(node, regions);
        return regions;
    }

    private static void collect(SyntaxNode node, List<String> regions) {
        if (node.kind() == SyntaxKind.UNPARSED) {
            String text = node.text().strip();
            regions.add(text.length() > 90 ? text.substring(0, 90) + "..." : text);
            return;
        }
        for (SyntaxNode child : node.children()) {
            collect(child, regions);
        }
    }

    private static List<Path> corpus() throws IOException {
        Path repository = Path.of("..").toAbsolutePath().normalize();
        try (Stream<Path> files = Files.walk(repository)) {
            List<Path> javaFiles = files.filter(Files::isRegularFile)
                    .filter(path -> path.toString().endsWith(".java"))
                    .filter(path -> !path.toString().contains("/build/"))
                    .sorted()
                    .toList();
            assertTrue(javaFiles.size() > 20, "corpus should not be empty");
            return javaFiles;
        }
    }

    /**
     * The same corpus under a style that rewrites, which is the strongest signal available that the
     * rewrite stage and the account it gives of itself agree.
     *
     * <p>Every file must verify. A file whose rewrites were dropped comes back with a warning rather
     * than an error, so a passing run means the ledger matched the output, not merely that nothing
     * crashed.
     */
    @TestFactory
    Stream<DynamicTest> everySourceFileSurvivesHavingBracesAdded() throws IOException {
        Path repository = Path.of("..").toAbsolutePath().normalize();
        Style bracing = Style.builder()
                .set(BraceRules.IF_ELSE, BracePolicy.ALWAYS)
                .set(BraceRules.FOR_LOOP, BracePolicy.ALWAYS)
                .set(BraceRules.WHILE_LOOP, BracePolicy.ALWAYS)
                .build();
        Formatter formatter = FormatJ.newFormatter().style(bracing).build();
        return corpus().stream().map(path -> DynamicTest.dynamicTest(
                repository.relativize(path).toString(),
                () -> {
                    String source = Files.readString(path, StandardCharsets.UTF_8);
                    FormatResult once = formatter.format(FormatRequest.of(source).withName(path.toString()));
                    assertTrue(!once.hasErrors(), () -> "formatting failed: " + once.diagnostics());
                    assertTrue(
                            once.diagnostics().stream()
                                    .noneMatch(d -> d.severity() == Diagnostic.Severity.WARNING),
                            () -> "rewrites were dropped: " + once.diagnostics());

                    FormatResult twice = formatter.format(FormatRequest.of(once.text()).withName(path.toString()));
                    assertEquals(once.text(), twice.text(), "formatting must be a fixed point");
                }));
    }

    /**
     * The same corpus under a style that sorts imports and deletes the unused ones.
     *
     * <p>Reordering is the edit class where a mistake is quietest — the file still compiles, it just
     * says something else — so it is the one most worth running over real code with the ledger
     * watching.
     */
    @TestFactory
    Stream<DynamicTest> everySourceFileSurvivesHavingItsImportsSorted() throws IOException {
        Path repository = Path.of("..").toAbsolutePath().normalize();
        Style sorting = Style.builder()
                .set(ImportRules.ORDER, SortOrder.ASCENDING)
                .set(ImportRules.REMOVE_UNUSED, true)
                .build();
        Formatter formatter = FormatJ.newFormatter().style(sorting).build();
        return corpus().stream().map(path -> DynamicTest.dynamicTest(
                repository.relativize(path).toString(),
                () -> {
                    String source = Files.readString(path, StandardCharsets.UTF_8);
                    FormatResult once = formatter.format(FormatRequest.of(source).withName(path.toString()));
                    assertTrue(!once.hasErrors(), () -> "formatting failed: " + once.diagnostics());
                    assertTrue(
                            once.diagnostics().stream()
                                    .noneMatch(d -> d.severity() == Diagnostic.Severity.WARNING),
                            () -> "rewrites were dropped: " + once.diagnostics());

                    FormatResult twice = formatter.format(FormatRequest.of(once.text()).withName(path.toString()));
                    assertEquals(once.text(), twice.text(), "formatting must be a fixed point");
                }));
    }

    @TestFactory
    Stream<DynamicTest> everySourceFileSatisfiesTheInvariants() throws IOException {
        Path repository = Path.of("..").toAbsolutePath().normalize();
        try (Stream<Path> files = Files.walk(repository)) {
            List<Path> javaFiles = files.filter(Files::isRegularFile)
                    .filter(path -> path.toString().endsWith(".java"))
                    .filter(path -> !path.toString().contains("/build/"))
                    .sorted()
                    .toList();
            assertTrue(javaFiles.size() > 20, "corpus should not be empty");
            Formatter formatter = FormatJ.defaultFormatter();
            return javaFiles.stream()
                    .map(path -> DynamicTest.dynamicTest(
                            repository.relativize(path).toString(),
                            () -> {
                                String source = Files.readString(path, StandardCharsets.UTF_8);
                                assertEquals(
                                        source,
                                        JavaLexer.toSource(JavaLexer.tokenize(source)),
                                        "lexer must round-trip");

                                ParseResult parsed = JavaParser.parse(source, LanguageLevel.LATEST, false);
                                assertEquals(source, parsed.root().text(), "the tree must round-trip");
                                List<String> unparsed = unparsedRegions(parsed.root());
                                assertTrue(unparsed.isEmpty(), () -> "unparsed regions: " + unparsed);

                                FormatResult once =
                                        formatter.format(FormatRequest.of(source).withName(path.toString()));
                                assertTrue(
                                        !once.hasErrors(),
                                        () -> "formatting failed: " + once.diagnostics());
                                ParseResult formatted =
                                        JavaParser.parse(once.text(), LanguageLevel.LATEST, false);
                                assertTrue(
                                        TokenEquivalence.firstDifference(
                                                        parsed.root().green(), formatted.root().green())
                                                == null,
                                        () -> "formatting changed the program: "
                                                + TokenEquivalence.firstDifference(
                                                        parsed.root().green(), formatted.root().green()));

                                FormatResult twice =
                                        formatter.format(FormatRequest.of(once.text()).withName(path.toString()));
                                assertEquals(once.text(), twice.text(), "formatting must be a fixed point");
                            }));
        }
    }

}
