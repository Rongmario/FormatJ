package zone.rong.formatj.api.rules;

import zone.rong.formatj.api.Option;

/** Minimum blank lines FormatJ inserts around declarations and blocks. */
public final class BlankLineRules {

    public static final Option<Integer> MAX_CONSECUTIVE = Option.ofInt(
        "blank-lines.max-consecutive",
        1,
        "Most consecutive blank lines kept anywhere in a body"
    );

    public static final Option<Integer> AFTER_PACKAGE = Option.ofInt(
        "blank-lines.after-package",
        1,
        "Blank lines after the package declaration"
    );

    public static final Option<Integer> AFTER_IMPORTS = Option.ofInt(
        "blank-lines.after-imports",
        1,
        "Blank lines after the last import"
    );

    public static final Option<Integer> BEFORE_CLASS = Option.ofInt(
        "blank-lines.before-class",
        1,
        "Blank lines before a nested type declaration"
    );

    public static final Option<Integer> BEFORE_METHOD = Option.ofInt(
        "blank-lines.before-method",
        1,
        "Blank lines before a method or constructor"
    );

    public static final Option<Integer> BEFORE_COMPACT_CONSTRUCTOR = Option.ofInt(
        "blank-lines.before-compact-constructor",
        BEFORE_METHOD,
        "Blank lines before a compact canonical constructor"
    );

    public static final Option<Integer> INSIDE_COMPACT_CONSTRUCTOR = Option.ofInt(
        "blank-lines.inside-compact-constructor",
        0,
        "Blank lines just inside the braces of a compact canonical constructor"
    );

    public static final Option<Integer> BEFORE_FIELD = Option.ofInt(
        "blank-lines.before-field",
        0,
        "Blank lines before a field declaration"
    );

    public static final Option<Integer> AFTER_CLASS_OPENING_BRACE = Option.ofInt(
        "blank-lines.after-class-opening-brace",
        1,
        "Blank lines just inside a type body"
    );

    public static final Option<Integer> BEFORE_CLASS_CLOSING_BRACE = Option.ofInt(
        "blank-lines.before-class-closing-brace",
        1,
        "Blank lines just before a type body closes"
    );

    public static final Option<Integer> AFTER_MODULE_OPENING_BRACE = Option.ofInt(
        "blank-lines.after-module-opening-brace",
        AFTER_CLASS_OPENING_BRACE,
        "Blank lines just inside a module body"
    );

    public static final Option<Integer> BEFORE_MODULE_CLOSING_BRACE = Option.ofInt(
        "blank-lines.before-module-closing-brace",
        BEFORE_CLASS_CLOSING_BRACE,
        "Blank lines just before a module body closes"
    );

    public static final Option<Boolean> STRIP_AT_BRACE_EDGES = Option.ofBoolean(
        "blank-lines.strip-at-brace-edges",
        false,
        "Drop blank lines the author left just inside the braces of a body"
    );

    public static final Option<Integer> AROUND_INITIALIZER_BLOCK = Option.ofInt(
        "blank-lines.around-initializer-block",
        1,
        "Blank lines around an instance or static initializer"
    );

    public static final Option<Integer> AFTER_ENUM_CONSTANTS = Option.ofInt(
        "blank-lines.after-enum-constants",
        0,
        "Blank lines between the constants and the body of an enum"
    );

    public static final Option<Integer> BEFORE_FIRST_ENUM_CONSTANT = Option.ofInt(
        "blank-lines.before-first-enum-constant",
        1,
        "Blank lines between an enum's brace and its first constant"
    );

    public static final Option<Integer> BETWEEN_MEMBER_GROUPS = Option.ofInt(
        "blank-lines.between-member-groups",
        1,
        "Blank lines between neighbouring members of different kinds"
    );

    public static final Option<Integer> BETWEEN_MODULE_DIRECTIVE_GROUPS = Option.ofInt(
        "blank-lines.between-module-directive-groups",
        0,
        "Blank lines between adjacent groups of different module directive types"
    );

    public static final Option<Integer> BETWEEN_SWITCH_CASES = Option.ofInt(
        "blank-lines.between-switch-cases",
        0,
        "Blank lines between the cases of a switch"
    );

    private BlankLineRules() { }

}
