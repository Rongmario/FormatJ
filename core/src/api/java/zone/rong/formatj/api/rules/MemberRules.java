package zone.rong.formatj.api.rules;

import java.util.List;

import zone.rong.formatj.api.Option;

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

    public static final Option<Boolean> KEEP_OVERLOADS_TOGETHER = Option.ofBoolean(
        "members.keep-overloads-together",
        true,
        "Keep methods of one name and staticness side by side, where the best-ranked of them sorts"
    );

    private MemberRules() { }

}
