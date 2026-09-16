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

    interface Foo {
    }

    static class Impl implements Foo {
    }

    static <T extends @A Foo> void bounded(T value) {
    }

    void use(Object o) {
        String @A [] names = new String @A [3];
        names[0] = "a";

        Outer.@A Inner inner = new Outer.Inner();

        String s = (@A String) o;

        bounded(new Impl());
    }

}
