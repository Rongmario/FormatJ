package zone.rong.formatj.api.rules;

import zone.rong.formatj.api.Option;
import zone.rong.formatj.api.StyleBuilder;

/** Semicolons that carry no meaning. */
public final class SemicolonRules {

    public static final Option<Boolean> REMOVE_REDUNDANT = Option.ofBoolean(
        "semicolons.remove-redundant",
        true,
        "Remove stray semicolons between members and after a top-level type"
    );

    private SemicolonRules() { }

    /** Fluent view of the {@code semicolons.*} rules. */
    public static final class Builder {

        private final StyleBuilder style;

        public Builder(StyleBuilder style) {
            this.style = style;
        }

        public Builder removeRedundant(boolean value) {
            style.set(REMOVE_REDUNDANT, value);
            return this;
        }

    }

}
