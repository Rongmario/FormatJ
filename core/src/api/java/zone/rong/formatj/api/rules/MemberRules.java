package zone.rong.formatj.api.rules;

import zone.rong.formatj.api.Option;
import zone.rong.formatj.api.StyleBuilder;

/** The order of members in a type body. */
public final class MemberRules {

    public static final Option<MemberOrder> ORDER = Option.ofEnum(
        "members.order",
        MemberOrder.INTELLIJ,
        "Ordering of the members of a type body"
    );

    public static final Option<Boolean> FIELD_VISIBILITY_ORDER = Option.ofBoolean(
        "members.field-visibility-order",
        true,
        "Sort fields of one kind by visibility within their group, public first"
    );

    public static final Option<Boolean> METHOD_VISIBILITY_ORDER = Option.ofBoolean(
        "members.method-visibility-order",
        true,
        "Sort methods of one kind by visibility within their group, public first"
    );

    private MemberRules() { }

    /** Fluent view of the {@code members.*} rules. */
    public static final class Builder {

        private final StyleBuilder style;

        public Builder(StyleBuilder style) {
            this.style = style;
        }

        public Builder order(MemberOrder value) {
            style.set(ORDER, value);
            return this;
        }

        public Builder fieldVisibilityOrder(boolean value) {
            style.set(FIELD_VISIBILITY_ORDER, value);
            return this;
        }

        public Builder methodVisibilityOrder(boolean value) {
            style.set(METHOD_VISIBILITY_ORDER, value);
            return this;
        }

    }

}
