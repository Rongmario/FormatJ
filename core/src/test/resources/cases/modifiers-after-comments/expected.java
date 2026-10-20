/** The Javadoc stays in front of the class. */
public final class A {

    @Deprecated protected static final int MARKED = 1;
    // A line comment stays in front of the field.
    private static final long BIG = 1L;

    /* A block comment stays in front of the method. */
    public static synchronized void run() { }

}
