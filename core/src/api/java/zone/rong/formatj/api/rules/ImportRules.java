package zone.rong.formatj.api.rules;

import zone.rong.formatj.api.Option;
import zone.rong.formatj.api.StyleBuilder;
import java.util.List;

/** Import ordering, grouping and wildcard policy. */
public final class ImportRules {

    public static final Option<List<String>> GROUPS =
            Option.ofStringList(
                    "imports.groups",
                    List.of("java", "javax", "*"),
                    "Package prefixes forming import groups, in order; * is the catch-all");

    public static final Option<SortOrder> ORDER =
            Option.ofEnum(
                    "imports.order",
                    SortOrder.PRESERVE,
                    "Sort order applied within an import group; preserve leaves the whole run alone, "
                            + "which also switches off grouping, static placement and module ordering");

    public static final Option<StaticImportPlacement> STATIC_PLACEMENT =
            Option.ofEnum(
                    "imports.static-placement",
                    StaticImportPlacement.LAST,
                    "Where static imports sit relative to ordinary ones");

    public static final Option<Boolean> BLANK_LINE_BETWEEN_GROUPS =
            Option.ofBoolean("imports.blank-line-between-groups", true, "Separate import groups with a blank line");

    public static final Option<Boolean> REMOVE_UNUSED =
            Option.ofBoolean("imports.remove-unused", false, "Delete imports the file does not reference");

    public static final Option<Integer> CLASS_COUNT_TO_USE_WILDCARD =
            Option.ofInt(
                    "imports.class-count-to-use-wildcard",
                    0,
                    "Imports from one package before collapsing to a wildcard; 0 disables");

    public static final Option<Integer> STATIC_COUNT_TO_USE_WILDCARD =
            Option.ofInt(
                    "imports.static-count-to-use-wildcard",
                    0,
                    "Static imports from one type before collapsing to a wildcard; 0 disables");

    public static final Option<Boolean> KEEP_EXISTING_WILDCARDS =
            Option.ofBoolean("imports.keep-existing-wildcards", true, "Leave a wildcard import the author wrote alone");

    public static final Option<Boolean> MODULE_IMPORTS_FIRST =
            Option.ofBoolean("imports.module-imports-first", true, "Place module imports before every other import");

    private ImportRules() { }

    /** Fluent view of the {@code imports.*} rules. */
    public static final class Builder {

        private final StyleBuilder style;

        public Builder(StyleBuilder style) {
            this.style = style;
        }

        public Builder groups(List<String> value) {
            style.set(GROUPS, value);
            return this;
        }

        public Builder order(SortOrder value) {
            style.set(ORDER, value);
            return this;
        }

        public Builder staticPlacement(StaticImportPlacement value) {
            style.set(STATIC_PLACEMENT, value);
            return this;
        }

        public Builder blankLineBetweenGroups(boolean value) {
            style.set(BLANK_LINE_BETWEEN_GROUPS, value);
            return this;
        }

        public Builder removeUnused(boolean value) {
            style.set(REMOVE_UNUSED, value);
            return this;
        }

        public Builder classCountToUseWildcard(int value) {
            style.set(CLASS_COUNT_TO_USE_WILDCARD, value);
            return this;
        }

        public Builder staticCountToUseWildcard(int value) {
            style.set(STATIC_COUNT_TO_USE_WILDCARD, value);
            return this;
        }

        public Builder keepExistingWildcards(boolean value) {
            style.set(KEEP_EXISTING_WILDCARDS, value);
            return this;
        }

        public Builder moduleImportsFirst(boolean value) {
            style.set(MODULE_IMPORTS_FIRST, value);
            return this;
        }

    }

}
