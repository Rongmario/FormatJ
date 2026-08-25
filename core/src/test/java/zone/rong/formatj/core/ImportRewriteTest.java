package zone.rong.formatj.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import zone.rong.formatj.api.FormatRequest;
import zone.rong.formatj.api.Formatter;
import zone.rong.formatj.api.Style;
import zone.rong.formatj.api.StyleBuilder;
import zone.rong.formatj.api.rules.ImportRules;
import zone.rong.formatj.api.rules.SortOrder;
import zone.rong.formatj.api.rules.StaticImportPlacement;
import java.util.ArrayList;
import java.util.List;
import java.util.function.UnaryOperator;
import org.junit.jupiter.api.Test;

/** What the import rules reorder, what they delete, and what they refuse to touch. */
class ImportRewriteTest {

    private static String format(String source, UnaryOperator<StyleBuilder> rules) {
        Style style = rules.apply(Style.builder().set(ImportRules.ORDER, SortOrder.ASCENDING)).build();
        Formatter formatter = FormatJ.newFormatter().style(style).build();
        return formatter.format(FormatRequest.of(source).withName("T.java")).text();
    }

    /** The import declarations of a formatted file, in order, blank lines shown as an empty string. */
    private static List<String> importsOf(String formatted) {
        List<String> lines =
                new ArrayList<>(
                        formatted.lines()
                                .dropWhile(line -> !line.startsWith("import"))
                                .takeWhile(line -> line.isBlank() || line.startsWith("import"))
                                .map(String::strip)
                                .toList());
        while (!lines.isEmpty() && lines.getLast().isBlank()) {
            lines.removeLast();
        }
        return List.copyOf(lines);
    }

    private static final String MIXED = """
            package demo;

            import zone.rong.Thing;
            import java.util.Map;
            import static org.junit.Assertions.assertTrue;
            import javax.annotation.Nullable;
            import java.util.List;

            class T {

                List<String> run(Map<String, String> in, @Nullable Thing t) {
                    assertTrue(in != null);
                    return List.of();
                }

            }
            """;

    @Test
    void preserveLeavesTheRunExactlyAsWritten() {
        Formatter formatter = FormatJ.newFormatter().style(Style.defaults()).build();
        String formatted = formatter.format(FormatRequest.of(MIXED).withName("T.java")).text();
        assertEquals(
                List.of(
                        "import zone.rong.Thing;",
                        "import java.util.Map;",
                        "import static org.junit.Assertions.assertTrue;",
                        "import javax.annotation.Nullable;",
                        "import java.util.List;"),
                importsOf(formatted));
    }

    @Test
    void ascendingSortsWithinGroupsAndSeparatesThem() {
        assertEquals(
                List.of(
                        "import java.util.List;",
                        "import java.util.Map;",
                        "",
                        "import javax.annotation.Nullable;",
                        "",
                        "import zone.rong.Thing;",
                        "",
                        "import static org.junit.Assertions.assertTrue;"),
                importsOf(format(MIXED, style -> style)));
    }

    @Test
    void descendingReversesWithinEachGroupButNotTheGroups() {
        assertEquals(
                List.of(
                        "import java.util.Map;",
                        "import java.util.List;",
                        "",
                        "import javax.annotation.Nullable;",
                        "",
                        "import zone.rong.Thing;",
                        "",
                        "import static org.junit.Assertions.assertTrue;"),
                importsOf(format(MIXED, style -> style.set(ImportRules.ORDER, SortOrder.DESCENDING))));
    }

    @Test
    void staticImportsCanLeadInstead() {
        assertEquals(
                "import static org.junit.Assertions.assertTrue;",
                importsOf(
                        format(
                                MIXED,
                                style -> style.set(
                                        ImportRules.STATIC_PLACEMENT,
                                        StaticImportPlacement.FIRST))).getFirst());
    }

    @Test
    void staticImportsAreOneGroupRatherThanBeingSplitByPackage() {
        String source = """
                package demo;

                import static org.junit.Assertions.assertTrue;
                import static java.util.Objects.requireNonNull;

                class T {

                    void run(Object o) {
                        assertTrue(requireNonNull(o) != null);
                    }

                }
                """;
        assertEquals(
                List.of(
                        "import static java.util.Objects.requireNonNull;",
                        "import static org.junit.Assertions.assertTrue;"),
                importsOf(format(source, style -> style)));
    }

    @Test
    void groupsAreConfigurable() {
        assertEquals(
                List.of(
                        "import zone.rong.Thing;",
                        "",
                        "import java.util.List;",
                        "import java.util.Map;",
                        "import javax.annotation.Nullable;",
                        "",
                        "import static org.junit.Assertions.assertTrue;"),
                importsOf(format(MIXED, style -> style.set(ImportRules.GROUPS, List.of("zone.rong", "*")))));
    }

    @Test
    void theBlankLineBetweenGroupsCanBeTurnedOff() {
        assertEquals(
                List.of(
                        "import java.util.List;",
                        "import java.util.Map;",
                        "import javax.annotation.Nullable;",
                        "import zone.rong.Thing;",
                        "import static org.junit.Assertions.assertTrue;"),
                importsOf(format(MIXED, style -> style.set(ImportRules.BLANK_LINE_BETWEEN_GROUPS, false))));
    }

    @Test
    void theAuthorsOwnBlankLinesInsideTheRunDoNotSurviveSorting() {
        String source = """
                package demo;

                import java.util.Map;


                import java.util.List;

                class T {

                    List<String> run(Map<String, String> in) {
                        return List.of();
                    }

                }
                """;
        assertEquals(List.of("import java.util.List;", "import java.util.Map;"), importsOf(format(source, s -> s)));
    }

    // -------------------------------------------------------------- removal

    private static final String UNUSED = """
            package demo;

            import java.util.List;
            import java.util.Map;
            import java.util.concurrent.*;
            import java.util.Listener;

            /**
             * Hands back a {@link Map}.
             */
            class T {

                List<String> run() {
                    return List.of();
                }

            }
            """;

    @Test
    void nothingIsRemovedUnlessAsked() {
        assertEquals(4, importsOf(format(UNUSED, style -> style)).stream().filter(l -> !l.isBlank()).count());
    }

    @Test
    void anUnreferencedImportGoes() {
        assertTrue(!format(UNUSED, style -> style.set(ImportRules.REMOVE_UNUSED, true)).contains("java.util.Listener"));
    }

    @Test
    void anImportMentionedOnlyInJavadocStays() {
        assertTrue(
                format(UNUSED, style -> style.set(ImportRules.REMOVE_UNUSED, true)).contains("import java.util.Map;"));
    }

    @Test
    void anOnDemandImportStaysBecauseItsUseCannotBeSeen() {
        assertTrue(
                format(UNUSED, style -> style.set(ImportRules.REMOVE_UNUSED, true)).contains(
                        "import java.util.concurrent.*;"));
    }

    @Test
    void aNameIsMatchedWholeRatherThanAsAPrefix() {
        // Listener must not be kept alive by the List in the body, nor List killed by Listener going.
        String formatted = format(UNUSED, style -> style.set(ImportRules.REMOVE_UNUSED, true));
        assertTrue(formatted.contains("import java.util.List;"));
        assertTrue(!formatted.contains("Listener"));
    }

    @Test
    void aFileWithNoImportsIsLeftAlone() {
        String source = """
                package demo;

                class T {

                    void run() { }

                }
                """;
        assertEquals(source, format(source, style -> style.set(ImportRules.REMOVE_UNUSED, true)));
    }

    @Test
    void sortingSettlesAfterOnePass() {
        String once = format(MIXED, style -> style.set(ImportRules.REMOVE_UNUSED, true));
        assertEquals(once, format(once, style -> style.set(ImportRules.REMOVE_UNUSED, true)));
    }

}
