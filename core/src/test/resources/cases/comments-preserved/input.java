package sample;

/** A documented class. */
public class Commented {
    // a leading comment
    private int count; // a trailing comment
    private Map<String, Integer /* units, not ids */> lookup = new HashMap<>();

    /*
     * A block comment.
     */
    void run() {
        // inside
    }

    void empty() {
        // nothing to do yet
    }
}
