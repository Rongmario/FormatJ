import java.lang.annotation.ElementType;
import java.lang.annotation.Target;

class TypeAnnotations {

    @Target(ElementType.TYPE_USE)
    @interface A {
    }

    static class Outer {

        static class Inner {
        }

    }

    static class Enclosing {

        class Inner {
        }

    }

    interface Foo {
    }

    static class Impl implements Foo {
    }

    static <T extends @A Foo> void bounded(T value) {
    }

    String field @A [];

    String returns() @A [] {
        return null;
    }

    void parameters(String names @A [], String @A ... values) {
    }

    void use(Object o) {
        String @A [] names = new String @A [3];
        String local @A [] = null;
        String annotated = new @A String();
        int[] numbers = new @A int[1];
        Object nested = new Enclosing().new @A Inner();
        names[0] = "a";

        Outer.@A Inner inner = new Outer.Inner();

        String s = (@A String) o;

        bounded(new Impl());
    }

}
