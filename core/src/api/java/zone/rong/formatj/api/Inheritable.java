package zone.rong.formatj.api;

import java.util.Objects;

/** A rule value that either overrides an older rule or inherits from it. */
public record Inheritable<T>(T override) {

    /** Uses the inherited rule. */
    public static <T> Inheritable<T> inherit() {
        return new Inheritable<>(null);
    }

    /** Uses a concrete override. */
    public static <T> Inheritable<T> of(T value) {
        return new Inheritable<>(Objects.requireNonNull(value, "value"));
    }

    /** Whether this value defers to the rule it inherits from. */
    public boolean inherits() {
        return override == null;
    }

    /** The override when present, otherwise {@code inherited}. */
    public T orElse(T inherited) {
        return inherits() ? Objects.requireNonNull(inherited, "inherited") : override;
    }

}
