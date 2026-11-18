package zone.rong.formatj.api.rules;

import java.nio.charset.Charset;

import zone.rong.formatj.api.Option;
import zone.rong.formatj.api.Style;

/** Whole-file concerns: encoding, line terminators and the last line. */
public final class FileRules {

    public static final Option<LineEnding> LINE_ENDING = Option.ofEnum(
        "file.line-ending",
        LineEnding.LF,
        "Line terminator written to formatted output"
    );

    public static final Option<Boolean> FINAL_NEWLINE = Option.ofBoolean(
        "file.final-newline",
        true,
        "End every file with a line terminator"
    );

    public static final Option<Boolean> TRIM_TRAILING_WHITESPACE = Option.ofBoolean(
        "file.trim-trailing-whitespace",
        true,
        "Strip whitespace at the end of every line"
    );

    public static final Option<String> CHARSET = Option.ofString(
        "file.charset",
        "UTF-8",
        "Charset used to read and write source files"
    );

    public static final Option<Integer> TAB_WIDTH = Option.ofInt(
        "file.tab-width",
        4,
        1,
        "Columns a tab character occupies when measuring line length"
    );

    private FileRules() { }

    /** The charset {@link #CHARSET} names, for reading and writing source files. */
    public static Charset charset(Style style) {
        String name = style.get(CHARSET);
        try {
            return Charset.forName(name);
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException(CHARSET.key() + " names an unknown charset '" + name + "'", e);
        }
    }

}
