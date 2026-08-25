package zone.rong.formatj.api;

/**
 * The Java release whose syntax the parser accepts.
 *
 * <p>This is independent of the release FormatJ itself runs on: a formatter running on Java 25 can
 * be told to parse a file as Java 17 so that, say, {@code sealed} is still a contextual keyword.
 */
public enum LanguageLevel {

    JAVA_17(17),
    JAVA_18(18),
    JAVA_19(19),
    JAVA_20(20),
    JAVA_21(21),
    JAVA_22(22),
    JAVA_23(23),
    JAVA_24(24),
    JAVA_25(25)
    ;

    /** The newest release FormatJ knows about. */
    public static final LanguageLevel LATEST = JAVA_25;

    private final int release;

    LanguageLevel(int release) {
        this.release = release;
    }

    /** The release number, e.g. {@code 25}. */
    public int release() {
        return release;
    }

    /** Whether this level is at least {@code other}. */
    public boolean isAtLeast(LanguageLevel other) {
        return release >= other.release;
    }

    /** Looks up a level by release number, e.g. {@code 21} or {@code "21"}. */
    public static LanguageLevel ofRelease(int release) {
        for (LanguageLevel level : values()) {
            if (level.release == release) {
                return level;
            }
        }
        throw new IllegalArgumentException("Unsupported Java release: " + release);
    }

}
