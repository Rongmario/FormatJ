class A {

    private int count;
    protected int shared;

    static int counter;

    public final int fixed = 3;

    {
        count = 4;
    }

    public static final int CONSTANT = 1;

    static {
        counter = 2;
    }

    private void close() { }

    public void open() { }

}
