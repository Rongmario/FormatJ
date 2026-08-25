package zone.rong.formatj.core.imports;

import zone.rong.formatj.core.cst.GreenNode;
import zone.rong.formatj.core.cst.SyntaxKind;
import zone.rong.formatj.core.lexer.Token;
import java.util.HashSet;
import java.util.Set;

/**
 * Whether a file still mentions a name, which is all {@code imports.remove-unused} is allowed to go
 * on.
 *
 * <p>A formatter has no type resolution, so "unused" here means the far weaker "the simple name
 * appears nowhere else in the file". That is deliberately generous: it keeps an import that a
 * fully-qualified reference made redundant, and it keeps an import whose name is only mentioned in a
 * comment. Both of those are imports somebody might argue should go, and neither is worth the risk of
 * being wrong about.
 *
 * <p>Javadoc counts. An import referenced only by {@code {@link Foo}} is load-bearing for the
 * documentation even though the compiler would not miss it.
 */
public final class ImportUsage {

    private ImportUsage() { }

    /** The simple names a file mentions anywhere other than in its own import declarations. */
    public static Set<String> namesMentioned(GreenNode compilationUnit) {
        Set<String> names = new HashSet<>();
        collect(compilationUnit, names, false);
        return names;
    }

    /**
     * Whether a comment anywhere in the file spells out this name.
     *
     * <p>Separate from {@link #namesMentioned} because comments are matched as words in prose rather
     * than read as tokens.
     */
    public static boolean mentionedInComments(GreenNode compilationUnit, String name) {
        return commentsMention(compilationUnit, name);
    }

    private static void collect(GreenNode node, Set<String> names, boolean insideImport) {
        if (node instanceof GreenNode.Leaf leaf) {
            if (!insideImport && leaf.token().token().kind().isSignificant()) {
                names.add(leaf.lexeme());
            }
            return;
        }
        boolean isImport = insideImport || node.kind() == SyntaxKind.IMPORT_DECLARATION;
        for (GreenNode child : node.children()) {
            collect(child, names, isImport);
        }
    }

    private static boolean commentsMention(GreenNode node, String name) {
        if (node instanceof GreenNode.Leaf leaf) {
            for (Token comment : leaf.token().leadingComments()) {
                if (mentions(comment.text(), name)) {
                    return true;
                }
            }
            for (Token comment : leaf.token().trailingComments()) {
                if (mentions(comment.text(), name)) {
                    return true;
                }
            }
            return false;
        }
        for (GreenNode child : node.children()) {
            if (commentsMention(child, name)) {
                return true;
            }
        }
        return false;
    }

    /** The name as a whole word, so {@code List} in a comment is not found inside {@code Listener}. */
    private static boolean mentions(String text, String name) {
        int from = 0;
        while (true) {
            int at = text.indexOf(name, from);
            if (at < 0) {
                return false;
            }
            boolean beforeIsBoundary = at == 0 || !isNamePart(text.charAt(at - 1));
            int after = at + name.length();
            boolean afterIsBoundary = after >= text.length() || !isNamePart(text.charAt(after));
            if (beforeIsBoundary && afterIsBoundary) {
                return true;
            }
            from = at + 1;
        }
    }

    private static boolean isNamePart(char character) {
        return Character.isJavaIdentifierPart(character);
    }

}
