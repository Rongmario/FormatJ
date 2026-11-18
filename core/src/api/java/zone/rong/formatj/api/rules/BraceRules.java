package zone.rong.formatj.api.rules;

import zone.rong.formatj.api.Option;

/** Brace placement, and which bodies are required to have braces at all. */
public final class BraceRules {

    public static final Option<BracePlacement> CLASS_PLACEMENT = Option.ofEnum(
        "braces.class-placement",
        BracePlacement.END_OF_LINE,
        "Opening brace position for a type declaration"
    );

    public static final Option<BracePlacement> METHOD_PLACEMENT = Option.ofEnum(
        "braces.method-placement",
        BracePlacement.END_OF_LINE,
        "Opening brace position for a method or constructor"
    );

    public static final Option<BracePlacement> CONTROL_PLACEMENT = Option.ofEnum(
        "braces.control-placement",
        BracePlacement.END_OF_LINE,
        "Opening brace position for a control statement"
    );

    public static final Option<BracePlacement> LAMBDA_PLACEMENT = Option.ofEnum(
        "braces.lambda-placement",
        BracePlacement.END_OF_LINE,
        "Opening brace position for a lambda block body"
    );

    public static final Option<BracePlacement> MODULE_PLACEMENT = Option.ofEnum(
        "braces.module-placement",
        CLASS_PLACEMENT,
        "Opening brace position for a module declaration"
    );

    public static final Option<BracePolicy> IF_ELSE = Option.ofEnum(
        "braces.if-else",
        BracePolicy.ALWAYS,
        "Braces around if and else bodies"
    );

    public static final Option<BracePolicy> FOR_LOOP = Option.ofEnum(
        "braces.for-loop",
        BracePolicy.ALWAYS,
        "Braces around for and enhanced-for bodies"
    );

    public static final Option<BracePolicy> WHILE_LOOP = Option.ofEnum(
        "braces.while-loop",
        BracePolicy.ALWAYS,
        "Braces around while and do-while bodies"
    );

    public static final Option<BracePolicy> LAMBDA_BODY = Option.ofEnum(
        "braces.lambda-body",
        BracePolicy.WHEN_MULTI_STATEMENT,
        "Braces around a lambda body"
    );

    public static final Option<BracePolicy> ARROW_CASE_BODY = Option.ofEnum(
        "braces.arrow-case-body",
        BracePolicy.WHEN_MULTI_STATEMENT,
        "Braces around the body of an arrow case"
    );

    public static final Option<Boolean> ELSE_ON_NEW_LINE = Option.ofBoolean(
        "braces.else-on-new-line",
        false,
        "Put else on the line after the closing brace"
    );

    public static final Option<Boolean> CATCH_ON_NEW_LINE = Option.ofBoolean(
        "braces.catch-on-new-line",
        false,
        "Put catch on the line after the closing brace"
    );

    public static final Option<Boolean> FINALLY_ON_NEW_LINE = Option.ofBoolean(
        "braces.finally-on-new-line",
        false,
        "Put finally on the line after the closing brace"
    );

    public static final Option<EmptyBodyStyle> EMPTY_CLASS_BODY = Option.ofEnum(
        "braces.empty-class-body",
        EmptyBodyStyle.SPACED,
        "Rendering of an empty type body"
    );

    public static final Option<EmptyBodyStyle> EMPTY_RECORD_BODY = Option.ofEnum(
        "braces.empty-record-body",
        EMPTY_CLASS_BODY,
        "Rendering of an empty record body"
    );

    public static final Option<EmptyBodyStyle> EMPTY_MODULE_BODY = Option.ofEnum(
        "braces.empty-module-body",
        EMPTY_CLASS_BODY,
        "Rendering of an empty module body"
    );

    public static final Option<EmptyBodyStyle> EMPTY_METHOD_BODY = Option.ofEnum(
        "braces.empty-method-body",
        EmptyBodyStyle.SPACED,
        "Rendering of an empty method body"
    );

    public static final Option<EmptyBodyStyle> EMPTY_CONTROL_BODY = Option.ofEnum(
        "braces.empty-control-body",
        EmptyBodyStyle.EXPANDED,
        "Rendering of an empty control statement body"
    );

    private BraceRules() { }

}
