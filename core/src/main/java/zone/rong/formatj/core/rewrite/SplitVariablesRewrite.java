package zone.rong.formatj.core.rewrite;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;

import zone.rong.formatj.api.Option;
import zone.rong.formatj.api.Style;
import zone.rong.formatj.api.rules.DeclarationRules;
import zone.rong.formatj.api.rules.ModifierOrder;
import zone.rong.formatj.api.rules.ModifierRules;
import zone.rong.formatj.api.rules.VisibilityPosition;
import zone.rong.formatj.core.cst.GreenNode;
import zone.rong.formatj.core.cst.ProgramTokens;
import zone.rong.formatj.core.cst.SyntaxKind;
import zone.rong.formatj.core.cst.SyntaxToken;
import zone.rong.formatj.core.lexer.Token;
import zone.rong.formatj.core.lexer.TokenKind;

/**
 * Splits a declaration of several variables into one declaration per variable, repeating the head.
 *
 * <p>{@code int a, b = 2, c;} becomes {@code int a; int b = 2; int c;} — fields and locals alike —
 * with the modifiers, annotations and type written again for each declarator. Everything the head is
 * made of is reused from the original tree for the first declaration; the later declarations are given
 * synthetic copies of it, which carry no comments. That is what makes the split's comment behaviour
 * fall out of the token layout instead of being a rule of its own: comments riding on the head, a
 * Javadoc above it included, print once, on the first declaration, and each comma's comments — which
 * have no home once the comma goes — cause the whole declaration to be declined.
 *
 * <p>Array dimensions written beside the name belong to that variable alone: {@code int a[], b;} is
 * split to {@code int[] a;} and {@code int b;}, the brackets moving onto the type they now describe.
 * A declarator that carries an annotation of its own is declined, since what that annotation applies
 * to is the author's to spell out.
 *
 * <p>Edits are declared token by token: each comma is removed in favour of a semicolon and a repeated
 * head, and every name-attached pair of brackets is deleted from the declarator and written again
 * before the name, where the type keeps it. {@link #expectedEdits} derives that edit set from a tree
 * on its own, which is what {@code RewriteVerification} holds each declared edit against.
 */
public final class SplitVariablesRewrite implements Rewrite {

    private static final Set<String> INTERFACE_FIELD = Set.of("public", "static", "final");

    /**
     * Every edit the split could declare for {@code tree}, whichever declarations qualify.
     *
     * <p>Derived from the tokens alone, so the verifier can compare each declared edit against them
     * without trusting the rewrite's own bookkeeping.
     */
    public static List<TokenEdit> expectedEdits(GreenNode tree, Style style) {
        Map<GreenNode.Leaf, Integer> positions = ProgramTokens.positions(tree);
        List<TokenEdit> edits = new ArrayList<>();
        collect(tree, false, false, positions, style::get, edits);
        return List.copyOf(edits);
    }

    /**
     * @param interfaceOwner whether {@code node}, when it is a type body, belongs to an interface
     * @param inInterfaceBody whether {@code node} is a member of an interface's body
     */
    private static void collect(
        GreenNode node,
        boolean interfaceOwner,
        boolean inInterfaceBody,
        Map<GreenNode.Leaf, Integer> positions,
        Function<Option<?>, Object> rule,
        List<TokenEdit> edits
    ) {
        if (node instanceof GreenNode.Leaf) {
            return;
        }
        for (GreenNode child : node.children()) {
            boolean body = node.kind() == SyntaxKind.CLASS_BODY;
            collect(child, interfaceLike(node.kind()), body && interfaceOwner, positions, rule, edits);
        }
        split(node, candidate -> firstPosition(candidate, positions), copiedHead(rule, inInterfaceBody), edits);
    }

    private static boolean interfaceLike(SyntaxKind kind) {
        return kind == SyntaxKind.INTERFACE_DECLARATION || kind == SyntaxKind.ANNOTATION_TYPE_DECLARATION;
    }

    /**
     * How the later declarations' copies of the head are made. A copy has no source position for the
     * modifier rules to record an edit at, and whatever they would change in it would only change on
     * the next pass, so the copy is written the way they leave the original head: in canonical order,
     * and inside an interface without the public, static and final it implies.
     */
    private static HeadCopier copiedHead(Function<Option<?>, Object> rule, boolean inInterfaceBody) {
        boolean sort = rule.apply(ModifierRules.ORDER) == ModifierOrder.CANONICAL;
        VisibilityPosition position = (VisibilityPosition) rule.apply(ModifierRules.VISIBILITY_POSITION);
        boolean strip = inInterfaceBody && Boolean.TRUE.equals(rule.apply(ModifierRules.REMOVE_REDUNDANT));
        return (kind, head) -> {
            List<GreenNode> copy = syntheticCopy(head);
            if (sort) {
                copy = ModifierRewrite.sortedHead(kind, copy, position);
            }
            return strip && kind == SyntaxKind.FIELD_DECLARATION ? withoutImplied(copy) : copy;
        };
    }

    private static List<GreenNode> withoutImplied(List<GreenNode> head) {
        if (head.getFirst().kind() != SyntaxKind.MODIFIERS) {
            return head;
        }
        List<GreenNode> kept = head.getFirst()
            .children()
            .stream()
            .filter(child -> !(child instanceof GreenNode.Leaf leaf && INTERFACE_FIELD.contains(leaf.decodedLexeme())))
            .toList();
        List<GreenNode> stripped = new ArrayList<>(head.subList(1, head.size()));
        if (!kept.isEmpty()) {
            stripped.addFirst(GreenNode.branch(SyntaxKind.MODIFIERS, kept));
        }
        return stripped;
    }

    /** The position of a node's first token that was in the original source. */
    private static int firstPosition(GreenNode node, Map<GreenNode.Leaf, Integer> positions) {
        for (GreenNode.Leaf leaf : ProgramTokens.leaves(node)) {
            Integer position = positions.get(leaf);
            if (position != null) {
                return position;
            }
        }
        return -1;
    }

    /**
     * The declarations one declaration splits into, with the edits it needs appended to {@code edits};
     * null when it does not qualify.
     */
    private static List<GreenNode> split(
        GreenNode declaration,
        PositionLookup positions,
        HeadCopier copier,
        List<TokenEdit> edits
    ) {
        if (declaration.kind() != SyntaxKind.FIELD_DECLARATION &&
                declaration.kind() != SyntaxKind.LOCAL_VARIABLE_DECLARATION ||
            positions.of(declaration) < 0) {
            return null;
        }
        List<GreenNode> children = declaration.children();
        int firstDeclarator = firstDeclarator(children);
        if (firstDeclarator < 1 ||
            !(children.getLast() instanceof GreenNode.Leaf tail) ||
            !tail.decodedLexeme().equals(";")) {
            return null;
        }
        // The declarators alternate with their commas, except that the last one has none.
        List<Integer> declarators = new ArrayList<>();
        for (int i = firstDeclarator; i < children.size() - 1; i += 2) {
            if (children.get(i).kind() != SyntaxKind.VARIABLE_DECLARATOR ||
                i + 1 < children.size() - 1 && !isLexeme(children.get(i + 1), ",") ||
                !isName(children.get(i).children().getFirst())) {
                return null;
            }
            declarators.add(i);
        }
        if (declarators.size() < 2) {
            return null;
        }
        List<List<GreenNode>> brackets = new ArrayList<>(declarators.size());
        for (int i : declarators) {
            GreenNode separator = children.get(i + 1);
            if (isLexeme(separator, ",") && (positions.of(separator) < 0 || Synthetic.carriesComments(separator))) {
                return null;
            }
            List<GreenNode> own = dims(children.get(i).children());
            if (own == null) {
                return null;
            }
            brackets.add(own);
        }
        List<GreenNode> head = children.subList(0, firstDeclarator);
        if (head.getLast().kind() == SyntaxKind.VARIABLE_DECLARATOR || positions.of(head.getLast()) < 0) {
            return null;
        }

        List<GreenNode> split = new ArrayList<>();
        for (int position = 0; position < declarators.size(); position++) {
            int index = declarators.get(position);
            GreenNode declarator = children.get(index);
            List<GreenNode> own = brackets.get(position);
            List<GreenNode> type = new ArrayList<>(position == 0 ? head : copier.copy(declaration.kind(), head));
            if (!own.isEmpty()) {
                type.set(type.size() - 1, wrap(type.getLast(), own));
            }
            GreenNode trimmed = GreenNode.branch(
                SyntaxKind.VARIABLE_DECLARATOR,
                concat(List.of(children.get(index).children().getFirst()), afterBrackets(declarator.children()))
            );
            List<GreenNode> whole = new ArrayList<>(type);
            whole.add(trimmed);
            whole.add(position == declarators.size() - 1 ? tail : Synthetic.separator(";"));
            split.add(GreenNode.branch(declaration.kind(), whole));
            if (position > 0) {
                // The repeated head prints again here; the ledger says so token for token.
                edits.add(new TokenEdit(
                    DeclarationRules.SPLIT_VARIABLES,
                    "declaration split",
                    positions.of(children.get(index - 1)),
                    List.of(","),
                    splitLexemes(declaration.kind(), whole.subList(0, whole.size() - 2)),
                    TokenEdit.Bias.INNERMOST_FIRST
                ));
                deleteBrackets(own, positions).ifPresent(edits::add);
            }
        }
        // The first declaration keeps its head's original tokens, so its own brackets only need to be
        // relocated: deleted from where they followed the name, written again before it.
        List<GreenNode> first = brackets.getFirst();
        if (!first.isEmpty()) {
            GreenNode name = children.get(declarators.getFirst()).children().getFirst();
            TokenEdit beforeName = insertBracketsBeforeName(name, first, positions);
            if (beforeName == null || deleteBrackets(first, positions).isEmpty()) {
                return null;
            }
            deleteBrackets(first, positions).ifPresent(edits::add);
            edits.add(beforeName);
        }
        return split;
    }

    /** What the declared edit inserts at a comma: a semicolon and the repeated head. */
    private static List<String> splitLexemes(SyntaxKind kind, List<GreenNode> headAndType) {
        List<String> inserted = new ArrayList<>();
        inserted.add(";");
        inserted.addAll(ProgramTokens.lexemes(GreenNode.branch(kind, List.copyOf(headAndType))));
        return List.copyOf(inserted);
    }

    /** The head each split declaration repeats, cloned into comment-free tokens for the later ones. */
    private static List<GreenNode> syntheticCopy(List<GreenNode> head) {
        List<GreenNode> cloned = new ArrayList<>(head.size());
        for (GreenNode node : head) {
            cloned.add(syntheticCopy(node));
        }
        return cloned;
    }

    private static GreenNode syntheticCopy(GreenNode node) {
        if (node instanceof GreenNode.Leaf leaf) {
            return GreenNode.leaf(SyntaxToken.of(Token.synthetic(leaf.token().token().kind(), leaf.lexeme())));
        }
        List<GreenNode> cloned = new ArrayList<>(node.children().size());
        for (GreenNode child : node.children()) {
            cloned.add(syntheticCopy(child));
        }
        return GreenNode.branch(node.kind(), cloned);
    }

    /** A declarator's name-attached bracket pairs, or null when they are annotated or irregular. */
    private static List<GreenNode> dims(List<GreenNode> declarator) {
        List<GreenNode> brackets = new ArrayList<>();
        int next = 1;
        while (next + 1 < declarator.size() &&
            isLexeme(declarator.get(next), "[") &&
            isLexeme(declarator.get(next + 1), "]")) {
            brackets.add(declarator.get(next));
            brackets.add(declarator.get(next + 1));
            next += 2;
        }
        if (next < declarator.size() && declarator.get(next).kind() == SyntaxKind.ANNOTATION) {
            return null;
        }
        return brackets;
    }

    private static List<GreenNode> afterBrackets(List<GreenNode> declarator) {
        int next = 1;
        while (next + 1 < declarator.size() &&
            isLexeme(declarator.get(next), "[") &&
            isLexeme(declarator.get(next + 1), "]")) {
            next += 2;
        }
        return List.copyOf(declarator.subList(next, declarator.size()));
    }

    private static GreenNode wrap(GreenNode type, List<GreenNode> brackets) {
        GreenNode wrapped = GreenNode.branch(SyntaxKind.ARRAY_TYPE, concat(List.of(type), brackets.subList(0, 2)));
        for (int i = 2; i < brackets.size(); i += 2) {
            wrapped = GreenNode.branch(SyntaxKind.ARRAY_TYPE, List.of(wrapped, brackets.get(i), brackets.get(i + 1)));
        }
        return wrapped;
    }

    private static Optional<TokenEdit> deleteBrackets(List<GreenNode> brackets, PositionLookup positions) {
        if (brackets.isEmpty()) {
            return Optional.empty();
        }
        int position = positions.of(brackets.getFirst());
        List<String> lexemes = new ArrayList<>();
        for (GreenNode bracket : brackets) {
            if (position < 0 || positions.of(bracket) != position + lexemes.size()) {
                return Optional.empty();
            }
            lexemes.add(((GreenNode.Leaf) bracket).lexeme());
        }
        return Optional.of(new TokenEdit(
            DeclarationRules.SPLIT_VARIABLES,
            "array brackets moved to the type",
            position,
            lexemes,
            List.of(),
            TokenEdit.Bias.INNERMOST_FIRST
        ));
    }

    private static TokenEdit insertBracketsBeforeName(
        GreenNode name,
        List<GreenNode> brackets,
        PositionLookup positions
    ) {
        int position = positions.of(name);
        if (position < 0) {
            return null;
        }
        List<String> lexemes = new ArrayList<>();
        for (GreenNode bracket : brackets) {
            lexemes.add(((GreenNode.Leaf) bracket).lexeme());
        }
        return new TokenEdit(
            DeclarationRules.SPLIT_VARIABLES,
            "array brackets moved to the type",
            position,
            List.of(),
            lexemes,
            TokenEdit.Bias.INNERMOST_FIRST
        );
    }

    private static boolean isName(GreenNode node) {
        return node instanceof GreenNode.Leaf leaf && leaf.token().token().kind() == TokenKind.IDENTIFIER;
    }

    private static int firstDeclarator(List<GreenNode> children) {
        for (int i = 0; i < children.size(); i++) {
            if (children.get(i).kind() == SyntaxKind.VARIABLE_DECLARATOR) {
                return i;
            }
        }
        return -1;
    }

    private static boolean isLexeme(GreenNode node, String lexeme) {
        return node instanceof GreenNode.Leaf leaf && leaf.decodedLexeme().equals(lexeme);
    }

    private static List<GreenNode> concat(List<GreenNode> first, List<GreenNode> second) {
        List<GreenNode> all = new ArrayList<>(first.size() + second.size());
        all.addAll(first);
        all.addAll(second);
        return all;
    }

    /** The children of {@code parent} with each declaration of several variables split, or the same list. */
    private static List<GreenNode> splitChildren(GreenNode parent, HeadCopier copier, RewriteContext context) {
        List<GreenNode> children = parent.children();
        List<GreenNode> rewritten = null;
        int offset = 0;
        for (int i = 0; i < children.size(); i++) {
            List<TokenEdit> edits = new ArrayList<>();
            List<GreenNode> split = split(children.get(i), context::firstPosition, copier, edits);
            if (split == null) {
                continue;
            }
            edits.forEach(context::record);
            if (rewritten == null) {
                rewritten = new ArrayList<>(children);
            }
            rewritten.remove(i + offset);
            rewritten.addAll(i + offset, split);
            offset += split.size() - 1;
        }
        return rewritten == null ? children : rewritten;
    }

    @Override
    public String name() {
        return DeclarationRules.SPLIT_VARIABLES.key();
    }

    @Override
    public boolean enabled(RewriteContext context) {
        return context.rule(DeclarationRules.SPLIT_VARIABLES);
    }

    @Override
    public GreenNode rewrite(GreenNode node, RewriteContext context) {
        // A type body is split from its owner, which knows whether the body belongs to an interface.
        if (node.kind() == SyntaxKind.CLASS_BODY) {
            return node;
        }
        List<GreenNode> children = new ArrayList<>(splitChildren(node, copiedHead(context::rule, false), context));
        HeadCopier inBody = copiedHead(context::rule, interfaceLike(node.kind()));
        for (int i = 0; i < children.size(); i++) {
            GreenNode child = children.get(i);
            if (child.kind() == SyntaxKind.CLASS_BODY) {
                List<GreenNode> members = splitChildren(child, inBody, context);
                if (members != child.children()) {
                    children.set(i, GreenNode.branch(child.kind(), members));
                }
            }
        }
        return children.equals(node.children()) ? node : GreenNode.branch(node.kind(), children);
    }

    /** Where a node's tokens sat in the original stream: the rewrite's context, or the verifier's map. */
    interface PositionLookup {

        int of(GreenNode node);

    }

    private interface HeadCopier {

        List<GreenNode> copy(SyntaxKind kind, List<GreenNode> head);

    }

}
