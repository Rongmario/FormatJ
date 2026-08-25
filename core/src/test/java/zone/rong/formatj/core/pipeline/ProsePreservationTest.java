package zone.rong.formatj.core.pipeline;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import zone.rong.formatj.api.LanguageLevel;
import zone.rong.formatj.api.Style;
import zone.rong.formatj.api.rules.JavadocRules;
import zone.rong.formatj.api.rules.JavadocTagOrder;
import zone.rong.formatj.core.cst.GreenNode;
import zone.rong.formatj.core.parser.JavaParser;
import org.junit.jupiter.api.Test;

/**
 * What the prose check has to catch, written as the pairs of files it has to tell apart.
 *
 * <p>No rule the formatter ships produces the second half of any of these pairs. That is the point:
 * the check exists so that a rule which one day does produce one is stopped, and a check nobody has
 * watched fail is not known to work.
 */
class ProsePreservationTest {

    private static GreenNode parse(String source) {
        return JavaParser.parse(source, LanguageLevel.LATEST, false).root().green();
    }

    private static String difference(String before, String after) {
        return ProsePreservation.firstDifference(parse(before), parse(after), Style.defaults());
    }

    private static String differenceReordering(String before, String after) {
        return ProsePreservation.firstDifference(
                parse(before),
                parse(after),
                Style.builder().set(JavadocRules.TAG_ORDER, JavadocTagOrder.CANONICAL).build());
    }

    // -------------------------------------------------------- what is allowed

    @Test
    void rewrappingTheSameWordsIsNotAChange() {
        assertNull(
                difference(
                        "class T {\n    // alpha beta gamma delta\n    void f() { }\n}\n",
                        "class T {\n    // alpha beta\n    // gamma delta\n    void f() { }\n}\n"));
    }

    @Test
    void movingWordsBetweenACommentsMarkersIsNotAChange() {
        assertNull(
                difference(
                        "class T {\n    /**\n     * alpha\n     * beta\n     */\n    void f() { }\n}\n",
                        "class T {\n    /** alpha beta */\n    void f() { }\n}\n"));
    }

    @Test
    void aParagraphMarkerMayBeWritten() {
        assertNull(
                difference(
                        "class T {\n    /**\n     * alpha\n     *\n     * beta\n     */\n    void f() { }\n}\n",
                        "class T {\n    /**\n     * alpha\n     * <p>\n     * beta\n     */\n    void f() { }\n}\n"));
    }

    @Test
    void trailingSpaceInsideASampleIsTheFileRulesToRemove() {
        assertNull(
                difference(
                        "class T {\n    /**\n     * <pre>\n     * int x = 1;   \n     * </pre>\n     */\n"
                                + "    void f() { }\n}\n",
                        "class T {\n    /**\n     * <pre>\n     * int x = 1;\n     * </pre>\n     */\n"
                                + "    void f() { }\n}\n"));
    }

    // ------------------------------------------------------ what is refused

    @Test
    void aWordThatWentMissingFails() {
        String problem =
                difference(
                        "class T {\n    // alpha beta gamma\n    void f() { }\n}\n",
                        "class T {\n    // alpha gamma\n    void f() { }\n}\n");
        assertNotNull(problem);
        assertTrue(problem.contains("beta"), problem);
    }

    @Test
    void aWordThatChangedFails() {
        String problem =
                difference(
                        "class T {\n    // alpha beta\n    void f() { }\n}\n",
                        "class T {\n    // alpha betas\n    void f() { }\n}\n");
        assertNotNull(problem);
        assertTrue(problem.contains("'beta' became 'betas'"), problem);
    }

    @Test
    void twoWordsRunTogetherFails() {
        String problem =
                difference(
                        "class T {\n    // alpha beta\n    void f() { }\n}\n",
                        "class T {\n    // alphabeta\n    void f() { }\n}\n");
        assertNotNull(problem);
    }

    @Test
    void aWholeCommentThatDisappearedFails() {
        String problem =
                difference(
                        "class T {\n    // alpha\n    void f() { }\n}\n",
                        "class T {\n    void f() { }\n}\n");
        assertNotNull(problem);
        assertTrue(problem.contains("lost"), problem);
    }

    @Test
    void reformattingACodeSampleFails() {
        String problem =
                difference(
                        "class T {\n    /**\n     * {@code a  b}\n     */\n    void f() { }\n}\n",
                        "class T {\n    /**\n     * {@code a b}\n     */\n    void f() { }\n}\n");
        assertNotNull(problem);
        assertTrue(problem.contains("verbatim"), problem);
    }

    @Test
    void reflowingAPreBlockFails() {
        String problem =
                difference(
                        "class T {\n    /**\n     * <pre>\n     * one\n     * two\n     * </pre>\n     */\n"
                                + "    void f() { }\n}\n",
                        "class T {\n    /**\n     * <pre>\n     * one two\n     * </pre>\n     */\n"
                                + "    void f() { }\n}\n");
        assertNotNull(problem);
    }

    // ------------------------------------------------------------ reordering

    @Test
    void reorderingIsRefusedUntilTheRuleThatDoesItIsOn() {
        String before = "class T {\n    /**\n     * @return r\n     * @param a x\n     */\n    int f(int a) { return a; }\n}\n";
        String after = "class T {\n    /**\n     * @param a x\n     * @return r\n     */\n    int f(int a) { return a; }\n}\n";
        assertNotNull(difference(before, after));
        assertNull(differenceReordering(before, after));
    }

    @Test
    void reorderingDoesNotExcuseLosingAWord() {
        String problem =
                differenceReordering(
                        "class T {\n    /**\n     * @return r\n     * @param a x\n     */\n    int f(int a) { return a; }\n}\n",
                        "class T {\n    /**\n     * @param a\n     * @return r\n     */\n    int f(int a) { return a; }\n}\n");
        assertNotNull(problem);
        assertTrue(problem.contains("lost"), problem);
    }

}
