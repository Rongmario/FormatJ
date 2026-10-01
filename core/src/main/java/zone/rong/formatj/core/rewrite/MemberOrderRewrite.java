package zone.rong.formatj.core.rewrite;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import zone.rong.formatj.api.rules.CommentRules;
import zone.rong.formatj.api.rules.MemberOrder;
import zone.rong.formatj.api.rules.MemberRules;
import zone.rong.formatj.core.cst.GreenNode;
import zone.rong.formatj.core.cst.MemberGroup;
import zone.rong.formatj.core.cst.ProgramTokens;
import zone.rong.formatj.core.cst.SyntaxKind;
import zone.rong.formatj.core.lexer.Token;

/**
 * Sorts the members of each type body into IntelliJ IDEA's default arrangement order.
 *
 * <p>The sort is stable, so members of one group keep the order the author gave them, and a member
 * moves as a whole node, which carries its comments, annotations and trailing comment with it. The
 * move is declared as a reordering of whole stretches of the original tokens, so adding or dropping a
 * token cannot hide inside it.
 *
 * <p>A body is left alone when moving things in it could change what the program does or could lose
 * the author's markers: it holds a formatter-off region, a stray {@code ;} or a region the parser
 * could not read. Java runs field initializers and initializer blocks in textual order, so a body is
 * also left alone, with a warning, when the sort would swap two of them that depend on the order.
 *
 * <p>This runs before every rewrite that edits a member's own tokens. Those edits are recorded
 * against the original token positions, which a member still has when this rewrite looks at it.
 */
public final class MemberOrderRewrite implements Rewrite {

    @Override
    public String name() {
        return "members";
    }

    @Override
    public boolean enabled(RewriteContext context) {
        return context.rule(MemberRules.ORDER) != MemberOrder.PRESERVE;
    }

    @Override
    public GreenNode rewrite(GreenNode node, RewriteContext context) {
        boolean interfaceBody = node.kind() == SyntaxKind.INTERFACE_DECLARATION ||
            node.kind() == SyntaxKind.ANNOTATION_TYPE_DECLARATION;
        List<GreenNode> rewritten = null;
        List<GreenNode> children = node.children();
        for (int i = 0; i < children.size(); i++) {
            GreenNode child = children.get(i);
            if (child.kind() != SyntaxKind.CLASS_BODY) {
                continue;
            }
            GreenNode sorted = sort(child, node, interfaceBody, context);
            if (sorted != child) {
                if (rewritten == null) {
                    rewritten = new ArrayList<>(children);
                }
                rewritten.set(i, sorted);
            }
        }
        return rewritten == null ? node : GreenNode.branch(node.kind(), rewritten);
    }

    private static GreenNode sort(GreenNode body, GreenNode owner, boolean interfaceBody, RewriteContext context) {
        List<GreenNode> children = body.children();
        int first = children.get(1).kind() == SyntaxKind.ENUM_CONSTANTS ? 2 : 1;
        List<GreenNode> members = children.subList(first, children.size() - 1);
        if (members.size() < 2 || turnsFormattingOff(members, children.getLast(), context)) {
            return body;
        }

        List<Integer> groups = new ArrayList<>(members.size());
        for (GreenNode member : members) {
            int group = MemberGroup.of(member, interfaceBody);
            if (group == MemberGroup.NONE || context.firstPosition(member) < 0) {
                return body;
            }
            groups.add(group);
        }

        List<Integer> order = new ArrayList<>(members.size());
        for (int i = 0; i < members.size(); i++) {
            order.add(i);
        }
        order.sort(Comparator.comparingInt(groups::get));
        if (order.equals(order.stream().sorted().toList())) {
            return body;
        }

        String conflict = initializationConflict(members, groups, order);
        if (conflict != null) {
            GreenNode.Leaf open = (GreenNode.Leaf) children.getFirst();
            Token token = open.token().token();
            context.warn("members of " + name(owner) + " left in place: " + conflict, token.line(), token.column());
            return body;
        }

        List<TokenEdit.Span> spans = new ArrayList<>(members.size());
        List<GreenNode> rebuilt = new ArrayList<>(children.subList(0, first));
        for (int index : order) {
            GreenNode member = members.get(index);
            spans.add(new TokenEdit.Span(context.firstPosition(member), context.endPosition(member)));
            rebuilt.add(member);
        }
        rebuilt.add(children.getLast());
        context.record(TokenEdit.reorder(MemberRules.ORDER, "members sorted by kind", spans));
        return GreenNode.branch(body.kind(), rebuilt);
    }

    private static boolean turnsFormattingOff(List<GreenNode> members, GreenNode close, RewriteContext context) {
        if (!context.rule(CommentRules.HONOUR_FORMATTER_OFF)) {
            return false;
        }
        String off = context.rule(CommentRules.OFF_MARKER);
        String on = context.rule(CommentRules.ON_MARKER);
        for (GreenNode node : concat(members, close)) {
            for (Token trivia : ProgramTokens.leaves(node).getFirst().token().leading()) {
                if (trivia.kind().isComment() &&
                    (!off.isBlank() && trivia.decodedText().contains(off) ||
                        !on.isBlank() && trivia.decodedText().contains(on))) {
                    return true;
                }
            }
        }
        return false;
    }

    private static List<GreenNode> concat(List<GreenNode> members, GreenNode close) {
        List<GreenNode> all = new ArrayList<>(members);
        all.add(close);
        return all;
    }

    // ------------------------------------------------------ initialization order

    /**
     * Describes the first pair the sort would swap whose initialization depends on the order, or
     * returns null when every swap is safe.
     *
     * <p>Only members of one staticness share an initialization order. Two initializing members may
     * swap when both are fields with constant-like initializers that do not mention each other. An
     * initializing member and a field without an initializer may swap when the first does not mention
     * the second by name, since a simple-name use before the declaration does not compile.
     */
    private static String initializationConflict(List<GreenNode> members, List<Integer> groups, List<Integer> order) {
        int[] newIndex = new int[members.size()];
        for (int i = 0; i < order.size(); i++) {
            newIndex[order.get(i)] = i;
        }
        for (int earlier = 0; earlier < members.size(); earlier++) {
            if (groups.get(earlier) > INSTANCE_INITIALIZER) {
                continue;
            }
            for (int later = earlier + 1; later < members.size(); later++) {
                if (newIndex[earlier] < newIndex[later] ||
                    groups.get(later) > INSTANCE_INITIALIZER ||
                    isStatic(groups.get(earlier)) != isStatic(groups.get(later))) {
                    continue;
                }
                GreenNode a = members.get(earlier);
                GreenNode b = members.get(later);
                if (!initializes(a) && !initializes(b)) {
                    continue;
                }
                if (!swapIsSafe(a, b)) {
                    return "moving " + describe(b) + " above " + describe(a) + " would change initialization order";
                }
            }
        }
        return null;
    }

    private static final int STATIC_INITIALIZER = 9;
    private static final int INSTANCE_INITIALIZER = 18;

    private static boolean isStatic(int group) {
        return group <= STATIC_INITIALIZER;
    }

    private static boolean swapIsSafe(GreenNode a, GreenNode b) {
        Set<String> namesA = declaredNames(a);
        Set<String> namesB = declaredNames(b);
        if (initializes(a) && initializes(b)) {
            return a.kind() == SyntaxKind.FIELD_DECLARATION &&
                b.kind() == SyntaxKind.FIELD_DECLARATION &&
                constantLike(initializers(a), namesB) &&
                constantLike(initializers(b), namesA);
        }
        GreenNode initializing = initializes(a) ? a : b;
        Set<String> other = initializes(a) ? namesB : namesA;
        return ProgramTokens.lexemes(initializing).stream().noneMatch(other::contains);
    }

    /** A field with an initializer, or an initializer block. */
    private static boolean initializes(GreenNode member) {
        if (member.kind() == SyntaxKind.INITIALIZER_BLOCK) {
            return true;
        }
        return member.kind() == SyntaxKind.FIELD_DECLARATION && !initializers(member).isEmpty();
    }

    private static List<GreenNode> initializers(GreenNode field) {
        List<GreenNode> initializers = new ArrayList<>();
        for (GreenNode declarator : field.children()) {
            if (declarator.kind() != SyntaxKind.VARIABLE_DECLARATOR) {
                continue;
            }
            List<GreenNode> parts = declarator.children();
            for (int i = 0; i < parts.size() - 1; i++) {
                if (isLexeme(parts.get(i), "=")) {
                    initializers.addAll(parts.subList(i + 1, parts.size()));
                }
            }
        }
        return initializers;
    }

    private static Set<String> declaredNames(GreenNode member) {
        Set<String> names = new HashSet<>();
        for (GreenNode declarator : member.children()) {
            if (declarator.kind() == SyntaxKind.VARIABLE_DECLARATOR) {
                names.add(((GreenNode.Leaf) declarator.children().getFirst()).decodedLexeme());
            }
        }
        return names;
    }

    private static String describe(GreenNode member) {
        if (member.kind() == SyntaxKind.INITIALIZER_BLOCK) {
            return isStatic(MemberGroup.of(member, false)) ? "a static initializer" : "an initializer block";
        }
        return String.join(", ", declaredNames(member).stream().sorted().toList());
    }

    /** Whether the initializers can only produce a value, from literals and operators and names. */
    private static boolean constantLike(List<GreenNode> initializers, Set<String> forbidden) {
        return initializers.stream().allMatch(node -> constantLike(node, forbidden));
    }

    private static boolean constantLike(GreenNode node, Set<String> forbidden) {
        if (node instanceof GreenNode.Leaf leaf) {
            String lexeme = leaf.decodedLexeme();
            return switch (leaf.token().token().kind()) {
                case IDENTIFIER -> !forbidden.contains(lexeme);
                case KEYWORD ->
                    !lexeme.equals("this") &&
                        !lexeme.equals("super") &&
                        !lexeme.equals("new") &&
                        !lexeme.equals("class");
                case OPERATOR ->
                    !lexeme.equals("++") && !lexeme.equals("--") && !lexeme.equals("->") && !lexeme.equals("::");
                default -> true;
            };
        }
        return switch (node.kind()) {
            case LITERAL, NAME, QUALIFIED_NAME, MEMBER_ACCESS, BINARY_EXPRESSION, UNARY_EXPRESSION,
                PARENTHESIZED_EXPRESSION, TERNARY_EXPRESSION, PRIMITIVE_TYPE ->
                node.children().stream().allMatch(child -> constantLike(child, forbidden));
            case CAST_EXPRESSION ->
                node.children().size() == 4 &&
                    isConstantType(node.children().get(1)) &&
                    constantLike(node.children().getLast(), forbidden);
            default -> false;
        };
    }

    private static boolean isConstantType(GreenNode type) {
        return type.kind() == SyntaxKind.PRIMITIVE_TYPE || ProgramTokens.lexemes(type).equals(List.of("String"));
    }

    private static boolean isLexeme(GreenNode node, String lexeme) {
        return node instanceof GreenNode.Leaf leaf && leaf.decodedLexeme().equals(lexeme);
    }

    /** The declared name of a type, or a description of an unnamed body's owner. */
    private static String name(GreenNode owner) {
        List<GreenNode> children = owner.children();
        for (int i = 0; i < children.size() - 1; i++) {
            if (owner.kind().isTypeDeclaration() &&
                children.get(i) instanceof GreenNode.Leaf keyword &&
                Set.of("class", "interface", "enum", "record").contains(keyword.decodedLexeme()) &&
                children.get(i + 1) instanceof GreenNode.Leaf name) {
                return name.decodedLexeme();
            }
        }
        return "an anonymous class";
    }

}
