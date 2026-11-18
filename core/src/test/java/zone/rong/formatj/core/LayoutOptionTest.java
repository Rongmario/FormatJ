package zone.rong.formatj.core;

import java.util.function.Consumer;

import org.junit.jupiter.api.Test;
import zone.rong.formatj.api.FormatRequest;
import zone.rong.formatj.api.FormatResult;
import zone.rong.formatj.api.Formatter;
import zone.rong.formatj.api.Preset;
import zone.rong.formatj.api.Style;
import zone.rong.formatj.api.StyleBuilder;
import zone.rong.formatj.api.rules.AnnotationPlacement;
import zone.rong.formatj.api.rules.AnnotationRules;
import zone.rong.formatj.api.rules.AssignmentBreak;
import zone.rong.formatj.api.rules.BlankLineRules;
import zone.rong.formatj.api.rules.BracePlacement;
import zone.rong.formatj.api.rules.BracePolicy;
import zone.rong.formatj.api.rules.BraceRules;
import zone.rong.formatj.api.rules.ChainPolicy;
import zone.rong.formatj.api.rules.ClosingDelimiter;
import zone.rong.formatj.api.rules.EmptyBodyStyle;
import zone.rong.formatj.api.rules.FileRules;
import zone.rong.formatj.api.rules.LineEnding;
import zone.rong.formatj.api.rules.MemberOrder;
import zone.rong.formatj.api.rules.MemberRules;
import zone.rong.formatj.api.rules.ModifierOrder;
import zone.rong.formatj.api.rules.ModifierRules;
import zone.rong.formatj.api.rules.PreservationRules;
import zone.rong.formatj.api.rules.SpacingRules;
import zone.rong.formatj.api.rules.WrapPolicy;
import zone.rong.formatj.api.rules.WrappingRules;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Layout rules that were in the catalogue before the emitter consulted them. */
class LayoutOptionTest {

    private static String format(String source, Consumer<StyleBuilder> configure) {
        // Layout is the subject here, so a lambda's block body is left as written.
        StyleBuilder builder = Style.builder().set(BraceRules.LAMBDA_BODY, BracePolicy.PRESERVE);
        configure.accept(builder);
        Formatter formatter = FormatJ.newFormatter().style(builder.build()).build();
        FormatResult result = formatter.format(FormatRequest.of(source).withName("A.java"));
        assertFalse(result.hasErrors(), () -> result.diagnostics().toString());
        return result.text();
    }

    @Test
    void anEmptyMethodBodyFollowsItsOwnRuleNotTheClassBodyRule() {
        String source = """
                class A {

                    void f() {
                    }

                }
                """;

        String compact = format(source, style -> style.set(BraceRules.EMPTY_METHOD_BODY, EmptyBodyStyle.COMPACT));

        assertTrue(compact.contains("void f() {}"), compact);
        // The class body keeps its own, separate rule.
        assertTrue(compact.contains("class A {\n"), compact);
    }

    @Test
    void anEmptyControlBodyIsUnaffectedByTheMethodBodyRule() {
        String source = "class A {\n    void f() {\n        while (x) {\n        }\n    }\n}\n";

        String compact = format(source, style -> style.set(BraceRules.EMPTY_METHOD_BODY, EmptyBodyStyle.COMPACT));

        assertTrue(compact.contains("while (x) {\n        }"), compact);
    }

    @Test
    void aLambdaBlockBraceFollowsTheLambdaPlacementRule() {
        String source = "class A {\n    void f() {\n        run(() -> {\n            g();\n        });\n    }\n}\n";

        String nextLine = format(source, style -> style.set(BraceRules.LAMBDA_PLACEMENT, BracePlacement.NEXT_LINE));

        assertTrue(nextLine.contains("run(() ->\n"), nextLine);
        assertTrue(nextLine.contains("{\n"), nextLine);
        assertFalse(nextLine.contains("-> {"), nextLine);
    }

    @Test
    void aLambdaBlockBraceStaysOnTheArrowLineByDefault() {
        String source = "class A {\n    void f() {\n        run(() -> {\n            g();\n        });\n    }\n}\n";

        String formatted = format(source, style -> { });

        assertTrue(formatted.contains("() -> {"), formatted);
    }

    @Test
    void aBlockBodyHuggedAfterOtherArgumentsStillWrapsItsStatements() {
        String call = "call(aaaaaaaaaaaaaaaaaaaaaaaaaaaaaa, bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb, " +
            "cccccccccccccccccccccccccccccc, dddddddddddddddddddddddddddddddddddd);";
        String source = """
                class A {
                    void f() {
                        run(NAME, () -> {
                            CALL
                            g();
                        });
                        run(NAME, new Runnable() {
                            public void run() {
                                CALL
                                g();
                            }
                        });
                    }
                }
                """.replace("CALL", call);

        for (Preset preset : Preset.values()) {
            String formatted = format(source, style -> style.apply(preset.style()));
            int max = preset == Preset.GOOGLE ? 100 : 120;

            assertTrue(formatted.contains("run(NAME, () -> {"), formatted);
            assertTrue(formatted.contains("run(NAME, new Runnable() {"), formatted);
            assertTrue(formatted.lines().allMatch(line -> line.length() <= max), formatted);
        }
    }

    @Test
    void bracketSpacingAppliesToIndexesAndDimensions() {
        String source = "class A {\n    void f() {\n        int[] a = new int[n];\n        g(a[i]);\n    }\n}\n";

        String spaced = format(source, style -> style.set(SpacingRules.WITHIN_BRACKETS, true));

        assertTrue(spaced.contains("new int[ n ]"), spaced);
        assertTrue(spaced.contains("a[ i ]"), spaced);
        // An empty pair has nothing to pad.
        assertTrue(spaced.contains("int[] a"), spaced);
    }

    @Test
    void aSpaceCanBeAskedForBeforeStatementSemicolons() {
        String source = """
                package a;

                class A {

                    int x = 1;

                    void f() {
                        g();
                        int y = 2;
                        return;
                    }

                    void h();

                }
                """;

        String spaced = format(source, style -> style.set(SpacingRules.BEFORE_SEMICOLON, true));

        assertTrue(spaced.contains("package a ;"), spaced);
        assertTrue(spaced.contains("int x = 1 ;"), spaced);
        assertTrue(spaced.contains("g() ;"), spaced);
        assertTrue(spaced.contains("int y = 2 ;"), spaced);
        assertTrue(spaced.contains("return ;"), spaced);
        assertTrue(spaced.contains("void h() ;"), spaced);
    }

    @Test
    void theForHeaderKeepsItsOwnSemicolonRule() {
        String source = "class A {\n    void f() {\n        for (int i = 0; i < n; i++) {\n            g();\n" +
            "        }\n    }\n}\n";

        String spaced = format(source, style -> style.set(SpacingRules.BEFORE_SEMICOLON, true));

        assertTrue(spaced.contains("for (int i = 0; i < n; i++)"), spaced);
    }

    @Test
    void everyRuleUnderTestIsAFixedPoint() {
        String source = """
                package a;

                class A {

                    void f() {
                        int[] v = new int[n];
                        run(() -> {
                            g(v[0]);
                        });
                    }

                    void empty() {
                    }

                }
                """;
        Style style = Style.builder()
            .set(BraceRules.EMPTY_METHOD_BODY, EmptyBodyStyle.COMPACT)
            .set(BraceRules.LAMBDA_PLACEMENT, BracePlacement.NEXT_LINE)
            .set(SpacingRules.WITHIN_BRACKETS, true)
            .set(SpacingRules.BEFORE_SEMICOLON, true)
            .build();
        Formatter formatter = FormatJ.newFormatter().style(style).build();

        String once = formatter.format(FormatRequest.of(source).withName("A.java")).text();
        String twice = formatter.format(FormatRequest.of(once).withName("A.java")).text();

        assertEquals(once, twice);
    }

    @Test
    void anEmptyRecordBodyCanCollapseToOneToken() {
        String source = "record Point(int x, int y) {\n}\n";

        String collapsed = format(source, style -> style.set(BraceRules.EMPTY_RECORD_BODY, EmptyBodyStyle.COMPACT));
        String spaced = format(source, style -> style.set(BraceRules.EMPTY_CLASS_BODY, EmptyBodyStyle.SPACED));

        assertTrue(collapsed.contains("record Point(int x, int y) {}"), collapsed);
        assertTrue(spaced.contains("record Point(int x, int y) { }"), spaced);
    }

    @Test
    void aCompactConstructorCanBePaddedWithBlankLines() {
        String source = """
                record Range(int low, int high) {

                    Range {
                        check(low, high);
                    }

                }
                """;

        String padded = format(source, style -> style.set(BlankLineRules.INSIDE_COMPACT_CONSTRUCTOR, 1));

        assertTrue(padded.contains("Range {\n\n        check(low, high);\n\n    }"), padded);
    }

    @Test
    void thePermitsClauseCanStartOnItsOwnLine() {
        String source = "sealed interface Shape permits Circle, Square {\n}\n";

        String ownLine = format(source, style -> style.set(WrappingRules.PERMITS_ON_NEW_LINE, true));

        assertTrue(ownLine.contains("sealed interface Shape\n    permits Circle, Square {"), ownLine);
    }

    @Test
    void extendsAndImplementsHonourTheirWrappingRule() {
        String source = "class A extends B implements C, D {\n}\n";

        String chopped = format(
            source,
            style -> style.set(WrappingRules.EXTENDS_IMPLEMENTS, WrapPolicy.CHOP_DOWN_ALWAYS)
        );

        assertTrue(chopped.contains("class A extends B\n    implements C,\n    D {"), chopped);
    }

    @Test
    void aNeverWrappingImplementsClauseStaysOnOneLine() {
        String source = "class VeryLongClassNameIndeed implements AlphaInterface, BetaInterface, GammaInterface," +
            " DeltaInterface {\n}\n";

        String flat = format(source, style -> style.set(WrappingRules.EXTENDS_IMPLEMENTS, WrapPolicy.NEVER));

        // The whole header, keyword included: a break in front of implements is still a break.
        assertTrue(
            flat.contains(
                "class VeryLongClassNameIndeed implements AlphaInterface, BetaInterface," +
                    " GammaInterface, DeltaInterface {"
            ),
            flat
        );
    }

    @Test
    void aNeverWrappingImplementsClauseDoesNotBreakAHeaderThatAlreadyFits() {
        String source = "class A implements B {\n}\n";

        String flat = format(source, style -> style.set(WrappingRules.EXTENDS_IMPLEMENTS, WrapPolicy.NEVER));

        assertTrue(flat.contains("class A implements B {"), flat);
    }

    @Test
    void aCommentAboveAStatementDoesNotChopDownItsChain() {
        String source = """
                class A {

                    void f() {
                        // note
                        project.getTasks().withType(T.class).configureEach(t -> t.setX(1));
                    }

                }
                """;

        String formatted = format(source, style -> { });

        // The comment ends its own line and nothing else: the chain below it still fits on one.
        assertTrue(
            formatted.contains(
                "        // note\n" + "        project.getTasks().withType(T.class).configureEach(t -> t.setX(1));"
            ),
            formatted
        );
    }

    @Test
    void aCommentAboveAnUnbracedControlBodyDoesNotChopDownItsChain() {
        String source = """
                class A {

                    void f() {
                        if (ready)
                            // keep the old value
                            project.getTasks().withType(T.class).configureEach(t -> t.setX(1));
                    }

                }
                """;

        String formatted = format(source, style -> { });

        assertTrue(
            formatted.contains(
                "            // keep the old value\n" +
                    "            project.getTasks().withType(T.class).configureEach(t -> t.setX(1));"
            ),
            formatted
        );
    }

    @Test
    void breakAllWhenTooLongLeavesAChainWhoseOwnLineFits() {
        String source = """
                class A {

                    void f() {
                        project.getTasks().withType(Jar.class).configureEach(task -> {
                            task.setStale(false);
                        });
                    }

                }
                """;

        String formatted = format(
            source,
            style -> style.set(WrappingRules.CHAINED_CALLS, ChainPolicy.BREAK_ALL_WHEN_TOO_LONG)
        );

        // The lambda's own lines are not the chain being too long for one line, and the body they
        // hold is indented from the statement rather than from a wrap that did not happen.
        assertTrue(
            formatted.contains(
                "        project.getTasks().withType(Jar.class).configureEach(task -> {\n" +
                    "            task.setStale(false);\n" + "        });"
            ),
            formatted
        );
    }

    @Test
    void preserveChainPolicyKeepsTheAuthorsExactBreaks() {
        String flatSource = """
                class A {

                    void f() {
                        project.getTasks().withType().configureEach();
                    }

                }
                """;
        String brokenSource = """
                class A {

                    void f() {
                        project.getTasks()
                                .withType().configureEach();
                    }

                }
                """;
        Consumer<StyleBuilder> preserve = style -> style
            .set(WrappingRules.MAX_LINE_LENGTH, 30)
            .set(WrappingRules.CHAINED_CALLS, ChainPolicy.PRESERVE)
            .set(PreservationRules.RESPECT_EXISTING_CHAIN_BREAKS, false);

        String flat = format(flatSource, preserve);
        String broken = format(brokenSource, preserve);

        assertTrue(flat.contains("project.getTasks().withType().configureEach();"), flat);
        assertTrue(broken.contains("project.getTasks()\n" + "            .withType().configureEach();"), broken);
    }

    @Test
    void breakAllWhenTooLongPutsEveryLinkOnItsOwnLineOnceTheLineOverflows() {
        String source = """
                class A {

                    void f() {
                        project.getExtensions().getByName("publishing").getPublications().withType(Publication.class).configureEach(publication -> {
                            publication.setAlias(false);
                        });
                    }

                }
                """;

        String formatted = format(
            source,
            style -> style.set(WrappingRules.CHAINED_CALLS, ChainPolicy.BREAK_ALL_WHEN_TOO_LONG)
        );

        assertTrue(
            formatted.contains(
                "        project.getExtensions()\n" + "            .getByName(\"publishing\")\n" +
                    "            .getPublications()\n" + "            .withType(Publication.class)\n" +
                    "            .configureEach(publication -> {\n" + "                publication.setAlias(false);\n" +
                    "            });"
            ),
            formatted
        );
    }

    @Test
    void breakAllWhenTooLongIsAFixedPoint() {
        String source = """
                class A {

                    void f() {
                        project.getTasks().withType(Jar.class).configureEach(task -> {
                            task.setStale(false);
                        });
                        project.getExtensions().getByName("publishing").getPublications().withType(Publication.class).configureEach(publication -> {
                            publication.setAlias(false);
                        });
                    }

                }
                """;
        Style style = Style.builder().set(WrappingRules.CHAINED_CALLS, ChainPolicy.BREAK_ALL_WHEN_TOO_LONG).build();
        Formatter formatter = FormatJ.newFormatter().style(style).build();

        String once = formatter.format(FormatRequest.of(source).withName("A.java")).text();
        String twice = formatter.format(FormatRequest.of(once).withName("A.java")).text();

        assertEquals(once, twice);
    }

    @Test
    void aWrappedListCanEndOnTheClosingParenthesis() {
        String source = """
                class A {

                    void f() {
                        this.callIsLong(argumentOne, argumentTwo, argumentThree, argumentFour);
                    }

                }
                """;

        String formatted = format(
            source,
            style -> style.set(WrappingRules.MAX_LINE_LENGTH, 60)
                .set(WrappingRules.CLOSING_DELIMITER, ClosingDelimiter.OWN_LINE)
        );

        assertTrue(
            formatted.contains(
                "        this.callIsLong(\n" + "            argumentOne,\n" + "            argumentTwo,\n" +
                    "            argumentThree,\n" + "            argumentFour\n" + "        );"
            ),
            formatted
        );
    }

    @Test
    void theClosingParenthesisCanBeKeptAgainstTheLastElement() {
        String source = """
                class A {

                    void f() {
                        this.callIsLong(argumentOne, argumentTwo, argumentThree, argumentFour);
                    }

                }
                """;

        String formatted = format(
            source,
            style -> style.set(WrappingRules.MAX_LINE_LENGTH, 60)
                .set(WrappingRules.CLOSING_DELIMITER, ClosingDelimiter.ATTACHED)
        );

        assertTrue(formatted.contains("            argumentFour);"), formatted);
    }

    @Test
    void everyParenthesisedListFollowsTheOneRule() {
        String source = """
                class A {

                    void methodWithManyParameters(String firstParameter, String secondParameter, int third) {
                    }

                }
                """;

        String formatted = format(
            source,
            style -> style.set(WrappingRules.MAX_LINE_LENGTH, 60)
                .set(WrappingRules.CLOSING_DELIMITER, ClosingDelimiter.OWN_LINE)
        );

        // A file that dangles a call's parenthesis and hugs a declaration's reads as two styles.
        assertTrue(
            formatted.contains(
                "    void methodWithManyParameters(\n" + "        String firstParameter,\n" +
                    "        String secondParameter,\n" + "        int third\n" + "    ) {"
            ),
            formatted
        );
    }

    @Test
    void aHuggedTrailingLambdaKeepsItsClosingParenthesisWhereItIs() {
        String source = """
                class A {

                    void f() {
                        register("name", Jar.class, task -> {
                            task.setStale(false);
                        });
                    }

                }
                """;

        String formatted = format(source, style -> { });

        // The list never wrapped, so there is no line for the parenthesis to move to.
        assertTrue(formatted.contains("        });"), formatted);
    }

    @Test
    void googleStyleKeepsItsClosingParenthesisAttached() {
        String source = """
                class A {

                    void f() {
                        this.callIsLong(argumentOne, argumentTwo, argumentThree, argumentFour, argumentFive, argumentSix, argumentSeven);
                    }

                }
                """;
        Formatter formatter = FormatJ.newFormatter().style(Preset.GOOGLE.style()).build();

        String formatted = formatter.format(FormatRequest.of(source).withName("A.java")).text();

        assertFalse(formatted.contains("\n    );"), formatted);
        assertTrue(formatted.contains("argumentSeven);"), formatted);
    }

    @Test
    void googleStyleHugsATrailingLambdaAndKeepsItsCommentTogether() {
        String source = """
                class A {

                    void f() {
                        register("name", Jar.class, task -> {
                            // configure it
                            task.setStale(false);
                        });
                    }

                }
                """;
        Formatter formatter = FormatJ.newFormatter().style(Preset.GOOGLE.style()).build();

        String formatted = formatter.format(FormatRequest.of(source).withName("A.java")).text();

        assertTrue(
            formatted.contains(
                "    register(\"name\", Jar.class, task -> {\n" + "      // configure it\n" +
                    "      task.setStale(false);\n" + "    });"
            ),
            formatted
        );
    }

    @Test
    void aTrailingLambdaDoesNotChopDownTheArgumentsInFrontOfIt() {
        String source = """
                class A {

                    void f() {
                        register("name", Jar.class, task -> {
                            task.setStale(false);
                        });
                    }

                }
                """;

        String formatted = format(source, style -> { });

        // chop-down-if-long asks whether the list fits on its line, and the lambda's own lines are
        // not the list failing to: the arguments in front of it stay where the author had them.
        assertTrue(
            formatted.contains(
                "        register(\"name\", Jar.class, task -> {\n" + "            task.setStale(false);\n" +
                    "        });"
            ),
            formatted
        );
    }

    @Test
    void argumentsStillChopDownWhenTheirOwnLineDoesNotFit() {
        String source = """
                class A {

                    void f() {
                        registerWithARatherLongMethodNameIndeed("some rather long name here", AnotherType.class, someOtherArgument, task -> {
                            task.setStale(false);
                        });
                    }

                }
                """;

        String formatted = format(
            source,
            style -> style.set(WrappingRules.CLOSING_DELIMITER, ClosingDelimiter.OWN_LINE)
        );

        assertTrue(
            formatted.contains(
                "        registerWithARatherLongMethodNameIndeed(\n" + "            \"some rather long name here\",\n" +
                    "            AnotherType.class,\n" + "            someOtherArgument,\n" +
                    "            task -> {\n" + "                task.setStale(false);\n" + "            }\n" +
                    "        );"
            ),
            formatted
        );
    }

    @Test
    void onlyTheLastArgumentHugs() {
        String source = """
                class A {

                    void f() {
                        register("name", task -> {
                            task.setStale(false);
                        }, trailing);
                    }

                }
                """;

        String formatted = format(
            source,
            style -> style.set(WrappingRules.CLOSING_DELIMITER, ClosingDelimiter.OWN_LINE)
        );

        // An argument that ends mid-line would strand the ones after it against a closing brace.
        assertTrue(
            formatted.contains(
                "        register(\n" + "            \"name\",\n" + "            task -> {\n" +
                    "                task.setStale(false);\n" + "            },\n" + "            trailing\n" +
                    "        );"
            ),
            formatted
        );
    }

    @Test
    void aHuggedTrailingLambdaIsAFixedPoint() {
        String source = """
                class A {

                    void f() {
                        register("name", Jar.class, task -> {
                            task.setStale(false);
                        });
                        registerWithARatherLongMethodNameIndeed("some rather long name here", AnotherType.class, someOtherArgument, task -> {
                            task.setStale(false);
                        });
                    }

                }
                """;
        Formatter formatter = FormatJ.newFormatter().style(Style.builder().build()).build();

        String once = formatter.format(FormatRequest.of(source).withName("A.java")).text();
        String twice = formatter.format(FormatRequest.of(once).withName("A.java")).text();

        assertEquals(once, twice);
    }

    @Test
    void tryResourcesCanBeForcedToChopDown() {
        String source = "class A {\n    void f() {\n        try (X x = open()) {\n            g();\n        }\n" +
            "    }\n}\n";

        String chopped = format(source, style -> style.set(WrappingRules.TRY_RESOURCES, WrapPolicy.CHOP_DOWN_ALWAYS));

        assertTrue(chopped.contains("try (\n"), chopped);
    }

    @Test
    void tryResourcesFollowTheClosingDelimiterRule() {
        String source = """
                class A {

                    void f() {
                        try (X first = openFirst(); X second = openSecond()) {
                            g();
                        }
                    }

                }
                """;

        String attached = format(
            source,
            style -> style.set(WrappingRules.TRY_RESOURCES, WrapPolicy.CHOP_DOWN_ALWAYS)
                .set(WrappingRules.CLOSING_DELIMITER, ClosingDelimiter.ATTACHED)
        );
        String ownLine = format(
            source,
            style -> style.set(WrappingRules.TRY_RESOURCES, WrapPolicy.CHOP_DOWN_ALWAYS)
                .set(WrappingRules.CLOSING_DELIMITER, ClosingDelimiter.OWN_LINE)
        );

        assertTrue(attached.contains("            X second = openSecond()) {"), attached);
        assertTrue(ownLine.contains("            X second = openSecond()\n        ) {"), ownLine);
    }

    @Test
    void anEnumsFirstConstantHasItsOwnBlankLineRule() {
        String source = "enum Color {\n    RED,\n    GREEN;\n\n    void f() { }\n\n}\n";

        String tight = format(source, style -> style.set(BlankLineRules.BEFORE_FIRST_ENUM_CONSTANT, 0));

        assertTrue(tight.contains("enum Color {\n    RED,"), tight);
    }

    @Test
    void trailingWhitespaceInACommentCanBeKept() {
        String source = "class A {\n\n    /* padded   \n     * lines   \n     */\n    int x = 1;\n\n}\n";

        String trimmed = format(source, style -> { });
        String kept = format(source, style -> style.set(FileRules.TRIM_TRAILING_WHITESPACE, false));

        assertFalse(trimmed.contains("padded   \n"), trimmed);
        assertTrue(kept.contains("padded   \n"), kept);
    }

    @Test
    void preserveLineEndingKeepsTheSourcesOwnTerminator() {
        String crlfSource = "class A {\r\n\r\n    void f() {\r\n    }\r\n\r\n}\r\n";

        String formatted = format(crlfSource, style -> style.set(FileRules.LINE_ENDING, LineEnding.PRESERVE));

        assertTrue(formatted.contains("\r\n"), formatted);
        assertFalse(formatted.replace("\r\n", "").contains("\n"), formatted);
    }

    @Test
    void theCharsetRuleResolvesToACharset() {
        Style utf16 = Style.builder().set(FileRules.CHARSET, "UTF-16").build();

        assertEquals(java.nio.charset.StandardCharsets.UTF_16, FileRules.charset(utf16));
        assertEquals(java.nio.charset.StandardCharsets.UTF_8, FileRules.charset(Style.builder().build()));
    }

    @Test
    void anUnknownCharsetNamesTheKeyAndValueRatherThanFallingBackSilently() {
        Style nonsense = Style.builder().set(FileRules.CHARSET, "not-a-charset").build();

        IllegalArgumentException thrown = assertThrows(
            IllegalArgumentException.class,
            () -> FileRules.charset(nonsense)
        );
        assertTrue(thrown.getMessage().contains("file.charset"), thrown.getMessage());
        assertTrue(thrown.getMessage().contains("not-a-charset"), thrown.getMessage());
    }

    @Test
    void aLoneMarkerAnnotationCanShareTheDeclarationLine() {
        String source = """
                class A {

                    @Override
                    public String toString() {
                        return "";
                    }

                    @Deprecated
                    @Override
                    public int hashCode() {
                        return 0;
                    }

                }
                """;

        String inline = format(source, style -> style.set(AnnotationRules.SINGLE_MARKER_INLINE, true));

        assertTrue(inline.contains("@Override public String toString()"), inline);
        // Two annotations are no longer a lone marker, so they keep their own lines.
        assertTrue(inline.contains("@Deprecated\n    @Override\n    public int hashCode()"), inline);
    }

    @Test
    void anAnnotationWithArgumentsIsNotAMarker() {
        String source = "class A {\n\n    @SuppressWarnings(\"x\")\n    void f() { }\n\n}\n";

        String inline = format(source, style -> style.set(AnnotationRules.SINGLE_MARKER_INLINE, true));

        assertTrue(inline.contains("@SuppressWarnings(\"x\")\n    void f()"), inline);
    }

    @Test
    void parametersAndLocalsFollowTheParameterPlacementRule() {
        String source = "class A {\n\n    void f(@NotNull String a) {\n        @Marked int b = 1;\n    }\n\n}\n";

        String ownLines = format(
            source,
            style -> style.set(AnnotationRules.PARAMETER_PLACEMENT, AnnotationPlacement.NEW_LINE)
        );

        assertTrue(ownLines.contains("@NotNull\n        String a"), ownLines);
        assertTrue(ownLines.contains("@Marked\n        int b = 1;"), ownLines);
    }

    @Test
    void aLongAssignmentBreaksInsideItsValueByDefault() {
        String source = "class A {\n    void f() {\n        Result result = compute(firstArgument, secondArgument);\n" +
            "    }\n}\n";

        String inside = format(source, style -> style.set(WrappingRules.MAX_LINE_LENGTH, 60));
        String after = format(
            source,
            style -> style.set(WrappingRules.MAX_LINE_LENGTH, 60)
                .set(WrappingRules.ASSIGNMENT, WrapPolicy.WRAP_IF_LONG)
                .set(WrappingRules.ASSIGNMENT_BREAK, AssignmentBreak.AFTER_OPERATOR)
        );

        assertTrue(inside.contains("Result result = compute(\n            firstArgument,"), inside);
        assertTrue(after.contains("Result result =\n            compute(firstArgument, secondArgument);"), after);
    }

    @Test
    void aLoneCallArgumentStaysOnTheLineOfItsParenthesis() {
        String source = "class A {\n    void f() {\n        list.add(new Entry(firstArgument, secondArgument));\n" +
            "    }\n}\n";

        String hugged = format(source, style -> style.set(WrappingRules.MAX_LINE_LENGTH, 44));
        String wrapped = format(
            source,
            style -> style.set(WrappingRules.MAX_LINE_LENGTH, 44).set(WrappingRules.HUG_SOLE_ARGUMENT, false)
        );

        assertTrue(hugged.contains("list.add(new Entry(\n            firstArgument,"), hugged);
        assertTrue(wrapped.contains("list.add(\n            new Entry("), wrapped);
    }

    @Test
    void aFilledArgumentListCanStartOnTheLineOfItsParenthesis() {
        String source = "class A {\n    void f() {\n        call(firstArgument, secondArgument, thirdArgument);\n" +
            "    }\n}\n";

        String dangling = format(
            source,
            style -> style.set(WrappingRules.MAX_LINE_LENGTH, 44)
                .set(WrappingRules.METHOD_ARGUMENTS, WrapPolicy.WRAP_IF_LONG)
                .set(WrappingRules.BREAK_AFTER_OPEN_PAREN, false)
        );
        String attached = format(
            source,
            style -> style.set(WrappingRules.MAX_LINE_LENGTH, 44)
                .set(WrappingRules.METHOD_ARGUMENTS, WrapPolicy.WRAP_IF_LONG)
                .set(WrappingRules.BREAK_AFTER_OPEN_PAREN, false)
                .set(WrappingRules.CLOSING_DELIMITER, ClosingDelimiter.ATTACHED)
        );

        assertTrue(
            dangling.contains("call(firstArgument, secondArgument,\n            thirdArgument\n        );"),
            dangling
        );
        assertTrue(attached.contains("call(firstArgument, secondArgument,\n            thirdArgument);"), attached);
    }

    @Test
    void aQualifiedNameIsNotAChain() {
        String source = "class A {\n    void f() {\n        g(someArgument, Outer.Inner.SOME_CONSTANT_NAME);\n    }\n" +
            "}\n";

        String formatted = format(source, style -> style.set(WrappingRules.MAX_LINE_LENGTH, 40));

        assertTrue(formatted.contains("Outer.Inner.SOME_CONSTANT_NAME"), formatted);
    }

    @Test
    void anEmptyForClauseTakesNoSpace() {
        assertTrue(
            format("class A {\n    void f() {\n        for (; ;) {\n        }\n    }\n}\n", style -> { }).contains(
                "for (;;) {\n        }"
            )
        );
    }

    @Test
    void aFieldAfterAMethodIsSetApart() {
        String source = "class A {\n    void f() {}\n    int x;\n}\n";

        assertTrue(
            format(source, style -> style.set(MemberRules.ORDER, MemberOrder.PRESERVE)).contains(
                "void f() { }\n\n    int x;"
            )
        );
    }

    @Test
    void redundantModifiersGoBeforeTheRestAreOrdered() {
        String source = "interface I {\n    final static public int X = 1;\n}\nclass C {\n" +
            "    static private final int Z = 2;\n}\n";

        String formatted = format(
            source,
            style -> style.set(ModifierRules.ORDER, ModifierOrder.CANONICAL).set(ModifierRules.REMOVE_REDUNDANT, true)
        );

        assertTrue(formatted.contains("    int X = 1;"), formatted);
        assertTrue(formatted.contains("    private static final int Z = 2;"), formatted);
    }

    @Test
    void blankLinesJustInsideBracesCanBeStripped() {
        String source = "class A {\n\n    void f() {\n\n        g();\n\n        h();\n\n    }\n\n}\n";

        assertEquals(source, format(source, style -> { }));
        assertEquals(
            "class A {\n\n    void f() {\n        g();\n\n        h();\n    }\n\n}\n",
            format(source, style -> style.set(BlankLineRules.STRIP_AT_BRACE_EDGES, true))
        );
    }

    @Test
    void anAssertMessageBreaksBeforeItsCondition() {
        String condition = "value != null && value.hashCode() != 0 && value.toString().length() > 3";
        String source = "class A {\n\n    void f(Object value) {\n        assert " + condition +
            " : \"The value must be fine, really fine\";\n    }\n\n}\n";

        assertEquals(
            "class A {\n\n    void f(Object value) {\n        assert " + condition +
                "\n            : \"The value must be fine, really fine\";\n    }\n\n}\n",
            format(source, style -> { })
        );
    }

}
