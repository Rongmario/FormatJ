class Documented {

    /**
     * Runs the thing, with a description long enough that it has to
     * be refilled to fit the margin.
     * <p>
     * The second paragraph, which the paragraph rule marks.
     *
     * @param first  the first one
     * @param second the second one, described at some length so that
     *         the description has to wrap
     * @return what came of it
     * @throws IllegalStateException when it will not
     */
    int run(int first, int second) {
        return first + second;
    }

    /** Left alone. */
    void single() { }

    /**
     * Holds a sample.
     * <p>
     * <pre>
     *     int x =   1;
     * </pre>
     */
    void sample() { }

}
