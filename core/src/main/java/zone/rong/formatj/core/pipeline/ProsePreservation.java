package zone.rong.formatj.core.pipeline;

import zone.rong.formatj.api.Style;
import zone.rong.formatj.api.rules.JavadocTagOrder;
import zone.rong.formatj.api.rules.JavadocRules;
import zone.rong.formatj.core.comment.Prose;
import zone.rong.formatj.core.cst.GreenNode;
import zone.rong.formatj.core.cst.SyntaxToken;
import zone.rong.formatj.core.lexer.Token;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Checks that laying the file out did not change what its comments say.
 *
 * <p>The token check is blind to comments, because comments are not significant tokens. That was
 * affordable while the formatter only ever moved a comment; it stops being affordable the moment a
 * rule is allowed to re-wrap one, because re-wrapping and quietly dropping a line look identical to
 * every other check the formatter has. This is the check that tells them apart, and it is what
 * {@code comments.reflow} and the {@code javadoc.*} rules were waiting for.
 *
 * <p>The law is: <em>the same words, in the same order, and every verbatim region character for
 * character.</em> Whitespace between words is layout and may change freely — that is the entire
 * point of re-wrapping — but a word may not be altered, added, dropped, or joined to its neighbour,
 * and a {@code {@code}} block, a {@code <pre>} block or a {@code @snippet} may not be reformatted at
 * all. {@link Prose} defines both halves, so the rule that rewraps and the check that polices it
 * cannot disagree about where a code sample begins.
 *
 * <h2>Where it is anchored</h2>
 *
 * <p>Between the tree the rewrite stage produced and the tree the output parses back to — not
 * between the original and the output. Rewriting may legitimately move comments about, and
 * {@code RewriteVerification} already holds it to keeping every one of them intact. What is left for
 * this check is layout, and layout never reorders a comment, so the comparison can be a sequence
 * rather than a bag and stays strict.
 *
 * <h2>The two allowances</h2>
 *
 * <ul>
 *   <li>A bare {@code <p>} is dropped from both sides. It marks a paragraph rather than saying
 *       anything, which is what lets {@code javadoc.add-paragraph-tags} write one.
 *   <li>{@code javadoc.tag-order = canonical} reorders whole block tags on purpose, so with that rule
 *       on the comparison falls back to a bag when the sequence differs. Nothing else is relaxed: a
 *       word that went missing still fails, and the strict sequence check is what runs whenever the
 *       rule is off.
 * </ul>
 */
public final class ProsePreservation {

    private ProsePreservation() { }

    /**
     * Verifies that the formatted output says what the tree it came from said.
     *
     * @param before the tree as it went into layout, after any rewriting
     * @param after the tree obtained by re-parsing the formatter's output
     * @param style the style that was in force, which says whether prose may be reordered
     * @return a description of the first problem, or null when the prose came through intact
     */
    public static String firstDifference(GreenNode before, GreenNode after, Style style) {
        List<Prose.Atom> was = prose(before);
        List<Prose.Atom> now = prose(after);
        String difference = compare(was, now);
        if (difference == null) {
            return null;
        }
        if (style.get(JavadocRules.TAG_ORDER) == JavadocTagOrder.PRESERVE) {
            return difference;
        }
        // Reordering block tags reorders the words riding on them, which is what the rule is for.
        String reordered = sameBag(was, now);
        return reordered == null ? null : reordered;
    }

    // ------------------------------------------------------------ gathering

    /** Every atom of every comment in the tree, in source order. */
    static List<Prose.Atom> prose(GreenNode node) {
        List<Prose.Atom> atoms = new ArrayList<>();
        for (Token comment : comments(node)) {
            for (Prose.Atom atom : Prose.atoms(comment)) {
                if (atom.isParagraphMarker()) {
                    continue;
                }
                atoms.add(atom.verbatim() ? Prose.Atom.verbatim(trimLineEnds(atom.text())) : atom);
            }
        }
        return List.copyOf(atoms);
    }

    private static List<Token> comments(GreenNode node) {
        List<Token> comments = new ArrayList<>();
        collect(node, comments);
        return comments;
    }

    private static void collect(GreenNode node, List<Token> comments) {
        if (node instanceof GreenNode.Leaf leaf) {
            SyntaxToken token = leaf.token();
            comments.addAll(token.leadingComments());
            comments.addAll(token.trailingComments());
            return;
        }
        for (GreenNode child : node.children()) {
            collect(child, comments);
        }
    }

    /**
     * Drops the trailing spaces of every line of a verbatim region.
     *
     * <p>Applied to both sides, so it forgives nothing but the one difference that is already the
     * file rule's to make: {@code file.trim-trailing-whitespace} strips them as the line is closed,
     * and space at the end of a line cannot be what a code sample means.
     */
    private static String trimLineEnds(String text) {
        String[] lines = text.split("\n", -1);
        StringBuilder out = new StringBuilder(text.length());
        for (int i = 0; i < lines.length; i++) {
            if (i > 0) {
                out.append('\n');
            }
            int end = lines[i].length();
            while (end > 0 && (lines[i].charAt(end - 1) == ' ' || lines[i].charAt(end - 1) == '\t')) {
                end--;
            }
            out.append(lines[i], 0, end);
        }
        return out.toString();
    }

    // ----------------------------------------------------------- comparison

    private static String compare(List<Prose.Atom> was, List<Prose.Atom> now) {
        int shared = Math.min(was.size(), now.size());
        for (int i = 0; i < shared; i++) {
            Prose.Atom left = was.get(i);
            Prose.Atom right = now.get(i);
            if (left.equals(right)) {
                continue;
            }
            if (left.verbatim() || right.verbatim()) {
                return "a verbatim region was reformatted: " + describe(left) + " became " + describe(right);
            }
            return "the word '" + left.text() + "' became '" + right.text() + "'";
        }
        if (was.size() > now.size()) {
            return "comment text was lost: " + describe(was.get(shared)) + " is no longer there";
        }
        if (now.size() > was.size()) {
            return "comment text appeared: " + describe(now.get(shared)) + " was not there";
        }
        return null;
    }

    /** Whether both sides hold the same atoms, in any order. */
    private static String sameBag(List<Prose.Atom> was, List<Prose.Atom> now) {
        Map<Prose.Atom, Integer> counts = new LinkedHashMap<>();
        for (Prose.Atom atom : was) {
            counts.merge(atom, 1, Integer::sum);
        }
        for (Prose.Atom atom : now) {
            Integer count = counts.get(atom);
            if (count == null || count == 0) {
                return "comment text appeared: " + describe(atom) + " was not there";
            }
            counts.put(atom, count - 1);
        }
        for (Map.Entry<Prose.Atom, Integer> entry : counts.entrySet()) {
            if (entry.getValue() > 0) {
                return "comment text was lost: " + describe(entry.getKey()) + " is no longer there";
            }
        }
        return null;
    }

    private static String describe(Prose.Atom atom) {
        String text = atom.text();
        String shortened = text.length() <= 40 ? text : text.substring(0, 37) + "...";
        return "'" + shortened.replace("\n", "\\n") + "'";
    }

}
