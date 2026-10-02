package zone.rong.formatj.core;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import zone.rong.formatj.api.FormatRequest;
import zone.rong.formatj.api.FormatResult;
import zone.rong.formatj.api.LanguageLevel;
import zone.rong.formatj.core.cst.GreenNode;
import zone.rong.formatj.core.cst.SyntaxKind;
import zone.rong.formatj.core.cst.SyntaxNode;
import zone.rong.formatj.core.lexer.TokenKind;
import zone.rong.formatj.core.parser.JavaParser;
import zone.rong.formatj.core.parser.ParseResult;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ParserTest {

    private static String escape(String body) {
        return "\\" + body;
    }

    private static ParseResult parse(String source) {
        return JavaParser.parse(source, LanguageLevel.LATEST, true);
    }

    private static void assertParses(String source) {
        ParseResult result = parse(source);
        assertEquals(source, result.root().text(), "the tree must reproduce the source exactly");
        assertTrue(
            result.complete(),
            () -> "unparsed regions: " + unparsedText(result.root()) + " diagnostics: " + result.diagnostics()
        );
        assertTrue(unparsedText(result.root()).isEmpty(), () -> "unparsed: " + unparsedText(result.root()));
    }

    private static List<String> unparsedText(SyntaxNode node) {
        List<String> regions = new ArrayList<>();
        collectUnparsed(node, regions);
        return regions;
    }

    private static void collectUnparsed(SyntaxNode node, List<String> regions) {
        if (node.kind() == SyntaxKind.UNPARSED) {
            regions.add(node.text().trim());
            return;
        }
        for (SyntaxNode child : node.children()) {
            collectUnparsed(child, regions);
        }
    }

    private static GreenNode find(GreenNode node, SyntaxKind kind) {
        GreenNode match = findOrNull(node, kind);
        if (match == null) {
            throw new AssertionError("no " + kind + " in tree");
        }
        return match;
    }

    private static GreenNode findOrNull(GreenNode node, SyntaxKind kind) {
        if (node.kind() == kind) {
            return node;
        }
        for (GreenNode child : node.children()) {
            GreenNode match = findOrNull(child, kind);
            if (match != null) {
                return match;
            }
        }
        return null;
    }

    @ParameterizedTest
    @ValueSource(
        strings = {
            "\uFEFFclass A {}\n",
            "package a.b.c;\n",
            "import java.util.List;\n",
            "import static java.util.Objects.requireNonNull;\n",
            "import java.util.*;\n",
            "class A {}\n",
            "final class A extends B implements C, D {}\n",
            "sealed interface Shape permits Circle, Square {}\n",
            "non-sealed class Sub extends Shape {}\n",
            "class A { void f() { Object sealed; sealed = null; sealed.toString(); Object non; non = sealed; } }\n",
            "record Point(int x, int y) {}\n",
            "record Point(int x, int y) { Point { if (x < 0) { throw new IllegalArgumentException(); } } }\n",
            "enum Color { RED, GREEN, BLUE }\n",
            "enum Color { RED(1), GREEN(2); private final int code; Color(int code) { this.code = code; } }\n",
            "@interface Marker { String value() default \"\"; }\n",
            "class A { int x = 1, y = 2; }\n",
            "class A { static { System.out.println(1); } }\n",
            "class A { <T extends Comparable<T>> T max(T a, T b) { return a.compareTo(b) > 0 ? a : b; } }\n",
            "class A { void f() throws IOException, RuntimeException {} }\n",
            "class A { int[] a = new int[10]; int[][] b = {{1, 2}, {3}}; }\n",
            "class A { void f() { for (int i = 0; i < 10; i++) { g(i); } } }\n",
            "class A { void f() { for (String s : list) { g(s); } } }\n",
            "class A { void f() { while (true) { break; } } }\n",
            "class A { void f() { do { x++; } while (x < 3); } }\n",
            "class A { void f() { try (var in = open(); var out = create()) { copy(); } catch (IOException | RuntimeException e) { log(e); } finally { close(); } } }\n",
            "class A { void f() { synchronized (lock) { g(); } } }\n",
            "class A { void f() { assert x > 0 : \"positive\"; } }\n",
            "class A { void f() { label: for (;;) { continue label; } } }\n",
            "class A { void f() { if (a) { b(); } else if (c) { d(); } else { e(); } } }\n",
            "class A { void f() { switch (x) { case 1: g(); break; default: h(); } } }\n",
            "class A { String f(Object o) { return switch (o) { case Integer i when i > 2 -> \"big\"; case String s -> s; case null, default -> \"other\"; }; } }\n",
            "class A { int f(int t) { return switch (t) { case 1 -> { yield (t); } default -> { yield (int) (t - 1); } }; } }\n",
            "class A { void f() { switch (x) { case A -> g(); case B -> { h(); } case C -> throw new IllegalStateException(); } } }\n",
            "class A { boolean f(Object o) { return o instanceof Point(int x, int y) && x > y; } }\n",
            "class A { void f() { list.stream().map(x -> x + 1).filter(x -> x > 2).forEach(System.out::println); } }\n",
            "class A { String[] f(List<String> l) { return l.toArray(String[]::new); } }\n",
            "class A { void f() { g(byte[]::new); g(Class<?>[]::new); g(int[][]::new); } }\n",
            "class A { Class<?> c = String[][].class; }\n",
            "class A { Function<Integer, Integer> g = _ -> 1; }\n",
            "class A { Function<Integer, Integer> g = (_) -> 1; }\n",
            "class A { BiFunction<Integer, Integer, Integer> g = (_, x) -> x; }\n",
            "class A { IntPredicate p = (int _) -> true; }\n",
            "class A { Function<Integer, Integer> g = (var _) -> 1; }\n",
            "class A { IntBinaryOperator op = (int _, int y) -> y; }\n",
            "class A { Function<Integer, Integer> g = (final int _) -> 1; }\n",
            "class A { void f() { var _ = q.remove(); int _ = 1, y = 2; } }\n",
            "class A { void f() { for (int i = 0, _ = sideEffect(); i < 10; i++) { g(i); } } }\n",
            "class A { void f() { for (int _ : orderIDs) { total++; } } }\n",
            "class A { void f() { for (final char _ : s.toCharArray()) { len++; } } }\n",
            "class A { void f() { try (var _ = open(); var _ = create()) { work(); } } }\n",
            "class A { void f() { try { parse(); } catch (NumberFormatException _) { log(); } catch (IOException | RuntimeException _) { log(); } } }\n",
            "class A { String f(Object o) { return switch (o) { case Freelancer _, Intern _ -> \"other\"; case Salaried r -> r.name(); }; } }\n",
            "class A { String f(Object o) { return switch (o) { case _ -> \"none\"; case Integer _ -> \"int\"; }; } }\n",
            "class A { boolean f(Object o) { return o instanceof Point(_, int y) && y > 0; } }\n",
            "class A { boolean f(Object o) { return o instanceof ColoredPoint(Point p, Color _) || o instanceof ColoredPoint(Point p, _); } }\n",
            "class A { Runnable r = () -> {}; Function<Integer, Integer> g = (Integer x) -> x * 2; }\n",
            "class A { Object o = new Object() { public String toString() { return \"anon\"; } }; }\n",
            "class A { int x = (int) 3.5; Object y = (Runnable & Serializable) r; }\n",
            "class A { Object o = (Class<Object>) int.class; }\n",
            "class A { Class<?> c = String.class; Map<String, List<Integer>> m = new HashMap<>(); }\n",
            "class A { int x = a >> 2; int y = b >>> 3; int z = c << 1; boolean t = a > b; }\n",
            "class A { void f() { x >>= 2; y >>>= 1; z <<= 3; } }\n",
            "class A { List<List<String>> nested = new ArrayList<>(); }\n",
            "class A { void f(int... values) {} }\n",
            "class A { void f(@Deprecated final String s) {} }\n",
            "class A { String text = \"\"\"\n        hello\n        \"\"\"; }\n",
            "class A { void f() { g(); } } // trailing comment\n",
            "// leading comment\nclass A {}\n",
            "/** javadoc */\nclass A { /* inner */ void f() {} }\n",
            "class A { void f() { var x = 1; final var y = 2; } }\n",
            "class Outer { class Inner {} static class Nested {} }\n",
            "class A { void f() { class Local {} record R(int x) {} } }\n",
            "interface I { default int f() { return 1; } static int g() { return 2; } int h(); }\n",
            "class A { void f() { this.x = 1; super.g(); A.this.h(); } }\n",
            "class A { A() { this(1); } A(int x) { super(); } }\n"
        }
    )
    void parsesEveryConstructWithoutFallingBack(String source) {
        assertParses(source);
    }

    @Test
    void anUnnamedLambdaParameterStaysAKeyword() {
        ParseResult result = parse("class A { Function<Integer, Integer> g = _ -> 1; }\n");
        GreenNode lambda = find(result.root().green(), SyntaxKind.LAMBDA_EXPRESSION);
        GreenNode parameters = lambda.children().getFirst();
        assertEquals(SyntaxKind.LAMBDA_PARAMETERS, parameters.kind());
        assertTrue(
            parameters.children().getFirst() instanceof GreenNode.Leaf leaf &&
                leaf.lexeme().equals("_") &&
                leaf.token().token().kind() == TokenKind.KEYWORD
        );
    }

    @Test
    void unnamedVariablesNeedJava22() {
        String[] sources = {
            "class A { Function<Integer, Integer> g = _ -> 1; }\n",
            "class A { void f() { var _ = q.remove(); } }\n",
            "class A { void f() { for (int _ : xs) { n++; } } }\n",
            "class A { void f() { try (var _ = open()) { work(); } } }\n",
            "class A { void f() { try { parse(); } catch (Exception _) { } } }\n"
        };
        for (String source : sources) {
            assertParses(source);
            ParseResult java21 = JavaParser.parse(source, LanguageLevel.JAVA_21, false);
            assertEquals(source, java21.root().text(), source);
            assertFalse(unparsedText(java21.root()).isEmpty(), "unnamed variables are Java 22: " + source);
        }
    }

    @Test
    void unnamedVariablesAreNotFieldsOrMethodParameters() {
        assertFalse(unparsedText(parse("class A { int _ = 1; }\n").root()).isEmpty(), "fields cannot be unnamed");
        assertFalse(
            unparsedText(parse("class A { int x = 1, _ = 2; }\n").root()).isEmpty(),
            "later field declarators cannot be unnamed"
        );
        assertFalse(
            unparsedText(parse("class A { void f(int _) {} }\n").root()).isEmpty(),
            "method parameters cannot be unnamed"
        );
        assertFalse(
            unparsedText(parse("record Point(int _) {}\n").root()).isEmpty(),
            "record components cannot be unnamed"
        );
    }

    @Test
    void derivedRecordCreationParsesOnlyWithPreviewFeatures() {
        String source = "class A { Point moved(Point p) { return p with { x = 1; }; } }\n";

        assertParses(source);

        ParseResult withoutPreview = JavaParser.parse(source, LanguageLevel.LATEST, false);
        assertEquals(source, withoutPreview.root().text(), "the tree must round-trip either way");
        assertFalse(unparsedText(withoutPreview.root()).isEmpty(), "preview syntax is off by default");
    }

    @ParameterizedTest
    @ValueSource(
        strings = {
            "int broken = = 1; class kept {}\n",
            "class A { int broken = = 1; int kept = 2; }\n",
            "class A { void f() { int broken = = 1; int kept = 2; } }\n",
            "module example { requires ; uses kept.Service; }\n"
        }
    )
    void aBrokenConstructBecomesAnUnparsedRegionAndTheNextConstructStillParses(String source) {
        ParseResult result = parse(source);

        assertEquals(source, result.root().text());
        assertFalse(result.complete());
        assertFalse(result.hasErrors(), () -> result.diagnostics().toString());
        List<String> unparsed = unparsedText(result.root());
        assertEquals(1, unparsed.size(), () -> "regions: " + unparsed);
        assertFalse(unparsed.getFirst().contains("kept"), () -> "regions: " + unparsed);
        assertTrue(result.diagnostics().stream().anyMatch(d -> d.message().contains("left unformatted")));
    }

    @Test
    void theTreeIsBuiltFromRealNodesRatherThanOneUnparsedBlob() {
        ParseResult result = parse("package a;\n\nclass A {\n    void f() {}\n}\n");
        SyntaxNode unit = result.root();

        assertEquals(SyntaxKind.COMPILATION_UNIT, unit.kind());
        List<SyntaxKind> kinds = unit.children().stream().map(SyntaxNode::kind).toList();
        assertTrue(kinds.contains(SyntaxKind.PACKAGE_DECLARATION), () -> "kinds: " + kinds);
        assertTrue(kinds.contains(SyntaxKind.CLASS_DECLARATION), () -> "kinds: " + kinds);
    }

    @Test
    void triviaIsAttachedToTokensRatherThanLeftInTheChildLists() {
        ParseResult result = parse("// header\nclass A {} // tail\n");
        assertEquals("// header\nclass A {} // tail\n", result.root().text());

        GreenNode green = result.root().green();
        assertTrue(green.width() > 0);
    }

    @Test
    void enumTerminatorSitsInsideTheConstantList() {
        ParseResult result = parse("enum Color { RED, GREEN, BLUE; int x; }\n");
        GreenNode constants = find(result.root().green(), SyntaxKind.ENUM_CONSTANTS);
        GreenNode body = find(result.root().green(), SyntaxKind.CLASS_BODY);

        assertEquals(SyntaxKind.ENUM_CONSTANTS, constants.kind());
        assertTrue(constants.children().getLast() instanceof GreenNode.Leaf leaf && leaf.lexeme().equals(";"));
        assertTrue(
            body.children()
                .stream()
                .noneMatch(child -> child instanceof GreenNode.Leaf leaf && leaf.lexeme().equals(";")),
            "the body must not hold the terminator as a sibling of the constant list"
        );
        assertTrue(
            body.children().stream().anyMatch(child -> child.kind() == SyntaxKind.FIELD_DECLARATION),
            "members after the terminator stay in the body"
        );
    }

    @Test
    void enumWithoutTerminatorHasNoSemicolonChild() {
        ParseResult result = parse("enum Color { RED, GREEN, BLUE }\n");
        GreenNode constants = find(result.root().green(), SyntaxKind.ENUM_CONSTANTS);
        assertTrue(
            constants.children()
                .stream()
                .noneMatch(child -> child instanceof GreenNode.Leaf leaf && leaf.lexeme().equals(";"))
        );
    }

    @Test
    void anIfSpelledWithAUnicodeEscapeParsesAsAnIfStatement() {
        // i decodes to 'i', so "if" is spelled with an escape for its first letter.
        String template = "class A { void f(boolean b) { ESCAPEf (b) {} } }\n";
        String source = template.replace("ESCAPE", "\\" + "u0069");
        assertParses(source);
        ParseResult result = parse(source);
        assertEquals(SyntaxKind.IF_STATEMENT, find(result.root().green(), SyntaxKind.IF_STATEMENT).kind());
    }

    @Test
    void escapedPrimitiveModifierAndConstructorNamesParse() {
        assertParses("class A { " + "\\" + "u0069nt x = 1; }\n");
        assertParses("" + "\\" + "u0070ublic class A { int x = 1; }\n");
        assertParses("class " + "\\" + "u0041 { " + "\\" + "u0041() {} }\n");
        assertParses("class A { Object value = " + "\\" + "u006eew String(); }\n");
        assertParses("class A { String value = (String) " + "\\" + "u006eull; }\n");

        String escapedNew = "class A { Object value = " + "\\" + "u006eew String(); }\n";
        FormatResult formatted = FormatJ.defaultFormatter().format(FormatRequest.of(escapedNew));
        assertFalse(formatted.hasErrors(), () -> formatted.diagnostics().toString());
        assertTrue(formatted.text().contains("\\u006eew String"), formatted.text());
    }

    @Test
    void escapedSyntaxAndCommentBoundariesParseThroughTheTranslatedStream() {
        String source = escape("u0063") + "lass A " + escape("u007b") + " /" + escape("u002a") + " hidden *" +
            escape("u002f") + " String value " + escape("u003d") + " " + escape("u0022") + "ok" + escape("u0022") +
            escape("u003b") + " " + escape("u007d") + "\n";

        assertParses(source);
        FormatResult formatted = FormatJ.defaultFormatter().format(FormatRequest.of(source));
        assertFalse(formatted.hasErrors(), () -> formatted.diagnostics().toString());
        assertTrue(formatted.text().contains(escape("u002a") + " hidden *" + escape("u002f")), formatted.text());
        assertTrue(formatted.text().contains(escape("u0022") + "ok" + escape("u0022")), formatted.text());
    }

    @Test
    void escapedGreaterThanParticipatesInAdjacentShiftOperators() {
        String source = "class A { int f(int value) { return value >" + escape("u003e") + " 1; } }\n";

        assertParses(source);
        FormatResult formatted = FormatJ.defaultFormatter().format(FormatRequest.of(source));
        assertFalse(formatted.hasErrors(), () -> formatted.diagnostics().toString());
        assertTrue(formatted.text().contains(">" + escape("u003e")), formatted.text());
    }

    @Test
    void anEscapedTextBlockKeepsItsRawSpellingWhenFormatted() {
        String quotes = escape("u0022").repeat(3);
        String block = quotes + escape("u000a") + "text" + escape("u000a") + quotes;
        String source = "class A { String text = " + block + "; }\n";

        assertParses(source);
        FormatResult formatted = FormatJ.defaultFormatter().format(FormatRequest.of(source));
        assertFalse(formatted.hasErrors(), () -> formatted.diagnostics().toString());
        assertTrue(formatted.text().contains(block), formatted.text());
    }

    @Test
    void typeAnnotationsParseAtEveryArrayAndCreationPosition() {
        String prefix = "import java.lang.annotation.*; class A { " + "@Target(ElementType.TYPE_USE) @interface TA {} ";
        String[] members = {
            "void f(String @TA ... args) {}",
            "String names @TA [];",
            "void f() { String names @TA [] = null; }",
            "void f(String names @TA []) {}",
            "String f() @TA [] { return null; }",
            "String s = new @TA String();",
            "int[] numbers = new @TA int[1];",
            "static class Outer { class Inner {} } Object v = new Outer().new @TA Inner();"
        };
        for (String member : members) {
            assertParses(prefix + member + " }\n");
        }
    }

    @Test
    void aCompactFileMayUseModuleAsATypeName() {
        assertParses("class module {}\nmodule value = new module();\nvoid main() {}\n");
    }

    @Test
    void requiresKeywordsRemainValidModuleNamesWhenUnambiguous() {
        assertParses(
            "module example { requires transitive; requires static transitive.dep; requires transitive static dep; }\n"
        );
    }

}
