package zone.rong.formatj.core.pipeline;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import zone.rong.formatj.api.SourceRange;
import zone.rong.formatj.core.lexer.JavaLexer;
import zone.rong.formatj.core.lexer.Token;
import zone.rong.formatj.core.lexer.TokenKind;

/**
 * Line-oriented diff between the original source and a whole-file formatting result, used to keep
 * only the hunks that fall inside a caller's requested ranges. The rest of the file keeps its
 * original characters even where formatting the whole file would have reflowed it too.
 */
public final class LineDiffer {

    private static final long MAX_DIFF_TABLE_CELLS = 2_000_000L;
    private static final int MAX_ANCHOR_LINES = 100_000;
    private static final int MAX_ANCHOR_SCANS = 200_000;
    private static final int MAX_REFINEMENT_CHARACTERS = 2_000_000;

    private LineDiffer() { }

    static String splice(String original, String formatted, List<SourceRange> ranges) {
        if (original.equals(formatted) || ranges.isEmpty()) {
            return original.equals(formatted) ? original : formatted;
        }
        Split left = Split.of(original);
        Split right = Split.of(formatted);
        List<Hunk> hunks = refine(left, right, hunks(left.lines, right.lines));
        if (hunks.isEmpty()) {
            return original;
        }

        StringBuilder out = new StringBuilder(original.length());
        int line = 0;
        for (Hunk hunk : hunks) {
            left.append(out, line, hunk.originalStart);
            int startOffset = left.offsetOfLine(hunk.originalStart);
            int endOffset = left.offsetOfLine(hunk.originalEnd);
            if (overlaps(ranges, startOffset, endOffset) && selectedLines(left, hunk, ranges)) {
                right.append(out, hunk.formattedStart, hunk.formattedEnd);
            } else {
                left.append(out, hunk.originalStart, hunk.originalEnd);
            }
            line = hunk.originalEnd;
        }
        left.append(out, line, left.lines.size());
        return out.toString();
    }

    public static List<Hunk> hunks(List<String> original, List<String> formatted) {
        int n = original.size();
        int m = formatted.size();
        if (n == 0 && m == 0) {
            return List.of();
        }
        List<Hunk> hunks = new ArrayList<>();
        DiffBudget budget = new DiffBudget();
        AnchorIndex anchors = AnchorIndex.of(original, formatted);
        ArrayDeque<Window> pending = new ArrayDeque<>();
        pending.push(new Window(0, n, 0, m));
        while (!pending.isEmpty()) {
            Window window = trim(original, formatted, pending.pop());
            if (window.empty()) {
                continue;
            }
            if (window.originalLength() == 0 || window.formattedLength() == 0) {
                hunks.add(window.hunk());
                continue;
            }

            long cells = ((long) window.originalLength() + 1L) * ((long) window.formattedLength() + 1L);
            if (budget.claimCells(cells)) {
                addBoundedHunks(original, formatted, window, hunks);
                continue;
            }

            List<Anchor> matches = anchors.matches(window, budget);
            if (matches.isEmpty()) {
                hunks.add(window.hunk());
                continue;
            }

            List<Window> gaps = new ArrayList<>(matches.size() + 1);
            int originalLine = window.originalStart;
            int formattedLine = window.formattedStart;
            for (Anchor anchor : matches) {
                gaps.add(new Window(originalLine, anchor.originalLine, formattedLine, anchor.formattedLine));
                originalLine = anchor.originalLine + 1;
                formattedLine = anchor.formattedLine + 1;
            }
            gaps.add(new Window(originalLine, window.originalEnd, formattedLine, window.formattedEnd));
            for (int i = gaps.size() - 1; i >= 0; i--) {
                if (!gaps.get(i).empty()) {
                    pending.push(gaps.get(i));
                }
            }
        }

        hunks.sort(Comparator.comparingInt(Hunk::originalStart).thenComparingInt(Hunk::formattedStart));
        return List.copyOf(hunks);
    }

    private static Window trim(List<String> original, List<String> formatted, Window window) {
        int originalStart = window.originalStart;
        int originalEnd = window.originalEnd;
        int formattedStart = window.formattedStart;
        int formattedEnd = window.formattedEnd;
        while (originalStart < originalEnd &&
            formattedStart < formattedEnd &&
            original.get(originalStart).equals(formatted.get(formattedStart))) {
            originalStart++;
            formattedStart++;
        }
        while (originalStart < originalEnd &&
            formattedStart < formattedEnd &&
            original.get(originalEnd - 1).equals(formatted.get(formattedEnd - 1))) {
            originalEnd--;
            formattedEnd--;
        }
        return new Window(originalStart, originalEnd, formattedStart, formattedEnd);
    }

    private static void addBoundedHunks(
        List<String> original,
        List<String> formatted,
        Window window,
        List<Hunk> hunks
    ) {
        int n = window.originalLength();
        int m = window.formattedLength();
        int width = m + 1;
        int[] longest = new int[(n + 1) * width];
        for (int i = n - 1; i >= 0; i--) {
            for (int j = m - 1; j >= 0; j--) {
                if (original.get(window.originalStart + i).equals(formatted.get(window.formattedStart + j))) {
                    longest[i * width + j] = longest[(i + 1) * width + j + 1] + 1;
                } else {
                    longest[i * width + j] = Math.max(longest[(i + 1) * width + j], longest[i * width + j + 1]);
                }
            }
        }

        int originalLine = 0;
        int formattedLine = 0;
        int originalStart = -1;
        int formattedStart = -1;
        while (originalLine < n && formattedLine < m) {
            if (original.get(window.originalStart + originalLine)
                .equals(formatted.get(window.formattedStart + formattedLine))) {
                if (originalStart >= 0) {
                    hunks.add(new Hunk(
                        window.originalStart + originalStart,
                        window.originalStart + originalLine,
                        window.formattedStart + formattedStart,
                        window.formattedStart + formattedLine
                    ));
                    originalStart = -1;
                    formattedStart = -1;
                }
                originalLine++;
                formattedLine++;
            } else {
                if (originalStart < 0) {
                    originalStart = originalLine;
                    formattedStart = formattedLine;
                }
                if (longest[(originalLine + 1) * width + formattedLine] >=
                    longest[originalLine * width + formattedLine + 1]) {
                    originalLine++;
                } else {
                    formattedLine++;
                }
            }
        }
        if (originalLine < n || formattedLine < m) {
            if (originalStart < 0) {
                originalStart = originalLine;
                formattedStart = formattedLine;
            }
            originalLine = n;
            formattedLine = m;
        }
        if (originalStart >= 0) {
            hunks.add(new Hunk(
                window.originalStart + originalStart,
                window.originalStart + originalLine,
                window.formattedStart + formattedStart,
                window.formattedStart + formattedLine
            ));
        }
    }

    private static List<Hunk> refine(Split original, Split formatted, List<Hunk> hunks) {
        List<Hunk> refined = new ArrayList<>(hunks.size());
        RefinementBudget budget = new RefinementBudget();
        for (Hunk hunk : hunks) {
            refine(original, formatted, hunk, budget, refined);
        }
        return List.copyOf(refined);
    }

    private static void refine(
        Split original,
        Split formatted,
        Hunk hunk,
        RefinementBudget budget,
        List<Hunk> refined
    ) {
        if (hunk.originalEnd - hunk.originalStart < 2 || hunk.formattedEnd - hunk.formattedStart < 2) {
            refined.add(hunk);
            return;
        }
        long characters = contentLength(original.content, hunk.originalStart, hunk.originalEnd) +
            contentLength(formatted.content, hunk.formattedStart, hunk.formattedEnd);
        if (!budget.claim(characters)) {
            refined.add(hunk);
            return;
        }

        String originalContent = compact(original.content, hunk.originalStart, hunk.originalEnd);
        String formattedContent = compact(formatted.content, hunk.formattedStart, hunk.formattedEnd);
        if (originalContent.isEmpty() || !originalContent.equals(formattedContent)) {
            refined.add(hunk);
            return;
        }

        int[] originalLengths = compactLengths(original.content, hunk.originalStart, hunk.originalEnd);
        int[] formattedLengths = compactLengths(formatted.content, hunk.formattedStart, hunk.formattedEnd);
        int lastOriginal = hunk.originalStart;
        int lastFormatted = hunk.formattedStart;
        int lastLength = 0;
        int formattedBoundary = hunk.formattedStart + 1;
        for (int originalBoundary = hunk.originalStart + 1; originalBoundary < hunk.originalEnd; originalBoundary++) {
            int length = originalLengths[originalBoundary - hunk.originalStart];
            if (length <= lastLength || !original.safeBreaks[originalBoundary]) {
                continue;
            }
            while (formattedBoundary < hunk.formattedEnd &&
                formattedLengths[formattedBoundary - hunk.formattedStart] < length) {
                formattedBoundary++;
            }
            int candidate = formattedBoundary;
            while (candidate < hunk.formattedEnd &&
                formattedLengths[candidate - hunk.formattedStart] == length &&
                !formatted.safeBreaks[candidate]) {
                candidate++;
            }
            if (candidate >= hunk.formattedEnd || formattedLengths[candidate - hunk.formattedStart] != length) {
                continue;
            }
            addIfChanged(original, formatted, lastOriginal, originalBoundary, lastFormatted, candidate, refined);
            lastOriginal = originalBoundary;
            lastFormatted = candidate;
            lastLength = length;
            formattedBoundary = candidate + 1;
        }
        addIfChanged(original, formatted, lastOriginal, hunk.originalEnd, lastFormatted, hunk.formattedEnd, refined);
    }

    private static void addIfChanged(
        Split original,
        Split formatted,
        int originalStart,
        int originalEnd,
        int formattedStart,
        int formattedEnd,
        List<Hunk> hunks
    ) {
        if (!original.lines
            .subList(originalStart, originalEnd)
            .equals(formatted.lines.subList(formattedStart, formattedEnd))) {
            hunks.add(new Hunk(originalStart, originalEnd, formattedStart, formattedEnd));
        }
    }

    private static long contentLength(List<String> lines, int start, int end) {
        long length = 0;
        for (int i = start; i < end; i++) {
            length += lines.get(i).length();
        }
        return length;
    }

    private static String compact(List<String> lines, int start, int end) {
        StringBuilder compact = new StringBuilder();
        for (int i = start; i < end; i++) {
            compact.append(lines.get(i));
        }
        return compact.toString();
    }

    private static int[] compactLengths(List<String> lines, int start, int end) {
        int[] lengths = new int[end - start + 1];
        for (int i = start; i < end; i++) {
            lengths[i - start + 1] = lengths[i - start] + lines.get(i).length();
        }
        return lengths;
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

    private static boolean selectedLines(Split original, Hunk hunk, List<SourceRange> ranges) {
        for (int line = hunk.originalStart; line < hunk.originalEnd; line++) {
            if (!original.lines.get(line).isBlank() &&
                !overlaps(ranges, original.offsetOfLine(line), original.offsetOfLine(line + 1))) {
                return false;
            }
        }
        return true;
    }

    private static final class DiffBudget {

        private long tableCells = MAX_DIFF_TABLE_CELLS;
        private int anchorScans = MAX_ANCHOR_SCANS;

        boolean claimCells(long count) {
            if (count > tableCells) {
                return false;
            }
            tableCells -= count;
            return true;
        }

        boolean claimAnchorScans(int count) {
            if (count > anchorScans) {
                return false;
            }
            anchorScans -= count;
            return true;
        }

    }

    private static final class RefinementBudget {

        private long characters = MAX_REFINEMENT_CHARACTERS;

        boolean claim(long count) {
            if (count > characters) {
                return false;
            }
            characters -= count;
            return true;
        }

    }

    private record Window(int originalStart, int originalEnd, int formattedStart, int formattedEnd) {

        int originalLength() {
            return originalEnd - originalStart;
        }

        int formattedLength() {
            return formattedEnd - formattedStart;
        }

        boolean empty() {
            return originalLength() == 0 && formattedLength() == 0;
        }

        Hunk hunk() {
            return new Hunk(originalStart, originalEnd, formattedStart, formattedEnd);
        }

    }

    private record Anchor(int originalLine, int formattedLine) { }

    private record AnchorIndex(
        List<String> originalLines,
        Map<String, Integer> originalPositions,
        Map<String, Integer> formattedPositions
    ) {

        static AnchorIndex of(List<String> original, List<String> formatted) {
            if ((long) original.size() + formatted.size() > MAX_ANCHOR_LINES) {
                return new AnchorIndex(original, Map.of(), Map.of());
            }
            return new AnchorIndex(original, uniquePositions(original), uniquePositions(formatted));
        }

        List<Anchor> matches(Window window, DiffBudget budget) {
            if (originalPositions.isEmpty() || !budget.claimAnchorScans(window.originalLength())) {
                return List.of();
            }
            List<Anchor> candidates = new ArrayList<>();
            for (int i = window.originalStart; i < window.originalEnd; i++) {
                String line = originalLines.get(i);
                if (!Integer.valueOf(i).equals(originalPositions.get(line))) {
                    continue;
                }
                Integer formattedLine = formattedPositions.get(line);
                if (formattedLine != null &&
                    formattedLine >= window.formattedStart &&
                    formattedLine < window.formattedEnd &&
                    reliable(line)) {
                    candidates.add(new Anchor(i, formattedLine));
                }
            }
            return increasing(candidates);
        }

        private static Map<String, Integer> uniquePositions(List<String> lines) {
            Map<String, Integer> positions = new HashMap<>();
            for (int i = 0; i < lines.size(); i++) {
                String line = lines.get(i);
                Integer previous = positions.putIfAbsent(line, i);
                if (previous != null) {
                    positions.put(line, -1);
                }
            }
            positions.values().removeIf(position -> position < 0);
            return Map.copyOf(positions);
        }

        private static boolean reliable(String line) {
            if (line.isBlank()) {
                return false;
            }
            for (int i = 0; i < line.length(); i++) {
                char current = line.charAt(i);
                if (Character.isLetterOrDigit(current) || current == '_' || current == '$') {
                    return true;
                }
            }
            return false;
        }

        private static List<Anchor> increasing(List<Anchor> candidates) {
            if (candidates.size() < 2) {
                return List.copyOf(candidates);
            }
            int[] tails = new int[candidates.size()];
            int[] previous = new int[candidates.size()];
            Arrays.fill(previous, -1);
            int length = 0;
            for (int i = 0; i < candidates.size(); i++) {
                int formattedLine = candidates.get(i).formattedLine;
                int low = 0;
                int high = length;
                while (low < high) {
                    int middle = (low + high) >>> 1;
                    if (candidates.get(tails[middle]).formattedLine < formattedLine) {
                        low = middle + 1;
                    } else {
                        high = middle;
                    }
                }
                if (low > 0) {
                    previous[i] = tails[low - 1];
                }
                tails[low] = i;
                if (low == length) {
                    length++;
                }
            }

            Anchor[] result = new Anchor[length];
            int at = tails[length - 1];
            for (int i = length - 1; i >= 0; i--) {
                result[i] = candidates.get(at);
                at = previous[at];
            }
            return List.of(result);
        }

    }

    public record Hunk(int originalStart, int originalEnd, int formattedStart, int formattedEnd) { }

    record Split(
        List<String> lines,
        List<String> endings,
        List<String> content,
        int[] offsets,
        boolean[] safeBreaks,
        int sourceLength
    ) {

        int offsetOfLine(int index) {
            if (index < 0) {
                return 0;
            }
            if (index >= offsets.length) {
                return sourceLength;
            }
            return offsets[index];
        }

        void append(StringBuilder out, int start, int end) {
            for (int i = start; i < end; i++) {
                out.append(lines.get(i)).append(endings.get(i));
            }
        }

        static Split of(String text) {
            if (text.isEmpty()) {
                return new Split(List.of(), List.of(), List.of(), new int[0], new boolean[] { true }, 0);
            }
            List<String> lines = new ArrayList<>();
            List<String> endings = new ArrayList<>();
            List<Integer> starts = new ArrayList<>();
            int start = 0;
            for (int i = 0; i < text.length(); i++) {
                char current = text.charAt(i);
                if (current == '\n' || current == '\r') {
                    lines.add(text.substring(start, i));
                    starts.add(start);
                    int end = current == '\r' && i + 1 < text.length() && text.charAt(i + 1) == '\n' ? i + 2 : i + 1;
                    endings.add(text.substring(i, end));
                    i = end - 1;
                    start = end;
                }
            }
            if (start < text.length()) {
                lines.add(text.substring(start));
                endings.add("");
                starts.add(start);
            }
            int[] offsets = new int[starts.size()];
            for (int i = 0; i < starts.size(); i++) {
                offsets[i] = starts.get(i);
            }
            LexicalLines lexical = lexicalLines(text, offsets);
            return new Split(
                List.copyOf(lines),
                List.copyOf(endings),
                lexical.content,
                offsets,
                lexical.safeBreaks,
                text.length()
            );
        }

        private static LexicalLines lexicalLines(String text, int[] offsets) {
            List<StringBuilder> builders = new ArrayList<>(offsets.length);
            for (int ignored : offsets) {
                builders.add(new StringBuilder());
            }
            boolean[] safe = new boolean[offsets.length + 1];
            Arrays.fill(safe, true);
            List<Token> tokens = JavaLexer.tokenize(text);
            for (Token token : tokens) {
                if (token.kind() != TokenKind.WHITESPACE && token.kind() != TokenKind.END_OF_FILE) {
                    int line = Arrays.binarySearch(offsets, token.start());
                    line = line >= 0 ? line : Math.max(0, -line - 2);
                    builders.get(line).append(token.text());
                }
            }
            int tokenIndex = 0;
            for (int line = 1; line < offsets.length; line++) {
                int boundary = offsets[line];
                while (tokenIndex < tokens.size() && tokens.get(tokenIndex).end() <= boundary) {
                    tokenIndex++;
                }
                if (tokenIndex < tokens.size()) {
                    Token token = tokens.get(tokenIndex);
                    if (token.start() < boundary && boundary < token.end() && token.kind() != TokenKind.WHITESPACE) {
                        safe[line] = false;
                    }
                }
            }
            List<String> content = new ArrayList<>(builders.size());
            for (StringBuilder builder : builders) {
                content.add(builder.toString());
            }
            return new LexicalLines(List.copyOf(content), safe);
        }

    }

    private record LexicalLines(List<String> content, boolean[] safeBreaks) { }

}
