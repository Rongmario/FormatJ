package zone.rong.formatj.api.rules;

import zone.rong.formatj.api.Option;
import zone.rong.formatj.api.StyleBuilder;

/** How several variables in one declaration are laid out. */
public final class DeclarationRules {

    public static final Option<Boolean> SPLIT_VARIABLES = Option.ofBoolean(
        "declarations.split-variables",
        false,
        "Split a declaration of several variables into one declaration per variable, repeating the modifiers, annotations and type"
    );

    private DeclarationRules() { }

    /** Fluent view of the {@code declarations.*} rules. */
    public static final class Builder {

        private final StyleBuilder style;

        public Builder(StyleBuilder style) {
            this.style = style;
        }

        public Builder splitVariables(boolean value) {
            style.set(SPLIT_VARIABLES, value);
            return this;
        }

    }

}
