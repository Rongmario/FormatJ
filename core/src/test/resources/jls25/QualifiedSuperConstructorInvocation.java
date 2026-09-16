class QualifiedSuperConstructorInvocation {

    class Inner {

        Inner() {
            System.out.println("inner");
        }

    }

    static class Sub extends QualifiedSuperConstructorInvocation.Inner {

        Sub(QualifiedSuperConstructorInvocation outer) {
            outer.super();
        }

    }

    public static void main(String[] args) {
        new Sub(new QualifiedSuperConstructorInvocation());
    }

}
