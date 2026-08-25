package zone.rong.formatj.core.pipeline;

import zone.rong.formatj.core.lexer.JavaLexer;
import zone.rong.formatj.core.lexer.Token;
import java.util.ArrayList;
import java.util.List;

/**
 * Checks that formatting changed layout only.
 *
 * <p>Two sources are equivalent when their significant tokens, ignoring whitespace and comments,
 * match one for one. This is the safety net that lets FormatJ ship: if a rewrite ever loses a token,
 * duplicates one, or merges two, the pipeline notices and returns the original file.
 */
public final class TokenEquivalence {

    private TokenEquivalence() { }

    /** Whether the two sources differ only in whitespace and comments. */
    public static boolean equivalent(String before, String after) {
        return firstDifference(before, after) == null;
    }

    /**
     * The first significant token that differs, or null when the sources are equivalent.
     *
     * @return a human readable description of the difference, for use in a diagnostic
     */
    public static String firstDifference(String before, String after) {
        List<Token> left = significantTokens(before);
        List<Token> right = significantTokens(after);
        int shared = Math.min(left.size(), right.size());
        for (int i = 0; i < shared; i++) {
            Token expected = left.get(i);
            Token actual = right.get(i);
            if (!expected.text().equals(actual.text())) {
                return "token " + (i + 1) + " changed from '" + expected.text() + "' (line " + expected.line()
                        + ") to '" + actual.text() + "' (line " + actual.line() + ")";
            }
        }
        if (left.size() != right.size()) {
            return "token count changed from " + left.size() + " to " + right.size();
        }
        return null;
    }

    /** Every token that carries program meaning, in order. */
    public static List<Token> significantTokens(String source) {
        List<Token> significant = new ArrayList<>();
        for (Token token : JavaLexer.tokenize(source)) {
            if (token.kind().isSignificant()) {
                significant.add(token);
            }
        }
        return List.copyOf(significant);
    }

}
