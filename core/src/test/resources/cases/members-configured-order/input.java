class A {
    public static final int CONSTANT = 1;
    static int counter;
    static { counter = 2; }
    public final int fixed = 3;
    private int count;
    protected int shared;
    { count = 4; }
    public void open() { }
    private void close() { }
}
