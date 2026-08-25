package zone.rong.formatj.cli;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class UnifiedDiffTest {

    @Test
    void identicalTextsProduceNoDiff() {
        assertEquals("", UnifiedDiff.between("A.java", "same\n", "same\n"));
    }

    @Test
    void changedLinesAppearWithContextAndAHunkHeader() {
        String before = "one\ntwo\nthree\nfour\nfive\n";
        String after = "one\ntwo\nTHREE\nfour\nfive\n";

        String diff = UnifiedDiff.between("A.java", before, after);

        assertTrue(diff.startsWith("--- A.java\n+++ A.java (formatted)\n"), diff);
        assertTrue(diff.contains("@@ -1,5 +1,5 @@"), diff);
        assertTrue(diff.contains("-three"), diff);
        assertTrue(diff.contains("+THREE"), diff);
        assertTrue(diff.contains(" two"), diff);
    }

    @Test
    void addedLinesAreMarked() {
        String diff = UnifiedDiff.between("A.java", "a\n", "a\nb\n");
        assertTrue(diff.contains("+b"), diff);
    }

}
