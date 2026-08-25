package zone.rong.formatj.api.rules;

import zone.rong.formatj.api.Option;
import zone.rong.formatj.api.StyleBuilder;

/** Whole-file concerns: encoding, line terminators and the last line. */
public final class FileRules {

    public static final Option<LineEnding> LINE_ENDING =
            Option.ofEnum("file.line-ending", LineEnding.PRESERVE, "Line terminator written to formatted output");

    public static final Option<Boolean> FINAL_NEWLINE =
            Option.ofBoolean("file.final-newline", true, "End every file with a line terminator");

    public static final Option<Boolean> TRIM_TRAILING_WHITESPACE =
            Option.ofBoolean("file.trim-trailing-whitespace", true, "Strip whitespace at the end of every line");

    public static final Option<String> CHARSET =
            Option.ofString("file.charset", "UTF-8", "Charset used to read and write source files");

    public static final Option<Integer> TAB_WIDTH =
            Option.ofInt("file.tab-width", 4, "Columns a tab character occupies when measuring line length");

    private FileRules() {}

    /** Fluent view of the {@code file.*} rules. */
    public static final class Builder {

        private final StyleBuilder style;

        public Builder(StyleBuilder style) {
            this.style = style;
        }

        public Builder lineEnding(LineEnding value) {
            style.set(LINE_ENDING, value);
            return this;
        }

        public Builder finalNewline(boolean value) {
            style.set(FINAL_NEWLINE, value);
            return this;
        }

        public Builder trimTrailingWhitespace(boolean value) {
            style.set(TRIM_TRAILING_WHITESPACE, value);
            return this;
        }

        public Builder charset(String value) {
            style.set(CHARSET, value);
            return this;
        }

        public Builder tabWidth(int value) {
            style.set(TAB_WIDTH, value);
            return this;
        }

    }

}
