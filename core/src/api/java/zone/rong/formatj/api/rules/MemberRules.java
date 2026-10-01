package zone.rong.formatj.api.rules;

import zone.rong.formatj.api.Option;
import zone.rong.formatj.api.StyleBuilder;

/** The order of members in a type body. */
public final class MemberRules {

    public static final Option<MemberOrder> ORDER = Option.ofEnum(
            "members.order",
            MemberOrder.PRESERVE,
            "Ordering of the members of a type body");

    private MemberRules() {}

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

    }

}
