import java.io.IOException;
import java.io.Serializable;
import java.lang.annotation.ElementType;
import java.lang.annotation.Target;
import java.util.function.Function;

@Target(ElementType.TYPE_USE)
@interface TypeUse { }

class SeparatedOperatorForms<T extends @TypeUse Runnable & @TypeUse Serializable> {

    Function<String, String> reference = SeparatedOperatorForms::<String>identity;

    static <U> U identity(U value) {
        return value;
    }

    boolean matches(Object value) {
        return value instanceof String text && !text.isEmpty();
    }

    Runnable cast(Object value) {
        return (@TypeUse Runnable & @TypeUse Serializable) value;
    }

    void multicatch() {
        try {
            fail();
        } catch (@TypeUse IOException | @TypeUse IllegalArgumentException failure) {
            failure.printStackTrace();
        }
    }

    void fail() throws IOException {
        throw new IOException();
    }

}
