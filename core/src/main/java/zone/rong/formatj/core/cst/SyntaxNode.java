package zone.rong.formatj.core.cst;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * A green node placed in a file: the same immutable node, plus its parent and absolute offset.
 *
 * <p>Red nodes are created on demand while walking the tree and are cheap to throw away, so the
 * emitter can navigate upwards without the tree itself carrying parent pointers.
 */
public final class SyntaxNode {

    private final GreenNode green;
    private final SyntaxNode parent;
    private final int offset;

    private SyntaxNode(GreenNode green, SyntaxNode parent, int offset) {
        this.green = green;
        this.parent = parent;
        this.offset = offset;
    }

    /** Wraps a green node as the root of a file. */
    public static SyntaxNode root(GreenNode green) {
        return new SyntaxNode(green, null, 0);
    }

    public GreenNode green() {
        return green;
    }

    public SyntaxKind kind() {
        return green.kind();
    }

    public Optional<SyntaxNode> parent() {
        return Optional.ofNullable(parent);
    }

    /** Offset of this node's first character in the file. */
    public int offset() {
        return offset;
    }

    public int endOffset() {
        return offset + green.width();
    }

    /** Children in source order, each with its own absolute offset. */
    public List<SyntaxNode> children() {
        List<SyntaxNode> children = new ArrayList<>(green.children().size());
        int childOffset = offset;
        for (GreenNode child : green.children()) {
            children.add(new SyntaxNode(child, this, childOffset));
            childOffset += child.width();
        }
        return List.copyOf(children);
    }

    public String text() {
        return green.text();
    }

    @Override
    public String toString() {
        return kind() + "@" + offset + "+" + green.width();
    }

}
