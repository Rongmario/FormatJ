package zone.rong.formatj.core.cst;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import zone.rong.formatj.api.Style;
import zone.rong.formatj.api.rules.MemberRules;

/**
 * The group a type body member belongs to in IntelliJ IDEA's default Java arrangement order.
 *
 * <p>Both the layout (blank lines between members of different groups) and the {@code members.order}
 * rewrite classify through here, so they cannot disagree about which neighbours differ.
 */
public final class MemberGroup {

    /** Returned for anything that is not ordered: enum constants, stray semicolons, unparsed text. */
    public static final int NONE = -1;

    /** Five slots per field rank: four access levels, then the initializer that may follow them. */
    private static final int FIELD_RANK_SLOTS = 5;
    private static final int FIELD_KINDS = 4;
    private static final int CONSTRUCTORS = FIELD_RANK_SLOTS * (FIELD_KINDS + 1);
    /** Four slots so the access levels can be folded into the number. */
    private static final int STATIC_METHODS = CONSTRUCTORS + 1;
    private static final int METHODS = STATIC_METHODS + 4;
    private static final int ENUMS = METHODS + 4;
    private static final int INTERFACES = ENUMS + 1;
    private static final int STATIC_CLASSES = INTERFACES + 1;
    private static final int INNER_CLASSES = STATIC_CLASSES + 1;

    private static final Order SECTIONS = new Order(
        List.of(List.of("static-final"), List.of("static"), List.of("final"), List.of("instance")),
        List.of(),
        false,
        false,
        false
    );

    private MemberGroup() { }

    /**
     * @param member a child of a type body
     * @param interfaceBody whether the body belongs to an interface or annotation type, whose members
     *     are implicitly public and, for fields and nested classes, static
     * @return the group, or {@link #NONE}
     */
    public static int of(GreenNode member, boolean interfaceBody, Order order) {
        List<String> modifiers = modifiers(member);
        boolean isStatic = modifiers.contains("static");
        return switch (member.kind()) {
            case FIELD_DECLARATION -> {
                boolean fieldStatic = isStatic || interfaceBody;
                boolean fieldFinal = modifiers.contains("final") || interfaceBody;
                String kind = fieldStatic
                    ? (fieldFinal ? "static-final" : "static")
                    : (fieldFinal ? "final" : "instance");
                yield order.fieldRank(kind) * FIELD_RANK_SLOTS +
                    (order.fieldVisibility() ? order.access(modifiers, interfaceBody) : 0);
            }
            // An initializer follows the last field kind of its staticness, so it still runs after them.
            case INITIALIZER_BLOCK ->
                isStatic
                    ? Math.max(order.fieldRank("static-final"), order.fieldRank("static")) * FIELD_RANK_SLOTS + 4
                    : Math.max(order.fieldRank("final"), order.fieldRank("instance")) * FIELD_RANK_SLOTS + 4;
            case CONSTRUCTOR_DECLARATION, COMPACT_CONSTRUCTOR_DECLARATION -> CONSTRUCTORS;
            case METHOD_DECLARATION ->
                (isStatic ? STATIC_METHODS : METHODS) +
                    (order.methodVisibility() ? order.access(modifiers, interfaceBody) : 0);
            case ANNOTATION_ELEMENT_DECLARATION -> METHODS;
            case ENUM_DECLARATION -> ENUMS;
            case INTERFACE_DECLARATION, ANNOTATION_TYPE_DECLARATION -> INTERFACES;
            case RECORD_DECLARATION -> STATIC_CLASSES;
            case CLASS_DECLARATION -> isStatic || interfaceBody ? STATIC_CLASSES : INNER_CLASSES;
            default -> NONE;
        };
    }

    /**
     * The section a member sits in for blank-line purposes: its kind with access level folded away,
     * so a run of fields or methods that agree on {@code static} reads as one block. It does not
     * depend on the configured order, which only decides where each section goes.
     */
    public static int section(GreenNode member, boolean interfaceBody) {
        return of(member, interfaceBody, SECTIONS);
    }

    /**
     * The stable arrangement of a body's members, as indexes into {@code members}, or null when one of
     * them is not ordered.
     *
     * <p>With {@link Order#keepOverloads()} every method sorts with the best-ranked method of the same
     * name and staticness, so overloads of different access levels stay side by side.
     */
    public static List<Integer> arrange(List<GreenNode> members, boolean interfaceBody, Order order) {
        int[] groups = new int[members.size()];
        String[] families = new String[members.size()];
        Map<String, Integer> anchorOf = new HashMap<>();
        List<Integer> arrangement = new ArrayList<>(members.size());
        for (int i = 0; i < members.size(); i++) {
            GreenNode member = members.get(i);
            groups[i] = of(member, interfaceBody, order);
            if (groups[i] == NONE) {
                return null;
            }
            arrangement.add(i);
            families[i] = order.keepOverloads() && member.kind() == SyntaxKind.METHOD_DECLARATION
                ? modifiers(member).contains("static") + " " + methodName(member)
                : String.valueOf(i);
            Integer anchor = anchorOf.get(families[i]);
            if (anchor == null || groups[i] < groups[anchor]) {
                anchorOf.put(families[i], i);
            }
        }
        int[] anchors = new int[members.size()];
        for (int i = 0; i < members.size(); i++) {
            anchors[i] = anchorOf.get(families[i]);
        }
        arrangement.sort(
            Comparator.<Integer>comparingInt(i -> groups[anchors[i]])
                .thenComparingInt(i -> anchors[i])
                .thenComparingInt(i -> groups[i])
        );
        return arrangement;
    }

    /** Whether a field or initializer block runs in static initialization. */
    public static boolean isStatic(GreenNode member, boolean interfaceBody) {
        return modifiers(member).contains("static") || interfaceBody && member.kind() == SyntaxKind.FIELD_DECLARATION;
    }

    /** The identifier in front of a method's parameter list. */
    private static String methodName(GreenNode method) {
        List<GreenNode> children = method.children();
        for (int i = 1; i < children.size(); i++) {
            if (children.get(i).kind() == SyntaxKind.PARAMETERS && children.get(i - 1) instanceof GreenNode.Leaf name) {
                return name.decodedLexeme();
            }
        }
        return "";
    }

    private static List<String> modifiers(GreenNode member) {
        if (member.children().isEmpty() || member.children().getFirst().kind() != SyntaxKind.MODIFIERS) {
            return List.of();
        }
        return member.children()
            .getFirst()
            .children()
            .stream()
            .filter(GreenNode.Leaf.class::isInstance)
            .map(leaf -> ((GreenNode.Leaf) leaf).decodedLexeme())
            .toList();
    }

    /**
     * How members rank, from {@code members.field-order}, {@code members.visibility-order} and the
     * two switches that turn visibility ranking on.
     *
     * @param fields groups of {@code static-final}, {@code static}, {@code final} and {@code instance}
     * @param visibility groups of {@code public}, {@code protected}, {@code package-private} and
     *     {@code private}
     * @param keepOverloads whether methods of one name and staticness stay side by side
     */
    public record Order(
        List<List<String>> fields,
        List<List<String>> visibility,
        boolean fieldVisibility,
        boolean methodVisibility,
        boolean keepOverloads
    ) {

        /** The order the member rules of a style ask for. */
        public static Order of(Style style) {
            return new Order(
                style.get(MemberRules.FIELD_ORDER),
                style.get(MemberRules.VISIBILITY_ORDER),
                style.get(MemberRules.FIELD_VISIBILITY_ORDER),
                style.get(MemberRules.METHOD_VISIBILITY_ORDER),
                style.get(MemberRules.KEEP_OVERLOADS_TOGETHER)
            );
        }

        /** The index of the group holding a name, capped at {@code unlisted}, which is also the rank of a name no group holds. */
        private static int rank(List<List<String>> groups, String name, int unlisted) {
            for (int i = 0; i < groups.size(); i++) {
                if (groups.get(i).contains(name)) {
                    return Math.min(i, unlisted);
                }
            }
            return unlisted;
        }

        private int fieldRank(String kind) {
            return rank(fields, kind, FIELD_KINDS);
        }

        private int access(List<String> modifiers, boolean interfaceBody) {
            String level = interfaceBody || modifiers.contains("public")
                ? "public"
                : modifiers.contains("protected")
                    ? "protected"
                    : modifiers.contains("private") ? "private" : "package-private";
            return rank(visibility, level, 3);
        }

    }

}
