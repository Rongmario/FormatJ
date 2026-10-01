package zone.rong.formatj.core.cst;

import java.util.List;

/**
 * The group a type body member belongs to in IntelliJ IDEA's default Java arrangement order.
 *
 * <p>Both the layout (blank lines between members of different groups) and the {@code members.order}
 * rewrite classify through here, so they cannot disagree about which neighbours differ.
 */
public final class MemberGroup {

    /** Returned for anything that is not ordered: enum constants, stray semicolons, unparsed text. */
    public static final int NONE = -1;

    private static final int STATIC_FINAL_FIELDS = 1;
    private static final int STATIC_FIELDS = 5;
    private static final int STATIC_INITIALIZER = 9;
    private static final int FINAL_FIELDS = 10;
    private static final int FIELDS = 14;
    private static final int INSTANCE_INITIALIZER = 18;
    private static final int CONSTRUCTORS = 19;
    private static final int STATIC_METHODS = 20;
    private static final int METHODS = 21;
    private static final int ENUMS = 22;
    private static final int INTERFACES = 23;
    private static final int STATIC_CLASSES = 24;
    private static final int INNER_CLASSES = 25;

    private MemberGroup() {}

    /**
     * @param member a child of a type body
     * @param interfaceBody whether the body belongs to an interface or annotation type, whose members
     *     are implicitly public and, for fields and nested classes, static
     * @return the group from 1 to 25, or {@link #NONE}
     */
    public static int of(GreenNode member, boolean interfaceBody) {
        List<String> modifiers = modifiers(member);
        boolean isStatic = modifiers.contains("static");
        return switch (member.kind()) {
            case FIELD_DECLARATION -> {
                boolean fieldStatic = isStatic || interfaceBody;
                boolean fieldFinal = modifiers.contains("final") || interfaceBody;
                int first = fieldStatic
                        ? (fieldFinal ? STATIC_FINAL_FIELDS : STATIC_FIELDS)
                        : (fieldFinal ? FINAL_FIELDS : FIELDS);
                yield first + access(modifiers, interfaceBody);
            }
            case INITIALIZER_BLOCK -> isStatic ? STATIC_INITIALIZER : INSTANCE_INITIALIZER;
            case CONSTRUCTOR_DECLARATION, COMPACT_CONSTRUCTOR_DECLARATION -> CONSTRUCTORS;
            case METHOD_DECLARATION -> isStatic ? STATIC_METHODS : METHODS;
            case ANNOTATION_ELEMENT_DECLARATION -> METHODS;
            case ENUM_DECLARATION -> ENUMS;
            case INTERFACE_DECLARATION, ANNOTATION_TYPE_DECLARATION -> INTERFACES;
            case RECORD_DECLARATION -> STATIC_CLASSES;
            case CLASS_DECLARATION -> isStatic || interfaceBody ? STATIC_CLASSES : INNER_CLASSES;
            default -> NONE;
        };
    }

    private static int access(List<String> modifiers, boolean interfaceBody) {
        if (interfaceBody || modifiers.contains("public")) {
            return 0;
        }
        if (modifiers.contains("protected")) {
            return 1;
        }
        return modifiers.contains("private") ? 3 : 2;
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

}
