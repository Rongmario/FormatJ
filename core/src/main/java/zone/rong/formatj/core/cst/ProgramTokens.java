package zone.rong.formatj.core.cst;

import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

/**
 * The leaves of a tree that carry program meaning, in source order.
 *
 * <p>This is the coordinate system every check on the formatter's output is expressed in: the token
 * equivalence check compares two of these lists, and the rewrite stage records each edit it makes as
 * a position within one. Both therefore have to agree on what counts, which is why the walk lives
 * here once rather than in each of them.
 *
 * <p>Whitespace and comments never count; they are trivia hanging off a token, not children of a
 * node. The terminating semicolon of an enum constant list counts only when members follow it: with
 * nothing after the constants the semicolon is optional, so writing or dropping it is a style choice
 * rather than a change to the program.
 */
public final class ProgramTokens {

    private ProgramTokens() { }

    /** The program-carrying leaves of a tree, in source order. */
    public static List<GreenNode.Leaf> leaves(GreenNode node) {
        List<GreenNode.Leaf> leaves = new ArrayList<>();
        collect(node, leaves);
        return List.copyOf(leaves);
    }

    /** The lexemes of {@link #leaves}, in the same order. */
    public static List<String> lexemes(GreenNode node) {
        List<GreenNode.Leaf> leaves = leaves(node);
        List<String> lexemes = new ArrayList<>(leaves.size());
        for (GreenNode.Leaf leaf : leaves) {
            lexemes.add(leaf.lexeme());
        }
        return List.copyOf(lexemes);
    }

    /**
     * Maps each leaf of {@link #leaves} to its position in that list, by identity.
     *
     * <p>Identity, not equality: two {@code ;} leaves are equal but sit at different positions, and
     * the caller needs to know which one it is holding.
     */
    public static Map<GreenNode.Leaf, Integer> positions(GreenNode node) {
        List<GreenNode.Leaf> leaves = leaves(node);
        Map<GreenNode.Leaf, Integer> positions = new IdentityHashMap<>(leaves.size());
        for (int i = 0; i < leaves.size(); i++) {
            positions.put(leaves.get(i), i);
        }
        return positions;
    }

    private static void collect(GreenNode node, List<GreenNode.Leaf> leaves) {
        if (node instanceof GreenNode.Leaf leaf) {
            if (leaf.token().token().kind().isSignificant()) {
                leaves.add(leaf);
            }
            return;
        }
        if (node.kind() == SyntaxKind.CLASS_BODY) {
            List<GreenNode> children = node.children();
            for (int i = 0; i < children.size(); i++) {
                GreenNode child = children.get(i);
                if (child.kind() == SyntaxKind.ENUM_CONSTANTS) {
                    collectEnumConstants(child, leaves, membersFollow(children, i));
                } else {
                    collect(child, leaves);
                }
            }
            return;
        }
        for (GreenNode child : node.children()) {
            collect(child, leaves);
        }
    }

    private static void collectEnumConstants(GreenNode node, List<GreenNode.Leaf> leaves, boolean membersFollow) {
        List<GreenNode> children = node.children();
        boolean skipTerminator = !membersFollow && !children.isEmpty() && isSemicolon(children.getLast());
        int last = children.size() - 1;
        for (int i = 0; i < children.size(); i++) {
            if (skipTerminator && i == last) {
                continue;
            }
            collect(children.get(i), leaves);
        }
    }

    private static boolean membersFollow(List<GreenNode> body, int enumConstantsIndex) {
        for (int i = enumConstantsIndex + 1; i < body.size(); i++) {
            if (body.get(i).kind().isMember()) {
                return true;
            }
        }
        return false;
    }

    private static boolean isSemicolon(GreenNode node) {
        return node instanceof GreenNode.Leaf leaf && leaf.lexeme().equals(";");
    }

}
