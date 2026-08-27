package zone.rong.formatj.core.ir;

import java.util.ArrayList;
import java.util.List;

/**
 * Propagates forced breaks outwards.
 *
 * <p>A hard break inside a group means that group can never print flat, and neither can any group
 * containing it. Marking that once, before printing, keeps the printer's fit check local: it only
 * ever has to ask whether the text of a group fits, never whether something deep inside it will
 * force a break later.
 */
public final class DocBreaks {

    private DocBreaks() { }

    /** Returns an equivalent document with every group that must break marked as breaking. */
    public static Doc propagate(Doc doc) {
        return rewrite(doc).doc();
    }

    private record Rewritten(Doc doc, boolean forcesBreak) { }

    private static Rewritten rewrite(Doc doc) {
        return switch (doc) {
            case Doc.Text text -> new Rewritten(text, false);
            case Doc.BreakParent parent -> new Rewritten(parent, true);
            case Doc.Mark mark -> new Rewritten(mark, false);
            case Doc.Break lineBreak -> new Rewritten(lineBreak, lineBreak.kind() == Doc.BreakKind.HARD);
            case Doc.Concat concat -> {
                List<Doc> parts = new ArrayList<>(concat.parts().size());
                boolean forced = false;
                for (Doc part : concat.parts()) {
                    Rewritten rewritten = rewrite(part);
                    parts.add(rewritten.doc());
                    forced |= rewritten.forcesBreak();
                }
                yield new Rewritten(Doc.concat(parts), forced);
            }
            case Doc.Fill fill -> {
                List<Doc> parts = new ArrayList<>(fill.parts().size());
                boolean forced = false;
                for (Doc part : fill.parts()) {
                    Rewritten rewritten = rewrite(part);
                    parts.add(rewritten.doc());
                    forced |= rewritten.forcesBreak();
                }
                yield new Rewritten(Doc.fill(parts), forced);
            }
            case Doc.Group group -> {
                Rewritten content = rewrite(group.content());
                boolean inside = group.kind() == Doc.GroupKind.ALWAYS || content.forcesBreak();
                // A first-line group is the one thing a break inside does not settle: it still travels
                // outwards, because the line it makes is real, but this group is left to the printer's
                // fit check, which measures only as far as that break.
                Doc.GroupKind kind =
                        group.kind() == Doc.GroupKind.FIRST_LINE
                        ? Doc.GroupKind.FIRST_LINE
                        : inside ? Doc.GroupKind.ALWAYS : Doc.GroupKind.IF_NEEDED;
                yield new Rewritten(Doc.group(content.doc(), kind), inside);
            }
            case Doc.Indent indent -> {
                Rewritten content = rewrite(indent.content());
                yield new Rewritten(Doc.indent(indent.columns(), content.doc()), content.forcesBreak());
            }
            case Doc.IndentIfBreak indent -> {
                Rewritten content = rewrite(indent.content());
                yield new Rewritten(Doc.indentIfBreak(indent.columns(), content.doc()), content.forcesBreak());
            }
            case Doc.Align align -> {
                Rewritten content = rewrite(align.content());
                yield new Rewritten(Doc.align(content.doc()), content.forcesBreak());
            }
            case Doc.LineIndent indent -> {
                Rewritten content = rewrite(indent.content());
                yield new Rewritten(Doc.lineIndent(indent.columns(), content.doc()), content.forcesBreak());
            }
            case Doc.IfBreak ifBreak -> {
                Rewritten broken = rewrite(ifBreak.broken());
                Rewritten flat = rewrite(ifBreak.flat());
                yield new Rewritten(Doc.ifBreak(broken.doc(), flat.doc()), false);
            }
            // A line suffix is printed on a later line, so its breaks do not force the current group.
            case Doc.LineSuffix suffix -> new Rewritten(Doc.lineSuffix(rewrite(suffix.content()).doc()), false);
        };
    }

}
