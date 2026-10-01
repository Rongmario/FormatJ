package zone.rong.formatj.api.rules;

import zone.rong.formatj.api.Option;
import zone.rong.formatj.api.StyleBuilder;

/**
 * Declaration modifier ordering.
 *
 * <p>The canonical orders are:
 *
 * <ul>
 *   <li>classes: {@code public protected private abstract static sealed non-sealed final strictfp}
 *   <li>interfaces: {@code public protected private abstract static sealed non-sealed strictfp}
 *   <li>enums: {@code public protected private static strictfp}
 *   <li>records: {@code public protected private static final strictfp}
 *   <li>annotation types: {@code public protected private abstract static strictfp}
 *   <li>fields: {@code public protected private static final transient volatile}
 *   <li>methods and annotation elements: {@code public protected private abstract default static final synchronized native strictfp}
 *   <li>constructors: {@code public protected private}
 * </ul>
 *
 * <p>Annotations keep their source order and position among modifier slots.
 */
public final class ModifierRules {

    public static final Option<ModifierOrder> ORDER = Option.ofEnum(
        "modifiers.order",
        ModifierOrder.PRESERVE,
        "Ordering of declaration modifiers"
    );

    public static final Option<Boolean> REMOVE_REDUNDANT = Option.ofBoolean(
        "modifiers.remove-redundant",
        false,
        "Remove modifiers the language already implies"
    );

    private ModifierRules() { }

    /** Fluent view of the {@code modifiers.*} rules. */
    public static final class Builder {

        private final StyleBuilder style;

        public Builder(StyleBuilder style) {
            this.style = style;
        }

        public Builder order(ModifierOrder value) {
            style.set(ORDER, value);
            return this;
        }

        public Builder removeRedundant(boolean value) {
            style.set(REMOVE_REDUNDANT, value);
            return this;
        }

    }

}
