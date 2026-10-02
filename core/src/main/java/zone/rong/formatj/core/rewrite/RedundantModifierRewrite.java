package zone.rong.formatj.core.rewrite;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.function.Function;

import zone.rong.formatj.api.rules.ModifierRules;
import zone.rong.formatj.core.cst.GreenNode;
import zone.rong.formatj.core.cst.SyntaxKind;
import zone.rong.formatj.core.cst.SyntaxToken;
import zone.rong.formatj.core.lexer.Token;

/**
 * Removes modifiers the JLS implies.
 *
 * <p>A type body is handled by its owner, because what a member's modifiers imply depends on the
 * type around it. Anonymous classes, enum constant bodies and local types are left alone.
 */
public final class RedundantModifierRewrite implements Rewrite {

    private static final Set<String> NONE = Set.of();
    private static final Set<String> PUBLIC = Set.of("public");
    private static final Set<String> PUBLIC_ABSTRACT = Set.of("public", "abstract");
    private static final Set<String> PUBLIC_STATIC = Set.of("public", "static");
    private static final Set<String> PUBLIC_STATIC_FINAL = Set.of("public", "static", "final");
    private static final Set<String> STATIC = Set.of("static");
    private static final Set<String> STATIC_FINAL = Set.of("static", "final");
    private static final Set<String> FINAL = Set.of("final");
    private static final Set<String> PRIVATE = Set.of("private");

    private static GreenNode rewriteBody(GreenNode owner, RewriteContext context) {
        GreenNode body = owner.children().getLast();
        if (body.kind() != SyntaxKind.CLASS_BODY) {
            return owner;
        }
        GreenNode rewrittenBody = rewriteMembers(body, context, member -> redundant(owner.kind(), member));
        if (rewrittenBody == body) {
            return owner;
        }
        List<GreenNode> children = new ArrayList<>(owner.children());
        children.set(children.size() - 1, rewrittenBody);
        return GreenNode.branch(owner.kind(), children);
    }

    private static GreenNode rewriteMembers(
        GreenNode parent,
        RewriteContext context,
        Function<GreenNode, Set<String>> redundant
    ) {
        List<GreenNode> children = new ArrayList<>(parent.children());
        boolean changed = false;
        for (int i = 0; i < children.size(); i++) {
            GreenNode child = children.get(i);
            Set<String> modifiers = child.kind().isMember() ? redundant.apply(child) : NONE;
            if (modifiers.isEmpty()) {
                continue;
            }
            GreenNode stripped = strip(child, modifiers, context);
            if (stripped != child) {
                children.set(i, stripped);
                changed = true;
            }
        }
        return changed ? GreenNode.branch(parent.kind(), children) : parent;
    }

    /** The modifiers the language already implies for {@code member}, given the type that owns it. */
    private static Set<String> redundant(SyntaxKind owner, GreenNode member) {
        boolean interfaceLike = owner == SyntaxKind.INTERFACE_DECLARATION ||
            owner == SyntaxKind.ANNOTATION_TYPE_DECLARATION;
        return switch (member.kind()) {
            case FIELD_DECLARATION -> interfaceLike ? PUBLIC_STATIC_FINAL : NONE;
            case METHOD_DECLARATION, ANNOTATION_ELEMENT_DECLARATION -> {
                if (interfaceLike) {
                    yield isBodiless(member) ? PUBLIC_ABSTRACT : PUBLIC;
                }
                yield hasModifier(member, "private") ? FINAL : NONE;
            }
            case CLASS_DECLARATION -> interfaceLike ? PUBLIC_STATIC : NONE;
            case INTERFACE_DECLARATION, ENUM_DECLARATION, ANNOTATION_TYPE_DECLARATION ->
                interfaceLike ? PUBLIC_STATIC : STATIC;
            case RECORD_DECLARATION -> interfaceLike ? PUBLIC_STATIC_FINAL : STATIC_FINAL;
            case CONSTRUCTOR_DECLARATION -> owner == SyntaxKind.ENUM_DECLARATION ? PRIVATE : NONE;
            default -> NONE;
        };
    }

    private static boolean isBodiless(GreenNode method) {
        return method.children().getLast() instanceof GreenNode.Leaf leaf && leaf.decodedLexeme().equals(";");
    }

    private static boolean hasModifier(GreenNode declaration, String modifier) {
        GreenNode first = declaration.children().getFirst();
        return first.kind() == SyntaxKind.MODIFIERS &&
            first.children()
                .stream()
                .anyMatch(child -> child instanceof GreenNode.Leaf leaf && leaf.decodedLexeme().equals(modifier));
    }

    private static GreenNode strip(GreenNode declaration, Set<String> redundant, RewriteContext context) {
        List<GreenNode> children = declaration.children();
        if (children.getFirst().kind() != SyntaxKind.MODIFIERS) {
            return declaration;
        }
        Removal removal = remove(children.getFirst().children(), redundant, context);
        if (removal == null) {
            return declaration;
        }

        List<GreenNode> rewritten = new ArrayList<>();
        if (!removal.kept().isEmpty()) {
            rewritten.add(GreenNode.branch(SyntaxKind.MODIFIERS, removal.kept()));
        }
        int next = rewritten.size();
        rewritten.addAll(children.subList(1, children.size()));
        if (!removal.carried().isEmpty()) {
            GreenNode inheritor = inherit(removal.carried(), rewritten.get(next));
            if (inheritor == null) {
                return declaration;
            }
            rewritten.set(next, inheritor);
        }
        removal.edits().forEach(context::record);
        return GreenNode.branch(declaration.kind(), rewritten);
    }

    private static GreenNode rewriteResource(GreenNode resource, RewriteContext context) {
        Removal removal = remove(resource.children(), FINAL, context);
        if (removal == null) {
            return resource;
        }
        removal.edits().forEach(context::record);
        return GreenNode.branch(resource.kind(), removal.kept());
    }

    /**
     * Drops the redundant leaves among {@code items}, or returns null when nothing can be dropped safely.
     *
     * <p>Positions must ascend. They do not once {@code modifiers.order} has reordered the span,
     * whose own edit already covers those tokens, so they cannot be deleted a second time.
     */
    private static Removal remove(List<GreenNode> items, Set<String> redundant, RewriteContext context) {
        int last = -1;
        for (int i = 0; i < items.size(); i++) {
            if (isRedundant(items.get(i), redundant)) {
                last = i;
            }
        }
        int previous = -1;
        for (int i = 0; i <= last; i++) {
            int position = context.firstPosition(items.get(i));
            if (position <= previous) {
                return null;
            }
            previous = position;
        }

        List<GreenNode> kept = new ArrayList<>();
        List<TokenEdit> edits = new ArrayList<>();
        List<Token> pending = List.of();
        for (int i = 0; i < items.size(); i++) {
            GreenNode item = items.get(i);
            if (isRedundant(item, redundant)) {
                GreenNode.Leaf leaf = (GreenNode.Leaf) item;
                if (!leaf.token().trailingComments().isEmpty() ||
                    (i > 0 && !leaf.token().leadingComments().isEmpty())) {
                    kept.add(item);
                    continue;
                }
                edits.add(TokenEdit.delete(
                    ModifierRules.REMOVE_REDUNDANT,
                    "redundant modifier removed",
                    context.firstPosition(leaf),
                    leaf.lexeme()
                ));
                pending = concat(pending, leaf.token().leading());
                continue;
            }
            if (!pending.isEmpty()) {
                item = inherit(pending, item);
                if (item == null) {
                    return null;
                }
                pending = List.of();
            }
            kept.add(item);
        }
        return edits.isEmpty() ? null : new Removal(kept, edits, pending);
    }

    private static boolean isRedundant(GreenNode item, Set<String> redundant) {
        return item instanceof GreenNode.Leaf leaf && redundant.contains(leaf.decodedLexeme());
    }

    /**
     * Gives a removed token's leading trivia (a line break, blank lines, a doc comment) to the token
     * after it, so layout that hung off the removed token is kept. Returns null when that token has
     * comments of its own, because the two could not be told apart afterwards.
     */
    private static GreenNode inherit(List<Token> trivia, GreenNode node) {
        GreenNode.Leaf first = firstLeaf(node);
        if (!first.token().leadingComments().isEmpty()) {
            return null;
        }
        List<Token> own = first.token().leading().stream().filter(token -> !token.hasLineTerminator()).toList();
        SyntaxToken token = new SyntaxToken(concat(trivia, own), first.token().token(), first.token().trailing());
        return withFirstLeaf(node, GreenNode.leaf(token));
    }

    private static List<Token> concat(List<Token> first, List<Token> second) {
        List<Token> all = new ArrayList<>(first);
        all.addAll(second);
        return all;
    }

    private static GreenNode.Leaf firstLeaf(GreenNode node) {
        return node instanceof GreenNode.Leaf leaf ? leaf : firstLeaf(node.children().getFirst());
    }

    private static GreenNode withFirstLeaf(GreenNode node, GreenNode.Leaf replacement) {
        if (node instanceof GreenNode.Leaf) {
            return replacement;
        }
        List<GreenNode> children = new ArrayList<>(node.children());
        children.set(0, withFirstLeaf(children.getFirst(), replacement));
        return GreenNode.branch(node.kind(), children);
    }

    @Override
    public String name() {
        return ModifierRules.REMOVE_REDUNDANT.key();
    }

    @Override
    public boolean enabled(RewriteContext context) {
        return context.rule(ModifierRules.REMOVE_REDUNDANT);
    }

    @Override
    public GreenNode rewrite(GreenNode node, RewriteContext context) {
        return switch (node.kind()) {
            case COMPILATION_UNIT ->
                rewriteMembers(node, context, child -> child.kind() == SyntaxKind.RECORD_DECLARATION ? FINAL : NONE);
            case CLASS_DECLARATION, INTERFACE_DECLARATION, ENUM_DECLARATION, RECORD_DECLARATION,
                ANNOTATION_TYPE_DECLARATION -> rewriteBody(node, context);
            case RESOURCE -> rewriteResource(node, context);
            default -> node;
        };
    }

    /** What is left of a modifier list, the edits that say so, and trivia still owed to the next token. */
    private record Removal(List<GreenNode> kept, List<TokenEdit> edits, List<Token> carried) { }

}
