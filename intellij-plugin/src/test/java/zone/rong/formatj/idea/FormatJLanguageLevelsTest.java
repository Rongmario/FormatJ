package zone.rong.formatj.idea;

import org.junit.jupiter.api.Test;
import zone.rong.formatj.api.LanguageLevel;

import static org.junit.jupiter.api.Assertions.assertEquals;

class FormatJLanguageLevelsTest {

    @Test
    void releasesOlderThan17Become17() {
        assertEquals(LanguageLevel.JAVA_17, FormatJLanguageLevels.of(8));
        assertEquals(LanguageLevel.JAVA_17, FormatJLanguageLevels.of(17));
    }

    @Test
    void knownReleasesMapDirectly() {
        assertEquals(LanguageLevel.JAVA_21, FormatJLanguageLevels.of(21));
        assertEquals(LanguageLevel.JAVA_25, FormatJLanguageLevels.of(25));
    }

    @Test
    void newerReleasesCapAtLatest() {
        assertEquals(LanguageLevel.LATEST, FormatJLanguageLevels.of(99));
    }

}
