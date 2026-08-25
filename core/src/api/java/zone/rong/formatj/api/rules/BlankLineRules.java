package zone.rong.formatj.api.rules;

import zone.rong.formatj.api.Option;
import zone.rong.formatj.api.StyleBuilder;

/** Minimum blank lines FormatJ inserts around declarations and blocks. */
public final class BlankLineRules {

    public static final Option<Integer> MAX_CONSECUTIVE =
            Option.ofInt("blank-lines.max-consecutive", 1, "Most consecutive blank lines kept anywhere in a body");

    public static final Option<Integer> AFTER_PACKAGE =
            Option.ofInt("blank-lines.after-package", 1, "Blank lines after the package declaration");

    public static final Option<Integer> AFTER_IMPORTS =
            Option.ofInt("blank-lines.after-imports", 1, "Blank lines after the last import");

    public static final Option<Integer> BEFORE_CLASS =
            Option.ofInt("blank-lines.before-class", 1, "Blank lines before a nested type declaration");

    public static final Option<Integer> BEFORE_METHOD =
            Option.ofInt("blank-lines.before-method", 1, "Blank lines before a method or constructor");

    public static final Option<Integer> BEFORE_FIELD =
            Option.ofInt("blank-lines.before-field", 0, "Blank lines before a field declaration");

    public static final Option<Integer> AFTER_CLASS_OPENING_BRACE =
            Option.ofInt("blank-lines.after-class-opening-brace", 1, "Blank lines just inside a type body");

    public static final Option<Integer> BEFORE_CLASS_CLOSING_BRACE =
            Option.ofInt("blank-lines.before-class-closing-brace", 1, "Blank lines just before a type body closes");

    public static final Option<Integer> AROUND_INITIALIZER_BLOCK =
            Option.ofInt(
                    "blank-lines.around-initializer-block",
                    1,
                    "Blank lines around an instance or static initializer");

    public static final Option<Integer> BEFORE_RECORD_COMPACT_CONSTRUCTOR =
            Option.ofInt(
                    "blank-lines.before-record-compact-constructor",
                    1,
                    "Blank lines before a compact canonical constructor");

    public static final Option<Integer> AFTER_ENUM_CONSTANTS =
            Option.ofInt(
                    "blank-lines.after-enum-constants",
                    0,
                    "Blank lines between the constants and the body of an enum");

    public static final Option<Integer> BEFORE_FIRST_ENUM_CONSTANT =
            Option.ofInt(
                    "blank-lines.before-first-enum-constant",
                    1,
                    "Blank lines between an enum's brace and its first constant");

    public static final Option<Integer> BETWEEN_SWITCH_CASES =
            Option.ofInt("blank-lines.between-switch-cases", 0, "Blank lines between the cases of a switch");

    private BlankLineRules() { }

    /** Fluent view of the {@code blank-lines.*} rules. */
    public static final class Builder {

        private final StyleBuilder style;

        public Builder(StyleBuilder style) {
            this.style = style;
        }

        public Builder maxConsecutive(int value) {
            style.set(MAX_CONSECUTIVE, value);
            return this;
        }

        public Builder afterPackage(int value) {
            style.set(AFTER_PACKAGE, value);
            return this;
        }

        public Builder afterImports(int value) {
            style.set(AFTER_IMPORTS, value);
            return this;
        }

        public Builder beforeClass(int value) {
            style.set(BEFORE_CLASS, value);
            return this;
        }

        public Builder beforeMethod(int value) {
            style.set(BEFORE_METHOD, value);
            return this;
        }

        public Builder beforeField(int value) {
            style.set(BEFORE_FIELD, value);
            return this;
        }

        public Builder afterClassOpeningBrace(int value) {
            style.set(AFTER_CLASS_OPENING_BRACE, value);
            return this;
        }

        public Builder beforeClassClosingBrace(int value) {
            style.set(BEFORE_CLASS_CLOSING_BRACE, value);
            return this;
        }

        public Builder aroundInitializerBlock(int value) {
            style.set(AROUND_INITIALIZER_BLOCK, value);
            return this;
        }

        public Builder beforeRecordCompactConstructor(int value) {
            style.set(BEFORE_RECORD_COMPACT_CONSTRUCTOR, value);
            return this;
        }

        public Builder afterEnumConstants(int value) {
            style.set(AFTER_ENUM_CONSTANTS, value);
            return this;
        }

        public Builder beforeFirstEnumConstant(int value) {
            style.set(BEFORE_FIRST_ENUM_CONSTANT, value);
            return this;
        }

        public Builder betweenSwitchCases(int value) {
            style.set(BETWEEN_SWITCH_CASES, value);
            return this;
        }

    }

}
