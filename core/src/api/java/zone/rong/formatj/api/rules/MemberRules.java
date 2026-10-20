package zone.rong.formatj.api.rules;

import java.util.List;

import zone.rong.formatj.api.Option;
import zone.rong.formatj.api.StyleBuilder;

/**
 * The order of members in a type body.
 *
 * <p>{@code members.field-order} ranks the four kinds of field: {@code static-final}, {@code static},
 * {@code final} and {@code instance}. {@code members.visibility-order} ranks {@code public},
 * {@code protected}, {@code package-private} and {@code private}. In both, kinds written as one group
 * rank equal and keep their source order, and a kind left out ranks after every listed one. A static
 * initializer follows the last static field kind and an instance initializer the last instance one.
 */
public final class MemberRules {

    public static final Option<MemberOrder> ORDER = Option.ofEnum(
        "members.order",
        MemberOrder.INTELLIJ,
        "Ordering of the members of a type body"
    );

    public static final Option<List<List<String>>> FIELD_ORDER = Option.ofStringGroups(
        "members.field-order",
        List.of(List.of("static-final"), List.of("static"), List.of("final"), List.of("instance")),
        "Ranking of field kinds: static-final, static, final and instance"
    );

    public static final Option<List<List<String>>> VISIBILITY_ORDER = Option.ofStringGroups(
        "members.visibility-order",
        List.of(List.of("public"), List.of("protected"), List.of("package-private"), List.of("private")),
        "Ranking of access levels for fields and methods sorted by visibility"
    );

    public static final Option<Boolean> FIELD_VISIBILITY_ORDER = Option.ofBoolean(
        "members.field-visibility-order",
        true,
        "Sort fields of one kind by visibility within their group, in members.visibility-order"
    );

    public static final Option<Boolean> METHOD_VISIBILITY_ORDER = Option.ofBoolean(
        "members.method-visibility-order",
        true,
        "Sort methods of one kind by visibility within their group, in members.visibility-order"
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

        public Builder fieldOrder(List<List<String>> value) {
            style.set(FIELD_ORDER, value);
            return this;
        }

        public Builder visibilityOrder(List<List<String>> value) {
            style.set(VISIBILITY_ORDER, value);
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
