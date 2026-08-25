package zone.rong.formatj.api.rules;

import zone.rong.formatj.api.Option;
import zone.rong.formatj.api.StyleBuilder;

/** Sealed types and their permits clauses. */
public final class SealedRules {

    public static final Option<WrapPolicy> PERMITS_WRAPPING =
            Option.ofEnum("sealed.permits-wrapping", WrapPolicy.WRAP_IF_LONG, "Wrapping of a permits clause");

    public static final Option<SortOrder> PERMITS_ORDER =
            Option.ofEnum("sealed.permits-order", SortOrder.PRESERVE, "Sort order of the types in a permits clause");

    public static final Option<Boolean> PERMITS_ON_NEW_LINE =
            Option.ofBoolean("sealed.permits-on-new-line", false, "Start the permits clause on its own line");

    private SealedRules() {}

    /** Fluent view of the {@code sealed.*} rules. */
    public static final class Builder {

        private final StyleBuilder style;

        public Builder(StyleBuilder style) {
            this.style = style;
        }

        public Builder permitsWrapping(WrapPolicy value) {
            style.set(PERMITS_WRAPPING, value);
            return this;
        }

        public Builder permitsOrder(SortOrder value) {
            style.set(PERMITS_ORDER, value);
            return this;
        }

        public Builder permitsOnNewLine(boolean value) {
            style.set(PERMITS_ON_NEW_LINE, value);
            return this;
        }

    }

}
