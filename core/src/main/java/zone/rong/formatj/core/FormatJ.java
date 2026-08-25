package zone.rong.formatj.core;

import zone.rong.formatj.api.Formatter;
import zone.rong.formatj.api.FormatterBuilder;
import zone.rong.formatj.api.LanguageLevel;
import zone.rong.formatj.api.Style;
import zone.rong.formatj.core.pipeline.DefaultFormatter;
import java.util.Objects;

/**
 * Entry point to the formatter.
 *
 * <pre>{@code
 * Formatter formatter = FormatJ.newFormatter()
 *         .style(Style.preset(Preset.GOOGLE).build())
 *         .build();
 * String formatted = formatter.format(source);
 * }</pre>
 */
public final class FormatJ {

    private FormatJ() {}

    /** A builder for a formatter using default rules and the newest language level. */
    public static FormatterBuilder newFormatter() {
        return new DefaultFormatterBuilder();
    }

    /** A formatter with default rules, for the common case of not configuring anything. */
    public static Formatter defaultFormatter() {
        return newFormatter().build();
    }

    private static final class DefaultFormatterBuilder implements FormatterBuilder {

        private Style style = Style.defaults();
        private LanguageLevel languageLevel = LanguageLevel.LATEST;
        private boolean previewFeatures;
        private boolean verify = true;

        @Override
        public FormatterBuilder style(Style style) {
            this.style = Objects.requireNonNull(style, "style");
            return this;
        }

        @Override
        public FormatterBuilder languageLevel(LanguageLevel languageLevel) {
            this.languageLevel = Objects.requireNonNull(languageLevel, "languageLevel");
            return this;
        }

        @Override
        public FormatterBuilder previewFeatures(boolean previewFeatures) {
            this.previewFeatures = previewFeatures;
            return this;
        }

        @Override
        public FormatterBuilder verify(boolean verify) {
            this.verify = verify;
            return this;
        }

        @Override
        public Formatter build() {
            return new DefaultFormatter(style, languageLevel, previewFeatures, verify);
        }

    }

}
