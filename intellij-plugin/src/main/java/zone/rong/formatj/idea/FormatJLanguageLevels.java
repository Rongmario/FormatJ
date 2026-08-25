package zone.rong.formatj.idea;

import zone.rong.formatj.api.LanguageLevel;

/**
 * Maps an IntelliJ (or javac) release number onto FormatJ's parser levels.
 */
public final class FormatJLanguageLevels {

    private FormatJLanguageLevels() { }

    /**
     * The FormatJ level for {@code release}.
     * Capped at {@link LanguageLevel#LATEST}.
     */
    public static LanguageLevel of(int release) {
        if (release <= 17) {
            return LanguageLevel.JAVA_17;
        }
        try {
            return LanguageLevel.ofRelease(release);
        } catch (IllegalArgumentException e) {
            return LanguageLevel.LATEST;
        }
    }

}
