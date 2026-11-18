package zone.rong.formatj.core.rewrite;

import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import zone.rong.formatj.api.rules.WrappingRules;
import zone.rong.formatj.core.cst.GreenNode;
import zone.rong.formatj.core.cst.ProgramTokens;
import zone.rong.formatj.core.cst.SyntaxKind;
import zone.rong.formatj.core.cst.SyntaxToken;
import zone.rong.formatj.core.lexer.JavaLexer;
import zone.rong.formatj.core.lexer.Token;
import zone.rong.formatj.core.lexer.TokenKind;

/**
 * Splits string literals that run past the line length into concatenations.
 *
 * <p>Where a literal lands is only known once the file is printed, so this runs between layout passes
 * rather than in the {@link RewriteStage}. Each pass splits the literals the last print left too long,
 * and the caller prints again until nothing more splits.
 *
 * <p>The pieces are built into the same left-leaning chain the parser makes of {@code a + b + c}, so
 * the reprinted file parses back to the tree that printed it.
 */
public final class StringSplit {

    private final int maxWidth;
    private final int tabWidth;

    // Every piece, including the literals not yet split, mapped to the original literal's position.
    private final Map<GreenNode.Leaf, Integer> origin = new IdentityHashMap<>();
    private final Map<Integer, String> split = new LinkedHashMap<>();

    public StringSplit(GreenNode original, int maxWidth, int tabWidth) {
        this.maxWidth = maxWidth;
        this.tabWidth = tabWidth;
        ProgramTokens.positions(original).forEach((leaf, position) -> {
            if (leaf.token().token().kind() == TokenKind.STRING_LITERAL) {
                origin.put(leaf, position);
            }
        });
    }

    /**
     * The string literal leaves of {@code root} that {@code +} can be put inside without changing how
     * the expression around them parses.
     */
    public static Set<GreenNode.Leaf> splittable(GreenNode root) {
        Set<GreenNode.Leaf> found = Collections.newSetFromMap(new IdentityHashMap<>());
        collectSplittable(root, found);
        return found;
    }

    /**
     * A literal's lexeme cut after spaces and {@code \n} escapes into pieces of at most {@code budget}
     * characters each, quotes included. A word longer than the budget is a piece of its own.
     */
    static List<String> pieces(String lexeme, int budget) {
        String content = lexeme.substring(1, lexeme.length() - 1);
        List<String> words = new ArrayList<>();
        int wordStart = 0;
        int i = 0;
        while (i < content.length()) {
            int unit = escapeLength(content, i);
            i += unit;
            boolean space = unit == 1 && content.charAt(i - 1) == ' ';
            boolean newline = unit == 2 && content.charAt(i - 1) == 'n';
            if (i < content.length() && (newline || space && content.charAt(i) != ' ')) {
                words.add(content.substring(wordStart, i));
                wordStart = i;
            }
        }
        words.add(content.substring(wordStart));

        List<String> pieces = new ArrayList<>();
        StringBuilder piece = new StringBuilder();
        int newline = -1;
        for (String word : words) {
            while (!piece.isEmpty() && piece.length() + word.length() + 2 > budget) {
                // A line of a multi-line string reads better whole, so a piece ends at its last newline.
                int cut = newline > 0 ? newline : piece.length();
                pieces.add('"' + piece.substring(0, cut) + '"');
                piece.delete(0, cut);
                newline = -1;
            }
            piece.append(word);
            if (word.endsWith("\\n")) {
                newline = piece.length();
            }
        }
        pieces.add('"' + piece.toString() + '"');
        return pieces;
    }

    private static void collectSplittable(GreenNode node, Set<GreenNode.Leaf> found) {
        if (node instanceof GreenNode.Leaf || node.kind().isVerbatim()) {
            return;
        }
        List<GreenNode> children = node.children();
        for (int i = 0; i < children.size(); i++) {
            GreenNode child = children.get(i);
            GreenNode.Leaf literal = stringLiteral(child);
            if (literal != null && concatenates(node, i)) {
                found.add(literal);
            }
            collectSplittable(child, found);
        }
    }

    /** Whether child {@code index} of {@code parent} may become {@code a + b}. */
    private static boolean concatenates(GreenNode parent, int index) {
        List<GreenNode> children = parent.children();
        boolean last = index == children.size() - 1;
        return switch (parent.kind()) {
            case BINARY_EXPRESSION -> isPlus(parent);
            case ARGUMENTS, ARRAY_INITIALIZER, ANNOTATION_ARGUMENTS, PARENTHESIZED_EXPRESSION -> true;
            case VARIABLE_DECLARATOR, ASSIGNMENT_EXPRESSION, ANNOTATION_ELEMENT, DEFAULT_VALUE, LAMBDA_EXPRESSION ->
                last;
            case RETURN_STATEMENT, YIELD_STATEMENT -> index == 1;
            case TERNARY_EXPRESSION -> index == 2 || index == 4;
            case ASSERT_STATEMENT -> index == 3;
            default -> false;
        };
    }

    private static boolean isPlus(GreenNode binary) {
        List<GreenNode> children = binary.children();
        return children.size() == 3 &&
            children.get(1) instanceof GreenNode.Leaf operator &&
            operator.lexeme().equals("+");
    }

    /** The leaf of a plain string literal expression, or null. */
    private static GreenNode.Leaf stringLiteral(GreenNode node) {
        if (node.kind() != SyntaxKind.LITERAL || !(node.children().getFirst() instanceof GreenNode.Leaf leaf)) {
            return null;
        }
        Token token = leaf.token().token();
        return token.kind() == TokenKind.STRING_LITERAL && !token.hasUnicodeEscape() ? leaf : null;
    }

    /** How many characters the escape or plain character at {@code i} spans. */
    private static int escapeLength(String content, int i) {
        if (content.charAt(i) != '\\') {
            return 1;
        }
        char first = content.charAt(i + 1);
        if (first < '0' || first > '7') {
            return 2;
        }
        int digits = first <= '3' ? 3 : 2;
        int length = 1;
        while (length < digits && i + 1 + length < content.length()) {
            char next = content.charAt(i + 1 + length);
            if (next < '0' || next > '7') {
                break;
            }
            length++;
        }
        return 1 + length;
    }

    /** {@code left + a + b + ...}, using {@code operator} for the first link when there is one. */
    private static GreenNode chain(GreenNode left, List<GreenNode> operands, GreenNode operator) {
        GreenNode chain = left;
        for (GreenNode operand : operands) {
            GreenNode plus = operator != null ? operator : Synthetic.operator("+");
            operator = null;
            chain = GreenNode.branch(SyntaxKind.BINARY_EXPRESSION, List.of(chain, plus, operand));
        }
        return chain;
    }

    /**
     * Splits every eligible literal that {@code printed}, the print of {@code tree}, shows running past
     * the line length.
     *
     * @return the tree with those literals split, or {@code tree} itself when none could be
     */
    public GreenNode apply(GreenNode tree, String printed) {
        Map<GreenNode.Leaf, List<String>> pieces = new IdentityHashMap<>();
        List<GreenNode.Leaf> literals = new ArrayList<>();
        for (GreenNode.Leaf leaf : ProgramTokens.leaves(tree)) {
            if (leaf.token().token().kind() == TokenKind.STRING_LITERAL) {
                literals.add(leaf);
            }
        }
        Set<GreenNode.Leaf> eligible = splittable(tree);
        List<String> lines = printed.lines().toList();
        int index = 0;
        for (Token token : JavaLexer.tokenize(printed)) {
            if (token.kind() != TokenKind.STRING_LITERAL) {
                continue;
            }
            GreenNode.Leaf leaf = index < literals.size() ? literals.get(index++) : null;
            if (leaf == null || !leaf.lexeme().equals(token.text())) {
                // The print and the tree disagree, so nothing printed can be pinned on a leaf.
                return tree;
            }
            if (!eligible.contains(leaf) || !origin.containsKey(leaf) || Synthetic.carriesComments(leaf)) {
                continue;
            }
            String line = lines.get(token.line() - 1);
            int start = visualColumn(line, token.column() - 1);
            int end = visualColumn(line, token.column() - 1 + token.length());
            String rest = line.substring(token.column() - 1 + token.length()).stripTrailing();
            boolean closersOnly = rest.chars().allMatch(c -> ")];,}".indexOf(c) >= 0);
            if (end + (closersOnly ? rest.length() : 0) <= maxWidth) {
                continue;
            }
            int budget = Math.max(maxWidth - start - Math.max(2, closersOnly ? rest.length() : 0), maxWidth / 3);
            List<String> split = pieces(token.text(), budget);
            if (split.size() > 1) {
                pieces.put(leaf, split);
            }
        }
        return pieces.isEmpty() ? tree : rebuild(tree, pieces);
    }

    /** One edit per split literal, from its original lexeme to the pieces {@code tree} now holds. */
    public List<TokenEdit> edits(GreenNode tree) {
        Map<Integer, List<String>> inserted = new LinkedHashMap<>();
        for (GreenNode.Leaf leaf : ProgramTokens.leaves(tree)) {
            Integer position = origin.get(leaf);
            if (position != null && split.containsKey(position)) {
                List<String> tokens = inserted.computeIfAbsent(position, key -> new ArrayList<>());
                if (!tokens.isEmpty()) {
                    tokens.add("+");
                }
                tokens.add(leaf.lexeme());
            }
        }
        List<TokenEdit> edits = new ArrayList<>();
        inserted.forEach((position, tokens) -> edits.add(new TokenEdit(
            WrappingRules.SPLIT_LONG_STRINGS,
            "split a long string literal",
            position,
            List.of(split.get(position)),
            tokens,
            TokenEdit.Bias.INNERMOST_FIRST
        )));
        return edits;
    }

    private int visualColumn(String line, int index) {
        int column = 0;
        for (int i = 0; i < index; i++) {
            column = line.charAt(i) == '\t' ? (column / tabWidth + 1) * tabWidth : column + 1;
        }
        return column;
    }

    private GreenNode rebuild(GreenNode node, Map<GreenNode.Leaf, List<String>> pieces) {
        if (node instanceof GreenNode.Leaf || node.kind().isVerbatim()) {
            return node;
        }
        List<GreenNode> children = new ArrayList<>(node.children());
        boolean changed = false;
        for (int i = 0; i < children.size(); i++) {
            GreenNode child = children.get(i);
            GreenNode rebuilt = rebuild(child, pieces);
            GreenNode.Leaf literal = stringLiteral(child);
            boolean rightOperand = node.kind() == SyntaxKind.BINARY_EXPRESSION && i == 2;
            if (literal != null && pieces.containsKey(literal) && !rightOperand) {
                List<GreenNode> literals = literals(literal, pieces.get(literal));
                rebuilt = chain(literals.getFirst(), literals.subList(1, literals.size()), null);
            }
            changed |= rebuilt != child;
            children.set(i, rebuilt);
        }
        GreenNode.Leaf right = node.kind() == SyntaxKind.BINARY_EXPRESSION ? stringLiteral(children.get(2)) : null;
        if (right != null && pieces.containsKey(right)) {
            // The parser leans a run of + to the left, so the pieces extend the run rather than nest.
            List<GreenNode> literals = literals(right, pieces.get(right));
            return chain(children.getFirst(), literals, children.get(1));
        }
        return changed ? GreenNode.branch(node.kind(), children) : node;
    }

    /** The pieces as literal expressions, the first keeping the leading trivia and the last the trailing. */
    private List<GreenNode> literals(GreenNode.Leaf leaf, List<String> lexemes) {
        Integer position = origin.get(leaf);
        split.putIfAbsent(position, leaf.lexeme());
        SyntaxToken token = leaf.token();
        List<GreenNode> literals = new ArrayList<>();
        for (int i = 0; i < lexemes.size(); i++) {
            GreenNode.Leaf piece = GreenNode.leaf(new SyntaxToken(
                i == 0 ? token.leading() : List.of(),
                Token.synthetic(TokenKind.STRING_LITERAL, lexemes.get(i)),
                i == lexemes.size() - 1 ? token.trailing() : List.of()
            ));
            origin.put(piece, position);
            literals.add(GreenNode.branch(SyntaxKind.LITERAL, List.of(piece)));
        }
        return literals;
    }

}
