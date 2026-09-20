package zone.rong.formatj.core.pipeline;

import zone.rong.formatj.api.Style;
import zone.rong.formatj.api.rules.JavadocTagOrder;
import zone.rong.formatj.api.rules.JavadocRules;
import zone.rong.formatj.core.comment.Javadoc;
import zone.rong.formatj.core.comment.Prose;
import zone.rong.formatj.core.cst.GreenNode;
import zone.rong.formatj.core.cst.SyntaxToken;
import zone.rong.formatj.core.lexer.Token;
import zone.rong.formatj.core.lexer.TokenKind;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Checks that laying the file out did not change what its comments say.
 *
 * <p>The token check is blind to comments, because comments are not significant tokens. That was
 * affordable while the formatter only ever moved a comment; it stops being affordable the moment a
 * rule is allowed to re-wrap one, because re-wrapping and quietly dropping a line look identical to
 * every other check the formatter has. This is the check that tells them apart, and it is what
 * {@code comments.reflow} and the {@code javadoc.*} rules were waiting for.
 *
 * <p>Traditional comments keep the same words in the same order, and every verbatim region survives
 * character for character. Markdown documentation keeps comment, block, and tag boundaries. Plain
 * paragraph words may move between lines, while structural blocks survive character for character.
 * {@link Prose} defines the protected regions shared by the formatter and this check.
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
 *   <li>{@code javadoc.tag-order = canonical} sorts whole tag blocks before comparison. Tag and
 *       comment boundaries remain strict, so text cannot move between unrelated tags or comments.
 * </ul>
 */
public final class ProsePreservation {

    private static final String STRUCTURE = "\u0000documentation-structure:";

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
        return compare(prose(before, style), prose(after, style));
    }

    // ------------------------------------------------------------ gathering

    /** Every atom of every comment in the tree, in source order. */
    static List<Prose.Atom> prose(GreenNode node) {
        return prose(node, Style.defaults());
    }

    private static List<Prose.Atom> prose(GreenNode node, Style style) {
        List<Prose.Atom> atoms = new ArrayList<>();
        List<Token> comments = comments(node);
        boolean canonical = style.get(JavadocRules.TAG_ORDER) == JavadocTagOrder.CANONICAL;
        for (int i = 0; i < comments.size(); i++) {
            Token comment = comments.get(i);
            if (comment.hasUnicodeEscape()) {
                atoms.add(Prose.Atom.verbatim(trimLineEnds(comment.text())));
                continue;
            }
            if (Prose.isMarkdownComment(comment)) {
                List<Token> run = new ArrayList<>();
                run.add(comment);
                while (i + 1 < comments.size()) {
                    Token next = comments.get(i + 1);
                    if (!Prose.isMarkdownComment(next) || next.line() != comments.get(i).line() + 1) {
                        break;
                    }
                    run.add(next);
                    i++;
                }
                addDocumentation(atoms, Javadoc.parseMarkdown(run), canonical);
                continue;
            }
            if (comment.kind() == TokenKind.JAVADOC_COMMENT) {
                addDocumentation(atoms, Javadoc.parse(comment.text()), canonical);
                continue;
            }
            for (Prose.Atom atom : Prose.atoms(comment)) {
                if (atom.isParagraphMarker()) {
                    continue;
                }
                atoms.add(atom.verbatim() ? Prose.Atom.verbatim(trimLineEnds(atom.text())) : atom);
            }
        }
        return List.copyOf(atoms);
    }

    private static void addDocumentation(List<Prose.Atom> atoms, Javadoc comment, boolean canonical) {
        atoms.add(structure("comment-start:" + comment.form()));
        if (comment.form() == Javadoc.Form.MARKDOWN) {
            addMarkdownBlocks(atoms, comment.descriptionBlocks(), comment.markdownIndent());
        } else {
            addTraditionalWords(atoms, comment.description());
        }
        atoms.add(structure("tags-start"));

        List<Javadoc.Tag> tags = new ArrayList<>(comment.tags());
        if (canonical) {
            tags.sort(Comparator.comparingInt(Javadoc::canonicalRank));
        }
        for (Javadoc.Tag tag : tags) {
            atoms.add(structure("tag-start:" + tag.name() + ":" + tag.head()));
            if (comment.form() == Javadoc.Form.MARKDOWN) {
                addMarkdownBlocks(atoms, tag.description(), comment.markdownIndent());
            } else {
                List<String> body = new ArrayList<>();
                for (Javadoc.Block block : tag.description()) {
                    body.addAll(block.lines());
                }
                addTraditionalWords(atoms, body);
            }
            atoms.add(structure("tag-end"));
        }
        atoms.add(structure("comment-end"));
    }

    private static void addTraditionalWords(List<Prose.Atom> atoms, List<String> lines) {
        for (Prose.Atom atom : Prose.atoms(String.join("\n", lines))) {
            if (atom.isParagraphMarker()) {
                continue;
            }
            atoms.add(atom.verbatim() ? Prose.Atom.verbatim(trimLineEnds(atom.text())) : atom);
        }
    }

    private static void addMarkdownBlocks(List<Prose.Atom> atoms, List<Javadoc.Block> blocks, int indent) {
        for (Javadoc.Block block : blocks) {
            atoms.add(structure("block-start:" + block.kind()));
            if (block.kind() == Javadoc.BlockKind.PARAGRAPH) {
                atoms.addAll(Prose.atoms(markdownContent(block.lines(), indent)));
            } else if (block.kind() == Javadoc.BlockKind.PRESERVED) {
                atoms.add(Prose.Atom.verbatim(markdownContent(block.lines(), indent)));
            }
            atoms.add(structure("block-end"));
        }
    }

    private static String markdownContent(List<String> lines, int indent) {
        List<String> shifted = new ArrayList<>(lines.size());
        for (String line : lines) {
            shifted.add(Javadoc.removeIndent(line, indent));
        }
        return String.join("\n", shifted);
    }

    private static Prose.Atom structure(String name) {
        return Prose.Atom.verbatim(STRUCTURE + name);
    }

    private static boolean isStructure(Prose.Atom atom) {
        return atom.verbatim() && atom.text().startsWith(STRUCTURE);
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
     * Drops the trailing spaces of every line of a traditional comment's verbatim region.
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
            if (isStructure(left) && !isStructure(right)) {
                return "a documentation boundary was changed";
            }
            if (!isStructure(left) && isStructure(right)) {
                return "comment text was lost: " + describe(left) + " is no longer there";
            }
            if (isStructure(left)) {
                return "a documentation boundary was changed";
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

    private static String describe(Prose.Atom atom) {
        if (isStructure(atom)) {
            return "a documentation boundary";
        }
        String text = atom.text();
        String shortened = text.length() <= 40 ? text : text.substring(0, 37) + "...";
        return "'" + shortened.replace("\n", "\\n") + "'";
    }

}
