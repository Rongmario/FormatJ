package zone.rong.formatj.api.rules;

import zone.rong.formatj.api.Option;
import zone.rong.formatj.api.StyleBuilder;

/** Where annotations sit relative to what they annotate. */
public final class AnnotationRules {

    public static final Option<AnnotationPlacement> DECLARATION_PLACEMENT =
            Option.ofEnum(
                    "annotations.declaration-placement",
                    AnnotationPlacement.PRESERVE,
                    "Placement of an annotation on a type, method or field declaration");

    public static final Option<AnnotationPlacement> PARAMETER_PLACEMENT =
            Option.ofEnum(
                    "annotations.parameter-placement",
                    AnnotationPlacement.SAME_LINE,
                    "Placement of an annotation on a parameter or local variable");

    public static final Option<Boolean> SINGLE_MARKER_INLINE =
            Option.ofBoolean(
                    "annotations.single-marker-inline",
                    false,
                    "Keep a lone marker annotation on the line of its declaration");

    private AnnotationRules() {}

    /** Fluent view of the {@code annotations.*} rules. */
    public static final class Builder {

        private final StyleBuilder style;

        public Builder(StyleBuilder style) {
            this.style = style;
        }

        public Builder declarationPlacement(AnnotationPlacement value) {
            style.set(DECLARATION_PLACEMENT, value);
            return this;
        }

        public Builder parameterPlacement(AnnotationPlacement value) {
            style.set(PARAMETER_PLACEMENT, value);
            return this;
        }

        public Builder singleMarkerInline(boolean value) {
            style.set(SINGLE_MARKER_INLINE, value);
            return this;
        }

    }

}
