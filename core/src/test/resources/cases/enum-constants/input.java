package sample;

enum Color { RED, GREEN, BLUE }

enum Shade { RED, GREEN, BLUE; }

enum Named { A(1), B(2); private final int n; Named(int n) { this.n = n; } }

public enum LanguageLevel {
    JAVA_17(17),
    JAVA_25(25)
    ;

    public static final LanguageLevel LATEST = JAVA_25;

    private final int release;

    LanguageLevel(int release) {
        this.release = release;
    }
}
