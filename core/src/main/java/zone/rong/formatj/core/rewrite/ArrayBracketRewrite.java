package zone.rong.formatj.core.rewrite;

import java.util.ArrayList;
import java.util.List;

import zone.rong.formatj.api.rules.ArrayRules;
import zone.rong.formatj.api.rules.BracketStyle;
import zone.rong.formatj.core.cst.GreenNode;
import zone.rong.formatj.core.cst.ProgramTokens;
import zone.rong.formatj.core.cst.SyntaxKind;

/**
 * Moves C-style array brackets from a variable to its type, so {@code int a[]} becomes {@code int[] a}.
 *
 * <p>Method return brackets, annotated brackets, varargs and brackets with comments are left alone,
 * and so is a declaration whose declarators do not all carry the same brackets.
 */
public final class ArrayBracketRewrite implements Rewrite {

    @Override
    public String name() {
        return ArrayRules.C_STYLE_BRACKETS.key();
    }

    @Override
    public boolean enabled(RewriteContext context) {
        return context.rule(ArrayRules.C_STYLE_BRACKETS) == BracketStyle.JAVA;
    }

    @Override
    public GreenNode rewrite(GreenNode node, RewriteContext context) {
        return switch (node.kind()) {
            case FIELD_DECLARATION, LOCAL_VARIABLE_DECLARATION -> rewriteDeclarators(node, context);
            case PARAMETER -> rewriteParameter(node, context);
            default -> node;
        };
    }

    private static GreenNode rewriteDeclarators(GreenNode declaration, RewriteContext context) {
        List<GreenNode> children = declaration.children();
        int first = -1;
        int dimensions = -1;
        for (int i = 0; i < children.size(); i++) {
            if (children.get(i).kind() != SyntaxKind.VARIABLE_DECLARATOR) {
                continue;
            }
            int count = dimensions(children.get(i).children());
            if (count <= 0 || (dimensions >= 0 && count != dimensions)) {
                return declaration;
            }
            dimensions = count;
            first = first < 0 ? i : first;
        }
        if (first < 1 || endsWithEllipsis(children.get(first - 1))) {
            return declaration;
        }

        List<TokenEdit> edits = new ArrayList<>();
        List<GreenNode> rewritten = new ArrayList<>(children);
        for (int i = first; i < children.size(); i++) {
            if (children.get(i).kind() != SyntaxKind.VARIABLE_DECLARATOR) {
                continue;
            }
            List<GreenNode> declarator = children.get(i).children();
            TokenEdit delete = delete(declarator.subList(1, 1 + 2 * dimensions), context);
            if (delete == null) {
                return declaration;
            }
            edits.add(delete);
            rewritten.set(
                i,
                GreenNode.branch(
                    SyntaxKind.VARIABLE_DECLARATOR,
                    concat(declarator.subList(0, 1), declarator.subList(1 + 2 * dimensions, declarator.size()))
                )
            );
        }

        TokenEdit insert = insert(children.get(first).children().getFirst(), dimensions, context);
        if (insert == null) {
            return declaration;
        }
        edits.add(insert);
        edits.forEach(context::record);
        rewritten.set(first - 1, withBrackets(children.get(first - 1), dimensions));
        return GreenNode.branch(declaration.kind(), rewritten);
    }

    private static GreenNode rewriteParameter(GreenNode parameter, RewriteContext context) {
        List<GreenNode> children = parameter.children();
        int end = children.size();
        while (end >= 2 && isLeaf(children.get(end - 1), "]") && isLeaf(children.get(end - 2), "[")) {
            end -= 2;
        }
        int dimensions = (children.size() - end) / 2;
        if (dimensions == 0 ||
            end < 2 ||
            !(children.get(end - 1) instanceof GreenNode.Leaf name) ||
            endsWithEllipsis(children.get(end - 2))) {
            return parameter;
        }

        List<GreenNode> brackets = children.subList(end, children.size());
        TokenEdit delete = delete(brackets, context);
        TokenEdit insert = insert(name, dimensions, context);
        if (delete == null || insert == null) {
            return parameter;
        }
        context.record(delete);
        context.record(insert);

        List<GreenNode> rewritten = new ArrayList<>(children.subList(0, end));
        rewritten.set(end - 2, withBrackets(children.get(end - 2), dimensions));
        return GreenNode.branch(parameter.kind(), rewritten);
    }

    /** The number of plain bracket pairs after a declarator's name, or -1 when annotated. */
    private static int dimensions(List<GreenNode> declarator) {
        int next = 1;
        while (next + 1 < declarator.size() &&
            isLeaf(declarator.get(next), "[") &&
            isLeaf(declarator.get(next + 1), "]")) {
            next += 2;
        }
        if (next < declarator.size() && declarator.get(next).kind() == SyntaxKind.ANNOTATION) {
            return -1;
        }
        return (next - 1) / 2;
    }

    /** Deletes a run of bracket leaves that sit next to each other in the original program. */
    private static TokenEdit delete(List<GreenNode> brackets, RewriteContext context) {
        int position = context.firstPosition(brackets.getFirst());
        List<String> lexemes = new ArrayList<>();
        for (int i = 0; i < brackets.size(); i++) {
            if (position < 0 ||
                context.firstPosition(brackets.get(i)) != position + i ||
                Synthetic.carriesComments(brackets.get(i))) {
                return null;
            }
            lexemes.add(((GreenNode.Leaf) brackets.get(i)).lexeme());
        }
        return new TokenEdit(
            ArrayRules.C_STYLE_BRACKETS,
            "array brackets moved to the type",
            position,
            lexemes,
            List.of(),
            TokenEdit.Bias.INNERMOST_FIRST
        );
    }

    private static TokenEdit insert(GreenNode name, int dimensions, RewriteContext context) {
        int position = context.firstPosition(name);
        if (position < 0) {
            return null;
        }
        List<String> brackets = new ArrayList<>();
        for (int i = 0; i < dimensions; i++) {
            brackets.addAll(List.of("[", "]"));
        }
        return new TokenEdit(
            ArrayRules.C_STYLE_BRACKETS,
            "array brackets moved to the type",
            position,
            List.of(),
            brackets,
            TokenEdit.Bias.INNERMOST_FIRST
        );
    }

    private static GreenNode withBrackets(GreenNode type, int dimensions) {
        GreenNode array = type;
        for (int i = 0; i < dimensions; i++) {
            array = GreenNode.branch(
                SyntaxKind.ARRAY_TYPE,
                List.of(array, Synthetic.separator("["), Synthetic.separator("]"))
            );
        }
        return array;
    }

    private static boolean endsWithEllipsis(GreenNode type) {
        List<GreenNode.Leaf> leaves = ProgramTokens.leaves(type);
        return !leaves.isEmpty() && leaves.getLast().decodedLexeme().equals("...");
    }

    private static boolean isLeaf(GreenNode node, String lexeme) {
        return node instanceof GreenNode.Leaf leaf && leaf.decodedLexeme().equals(lexeme);
    }

    private static List<GreenNode> concat(List<GreenNode> first, List<GreenNode> second) {
        List<GreenNode> all = new ArrayList<>(first);
        all.addAll(second);
        return all;
    }

}
