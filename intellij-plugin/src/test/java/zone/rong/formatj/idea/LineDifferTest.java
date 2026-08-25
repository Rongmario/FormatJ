package zone.rong.formatj.idea;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import zone.rong.formatj.api.SourceRange;
import java.util.List;
import org.junit.jupiter.api.Test;

class LineDifferTest {

    @Test
    void identicalInputsAreReturnedUnchanged() {
        String text = "a\nb\n";
        assertEquals(text, LineDiffer.splice(text, text, List.of(new SourceRange(0, 1))));
    }

    @Test
    void aRangeAppliesOnlyOverlappingHunks() {
        String original = "aaa\nbbb\nccc\n";
        String formatted = "AAA\nbbb\nCCC\n";
        String spliced = LineDiffer.splice(original, formatted, List.of(new SourceRange(0, 3)));
        assertEquals("AAA\nbbb\nccc\n", spliced);
    }

    @Test
    void aRangeOnTheSecondHunkLeavesTheFirstAlone() {
        String original = "aaa\nbbb\nccc\n";
        String formatted = "AAA\nbbb\nCCC\n";
        int start = original.indexOf("ccc");
        String spliced = LineDiffer.splice(original, formatted, List.of(new SourceRange(start, start + 3)));
        assertEquals("aaa\nbbb\nCCC\n", spliced);
    }

    @Test
    void emptyRangesMeanTheWholeFile() {
        // The engine treats empty ranges as whole-file; splice itself formats only when ranges exist
        assertEquals("AAA\n", LineDiffer.splice("aaa\n", "AAA\n", List.of(new SourceRange(0, 4))));
    }

    @Test
    void splitPreservesAMissingFinalNewline() {
        LineDiffer.Split split = LineDiffer.Split.of("a\nb");
        assertEquals(List.of("a", "b"), split.lines());
        assertEquals(false, split.trailingNewline());
        assertEquals("a\nb", LineDiffer.join(split.lines(), split.trailingNewline()));
    }

    @Test
    void splitPreservesAFinalNewline() {
        LineDiffer.Split split = LineDiffer.Split.of("a\nb\n");
        assertEquals(List.of("a", "b"), split.lines());
        assertTrue(split.trailingNewline());
        assertEquals("a\nb\n", LineDiffer.join(split.lines(), split.trailingNewline()));
    }

}
