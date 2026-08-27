package zone.rong.formatj.core.layout;

import zone.rong.formatj.core.ir.AlignmentSite;
import java.util.ArrayList;
import java.util.List;

/**
 * Pads the marks the printer recorded so that a run of neighbouring lines shares a column.
 *
 * <p>This runs after the text has been printed, and that is not an implementation detail. Every other
 * layout decision is local — one construct, and what is left of its line — but alignment is about
 * where several lines each happened to end up, which nothing knows until they have all been printed.
 * Deciding it during printing would mean measuring a line against a column that a later line has not
 * chosen yet.
 *
 * <p>Running afterwards also buys the property that matters most: alignment can never change which
 * breaks were taken. Padding is inserted into finished text, so the wrapping of a file is exactly
 * what it would have been with every alignment rule off, and formatting stays a fixed point — the
 * second pass reprints the same unpadded text and pads it the same way. The price is that an aligned
 * line can end past {@code wrapping.max-line-length}, since the column it is padded to is not known
 * when the margin is being honoured.
 *
 * <p>A run is a maximal set of marks for one rule that sit on consecutive lines at the same
 * indentation, one mark per line. That is what makes a blank line, a comment line, a line that
 * wrapped, or a change of nesting depth end a run, which is the behaviour an author expects: the
 * alignment of a group of declarations should not be dragged out of shape by something several lines
 * away.
 */
public final class ColumnAligner {

    private final int tabWidth;
    private final int trailingCommentColumn;

    public ColumnAligner(int tabWidth) {
        this(tabWidth, 0);
    }

    /**
     * @param trailingCommentColumn 1-based column a trailing comment should start at; {@code 0}
     *     disables the rule
     */
    public ColumnAligner(int tabWidth, int trailingCommentColumn) {
        this.tabWidth = tabWidth;
        this.trailingCommentColumn = trailingCommentColumn;
    }

    /** The printed text with every alignment run padded to its shared column. */
    public String align(DocPrinter.Printed printed) {
        if (printed.marks().isEmpty()) {
            return printed.text();
        }
        String text = printed.text();
        List<DocPrinter.Mark> marks = new ArrayList<>(printed.marks());
        marks.sort((left, right) -> Integer.compare(left.offset(), right.offset()));

        // One site at a time, left to right across the line: padding at a mark moves every mark after
        // it, so the text and the offsets are brought up to date before the next site is measured.
        for (AlignmentSite site : AlignmentSite.values()) {
            List<Insertion> insertions = insertionsFor(text, marks, site);
            if (insertions.isEmpty()) {
                continue;
            }
            text = apply(text, insertions);
            shift(marks, insertions);
        }
        return text;
    }

    /** One run's worth of padding at one mark. */
    private record Insertion(int offset, int spaces) { }

    private List<Insertion> insertionsFor(String text, List<DocPrinter.Mark> marks, AlignmentSite site) {
        List<Position> positions = positions(text, marks, site);
        if (site == AlignmentSite.TRAILING_COMMENT_COLUMN) {
            return padsToColumn(positions);
        }
        List<Insertion> insertions = new ArrayList<>();
        int start = 0;
        while (start < positions.size()) {
            int end = start + 1;
            while (end < positions.size() && continues(positions.get(end - 1), positions.get(end))) {
                end++;
            }
            if (end - start > 1) {
                int target = 0;
                for (int i = start; i < end; i++) {
                    target = Math.max(target, positions.get(i).column());
                }
                for (int i = start; i < end; i++) {
                    Position position = positions.get(i);
                    if (position.column() < target) {
                        insertions.add(new Insertion(position.offset(), target - position.column()));
                    }
                }
            }
            start = end;
        }
        return insertions;
    }

    /**
     * Pads each trailing comment independently so it starts at the configured column.
     *
     * <p>A line whose code already reaches past that column is left with its ordinary spacing: padding
     * cannot move a comment backwards, and it cannot move a line break.
     */
    private List<Insertion> padsToColumn(List<Position> positions) {
        if (trailingCommentColumn <= 0) {
            return List.of();
        }
        int target = trailingCommentColumn - 1;
        List<Insertion> insertions = new ArrayList<>();
        for (Position position : positions) {
            if (position.column() < target) {
                insertions.add(new Insertion(position.offset(), target - position.column()));
            }
        }
        return insertions;
    }

    /** Whether two marks belong to the same run. */
    private static boolean continues(Position previous, Position next) {
        return next.line() == previous.line() + 1 && next.indent().equals(previous.indent());
    }

    /**
     * @param line the mark's line number
     * @param column the visual column the mark sits at, with tabs expanded
     * @param indent the line's leading whitespace, which two lines must share to align with each other
     */
    private record Position(int offset, int line, int column, String indent) { }

    /**
     * Where each mark for one site sits, at most one per line.
     *
     * <p>A second mark of the same kind on one line — two assignments written on one line — has no
     * column of its own to share, so only the first is a candidate.
     */
    private List<Position> positions(String text, List<DocPrinter.Mark> marks, AlignmentSite site) {
        List<Position> positions = new ArrayList<>();
        int line = 0;
        int lineStart = 0;
        int scanned = 0;
        int lastLine = -1;
        for (DocPrinter.Mark mark : marks) {
            if (mark.site() != site) {
                continue;
            }
            // The marks are in offset order, so one walk of the text finds every line number.
            while (scanned < mark.offset()) {
                if (text.charAt(scanned) == '\n') {
                    line++;
                    lineStart = scanned + 1;
                }
                scanned++;
            }
            if (line == lastLine) {
                continue;
            }
            lastLine = line;
            positions.add(
                    new Position(mark.offset(), line, column(text, lineStart, mark.offset()), indent(text, lineStart)));
        }
        return positions;
    }

    private int column(String text, int lineStart, int offset) {
        int column = 0;
        for (int i = lineStart; i < offset; i++) {
            column = text.charAt(i) == '\t' ? (column / tabWidth + 1) * tabWidth : column + 1;
        }
        return column;
    }

    private static String indent(String text, int lineStart) {
        int end = lineStart;
        while (end < text.length() && (text.charAt(end) == ' ' || text.charAt(end) == '\t')) {
            end++;
        }
        return text.substring(lineStart, end);
    }

    /** Padding is written with spaces whatever the indentation setting: it is not indentation. */
    private static String apply(String text, List<Insertion> insertions) {
        StringBuilder out = new StringBuilder(text.length() + insertions.size() * 4);
        int copied = 0;
        for (Insertion insertion : insertions) {
            out.append(text, copied, insertion.offset());
            out.append(" ".repeat(insertion.spaces()));
            copied = insertion.offset();
        }
        out.append(text, copied, text.length());
        return out.toString();
    }

    private static void shift(List<DocPrinter.Mark> marks, List<Insertion> insertions) {
        for (int i = 0; i < marks.size(); i++) {
            DocPrinter.Mark mark = marks.get(i);
            int shift = 0;
            for (Insertion insertion : insertions) {
                if (insertion.offset() <= mark.offset()) {
                    shift += insertion.spaces();
                }
            }
            if (shift > 0) {
                marks.set(i, new DocPrinter.Mark(mark.offset() + shift, mark.site()));
            }
        }
    }

}
