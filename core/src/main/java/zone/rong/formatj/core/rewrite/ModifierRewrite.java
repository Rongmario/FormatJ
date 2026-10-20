package zone.rong.formatj.core.rewrite;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import zone.rong.formatj.api.rules.CommentRules;
import zone.rong.formatj.api.rules.ModifierOrder;
import zone.rong.formatj.api.rules.ModifierRules;
import zone.rong.formatj.api.rules.VisibilityPosition;
import zone.rong.formatj.core.cst.GreenNode;
import zone.rong.formatj.core.cst.ProgramTokens;
import zone.rong.formatj.core.cst.SyntaxKind;
import zone.rong.formatj.core.cst.SyntaxToken;
import zone.rong.formatj.core.lexer.Token;

/** Puts declaration modifiers in their declaration-specific canonical order. */
public final class ModifierRewrite implements Rewrite {

    private static final List<String> CLASS_ORDER = List.of(
        "public",
        "protected",
        "private",
        "abstract",
        "static",
        "sealed",
        "non-sealed",
        "final",
        "strictfp"
    );
    private static final List<String> INTERFACE_ORDER = List.of(
        "public",
        "protected",
        "private",
        "abstract",
        "static",
        "sealed",
        "non-sealed",
        "strictfp"
    );
    private static final List<String> ENUM_ORDER = List.of("public", "protected", "private", "static", "strictfp");
    private static final List<String> RECORD_ORDER = List.of(
        "public",
        "protected",
        "private",
        "static",
        "final",
        "strictfp"
    );
    private static final List<String> ANNOTATION_TYPE_ORDER = List.of(
        "public",
        "protected",
        "private",
        "abstract",
        "static",
        "strictfp"
    );
    private static final List<String> FIELD_ORDER = List.of(
        "public",
        "protected",
        "private",
        "static",
        "final",
        "transient",
        "volatile"
    );
    private static final List<String> METHOD_ORDER = List.of(
        "public",
        "protected",
        "private",
        "abstract",
        "default",
        "static",
        "final",
        "synchronized",
        "native",
        "strictfp"
    );
    private static final List<String> CONSTRUCTOR_ORDER = List.of("public", "protected", "private");
    private static final List<String> VISIBILITY = List.of("public", "protected", "private");

    private static List<Part> parts(GreenNode modifiers) {
        List<GreenNode> children = modifiers.children();
        List<Part> parts = new ArrayList<>();
        for (int i = 0; i < children.size(); i++) {
            GreenNode child = children.get(i);
            if (child.kind() == SyntaxKind.ANNOTATION) {
                parts.add(new Part(List.of(child), null));
                continue;
            }
            if (!(child instanceof GreenNode.Leaf leaf)) {
                return null;
            }
            if (leaf.decodedLexeme().equals("non") &&
                i + 2 < children.size() &&
                decoded(children.get(i + 1)).equals("-") &&
                decoded(children.get(i + 2)).equals("sealed")) {
                parts.add(new Part(List.of(child, children.get(i + 1), children.get(i + 2)), "non-sealed"));
                i += 2;
                continue;
            }
            parts.add(new Part(List.of(child), leaf.decodedLexeme()));
        }
        return List.copyOf(parts);
    }

    private static String decoded(GreenNode node) {
        return node instanceof GreenNode.Leaf leaf ? leaf.decodedLexeme() : "";
    }

    /**
     * Whether a comment is attached anywhere but in front of the first token. The comments in front,
     * a declaration's Javadoc among them, belong to the declaration and stay at its start.
     */
    private static boolean hasInnerComments(GreenNode node) {
        List<GreenNode.Leaf> leaves = ProgramTokens.leaves(node);
        for (int i = 0; i < leaves.size(); i++) {
            SyntaxToken token = leaves.get(i).token();
            if (!token.trailingComments().isEmpty() || i > 0 && !token.leadingComments().isEmpty()) {
                return true;
            }
        }
        return false;
    }

    /** Whether the comments in front of the declaration turn formatting off or back on. */
    private static boolean marksFormatting(GreenNode modifiers, RewriteContext context) {
        if (!context.rule(CommentRules.HONOUR_FORMATTER_OFF)) {
            return false;
        }
        String off = context.rule(CommentRules.OFF_MARKER);
        String on = context.rule(CommentRules.ON_MARKER);
        for (Token comment : ProgramTokens.leaves(modifiers).getFirst().token().leadingComments()) {
            if (!off.isBlank() && comment.decodedText().contains(off) ||
                !on.isBlank() && comment.decodedText().contains(on)) {
                return true;
            }
        }
        return false;
    }

    /** Swaps the leading trivia of the first leaves of two parts. */
    private static List<Part> swapLeading(List<Part> parts, int first, int other) {
        GreenNode.Leaf a = (GreenNode.Leaf) parts.get(first).nodes().getFirst();
        GreenNode.Leaf b = (GreenNode.Leaf) parts.get(other).nodes().getFirst();
        List<Part> swapped = new ArrayList<>(parts);
        swapped.set(first, parts.get(first).withFirst(withLeading(a, b.token().leading())));
        swapped.set(other, parts.get(other).withFirst(withLeading(b, a.token().leading())));
        return swapped;
    }

    private static GreenNode.Leaf withLeading(GreenNode.Leaf leaf, List<Token> leading) {
        return GreenNode.leaf(new SyntaxToken(leading, leaf.token().token(), leaf.token().trailing()));
    }

    private static List<String> order(SyntaxKind kind) {
        return switch (kind) {
            case CLASS_DECLARATION -> CLASS_ORDER;
            case INTERFACE_DECLARATION -> INTERFACE_ORDER;
            case ENUM_DECLARATION -> ENUM_ORDER;
            case RECORD_DECLARATION -> RECORD_ORDER;
            case ANNOTATION_TYPE_DECLARATION -> ANNOTATION_TYPE_ORDER;
            case FIELD_DECLARATION -> FIELD_ORDER;
            case METHOD_DECLARATION, ANNOTATION_ELEMENT_DECLARATION -> METHOD_ORDER;
            case CONSTRUCTOR_DECLARATION, COMPACT_CONSTRUCTOR_DECLARATION -> CONSTRUCTOR_ORDER;
            default -> null;
        };
    }

    private static List<String> visibilityLast(List<String> order) {
        List<String> moved = new ArrayList<>(order);
        moved.removeAll(VISIBILITY);
        moved.addAll(VISIBILITY.stream().filter(order::contains).toList());
        return moved;
    }

    @Override
    public String name() {
        return "modifiers";
    }

    @Override
    public boolean enabled(RewriteContext context) {
        return context.rule(ModifierRules.ORDER) == ModifierOrder.CANONICAL;
    }

    @Override
    public GreenNode rewrite(GreenNode node, RewriteContext context) {
        List<String> canonical = order(node.kind());
        if (canonical == null || node.children().isEmpty()) {
            return node;
        }
        List<String> order = context.rule(ModifierRules.VISIBILITY_POSITION) == VisibilityPosition.LAST
            ? visibilityLast(canonical)
            : canonical;

        GreenNode modifiers = node.children().getFirst();
        if (modifiers.kind() != SyntaxKind.MODIFIERS ||
            hasInnerComments(modifiers) ||
            marksFormatting(modifiers, context)) {
            return node;
        }

        List<Part> parts = parts(modifiers);
        if (parts == null) {
            return node;
        }

        List<Part> sortable = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        for (Part part : parts) {
            if (part.modifier() == null) {
                continue;
            }
            if (!order.contains(part.modifier()) || !seen.add(part.modifier())) {
                return node;
            }
            sortable.add(part);
        }
        if (sortable.size() < 2) {
            return node;
        }

        List<Part> sorted = new ArrayList<>(sortable);
        sorted.sort(Comparator.comparingInt(part -> order.indexOf(part.modifier())));
        if (sortable.equals(sorted)) {
            return node;
        }

        List<Part> placed = new ArrayList<>();
        int next = 0;
        for (Part part : parts) {
            placed.add(part.modifier() == null ? part : sorted.get(next++));
        }
        // The trivia in front of the declaration stays in front of it, whichever keyword moves there.
        if (placed.getFirst() != parts.getFirst()) {
            placed = swapLeading(placed, 0, placed.indexOf(parts.getFirst()));
        }
        List<GreenNode> modifierChildren = new ArrayList<>();
        for (Part part : placed) {
            modifierChildren.addAll(part.nodes());
        }
        GreenNode rewrittenModifiers = GreenNode.branch(SyntaxKind.MODIFIERS, modifierChildren);

        int position = context.firstPosition(modifiers);
        if (position < 0) {
            return node;
        }
        context.record(new TokenEdit(
            ModifierRules.ORDER,
            "declaration modifiers reordered",
            position,
            ProgramTokens.lexemes(modifiers),
            ProgramTokens.lexemes(rewrittenModifiers),
            TokenEdit.Bias.INNERMOST_FIRST
        ));

        List<GreenNode> declaration = new ArrayList<>(node.children());
        declaration.set(0, rewrittenModifiers);
        return GreenNode.branch(node.kind(), declaration);
    }

    private record Part(List<GreenNode> nodes, String modifier) {

        private Part {
            nodes = List.copyOf(nodes);
        }

        private Part withFirst(GreenNode first) {
            List<GreenNode> replaced = new ArrayList<>(nodes);
            replaced.set(0, first);
            return new Part(replaced, modifier);
        }

    }

}
