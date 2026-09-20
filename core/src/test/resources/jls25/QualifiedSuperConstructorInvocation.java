class QualifiedSuperConstructorInvocation {

    class Inner {

        Inner() {
            System.out.println("inner");
        }

        Inner(int value) {
        }

        <T> Inner(T value) {
        }

    }

    static class Sub extends QualifiedSuperConstructorInvocation.Inner {

        Sub(QualifiedSuperConstructorInvocation outer) {
            outer.super();
        }

        Sub(QualifiedSuperConstructorInvocation outer, int value) {
            outer.super(value);
        }

        Sub(QualifiedSuperConstructorInvocation outer, String value) {
            outer.<String>super(value);
        }

    }

    public static void main(String[] args) {
        new Sub(new QualifiedSuperConstructorInvocation());
    }

}
