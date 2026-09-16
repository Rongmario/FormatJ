package zone.rong.formatj.core.rewrite;

import zone.rong.formatj.api.rules.ImportRules;
import zone.rong.formatj.core.cst.GreenNode;
import zone.rong.formatj.core.cst.SyntaxKind;
import zone.rong.formatj.core.cst.SyntaxToken;
import zone.rong.formatj.core.imports.ImportEntry;
import zone.rong.formatj.core.imports.ImportOrder;
import zone.rong.formatj.core.imports.ImportUsage;
import zone.rong.formatj.core.lexer.Token;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Puts a file's imports in order, and deletes the ones it can prove nothing refers to.
 *
 * <p>Reordering imports cannot change what a program means. Two single-type imports of the same
 * simple name will not compile whatever order they are in, and on-demand imports lose to single-type
 * imports by rule rather than by position, so the whole run can be rearranged freely. Deleting one is
 * a different matter, and is done only under {@code imports.remove-unused} and only where
 * {@link ImportUsage} finds no mention of the name anywhere else in the file, comments included.
 *
 * <p>The blank line between groups is not this rewrite's business. Blank lines are whitespace, the
 * emitter owns whitespace, and both consult {@link ImportOrder} so that they agree on where one group
 * ends and the next begins.
 *
 * <h2>What this deliberately does not do</h2>
 *
 * <p>Collapsing several single-type imports into one on-demand import is not offered at all. It is
 * not a rearrangement of what is there; it changes which names the file resolves and can quietly pick
 * up a different type of the same simple name from the wildcarded package. Deciding that needs the
 * classpath, which a formatter does not have, so there is no rule for it.
 */
public final class ImportRewrite implements Rewrite {

    @Override
    public String name() {
        return "imports";
    }

    @Override
    public boolean enabled(RewriteContext context) {
        return ImportOrder.sorts(context.style()) || context.rule(ImportRules.REMOVE_UNUSED);
    }

    @Override
    public GreenNode rewrite(GreenNode node, RewriteContext context) {
        if (node.kind() != SyntaxKind.COMPILATION_UNIT) {
            return node;
        }
        List<ImportEntry> entries = ImportOrder.run(node);
        if (entries.isEmpty()) {
            return node;
        }

        boolean sorts = ImportOrder.sorts(context.style());
        List<ImportEntry> kept = context.rule(ImportRules.REMOVE_UNUSED) ? used(entries, node) : entries;
        List<ImportEntry> ordered = sorts ? ImportOrder.sorted(kept, context.style()) : kept;

        List<String> before = lexemes(entries);
        List<String> after = lexemes(ordered);
        if (!before.equals(after)) {
            int position = context.firstPosition(entries.getFirst().node());
            if (position < 0) {
                return node;
            }
            context.record(
                    new TokenEdit(
                            ImportRules.ORDER,
                            "imports reordered or removed",
                            position,
                            before,
                            after,
                            TokenEdit.Bias.INNERMOST_FIRST));
        }

        return replaceRun(node, entries, ordered, sorts);
    }

    /** The imports whose simple name the rest of the file still mentions. */
    private static List<ImportEntry> used(List<ImportEntry> entries, GreenNode compilationUnit) {
        Set<String> mentioned = ImportUsage.namesMentioned(compilationUnit);
        List<ImportEntry> kept = new ArrayList<>(entries.size());
        for (ImportEntry entry : entries) {
            if (!entry.isRemovable()
                    || mentioned.contains(entry.simpleName())
                    || ImportUsage.mentionedInComments(compilationUnit, entry.simpleName())) {
                kept.add(entry);
            }
        }
        return kept;
    }

    private static List<String> lexemes(List<ImportEntry> entries) {
        List<String> lexemes = new ArrayList<>();
        for (ImportEntry entry : entries) {
            lexemes.addAll(entry.lexemes());
        }
        return lexemes;
    }

    /** Swaps the run of import declarations for the reordered one, leaving the rest of the file alone. */
    private static GreenNode replaceRun(
            GreenNode compilationUnit,
            List<ImportEntry> before,
            List<ImportEntry> after,
            boolean sorted) {
        int start = ImportOrder.runStart(compilationUnit);
        List<GreenNode> replacement = new ArrayList<>(after.size());
        boolean changed = before.size() != after.size();
        for (int i = 0; i < after.size(); i++) {
            GreenNode declaration = after.get(i).node();
            GreenNode rebuilt = sorted ? withoutBlankLinesBefore(declaration) : declaration;
            if (!changed && rebuilt != compilationUnit.children().get(start + i)) {
                changed = true;
            }
            replacement.add(rebuilt);
        }
        if (!changed) {
            return compilationUnit;
        }
        List<GreenNode> children = new ArrayList<>(compilationUnit.children());
        children.subList(start, start + before.size()).clear();
        children.addAll(start, replacement);
        return GreenNode.branch(compilationUnit.kind(), children);
    }

    /**
     * Drops the blank lines an import was written with, so the grouping rule alone decides them.
     *
     * <p>Only the whitespace ahead of the declaration's own comments goes: that is the part
     * {@code blankLinesBefore} reads. Whitespace between a comment and the token it documents is how
     * the emitter spaces the two, and moving an import must not disturb it.
     */
    private static GreenNode withoutBlankLinesBefore(GreenNode declaration) {
        if (declaration instanceof GreenNode.Leaf leaf) {
            SyntaxToken token = leaf.token();
            List<Token> leading = new ArrayList<>(token.leading());
            boolean changed = false;
            while (!leading.isEmpty() && !leading.getFirst().kind().isComment()) {
                leading.removeFirst();
                changed = true;
            }
            return changed ? GreenNode.leaf(new SyntaxToken(leading, token.token(), token.trailing())) : declaration;
        }
        List<GreenNode> children = declaration.children();
        if (children.isEmpty()) {
            return declaration;
        }
        GreenNode first = withoutBlankLinesBefore(children.getFirst());
        if (first == children.getFirst()) {
            return declaration;
        }
        List<GreenNode> rewritten = new ArrayList<>(children);
        rewritten.set(0, first);
        return GreenNode.branch(declaration.kind(), rewritten);
    }

}
