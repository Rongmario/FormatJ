package zone.rong.formatj.idea;

import org.junit.jupiter.api.Test;
import zone.rong.formatj.api.LanguageLevel;

import static org.junit.jupiter.api.Assertions.assertEquals;

class FormatJFilesTest {

    @Test
    void releasesOlderThan17Become17() {
        assertEquals(LanguageLevel.JAVA_17, FormatJFiles.languageLevel(8));
        assertEquals(LanguageLevel.JAVA_17, FormatJFiles.languageLevel(17));
    }

    @Test
    void knownReleasesMapDirectly() {
        assertEquals(LanguageLevel.JAVA_21, FormatJFiles.languageLevel(21));
        assertEquals(LanguageLevel.JAVA_25, FormatJFiles.languageLevel(25));
    }

    @Test
    void newerReleasesCapAtLatest() {
        assertEquals(LanguageLevel.LATEST, FormatJFiles.languageLevel(99));
    }

}
