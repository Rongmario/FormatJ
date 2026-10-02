package zone.rong.formatj.cli;

import java.util.ArrayList;
import java.util.List;

import zone.rong.formatj.core.pipeline.LineDiffer;

/**
 * A line-based unified diff, used by {@code --diff}.
 *
 * <p>Small on purpose: the CLI needs to show what would change, not to be a diff library, so this
 * prints the core's line hunks with three lines of context and no rename or word detection.
 */
public final class UnifiedDiff {

    private static final int CONTEXT = 3;

    private UnifiedDiff() { }

    /** A unified diff of two texts, or an empty string when they are identical. */
    public static String between(String name, String before, String after) {
        if (before.equals(after)) {
            return "";
        }
        List<String> left = lines(before);
        List<String> right = lines(after);

        List<String> body = new ArrayList<>();
        int line = 0;
        for (LineDiffer.Hunk hunk : LineDiffer.hunks(left, right)) {
            left.subList(line, hunk.originalStart()).forEach(text -> body.add(" " + text));
            left.subList(hunk.originalStart(), hunk.originalEnd()).forEach(text -> body.add("-" + text));
            right.subList(hunk.formattedStart(), hunk.formattedEnd()).forEach(text -> body.add("+" + text));
            line = hunk.originalEnd();
        }
        left.subList(line, left.size()).forEach(text -> body.add(" " + text));

        StringBuilder out = new StringBuilder();
        out.append("--- ").append(name).append('\n');
        out.append("+++ ").append(name).append(" (formatted)\n");
        appendHunks(out, body);
        return out.toString();
    }

    private static void appendHunks(StringBuilder out, List<String> body) {
        int index = 0;
        int leftLine = 1;
        int rightLine = 1;
        while (index < body.size()) {
            if (body.get(index).startsWith(" ")) {
                leftLine++;
                rightLine++;
                index++;
                continue;
            }
            int start = Math.max(0, index - CONTEXT);
            int end = index;
            int trailing = 0;
            while (end < body.size() && trailing <= CONTEXT) {
                trailing = body.get(end).startsWith(" ") ? trailing + 1 : 0;
                end++;
            }
            int hunkLeftStart = leftLine - (index - start);
            int hunkRightStart = rightLine - (index - start);
            int leftCount = 0;
            int rightCount = 0;
            for (int k = start; k < end; k++) {
                char marker = body.get(k).charAt(0);
                if (marker != '+') {
                    leftCount++;
                }
                if (marker != '-') {
                    rightCount++;
                }
            }
            out.append("@@ -")
                .append(hunkLeftStart)
                .append(',')
                .append(leftCount)
                .append(" +")
                .append(hunkRightStart)
                .append(',')
                .append(rightCount)
                .append(" @@\n");
            for (int k = start; k < end; k++) {
                out.append(body.get(k)).append('\n');
            }
            for (int k = index; k < end; k++) {
                char marker = body.get(k).charAt(0);
                if (marker != '+') {
                    leftLine++;
                }
                if (marker != '-') {
                    rightLine++;
                }
            }
            index = end;
        }
    }

    /** Splits into lines, dropping the empty piece a trailing newline produces. */
    private static List<String> lines(String text) {
        List<String> lines = new ArrayList<>(List.of(text.split("\n", -1)));
        if (!lines.isEmpty() && lines.getLast().isEmpty()) {
            lines.removeLast();
        }
        return List.copyOf(lines);
    }

}
