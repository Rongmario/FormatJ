package zone.rong.formatj.api.rules;

import zone.rong.formatj.api.Inheritable;
import zone.rong.formatj.api.Option;
import zone.rong.formatj.api.StyleBuilder;

/** Layout rules for module declarations and directives. */
public final class ModuleRules {

    public static final Option<Inheritable<BracePlacement>> BRACE_PLACEMENT = Option.ofInheritableEnum(
        "module.brace-placement",
        BracePlacement.class,
        "Opening brace position for a module declaration, or inherit from braces.class-placement"
    );

    public static final Option<Inheritable<EmptyBodyStyle>> EMPTY_BODY = Option.ofInheritableEnum(
        "module.empty-body",
        EmptyBodyStyle.class,
        "Rendering of an empty module body, or inherit from braces.empty-class-body"
    );

    public static final Option<Inheritable<Integer>> BLANK_LINES_AFTER_OPENING_BRACE = Option.ofInheritableInt(
        "module.blank-lines-after-opening-brace",
        "Blank lines after a module's opening brace, or inherit from the class-body rule"
    );

    public static final Option<Inheritable<Integer>> BLANK_LINES_BEFORE_CLOSING_BRACE = Option.ofInheritableInt(
        "module.blank-lines-before-closing-brace",
        "Blank lines before a module's closing brace, or inherit from the class-body rule"
    );

    public static final Option<Integer> BLANK_LINES_BETWEEN_DIRECTIVE_GROUPS = Option.ofInt(
        "module.blank-lines-between-directive-groups",
        0,
        "Blank lines between adjacent groups of different module directive types"
    );

    public static final Option<WrapPolicy> EXPORTS_OPENS_TARGET_LIST_WRAPPING = Option.ofEnum(
        "module.exports-opens-target-list-wrapping",
        WrapPolicy.NEVER,
        "Wrapping of target module lists in exports and opens directives"
    );

    public static final Option<WrapPolicy> PROVIDES_IMPLEMENTATION_LIST_WRAPPING = Option.ofEnum(
        "module.provides-implementation-list-wrapping",
        WrapPolicy.NEVER,
        "Wrapping of implementation lists in provides directives"
    );

    private ModuleRules() { }

    /** Fluent view of the {@code module.*} rules. */
    public static final class Builder {

        private final StyleBuilder style;

        public Builder(StyleBuilder style) {
            this.style = style;
        }

        public Builder bracePlacement(BracePlacement value) {
            style.set(BRACE_PLACEMENT, Inheritable.of(value));
            return this;
        }

        public Builder inheritBracePlacement() {
            style.set(BRACE_PLACEMENT, Inheritable.inherit());
            return this;
        }

        public Builder emptyBody(EmptyBodyStyle value) {
            style.set(EMPTY_BODY, Inheritable.of(value));
            return this;
        }

        public Builder inheritEmptyBody() {
            style.set(EMPTY_BODY, Inheritable.inherit());
            return this;
        }

        public Builder blankLinesAfterOpeningBrace(int value) {
            style.set(BLANK_LINES_AFTER_OPENING_BRACE, Inheritable.of(value));
            return this;
        }

        public Builder inheritBlankLinesAfterOpeningBrace() {
            style.set(BLANK_LINES_AFTER_OPENING_BRACE, Inheritable.inherit());
            return this;
        }

        public Builder blankLinesBeforeClosingBrace(int value) {
            style.set(BLANK_LINES_BEFORE_CLOSING_BRACE, Inheritable.of(value));
            return this;
        }

        public Builder inheritBlankLinesBeforeClosingBrace() {
            style.set(BLANK_LINES_BEFORE_CLOSING_BRACE, Inheritable.inherit());
            return this;
        }

        public Builder blankLinesBetweenDirectiveGroups(int value) {
            style.set(BLANK_LINES_BETWEEN_DIRECTIVE_GROUPS, value);
            return this;
        }

        public Builder exportsOpensTargetListWrapping(WrapPolicy value) {
            style.set(EXPORTS_OPENS_TARGET_LIST_WRAPPING, value);
            return this;
        }

        public Builder providesImplementationListWrapping(WrapPolicy value) {
            style.set(PROVIDES_IMPLEMENTATION_LIST_WRAPPING, value);
            return this;
        }

    }

}
