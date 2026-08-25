package zone.rong.formatj.core.rewrite;

import zone.rong.formatj.api.rules.SealedRules;
import zone.rong.formatj.api.rules.SortOrder;
import zone.rong.formatj.core.cst.GreenNode;
import zone.rong.formatj.core.cst.ProgramTokens;
import zone.rong.formatj.core.cst.SyntaxKind;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Sorts the types in a {@code permits} clause.
 *
 * <p>The same shape as {@code imports.order}, and for the same reason: the clause is a set written
 * as a list, so the order the author put it in carries no meaning and rearranging it cannot change
 * what the program does. Nothing is added and nothing is dropped — a permitted subclass that went
 * missing would stop compiling, and one that appeared would permit something the author did not —
 * so the whole run of types is replaced as a single declared edit whose tokens are a permutation of
 * the ones that were there. That is exactly what the verifier re-derives, and it is why sorting needs
 * no other justification.
 *
 * <p>A comment on a type moves with it. Comments are compared as a bag across a rewrite precisely so
 * that a rearrangement which takes them along still passes.
 */
public final class SealedRewrite implements Rewrite {

    @Override
    public String name() {
        return "sealed";
    }

    @Override
    public boolean enabled(RewriteContext context) {
        return context.rule(SealedRules.PERMITS_ORDER) != SortOrder.PRESERVE;
    }

    @Override
    public GreenNode rewrite(GreenNode node, RewriteContext context) {
        if (node.kind() != SyntaxKind.PERMITS_CLAUSE) {
            return node;
        }
        List<GreenNode> children = node.children();
        List<Integer> slots = typeSlots(children);
        if (slots.size() < 2) {
            return node;
        }

        List<GreenNode> types = new ArrayList<>(slots.size());
        for (int slot : slots) {
            types.add(children.get(slot));
        }
        List<GreenNode> sorted = new ArrayList<>(types);
        sorted.sort(order(context.rule(SealedRules.PERMITS_ORDER)));

        List<String> before = lexemes(types);
        List<String> after = lexemes(sorted);
        if (before.equals(after)) {
            return node;
        }

        int position = context.firstPosition(children.get(slots.getFirst()));
        if (position < 0) {
            return node;
        }
        context.record(
                new TokenEdit(
                        SealedRules.PERMITS_ORDER,
                        "permitted types reordered",
                        position,
                        run(children, slots, types),
                        run(children, slots, sorted),
                        TokenEdit.Bias.INNERMOST_FIRST));

        List<GreenNode> rewritten = new ArrayList<>(children);
        for (int i = 0; i < slots.size(); i++) {
            rewritten.set(slots.get(i), sorted.get(i));
        }
        return GreenNode.branch(node.kind(), rewritten);
    }

    /**
     * The run from the first type to the last, with the types taken from {@code order}.
     *
     * <p>The commas are part of the edit rather than tokens it steps over, because an edit is one
     * contiguous splice. They come back unchanged, which is what makes the run a permutation of
     * itself: pass the original types for the run as it was, the sorted ones for the run as it will
     * be, and the two lists hold the same tokens in a different order.
     */
    private static List<String> run(List<GreenNode> children, List<Integer> slots, List<GreenNode> order) {
        List<String> lexemes = new ArrayList<>();
        int next = 0;
        for (int i = slots.getFirst(); i <= slots.getLast(); i++) {
            boolean isType = next < slots.size() && slots.get(next) == i;
            lexemes.addAll(ProgramTokens.lexemes(isType ? order.get(next++) : children.get(i)));
        }
        return lexemes;
    }

    /** Where the types sit among the clause's children; everything else is the keyword or a comma. */
    private static List<Integer> typeSlots(List<GreenNode> children) {
        List<Integer> slots = new ArrayList<>();
        for (int i = 1; i < children.size(); i++) {
            GreenNode child = children.get(i);
            if (!(child instanceof GreenNode.Leaf leaf) || !leaf.lexeme().equals(",")) {
                slots.add(i);
            }
        }
        return slots;
    }

    private static Comparator<GreenNode> order(SortOrder sort) {
        Comparator<GreenNode> ascending = Comparator.comparing(SealedRewrite::text);
        return sort == SortOrder.DESCENDING ? ascending.reversed() : ascending;
    }

    /** A type's tokens run together, so that {@code a.B} and {@code a . B} sort the same way. */
    private static String text(GreenNode type) {
        return String.join("", ProgramTokens.lexemes(type));
    }

    private static List<String> lexemes(List<GreenNode> nodes) {
        List<String> lexemes = new ArrayList<>();
        for (GreenNode node : nodes) {
            lexemes.addAll(ProgramTokens.lexemes(node));
        }
        return lexemes;
    }

}
