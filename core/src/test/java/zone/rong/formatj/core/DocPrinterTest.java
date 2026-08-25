package zone.rong.formatj.core;

import static org.junit.jupiter.api.Assertions.assertEquals;

import zone.rong.formatj.core.ir.Doc;
import zone.rong.formatj.core.layout.DocPrinter;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.Test;

class DocPrinterTest {

    private static Doc call(String receiver, String... arguments) {
        List<Doc> parts = Arrays.stream(arguments).map(Doc::text).toList();
        return Doc.group(
                Doc.concat(
                        Doc.text(receiver + "("),
                        Doc.indent(
                                4,
                                Doc.concat(Doc.softLine(), Doc.join(Doc.concat(Doc.text(","), Doc.line()), parts))),
                        Doc.softLine(),
                        Doc.text(")")));
    }

    @Test
    void keepsGroupFlatWhenItFits() {
        assertEquals("call(a, b)", DocPrinter.ofSpaces(40).print(call("call", "a", "b")));
    }

    @Test
    void breaksEveryLineOfAGroupThatDoesNotFit() {
        String printed = DocPrinter.ofSpaces(12).print(call("call", "alpha", "beta", "gamma"));
        assertEquals(
                """
                call(
                    alpha,
                    beta,
                    gamma
                )""",
                printed);
    }

    @Test
    void hardLineForcesEnclosingGroupsToBreak() {
        Doc document =
                Doc.group(
                        Doc.concat(
                                Doc.text("{"),
                                Doc.indent(4, Doc.concat(Doc.hardLine(), Doc.text("body"))),
                                Doc.hardLine(),
                                Doc.text("}")));
        assertEquals("{\n    body\n}", DocPrinter.ofSpaces(200).print(document));
    }

    @Test
    void indentUsesTheConfiguredUnit() {
        Doc document =
                Doc.group(
                        Doc.concat(
                                Doc.text("{"),
                                Doc.indent(4, Doc.concat(Doc.hardLine(), Doc.text("x"))),
                                Doc.hardLine(),
                                Doc.text("}")));
        assertEquals("{\n\tx\n}", new DocPrinter(80, true, 4, "\n").print(document));
    }

    @Test
    void lineSuffixIsHeldUntilTheNextBreak() {
        Doc document =
                Doc.concat(
                        Doc.text("int x = 1;"),
                        Doc.lineSuffix(Doc.text(" // set once")),
                        Doc.hardLine(),
                        Doc.text("int y = 2;"));
        assertEquals("int x = 1; // set once\nint y = 2;", DocPrinter.ofSpaces(80).print(document));
    }

    @Test
    void ifBreakChoosesByTheEnclosingGroupsMode() {
        Doc document =
                Doc.group(
                        Doc.concat(
                                Doc.text("["),
                                Doc.text("1"),
                                Doc.ifBreak(Doc.text(","), Doc.EMPTY),
                                Doc.softLine(),
                                Doc.text("]")));
        assertEquals("[1]", DocPrinter.ofSpaces(80).print(document));
    }

    @Test
    void trailingSpacesAreNeverLeftOnALine() {
        Doc document = Doc.concat(Doc.text("a "), Doc.hardLine(), Doc.text("b"));
        assertEquals("a\nb", DocPrinter.ofSpaces(80).print(document));
    }

}
