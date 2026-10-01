package zone.rong.formatj.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import zone.rong.formatj.api.Diagnostic;
import zone.rong.formatj.api.FormatRequest;
import zone.rong.formatj.api.FormatResult;
import zone.rong.formatj.api.LanguageLevel;
import zone.rong.formatj.api.Style;
import zone.rong.formatj.api.rules.MemberOrder;
import zone.rong.formatj.api.rules.MemberRules;
import zone.rong.formatj.api.rules.ModifierOrder;
import zone.rong.formatj.core.cst.GreenNode;
import zone.rong.formatj.core.parser.JavaParser;
import zone.rong.formatj.core.pipeline.RewriteVerification;
import zone.rong.formatj.core.rewrite.TokenEdit;
import java.io.IOException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;
import javax.tools.JavaCompiler;
import javax.tools.SimpleJavaFileObject;
import javax.tools.ToolProvider;
import org.junit.jupiter.api.Test;

class MemberOrderTest {

    private static final Style INTELLIJ = Style.builder()
            .members(members -> members.order(MemberOrder.INTELLIJ))
            .build();

    private static FormatResult format(String source, Style style) {
        return FormatJ.newFormatter().style(style).build().format(FormatRequest.of(source).withName("T.java"));
    }

    private static String sorted(String source) {
        FormatResult result = format(source, INTELLIJ);
        assertTrue(result.diagnostics().isEmpty(), () -> result.diagnostics().toString());
        return result.text();
    }

    @Test
    void everyKindOfMemberLandsInItsGroup() {
        String source = """
                class Everything {

                    class Inner {}
                    static class Nested {}
                    interface Iface {}
                    enum Kind { A }
                    void instanceMethod() {}
                    static void staticMethod() {}
                    Everything() {}
                    private int privateField = 1;
                    protected int protectedField;
                    public int publicField = 3;
                    int packageField;
                    public final int publicFinal = 2;
                    private final int privateFinal = 4;
                    private static int privateStatic;
                    public static final int PUBLIC_CONST = 5;
                    static int packageStatic = 6;
                    { privateField = 9; }
                    static { packageStatic = 7; }
                }
                """;
        String expected = """
                class Everything {
                    public static final int PUBLIC_CONST = 5;
                    static int packageStatic = 6;
                    private static int privateStatic;

                    static {
                        packageStatic = 7;
                    }

                    public final int publicFinal = 2;
                    private final int privateFinal = 4;
                    public int publicField = 3;
                    protected int protectedField;
                    int packageField;
                    private int privateField = 1;

                    {
                        privateField = 9;
                    }

                    Everything() {}

                    static void staticMethod() {}

                    void instanceMethod() {}

                    enum Kind {
                        A
                    }

                    interface Iface {}

                    static class Nested {}

                    class Inner {}
                }
                """;

        assertEquals(expected, sorted(source));
    }

    @Test
    void commentsAndAnnotationsTravelWithTheirMember() {
        String source = """
                class A {
                    // about b
                    void b() {} // after b
                    /** Docs for a. */
                    @Deprecated
                    static int a = 1; // after a
                }
                """;
        String expected = """
                class A {
                    /** Docs for a. */
                    @Deprecated
                    static int a = 1; // after a

                    // about b
                    void b() {} // after b
                }
                """;

        assertEquals(expected, sorted(source));
    }

    @Test
    void enumConstantsStayFirst() {
        String source = """
                enum E {
                    A, B;

                    void f() {}

                    static int X = 1;
                }
                """;
        String expected = """
                enum E {
                    A, B;

                    static int X = 1;

                    void f() {}
                }
                """;

        assertEquals(expected, sorted(source));
    }

    @Test
    void membersWhoseInitializationOrderWouldChangeStayPut() {
        String source = """
                class Foo {
                    private static final Object LOGGER = make();
                    public static final Object INSTANCE = make();
                }
                """;

        FormatResult once = format(source, INTELLIJ);
        assertEquals(source, once.text());
        assertEquals(1, once.diagnostics().size(), once.diagnostics().toString());
        Diagnostic warning = once.diagnostics().getFirst();
        assertEquals(Diagnostic.Severity.WARNING, warning.severity());
        assertEquals(
                "members of Foo left in place: moving INSTANCE above LOGGER would change initialization order",
                warning.message());

        FormatResult twice = format(once.text(), INTELLIJ);
        assertEquals(once.text(), twice.text());
        assertEquals(once.diagnostics(), twice.diagnostics());
    }

    @Test
    void aFieldThatReadsAnotherStaysAfterIt() {
        String source = """
                class Foo {
                    private static final int A = 1;
                    public static final int B = A + 1;
                }
                """;

        assertEquals(1, format(source, INTELLIJ).diagnostics().size());
    }

    @Test
    void constantInitializersMayChangeOrder() {
        String source = """
                class Foo {
                    private static final int A = 1 + (int) 2L;
                    public static final String B = "b" + Integer.MAX_VALUE;
                }
                """;
        String expected = """
                class Foo {
                    public static final String B = "b" + Integer.MAX_VALUE;
                    private static final int A = 1 + (int) 2L;
                }
                """;

        assertEquals(expected, sorted(source));
    }

    @Test
    void sortingSettlesWithTheRulesThatTouchMembersAndTheirNeighbours() {
        String source = """
                import java.util.List;
                import java.util.Map;

                interface Shape {
                    public static final int SIDES = 4;
                    public abstract int area();
                    static int unit() { return 1; };
                    static class Helper { static final int N = 1; };
                    int HALF = 2;
                }
                """;
        Style style = Style.builder()
                .members(members -> members.order(MemberOrder.INTELLIJ))
                .modifiers(modifiers -> modifiers.order(ModifierOrder.CANONICAL).removeRedundant(true))
                .imports(imports -> imports.removeUnused(true))
                .semicolons(semicolons -> semicolons.removeRedundant(true))
                .build();

        FormatResult once = format(source, style);
        assertTrue(once.diagnostics().isEmpty(), () -> once.diagnostics().toString());
        assertFalse(once.text().contains("import"), once.text());
        assertTrue(once.text().indexOf("int HALF") < once.text().indexOf("int area()"), once.text());
        assertEquals(once.text(), format(once.text(), style).text());
    }

    @Test
    void verificationAcceptsOnlyTheDeclaredStableOrder() {
        GreenNode original = parse("class A { void b() {} int a; }");
        GreenNode swapped = parse("class A { int a; void b() {} }");
        TokenEdit.Span method = new TokenEdit.Span(3, 9);
        TokenEdit.Span field = new TokenEdit.Span(9, 12);

        TokenEdit sorted = TokenEdit.reorder(MemberRules.ORDER, "test", List.of(field, method));
        assertEquals(null, RewriteVerification.verifyOutput(original, swapped, List.of(sorted)));
        assertTrue(RewriteVerification.verifyOutput(original, original, List.of(sorted)) != null);

        TokenEdit unsorted = TokenEdit.reorder(MemberRules.ORDER, "test", List.of(method, field));
        assertTrue(RewriteVerification.verifyOutput(original, original, List.of(unsorted)).contains("stable order"));
    }

    private static GreenNode parse(String source) {
        return JavaParser.parse(source, LanguageLevel.LATEST, false).root().green();
    }

    @Test
    void sortedSourceStillCompiles() throws IOException {
        String source = """
                class Machine {
                    class Part { int weight() { return SCALE * 2; } }
                    int total() { return new Part().weight() + size; }
                    Machine(int size) { this.size = size; }
                    private int size;
                    static final int SCALE = 3;
                    enum Mode { ON, OFF }
                    interface Probe { int read(Machine machine); }
                    static int twice(int value) { return value * 2; }
                }
                """;
        String formatted = sorted(source);

        assertTrue(compiles(source), "the unsorted source must compile");
        assertTrue(!formatted.equals(source) && compiles(formatted), formatted);
    }

    private static boolean compiles(String source) throws IOException {
        JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        Path out = Files.createTempDirectory("member-order");
        try {
            return compiler.getTask(
                    null,
                    null,
                    null,
                    List.of("-d", out.toString(), "-proc:none"),
                    null,
                    List.of(new SimpleJavaFileObject(
                            URI.create("string:///Machine.java"),
                            SimpleJavaFileObject.Kind.SOURCE) {

                        @Override
                        public CharSequence getCharContent(boolean ignoreEncodingErrors) {
                            return source;
                        }

                    }))
                    .call();
        } finally {
            try (var files = Files.walk(out)) {
                files.sorted(Comparator.reverseOrder()).forEach(path -> path.toFile().delete());
            }
        }
    }

}
