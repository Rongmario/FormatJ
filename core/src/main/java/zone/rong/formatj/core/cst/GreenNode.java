package zone.rong.formatj.core.cst;

import java.util.List;

/**
 * An immutable, position-free syntax node.
 *
 * <p>Green nodes know their own width but not where they sit in the file, which is what lets them be
 * shared and rebuilt cheaply. {@link SyntaxNode} pairs a green node with a parent and an absolute
 * offset when a caller needs to know where something is.
 */
public sealed interface GreenNode permits GreenNode.Leaf, GreenNode.Branch {

    SyntaxKind kind();

    /** Characters this node covers, trivia included. */
    int width();

    List<GreenNode> children();

    /** Appends this node's exact source text, preserving losslessness. */
    void appendTo(StringBuilder out);

    /** This node's exact source text. */
    default String text() {
        StringBuilder out = new StringBuilder(width());
        appendTo(out);
        return out.toString();
    }

    static Leaf leaf(SyntaxToken token) {
        return new Leaf(token);
    }

    static Branch branch(SyntaxKind kind, List<GreenNode> children) {
        return new Branch(kind, List.copyOf(children));
    }

    /** A node wrapping exactly one token and its trivia. */
    record Leaf(SyntaxToken token) implements GreenNode {

        @Override
        public SyntaxKind kind() {
            return SyntaxKind.TOKEN;
        }

        @Override
        public int width() {
            return token.width();
        }

        @Override
        public List<GreenNode> children() {
            return List.of();
        }

        @Override
        public void appendTo(StringBuilder out) {
            token.appendTo(out);
        }

        /** The token's text without trivia, e.g. {@code "{"}. */
        public String lexeme() {
            return token.text();
        }

    }

    /** A node with child nodes. */
    record Branch(SyntaxKind kind, List<GreenNode> children) implements GreenNode {

        @Override
        public int width() {
            int width = 0;
            for (GreenNode child : children) {
                width += child.width();
            }
            return width;
        }

        @Override
        public void appendTo(StringBuilder out) {
            for (GreenNode child : children) {
                child.appendTo(out);
            }
        }

    }

}
