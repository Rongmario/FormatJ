package zone.rong.formatj.api.rules;

import zone.rong.formatj.api.Option;
import zone.rong.formatj.api.StyleBuilder;

/** Column alignment of runs of related lines. */
public final class AlignmentRules {

    public static final Option<AlignmentPolicy> CONSECUTIVE_FIELDS =
            Option.ofEnum(
                    "alignment.consecutive-fields",
                    AlignmentPolicy.NONE,
                    "Align the names of consecutive field declarations");

    public static final Option<AlignmentPolicy> CONSECUTIVE_VARIABLES =
            Option.ofEnum(
                    "alignment.consecutive-variables",
                    AlignmentPolicy.NONE,
                    "Align the names of consecutive local declarations");

    public static final Option<AlignmentPolicy> CONSECUTIVE_ASSIGNMENTS =
            Option.ofEnum(
                    "alignment.consecutive-assignments",
                    AlignmentPolicy.NONE,
                    "Align the = of consecutive assignments");

    public static final Option<AlignmentPolicy> METHOD_CHAINS =
            Option.ofEnum("alignment.method-chains", AlignmentPolicy.NONE, "Align the dots of a wrapped method chain");

    public static final Option<AlignmentPolicy> ANNOTATION_VALUES =
            Option.ofEnum(
                    "alignment.annotation-values",
                    AlignmentPolicy.NONE,
                    "Align the values of an annotation's elements");

    public static final Option<AlignmentPolicy> SWITCH_ARROWS =
            Option.ofEnum(
                    "alignment.switch-arrows",
                    AlignmentPolicy.NONE,
                    "Align the arrows of a switch's case labels");

    public static final Option<AlignmentPolicy> TERNARY_BRANCHES =
            Option.ofEnum(
                    "alignment.ternary-branches",
                    AlignmentPolicy.ALIGN_WHEN_MULTILINE,
                    "Align the branches of a wrapped conditional");

    public static final Option<AlignmentPolicy> TRAILING_COMMENTS =
            Option.ofEnum(
                    "alignment.trailing-comments",
                    AlignmentPolicy.NONE,
                    "Align comments trailing consecutive lines");

    private AlignmentRules() {}

    /** Fluent view of the {@code alignment.*} rules. */
    public static final class Builder {

        private final StyleBuilder style;

        public Builder(StyleBuilder style) {
            this.style = style;
        }

        public Builder consecutiveFields(AlignmentPolicy value) {
            style.set(CONSECUTIVE_FIELDS, value);
            return this;
        }

        public Builder consecutiveVariables(AlignmentPolicy value) {
            style.set(CONSECUTIVE_VARIABLES, value);
            return this;
        }

        public Builder consecutiveAssignments(AlignmentPolicy value) {
            style.set(CONSECUTIVE_ASSIGNMENTS, value);
            return this;
        }

        public Builder methodChains(AlignmentPolicy value) {
            style.set(METHOD_CHAINS, value);
            return this;
        }

        public Builder annotationValues(AlignmentPolicy value) {
            style.set(ANNOTATION_VALUES, value);
            return this;
        }

        public Builder switchArrows(AlignmentPolicy value) {
            style.set(SWITCH_ARROWS, value);
            return this;
        }

        public Builder ternaryBranches(AlignmentPolicy value) {
            style.set(TERNARY_BRANCHES, value);
            return this;
        }

        public Builder trailingComments(AlignmentPolicy value) {
            style.set(TRAILING_COMMENTS, value);
            return this;
        }

    }

}
