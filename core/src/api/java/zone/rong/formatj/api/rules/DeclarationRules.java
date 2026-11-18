package zone.rong.formatj.api.rules;

import zone.rong.formatj.api.Option;

/** Which tokens a declaration is written with: variables per declaration, brackets, permits order, semicolons. */
public final class DeclarationRules {

    public static final Option<Boolean> SPLIT_VARIABLES = Option.ofBoolean(
        "declarations.split-variables",
        false,
        "Split a declaration of several variables into one declaration per variable, repeating the modifiers, " +
            "annotations and type"
    );

    public static final Option<BracketStyle> ARRAY_BRACKETS = Option.ofEnum(
        "declarations.array-brackets",
        BracketStyle.JAVA,
        "Placement of array brackets on declared variables"
    );

    public static final Option<SortOrder> PERMITS_ORDER = Option.ofEnum(
        "declarations.permits-order",
        SortOrder.PRESERVE,
        "Sort order of the types in a permits clause"
    );

    public static final Option<Boolean> REMOVE_REDUNDANT_SEMICOLONS = Option.ofBoolean(
        "declarations.remove-redundant-semicolons",
        true,
        "Remove stray semicolons between members and after a top-level type"
    );

    /**
     * Whether a no-argument enum must end its constant list with {@code ;}.
     *
     * <p>The language already requires the semicolon when any constant has arguments, or when
     * fields, methods or nested types follow. This option only governs the optional case: a
     * no-argument constant list with nothing after it.
     */
    public static final Option<Boolean> REQUIRE_ENUM_SEMICOLON = Option.ofBoolean(
        "declarations.require-enum-semicolon",
        false,
        "Always write a semicolon after the last no-argument enum constant"
    );

    private DeclarationRules() { }

}
