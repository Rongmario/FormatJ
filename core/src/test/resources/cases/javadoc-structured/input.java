class Documented {

    /**
     * Runs the thing, with a description long enough that it has to be refilled to fit the margin.
     *
     * The second paragraph, which the paragraph rule marks.
     * @return what came of it
     * @param first the first one
     * @param second the second one, described at some length so that the description has to wrap
     * @throws IllegalStateException when it will not
     */
    int run(int first, int second) {
        return first + second;
    }

    /** Left alone. */
    void single() { }

    /**
     * Holds a sample.
     *
     * <pre>
     *     int x =   1;
     * </pre>
     */
    void sample() { }

}
