package zone.rong.formatj.core;

import java.io.IOException;
import java.io.StringWriter;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.function.Consumer;

import javax.tools.DiagnosticCollector;
import javax.tools.DocumentationTool;
import javax.tools.JavaFileObject;
import javax.tools.SimpleJavaFileObject;
import javax.tools.StandardJavaFileManager;
import javax.tools.ToolProvider;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import zone.rong.formatj.api.FormatRequest;
import zone.rong.formatj.api.FormatResult;
import zone.rong.formatj.api.LanguageLevel;
import zone.rong.formatj.api.Style;
import zone.rong.formatj.api.StyleBuilder;
import zone.rong.formatj.api.rules.CommentReflow;
import zone.rong.formatj.api.rules.CommentRules;
import zone.rong.formatj.api.rules.JavadocRules;
import zone.rong.formatj.api.rules.WrappingRules;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * The rules that were waiting on the prose check: {@code comments.reflow} and the {@code javadoc.*}
 * family.
 *
 * <p>Each is checked for what it does and, where the answer is "nothing", for the fact that it leaves
 * the comment byte for byte as the author wrote it. A rule that quietly re-spaced every comment it
 * walked past on its way to the one it wanted would pass a test that only looked at its own output.
 */
class CommentProseRulesTest {

    private static String format(Consumer<StyleBuilder> rules, String body) {
        StyleBuilder builder = Style.builder();
        rules.accept(builder);
        FormatResult result = FormatJ.newFormatter()
            .style(builder.build())
            .languageLevel(LanguageLevel.LATEST)
            .build()
            .format(FormatRequest.of("class T {\n\n" + body + "\n}\n").withName("T.java"));
        assertFalse(result.hasErrors(), () -> result.diagnostics().toString());
        return result.text();
    }

    private static String reformat(Consumer<StyleBuilder> rules, String source) {
        StyleBuilder builder = Style.builder();
        rules.accept(builder);
        FormatResult result = FormatJ.newFormatter()
            .style(builder.build())
            .languageLevel(LanguageLevel.LATEST)
            .build()
            .format(FormatRequest.of(source).withName("T.java"));
        assertFalse(result.hasErrors(), () -> result.diagnostics().toString());
        return result.text();
    }

    private static void assertMarkdownPreserved(Consumer<StyleBuilder> rules, String comment) {
        String once = format(rules, comment + "    void f() { }\n");
        assertTrue(once.contains(comment), once);
        assertEquals(once, reformat(rules, once));
    }

    // --------------------------------------------------------- comments.reflow

    @Test
    void wrappingKeepsTheWordsAfterASpacedParamName() {
        String body = "    /**\n     * Runs.\n     *\n     * @param   s   the detail message.\n     */\n    void f(String s) { }\n";
        String formatted = format(rules -> rules.javadoc(javadoc -> javadoc.wrap(true)), body);
        assertTrue(formatted.contains("@param   s the detail message."), formatted);
    }

    @Test
    void paragraphTagsStayOutOfCodeSamples() {
        String body = "    /**\n     * Runs.\n     *\n     * <pre>\n     * a\n     *\n     * b\n     * </pre>\n     */\n    void f() { }\n";
        String formatted = format(rules -> rules.javadoc(javadoc -> javadoc.wrap(true).addParagraphTags(true)), body);
        assertTrue(formatted.contains("     * Runs.\n     *\n     * <pre>\n     * a\n     *\n     * b\n"), formatted);
    }

    @Test
    void reflowKeepsTheParagraphsOfABlockComment() {
        String source = "/* One.\n *\n * Two  three. */\nclass T { }\n";
        String formatted = reformat(
            rules -> rules.comments(comments -> comments.reflow(CommentReflow.REFLOW_TO_LINE_LENGTH)),
            source
        );
        assertTrue(formatted.startsWith("/*\n * One.\n *\n * Two three.\n */\n"), formatted);
    }

    @Test
    void reflowIsOffByDefault() {
        String body = "    // one two three four five six seven eight nine ten eleven twelve\n    void f() { }\n";
        assertTrue(
            format(style -> { }, body).contains("// one two three four five six seven eight nine ten eleven twelve")
        );
    }

    @Test
    void reflowRefillsARunOfLineCommentsToTheMargin() {
        String formatted = format(
            style -> style.set(CommentRules.REFLOW, CommentReflow.REFLOW_TO_LINE_LENGTH)
                .set(WrappingRules.MAX_LINE_LENGTH, 40),
            "    // one two three four five six seven eight nine\n    // ten\n    void f() { }\n"
        );
        assertTrue(formatted.contains("    // one two three four five six seven\n    // eight nine ten\n"), formatted);
    }

    @Test
    void reflowLeavesATrailingCommentOnItsOwnLine() {
        String formatted = format(
            style -> style.set(CommentRules.REFLOW, CommentReflow.REFLOW_TO_LINE_LENGTH)
                .set(WrappingRules.MAX_LINE_LENGTH, 40),
            "    void f() { } // one two three four five six seven eight\n"
        );
        assertTrue(formatted.contains("// one two three four five six seven eight"), formatted);
    }

    @Test
    void reflowLeavesCommentedOutCodeRagged() {
        String formatted = format(
            style -> style.set(CommentRules.REFLOW, CommentReflow.REFLOW_TO_LINE_LENGTH)
                .set(WrappingRules.MAX_LINE_LENGTH, 40),
            "    //g();\n    //h();\n    void f() { }\n"
        );
        assertTrue(formatted.contains("    //g();\n    //h();\n"), formatted);
    }

    @Test
    void reflowLeavesAFencedCodeBlockInADocCommentRunAlone() {
        String formatted = format(
            style -> style.set(CommentRules.REFLOW, CommentReflow.REFLOW_TO_LINE_LENGTH)
                .set(WrappingRules.MAX_LINE_LENGTH, 40),
            "    /// ```\n    ///   int x =   1;\n    /// ```\n    void f() { }\n"
        );
        assertTrue(formatted.contains("///   int x =   1;\n"), formatted);
    }

    @Test
    void markdownProbeCasesKeepTheirLineStructure() {
        List<String> comments = List.of(
            "    /// First paragraph.\n    ///\n    /// Second paragraph.\n",
            "    /// - first\n    ///   continued\n    /// - second\n",
            "    /// | Name | Value |\n    /// | --- | ---: |\n    /// | one | 1 |\n",
            "    /// @param value the value\n    /// @return the result\n",
            "    /// ```java\n    /// int x = 1;\n    /// ```\n",
            "    /// ~~~java\n    /// int x = 1;\n    /// ~~~\n"
        );
        for (String comment : comments) {
            assertMarkdownPreserved(style -> { }, comment);
            assertMarkdownPreserved(
                style -> style.set(CommentRules.REFLOW, CommentReflow.REFLOW_TO_LINE_LENGTH),
                comment
            );
        }
    }

    @Test
    void markdownHardBreakSurvivesWhenReflowIsDisabled() {
        assertMarkdownPreserved(style -> { }, "    /// first line  \n    /// second line\n");
    }

    @Test
    void markdownNestedListIndentationSurvives() {
        String comment = "    /// - outer\n    ///   - inner\n    ///     continuation\n";
        assertMarkdownPreserved(style -> { }, comment);
        assertMarkdownPreserved(style -> style.set(CommentRules.REFLOW, CommentReflow.REFLOW_TO_LINE_LENGTH), comment);
    }

    @Test
    void ordinaryAndMarkdownLineCommentsStayInSeparateRuns() {
        String comment = "    /// first run\n    // intervening comment\n    /// second run\n";
        assertMarkdownPreserved(style -> style.set(CommentRules.REFLOW, CommentReflow.REFLOW_TO_LINE_LENGTH), comment);
    }

    @Test
    void physicalBlankLineSeparatesMarkdownRuns() {
        String comment = "    /// ignored documentation\n\n    /// declaration documentation\n";
        assertMarkdownPreserved(style -> { }, comment);
        assertMarkdownPreserved(style -> style.set(CommentRules.REFLOW, CommentReflow.REFLOW_TO_LINE_LENGTH), comment);
    }

    @Test
    void javadocRulesFormatMarkdownWithoutFlatteningItsBlocks() {
        String formatted = format(
            style -> style.set(JavadocRules.WRAP, true)
                .set(JavadocRules.TAG_ORDER, zone.rong.formatj.api.rules.JavadocTagOrder.CANONICAL)
                .set(JavadocRules.ALIGN_TAG_DESCRIPTIONS, true)
                .set(JavadocRules.TAG_CONTINUATION_INDENT, 4)
                .set(WrappingRules.MAX_LINE_LENGTH, 48),
            "    /// This ordinary paragraph contains enough words to wrap safely at the configured margin.\n" +
                "    ///\n" + "    /// `code span with  spaces` stays here.\n" + "    ///\n" + "    /// # Heading\n" +
                "    /// Heading text.\n" + "    ///\n" + "    /// - first item\n" + "    ///   - nested item\n" +
                "    ///\n" + "    /// | Name | Value |\n" + "    /// | --- | ---: |\n" + "    /// | one | 1 |\n" +
                "    ///\n" + "    /// > quoted text\n" + "    ///\n" +
                "    /// Read [the guide][guide] before use.\n" + "    ///\n" +
                "    /// [guide]: https://example.com/guide\n" + "    ///\n" +
                "    /// @return a result description with enough words to wrap onto another line\n" +
                "    /// @param a short parameter description\n" +
                "    /// @param longer another parameter description\n" + "    int f(int a, int longer) { return a; }\n"
        );

        assertTrue(formatted.contains("/// This ordinary paragraph contains enough\n"), formatted);
        assertTrue(formatted.contains("/// `code span with  spaces` stays here.\n"), formatted);
        assertTrue(formatted.contains("/// # Heading\n    /// Heading text.\n"), formatted);
        assertTrue(formatted.contains("/// - first item\n    ///   - nested item\n"), formatted);
        assertTrue(formatted.contains("/// | Name | Value |\n    /// | --- | ---: |\n"), formatted);
        assertTrue(formatted.contains("/// > quoted text\n"), formatted);
        assertTrue(formatted.contains("/// Read [the guide][guide] before use.\n"), formatted);
        assertTrue(formatted.contains("/// [guide]: https://example.com/guide\n"), formatted);
        assertTrue(formatted.indexOf("@param a") < formatted.indexOf("@return"), formatted);
        assertTrue(formatted.contains("/// @param a      short parameter\n"), formatted);
        assertTrue(formatted.contains("\n    ///     "), formatted);
        assertEquals(
            formatted,
            reformat(
                style -> style.set(JavadocRules.WRAP, true)
                    .set(JavadocRules.TAG_ORDER, zone.rong.formatj.api.rules.JavadocTagOrder.CANONICAL)
                    .set(JavadocRules.ALIGN_TAG_DESCRIPTIONS, true)
                    .set(JavadocRules.TAG_CONTINUATION_INDENT, 4)
                    .set(WrappingRules.MAX_LINE_LENGTH, 48),
                formatted
            )
        );
    }

    @Test
    void ambiguousMarkdownStaysUnchanged() {
        String comment = "    /// Text with an unmatched ` delimiter.\n    /// @return literal text\n";
        assertMarkdownPreserved(
            style -> style.set(JavadocRules.WRAP, true)
                .set(JavadocRules.TAG_ORDER, zone.rong.formatj.api.rules.JavadocTagOrder.CANONICAL),
            comment
        );
    }

    @Test
    void emptyMarkdownRunSurvivesWrapping() {
        assertMarkdownPreserved(style -> style.set(JavadocRules.WRAP, true), "    ///\n");
    }

    @Test
    void markdownTagReaderIgnoresTagTextInCodeBlocks() {
        String formatted = format(
            style -> style.set(JavadocRules.TAG_ORDER, zone.rong.formatj.api.rules.JavadocTagOrder.CANONICAL),
            "    /// Description.\n" + "    ///\n" + "    /// ```text\n" + "    /// @return fenced sample\n" +
                "    /// ```\n" + "    ///\n" + "    ///     @param indented sample\n" + "    ///\n" +
                "    /// @return real result\n" + "    /// @param value real parameter\n" +
                "    int f(int value) { return value; }\n"
        );

        assertTrue(formatted.contains("/// @return fenced sample\n"), formatted);
        assertTrue(formatted.contains("///     @param indented sample\n"), formatted);
        assertTrue(formatted.indexOf("@param value") < formatted.lastIndexOf("@return real result"), formatted);
    }

    @Test
    void formattedMarkdownKeepsRepresentativeJavadocOutput(@TempDir Path output) throws IOException {
        assumeTrue(Runtime.version().feature() >= 25);
        String source = "public class T {\n" +
            "    /// This ordinary paragraph contains enough words to wrap at the configured margin.\n" + "    ///\n" +
            "    /// Read [the guide][guide] and use `value`.\n" + "    ///\n" + "    /// ```java\n" +
            "    /// int x = value;\n" + "    /// ```\n" + "    ///\n" + "    /// - first item\n" +
            "    ///   - nested item\n" + "    ///\n" + "    /// | Name | Value |\n" + "    /// | --- | ---: |\n" +
            "    /// | one | 1 |\n" + "    ///\n" + "    /// [guide]: https://example.com/guide\n" + "    ///\n" +
            "    /// @return the supplied value\n" + "    /// @param value the value to return\n" +
            "    public int f(int value) { return value; }\n" + "}\n";
        String formatted = reformat(
            style -> style.set(JavadocRules.WRAP, true)
                .set(JavadocRules.TAG_ORDER, zone.rong.formatj.api.rules.JavadocTagOrder.CANONICAL)
                .set(JavadocRules.ALIGN_TAG_DESCRIPTIONS, true)
                .set(WrappingRules.MAX_LINE_LENGTH, 56),
            source
        );

        String before = generateJavadoc(source, output.resolve("before"));
        String after = generateJavadoc(formatted, output.resolve("after"));
        assertEquals(codeBlock(before), codeBlock(after));
        assertEquals(normaliseHtmlWhitespace(methodDetails(before)), normaliseHtmlWhitespace(methodDetails(after)));
        assertTrue(after.contains("href=\"https://example.com/guide\""), after);
        assertTrue(after.contains("int x = value;"), after);
        assertTrue(after.contains("<ul>"), after);
        assertTrue(after.contains("<table"), after);
        assertTrue(after.contains("Parameters:"), after);
        assertTrue(after.contains("Returns:"), after);
    }

    private static String generateJavadoc(String source, Path output) throws IOException {
        DocumentationTool tool = ToolProvider.getSystemDocumentationTool();
        assumeTrue(tool != null);
        DiagnosticCollector<JavaFileObject> diagnostics = new DiagnosticCollector<>();
        StringWriter log = new StringWriter();
        JavaFileObject unit = new SimpleJavaFileObject(URI.create("string:///T.java"), JavaFileObject.Kind.SOURCE) {

            @Override
            public CharSequence getCharContent(boolean ignoreEncodingErrors) {
                return source;
            }

        };
        try (StandardJavaFileManager files = tool.getStandardFileManager(diagnostics, null, null)) {
            boolean documented = tool.getTask(
                log,
                files,
                diagnostics,
                null,
                List.of("-quiet", "-private", "-notimestamp", "-d", output.toString()),
                List.of(unit)
            )
                .call();
            assertTrue(documented, () -> log + "\n" + diagnostics.getDiagnostics());
        }
        return Files.readString(output.resolve("T.html"));
    }

    private static String methodDetails(String html) {
        int id = html.indexOf("id=\"f(int)\"");
        assertTrue(id >= 0, html);
        int start = html.lastIndexOf("<section", id);
        int end = html.indexOf("</section>", id);
        assertTrue(start >= 0 && end > start, html);
        return html.substring(start, end + "</section>".length());
    }

    private static String codeBlock(String html) {
        int start = html.indexOf("<pre>");
        int end = html.indexOf("</pre>", start);
        assertTrue(start >= 0 && end > start, html);
        return html.substring(start, end + "</pre>".length());
    }

    private static String normaliseHtmlWhitespace(String html) {
        return html.replaceAll("\\s+", " ").trim();
    }

    // ------------------------------------------------------------- javadoc.*

    @Test
    void aCommentNoRuleHasAnythingToSayAboutComesOutUntouched() {
        String comment = "    /**\n     * Text.\n     *\n     * @param a x\n     */\n";
        String formatted = format(style -> { }, comment + "    void f(int a) { }\n");
        assertTrue(formatted.contains(comment), formatted);
    }

    @Test
    void blankLineBeforeTagsWritesTheBlankLineItAsksFor() {
        String formatted = format(
            style -> style.set(JavadocRules.BLANK_LINE_BEFORE_TAGS, true),
            "    /**\n     * Text.\n     * @param a x\n     */\n    void f(int a) { }\n"
        );
        assertTrue(formatted.contains("     * Text.\n     *\n     * @param a x\n"), formatted);
    }

    @Test
    void blankLineBeforeTagsTakesItAwayAgainWhenTurnedOff() {
        String formatted = format(
            style -> style.set(JavadocRules.BLANK_LINE_BEFORE_TAGS, false),
            "    /**\n     * Text.\n     *\n     * @param a x\n     */\n    void f(int a) { }\n"
        );
        assertTrue(formatted.contains("     * Text.\n     * @param a x\n"), formatted);
    }

    @Test
    void keepSingleLineLeavesAOneLineCommentOnOneLine() {
        String formatted = format(
            style -> style.set(JavadocRules.WRAP, true).set(JavadocRules.KEEP_SINGLE_LINE, true),
            "    /** Text. */\n    void f() { }\n"
        );
        assertTrue(formatted.contains("    /** Text. */\n"), formatted);
    }

    @Test
    void keepSingleLineTurnedOffSpreadsItOut() {
        String formatted = format(
            style -> style.set(JavadocRules.WRAP, true).set(JavadocRules.KEEP_SINGLE_LINE, false),
            "    /** Text. */\n    void f() { }\n"
        );
        assertTrue(formatted.contains("    /**\n     * Text.\n     */\n"), formatted);
    }

    @Test
    void tagContinuationIndentPushesTheSecondLineOfATagOver() {
        String formatted = format(
            style -> style.set(JavadocRules.WRAP, true)
                .set(JavadocRules.TAG_CONTINUATION_INDENT, 4)
                .set(WrappingRules.MAX_LINE_LENGTH, 40),
            "    /**\n     * @param a one two three four five six seven\n     */\n    void f(int a) { }\n"
        );
        assertTrue(formatted.contains("\n     *     "), formatted);
    }

    @Test
    void wrappingNeverTouchesASample() {
        String formatted = format(
            style -> style.set(JavadocRules.WRAP, true).set(WrappingRules.MAX_LINE_LENGTH, 40),
            "    /**\n     * <pre>\n     *   int x =   1;\n     * </pre>\n     */\n    void f() { }\n"
        );
        assertTrue(formatted.contains("     *   int x =   1;\n"), formatted);
    }

    @Test
    void wrappingNeverTouchesAFencedCodeBlock() {
        String formatted = format(
            style -> style.set(JavadocRules.WRAP, true).set(WrappingRules.MAX_LINE_LENGTH, 40),
            "    /**\n     * ```\n     *   int x =   1;\n     * ```\n     */\n    void f() { }\n"
        );
        assertTrue(formatted.contains("     *   int x =   1;\n"), formatted);
    }

    @Test
    void tagOrderPutsTheTagsInTheConventionalOrder() {
        String formatted = format(
            style -> style.set(JavadocRules.TAG_ORDER, zone.rong.formatj.api.rules.JavadocTagOrder.CANONICAL),
            "    /**\n     * @return r\n     * @param a x\n     */\n    int f(int a) { return a; }\n"
        );
        assertTrue(formatted.indexOf("@param") < formatted.indexOf("@return"), formatted);
    }

    @Test
    void formattingStaysAFixedPointWithEveryProseRuleOn() {
        String body = "    /**\n     * One two three four five six seven eight nine ten eleven twelve.\n" +
            "     *\n     * @return r\n     * @param a x\n     */\n" +
            "    // a comment that is quite long and will need refilling at this margin\n" +
            "    int f(int a) { return a; }\n";
        Consumer<StyleBuilder> rules = style -> style.set(CommentRules.REFLOW, CommentReflow.REFLOW_TO_LINE_LENGTH)
            .set(JavadocRules.WRAP, true)
            .set(JavadocRules.TAG_ORDER, zone.rong.formatj.api.rules.JavadocTagOrder.CANONICAL)
            .set(JavadocRules.ALIGN_TAG_DESCRIPTIONS, true)
            .set(JavadocRules.ADD_PARAGRAPH_TAGS, true)
            .set(WrappingRules.MAX_LINE_LENGTH, 50);
        String once = format(rules, body);
        StyleBuilder builder = Style.builder();
        rules.accept(builder);
        String twice = FormatJ.newFormatter()
            .style(builder.build())
            .languageLevel(LanguageLevel.LATEST)
            .build()
            .format(FormatRequest.of(once))
            .text();
        assertEquals(once, twice);
    }

}
