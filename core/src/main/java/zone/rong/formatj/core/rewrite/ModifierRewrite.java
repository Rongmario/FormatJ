package zone.rong.formatj.core.rewrite;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import zone.rong.formatj.api.rules.ModifierOrder;
import zone.rong.formatj.api.rules.ModifierRules;
import zone.rong.formatj.core.cst.GreenNode;
import zone.rong.formatj.core.cst.ProgramTokens;
import zone.rong.formatj.core.cst.SyntaxKind;

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

    private static boolean hasComments(GreenNode node) {
        for (GreenNode.Leaf leaf : ProgramTokens.leaves(node)) {
            if (leaf.token().hasComments()) {
                return true;
            }
        }
        return false;
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
        List<String> order = order(node.kind());
        if (order == null || node.children().isEmpty()) {
            return node;
        }

        GreenNode modifiers = node.children().getFirst();
        if (modifiers.kind() != SyntaxKind.MODIFIERS || hasComments(modifiers)) {
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

        List<GreenNode> modifierChildren = new ArrayList<>();
        int next = 0;
        for (Part part : parts) {
            Part rewritten = part.modifier() == null ? part : sorted.get(next++);
            modifierChildren.addAll(rewritten.nodes());
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

    }

}
