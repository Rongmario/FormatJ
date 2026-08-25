package zone.rong.formatj.cli;

import java.util.ArrayList;
import java.util.List;

/**
 * A line-based unified diff, used by {@code --diff}.
 *
 * <p>Small on purpose: the CLI needs to show what would change, not to be a diff library, so this is
 * a longest-common-subsequence walk with three lines of context and no rename or word detection.
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
        int[][] lcs = longestCommonSubsequence(left, right);

        List<String> body = new ArrayList<>();
        int i = 0;
        int j = 0;
        while (i < left.size() && j < right.size()) {
            if (left.get(i).equals(right.get(j))) {
                body.add(" " + left.get(i));
                i++;
                j++;
            } else if (lcs[i + 1][j] >= lcs[i][j + 1]) {
                body.add("-" + left.get(i));
                i++;
            } else {
                body.add("+" + right.get(j));
                j++;
            }
        }
        while (i < left.size()) {
            body.add("-" + left.get(i++));
        }
        while (j < right.size()) {
            body.add("+" + right.get(j++));
        }

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

    private static int[][] longestCommonSubsequence(List<String> left, List<String> right) {
        int[][] lengths = new int[left.size() + 1][right.size() + 1];
        for (int i = left.size() - 1; i >= 0; i--) {
            for (int j = right.size() - 1; j >= 0; j--) {
                lengths[i][j] =
                        left.get(i).equals(right.get(j))
                                ? lengths[i + 1][j + 1] + 1
                                : Math.max(lengths[i + 1][j], lengths[i][j + 1]);
            }
        }
        return lengths;
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
