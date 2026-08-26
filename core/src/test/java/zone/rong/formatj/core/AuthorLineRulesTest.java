package zone.rong.formatj.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import zone.rong.formatj.api.FormatRequest;
import zone.rong.formatj.api.FormatResult;
import zone.rong.formatj.api.Formatter;
import zone.rong.formatj.api.Style;
import zone.rong.formatj.api.StyleBuilder;
import zone.rong.formatj.api.rules.WrapPolicy;
import java.util.function.Consumer;
import org.junit.jupiter.api.Test;

/**
 * The rules that read the author's own line structure.
 *
 * <p>They share one question — did the author write this on one line? — so they are tested together:
 * what matters as much as each rule's own effect is that they agree about the answer, and that a
 * construct which turns out not to fit on one line comes back looking exactly like one the author had
 * already spread out.
 */
class AuthorLineRulesTest {

    @Test
    void aBlockTheAuthorWroteOnOneLineStaysOnOneLine() {
        String source = "class A {\n\n    void f() {\n        if (x) { g(); }\n    }\n\n}\n";

        assertTrue(format(source, style -> { }).contains("if (x) { g(); }"));
        assertTrue(
                format(source, style -> style.preservation(p -> p.keepSimpleBlocksInline(false)))
                        .contains("if (x) {\n            g();\n        }"));
    }

    @Test
    void aBlockTheAuthorSpreadOutIsLeftSpreadOut() {
        String source = "class A {\n\n    void f() {\n        if (x) {\n            g();\n        }\n    }\n\n}\n";

        assertTrue(format(source, style -> { }).contains("if (x) {\n            g();\n        }"));
    }

    @Test
    void aOneLineBodyThatDoesNotFitLaysOutAsAnOrdinaryBody() {
        // The single line is only ever an option. When it does not fit, what comes out has to be what
        // an ordinary multi-line body produces, or formatting the result again would change it.
        String source = "class A {\n\n    void f() {\n        if (x) { alpha(); beta(); gamma(); }\n    }\n\n}\n";

        String narrow = format(source, style -> style.wrapping(w -> w.maxLineLength(30)));

        assertTrue(narrow.contains("if (x) {\n            alpha();\n            beta();\n            gamma();\n"
                + "        }"), narrow);
        assertEquals(narrow, format(narrow, style -> style.wrapping(w -> w.maxLineLength(30))));
    }

    @Test
    void aOneLineClassBodyDropsThePaddingItWouldHaveHadWhenBroken() {
        String source = "class A {\n\n    static class B { int x; }\n\n}\n";

        String inline = format(source, style -> style.wrapping(w -> w.keepSimpleClassesOnOneLine(true)));
        assertTrue(inline.contains("static class B { int x; }"), inline);

        // Too long for one line, so the blank lines the class body rule asks for come back.
        String narrow = format(
                source,
                style -> style.wrapping(w -> w.keepSimpleClassesOnOneLine(true).maxLineLength(20)));
        assertTrue(narrow.contains("static class B {\n\n        int x;\n\n    }"), narrow);
    }

    @Test
    void methodsAndLambdasHaveTheirOwnRules() {
        String source = "class A {\n\n    void f() { g(); }\n\n    void h() {\n        run(() -> { g(); });\n"
                + "    }\n\n}\n";

        String defaults = format(source, style -> { });
        // Lambdas keep their line by default, whole methods do not.
        assertTrue(defaults.contains("run(() -> { g(); })"), defaults);
        assertTrue(defaults.contains("void f() {\n        g();\n    }"), defaults);

        String both = format(
                source,
                style -> style.wrapping(w -> w.keepSimpleMethodsOnOneLine(true).keepSimpleLambdasOnOneLine(false)));
        assertTrue(both.contains("void f() { g(); }"), both);
        assertTrue(both.contains("run(() -> {\n            g();\n        })"), both);
    }

    @Test
    void aCommentThatEndedALineKeepsTheBodyOpen() {
        // A line comment cannot share a line with what follows it, so the body was never on one line
        // in the first place and the ordinary layout is what applies.
        String source = "class A {\n\n    void f() {\n        if (x) { g(); // done\n        }\n    }\n\n}\n";

        String formatted = format(source, style -> { });

        assertTrue(formatted.contains("if (x) {\n            g(); // done\n        }"), formatted);
    }

    @Test
    void aCommentTheAuthorKeptInlineStaysOnThatLine() {
        // A block comment is part of the line the author wrote, and stays on it. It is emitted as a
        // line suffix, so on a body that does stay on one line it ends up after the closing brace.
        String source = "class A {\n\n    void f() {\n        if (x) { g(); /* done */ }\n    }\n\n}\n";

        String formatted = format(source, style -> { });

        assertTrue(formatted.contains("if (x) { g(); } /* done */"), formatted);
    }

    @Test
    void aBreakAfterAnOpeningParenthesisSurvivesWhenTheRuleAsksIt() {
        String source = "class A {\n\n    void f() {\n        g(\n                a,\n                b);\n"
                + "    }\n\n}\n";

        assertTrue(format(source, style -> { }).contains("g(a, b);"));
        assertTrue(
                format(source, style -> style.preservation(p -> p.keepLineBreakAfterOpenParen(true)))
                        .contains("g(\n                a,\n                b\n        );"));
    }

    @Test
    void neverJoinLinesKeepsBreaksTheAuthorTookAnywhere() {
        String source = "class A {\n\n    void f() {\n        int x = one\n                + two;\n"
                + "        g(\n                a,\n                b);\n    }\n\n}\n";

        String joined = format(source, style -> { });
        assertTrue(joined.contains("int x = one + two;"), joined);
        assertTrue(joined.contains("g(a, b);"), joined);

        String kept = format(source, style -> style.preservation(p -> p.neverJoinLines(true)));
        assertTrue(kept.contains("int x = one\n                + two;"), kept);
        assertTrue(kept.contains("g(\n                a,\n                b\n        );"), kept);
    }

    @Test
    void aPatternIsTiedToItsTestOnlyWhileTheRuleSaysSo() {
        String source = "class A {\n\n    boolean f(Object candidate) {\n"
                + "        return candidate instanceof SomeLongTypeName binding && binding.ready();\n    }\n\n}\n";

        String tied = format(source, style -> style.wrapping(w -> w.maxLineLength(50)));
        assertTrue(tied.contains("candidate instanceof SomeLongTypeName binding"), tied);

        String free = format(
                source,
                style -> style.wrapping(w -> w.maxLineLength(50)).patterns(p -> p.keepSimplePatternInline(false)));
        assertTrue(free.contains("instanceof\n"), free);
    }

    @Test
    void caseNullDefaultIsOneLabelRatherThanTwo() {
        String source = "class A {\n\n    int f(Object o) {\n        return switch (o) {\n"
                + "            case null, default -> compute(withOneArgument, andAnother);\n        };\n"
                + "    }\n\n}\n";

        String together = format(source, style -> style.wrapping(w -> w.maxLineLength(50)));
        assertTrue(together.contains("case null, default -> compute("), together);

        String split = format(
                source,
                style -> style.wrapping(w -> w.maxLineLength(50)).switches(s -> s.nullDefaultOnOneLine(false)));
        assertTrue(split.contains("case null, default ->\n"), split);
    }

    @Test
    void aThrowsClauseWrapsAsItsPolicyAsks() {
        String source = "class A {\n\n    void f(int alpha, int beta) throws OneException, TwoException { }\n\n"
                + "    void g()\n            throws OneException { }\n\n}\n";

        String never = format(
                source,
                style -> style.wrapping(w -> w.throwsClause(WrapPolicy.NEVER).maxLineLength(50)));
        assertTrue(never.contains("throws OneException, TwoException"), never);
        assertFalse(never.contains("throws OneException,\n"), never);

        String preserve = format(source, style -> style.wrapping(w -> w.throwsClause(WrapPolicy.PRESERVE)));
        assertTrue(preserve.contains("void g()\n            throws OneException"), preserve);

        // The default rejoins a clause that fits.
        assertTrue(format(source, style -> { }).contains("void g() throws OneException"));
    }

    @Test
    void blankLinesCarryTheirIndentationOnlyWhenAskedTo() {
        String source = "class A {\n\n    void f() {\n        int x = 1;\n\n        int y = 2;\n    }\n\n}\n";

        assertTrue(format(source, style -> { }).contains("int x = 1;\n\n        int y"));
        assertTrue(
                format(source, style -> style.indent(i -> i.blankLines(true)))
                        .contains("int x = 1;\n        \n        int y"));
    }

    private static String format(String source, Consumer<StyleBuilder> configure) {
        StyleBuilder builder = Style.builder();
        configure.accept(builder);
        Formatter formatter = FormatJ.newFormatter().style(builder.build()).build();
        FormatResult result = formatter.format(FormatRequest.of(source).withName("A.java"));
        assertFalse(result.hasErrors(), () -> result.diagnostics().toString());
        return result.text();
    }

}
