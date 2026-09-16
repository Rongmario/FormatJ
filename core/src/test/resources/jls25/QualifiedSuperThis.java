class QualifiedSuperThis {

    static class Base {

        void greet() {
            System.out.println("base");
        }

    }

    static class Outer extends Base {

        @Override
        void greet() {
            System.out.println("outer");
        }

        class Inner {

            void report() {
                System.out.println(Outer.this);
                Outer.super.greet();
            }

        }

    }

    public static void main(String[] args) {
        new Outer().new Inner().report();
    }

}
