package zone.rong.formatj.idea;

import zone.rong.formatj.api.SourceRange;
import java.util.ArrayList;
import java.util.List;

/**
 * Line-oriented diff used to apply a whole-file FormatJ result only to the ranges IntelliJ asked for.
 * Core still formats the whole file; this reconstructs a document that keeps the rest of the author's layout.
 */
final class LineDiffer {

    private LineDiffer() { }

    static String splice(String original, String formatted, List<SourceRange> ranges) {
        if (original.equals(formatted) || ranges.isEmpty()) {
            return original.equals(formatted) ? original : formatted;
        }
        Split left = Split.of(original);
        Split right = Split.of(formatted);
        List<Hunk> hunks = hunks(left.lines, right.lines);
        if (hunks.isEmpty()) {
            return original;
        }

        List<String> out = new ArrayList<>();
        int line = 0;
        boolean appliedThroughEnd = false;
        for (Hunk hunk : hunks) {
            out.addAll(left.lines.subList(line, hunk.originalStart));
            int startOffset = left.offsetOfLine(hunk.originalStart);
            int endOffset = left.offsetOfLine(hunk.originalEnd);
            if (overlaps(ranges, startOffset, endOffset)) {
                out.addAll(hunk.replacement);
                appliedThroughEnd = hunk.originalEnd == left.lines.size();
            } else {
                out.addAll(left.lines.subList(hunk.originalStart, hunk.originalEnd));
                appliedThroughEnd = false;
            }
            line = hunk.originalEnd;
        }
        out.addAll(left.lines.subList(line, left.lines.size()));
        boolean trailingNewline = appliedThroughEnd ? right.trailingNewline : left.trailingNewline;
        if (line != left.lines.size()) {
            trailingNewline = left.trailingNewline;
        }
        return join(out, trailingNewline);
    }

    static List<Hunk> hunks(List<String> original, List<String> formatted) {
        int n = original.size();
        int m = formatted.size();
        if (n == 0 && m == 0) {
            return List.of();
        }
        if ((long) n * (long) m > 2_000_000L) {
            return original.equals(formatted) ? List.of() : List.of(new Hunk(0, n, List.copyOf(formatted)));
        }

        int[][] longest = new int[n + 1][m + 1];
        for (int i = n - 1; i >= 0; i--) {
            for (int j = m - 1; j >= 0; j--) {
                if (original.get(i).equals(formatted.get(j))) {
                    longest[i][j] = longest[i + 1][j + 1] + 1;
                } else {
                    longest[i][j] = Math.max(longest[i + 1][j], longest[i][j + 1]);
                }
            }
        }

        List<Step> script = new ArrayList<>();
        int i = 0;
        int j = 0;
        while (i < n && j < m) {
            if (original.get(i).equals(formatted.get(j))) {
                script.add(Step.EQUAL);
                i++;
                j++;
            } else if (longest[i + 1][j] >= longest[i][j + 1]) {
                script.add(Step.DELETE);
                i++;
            } else {
                script.add(Step.INSERT);
                j++;
            }
        }
        while (i < n) {
            script.add(Step.DELETE);
            i++;
        }
        while (j < m) {
            script.add(Step.INSERT);
            j++;
        }

        List<Hunk> hunks = new ArrayList<>();
        int originalLine = 0;
        int formattedLine = 0;
        int index = 0;
        while (index < script.size()) {
            Step step = script.get(index);
            if (step == Step.EQUAL) {
                originalLine++;
                formattedLine++;
                index++;
                continue;
            }
            int originalStart = originalLine;
            int formattedStart = formattedLine;
            while (index < script.size() && script.get(index) != Step.EQUAL) {
                if (script.get(index) == Step.DELETE) {
                    originalLine++;
                } else {
                    formattedLine++;
                }
                index++;
            }
            hunks.add(
                    new Hunk(
                            originalStart,
                            originalLine,
                            List.copyOf(formatted.subList(formattedStart, formattedLine))));
        }
        return hunks;
    }

    private static boolean overlaps(List<SourceRange> ranges, int startOffset, int endOffset) {
        for (SourceRange range : ranges) {
            if (startOffset == endOffset) {
                if (range.startOffset() <= startOffset && startOffset <= range.endOffset()) {
                    return true;
                }
            } else if (range.startOffset() < endOffset && startOffset < range.endOffset()) {
                return true;
            }
        }
        return false;
    }

    static String join(List<String> lines, boolean trailingNewline) {
        if (lines.isEmpty()) {
            return trailingNewline ? "\n" : "";
        }
        StringBuilder text = new StringBuilder();
        for (int i = 0; i < lines.size(); i++) {
            text.append(lines.get(i));
            if (i < lines.size() - 1 || trailingNewline) {
                text.append('\n');
            }
        }
        return text.toString();
    }

    private enum Step {

        EQUAL,
        DELETE,
        INSERT

    }

    record Hunk(int originalStart, int originalEnd, List<String> replacement) { }

    record Split(List<String> lines, boolean trailingNewline, int[] offsets) {

        int offsetOfLine(int index) {
            if (index < 0) {
                return 0;
            }
            if (index >= offsets.length) {
                return offsets.length == 0 ? 0 : endOffset();
            }
            return offsets[index];
        }

        private int endOffset() {
            if (lines.isEmpty()) {
                return trailingNewline ? 1 : 0;
            }
            int last = offsets[offsets.length - 1] + lines.getLast().length();
            return trailingNewline ? last + 1 : last;
        }

        static Split of(String text) {
            if (text.isEmpty()) {
                return new Split(List.of(), false, new int[0]);
            }
            List<String> lines = new ArrayList<>();
            List<Integer> starts = new ArrayList<>();
            int start = 0;
            for (int i = 0; i < text.length(); i++) {
                if (text.charAt(i) == '\n') {
                    lines.add(stripCarriageReturn(text.substring(start, i)));
                    starts.add(start);
                    start = i + 1;
                }
            }
            boolean trailingNewline = start == text.length();
            if (!trailingNewline) {
                lines.add(stripCarriageReturn(text.substring(start)));
                starts.add(start);
            }
            int[] offsets = new int[starts.size()];
            for (int i = 0; i < starts.size(); i++) {
                offsets[i] = starts.get(i);
            }
            return new Split(List.copyOf(lines), trailingNewline, offsets);
        }

        private static String stripCarriageReturn(String line) {
            return !line.isEmpty() && line.charAt(line.length() - 1) == '\r'
                    ? line.substring(0, line.length() - 1)
                    : line;
        }

    }

}
