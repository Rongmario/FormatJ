package zone.rong.formatj.core.imports;

import zone.rong.formatj.core.cst.GreenNode;
import zone.rong.formatj.core.cst.ProgramTokens;
import zone.rong.formatj.core.cst.SyntaxKind;
import java.util.List;

/**
 * One import declaration, read well enough to sort and to reason about.
 *
 * <p>Only what the import rules need: whether it is static, a module import or on demand, the name
 * it brings in, and the simple name a file would have to mention for the import to be earning its
 * place.
 *
 * @param node the declaration itself, reused as-is when imports are reordered
 * @param isStatic an {@code import static}
 * @param isModule an {@code import module}
 * @param isWildcard an on-demand import, ending in {@code .*}
 * @param name the dotted name, with the trailing {@code .*} of an on-demand import left off
 * @param simpleName the last segment of a single-type import, or null when there is no one name the
 *     import is responsible for, which is the case for on-demand and module imports
 */
public record ImportEntry(
        GreenNode node,
        boolean isStatic,
        boolean isModule,
        boolean isWildcard,
        String name,
        String simpleName) {

    /** Reads an {@code IMPORT_DECLARATION}, or returns null when the node is not one. */
    public static ImportEntry of(GreenNode node) {
        if (node.kind() != SyntaxKind.IMPORT_DECLARATION) {
            return null;
        }
        return parse(node, ProgramTokens.lexemes(node));
    }

    /**
     * Reads a declaration from its tokens alone, with no node behind it.
     *
     * <p>Verification works from the tokens an edit declared, not from the tree the rewrite built, so
     * it needs to understand an import it has only the lexemes of.
     */
    public static ImportEntry ofLexemes(List<String> tokens) {
        return parse(null, tokens);
    }

    private static ImportEntry parse(GreenNode node, List<String> tokens) {
        if (tokens.size() < 3 || !tokens.getFirst().equals("import")) {
            return null;
        }

        int at = 1;
        boolean isStatic = tokens.get(at).equals("static");
        boolean isModule = tokens.get(at).equals("module");
        if (isStatic || isModule) {
            at++;
        }

        StringBuilder name = new StringBuilder();
        boolean isWildcard = false;
        for (; at < tokens.size(); at++) {
            String token = tokens.get(at);
            if (token.equals(";")) {
                break;
            }
            if (token.equals("*")) {
                isWildcard = true;
                break;
            }
            name.append(token);
        }

        String dotted = trimTrailingDot(name.toString());
        if (dotted.isEmpty()) {
            return null;
        }
        String simpleName = isWildcard || isModule ? null : lastSegment(dotted);
        return new ImportEntry(node, isStatic, isModule, isWildcard, dotted, simpleName);
    }

    /** The declaration's tokens, which is what an edit to the import run is expressed in. */
    public List<String> lexemes() {
        return ProgramTokens.lexemes(node);
    }

    /** The declaration as it would be written, for comparing one against another. */
    public String text() {
        return (isStatic ? "import static " : isModule ? "import module " : "import ") + name
                + (isWildcard ? ".*" : "") + ";";
    }

    /**
     * Whether this import can be deleted at all, whatever the file references.
     *
     * <p>An on-demand import brings in names that are never spelled out, and a module import brings
     * in whole packages, so neither can be shown to be unused by reading the file's tokens. Deleting
     * one on the strength of a name search would be guessing.
     */
    public boolean isRemovable() {
        return simpleName != null;
    }

    private static String trimTrailingDot(String name) {
        return name.endsWith(".") ? name.substring(0, name.length() - 1) : name;
    }

    private static String lastSegment(String dotted) {
        int dot = dotted.lastIndexOf('.');
        return dot < 0 ? dotted : dotted.substring(dot + 1);
    }

}
