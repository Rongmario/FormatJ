package zone.rong.formatj.core.rewrite;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import zone.rong.formatj.api.Option;
import zone.rong.formatj.api.rules.FloatSuffix;
import zone.rong.formatj.api.rules.HexDigitCase;
import zone.rong.formatj.api.rules.LiteralRules;
import zone.rong.formatj.api.rules.LongSuffix;
import zone.rong.formatj.core.cst.GreenNode;
import zone.rong.formatj.core.cst.SyntaxToken;
import zone.rong.formatj.core.lexer.Token;
import zone.rong.formatj.core.lexer.TokenKind;

/**
 * Respells numeric literals. The value never changes, only the case of some of its characters and
 * the underscores between its digits.
 *
 * <p>Literals live in a single token, so like text blocks they are reached from their parent. A
 * literal containing a Unicode escape is left alone, since its token text is not what the program
 * reads.
 */
public final class LiteralRewrite implements Rewrite {

    /** Whether the last character of a numeric literal is a float or double suffix. */
    public static boolean isFloatingSuffix(String text) {
        char last = Character.toLowerCase(text.charAt(text.length() - 1));
        if (last != 'f' && last != 'd') {
            return false;
        }
        String lower = text.toLowerCase(Locale.ROOT);
        return lower.startsWith("0x") ? lower.indexOf('p') >= 0 : !lower.startsWith("0b");
    }

    /**
     * Recases the digits of a hex literal. The prefix, the {@code p} exponent and everything after
     * it (the exponent, and the {@code f} or {@code d} suffix of a hex float) are left as written,
     * and so is the {@code l} suffix of a hex integer.
     */
    private static String withHexDigitCase(String text, HexDigitCase digits) {
        if (digits == HexDigitCase.PRESERVE || !(text.startsWith("0x") || text.startsWith("0X"))) {
            return text;
        }
        int end = text.length();
        for (int i = 2; i < text.length(); i++) {
            if (text.charAt(i) == 'p' || text.charAt(i) == 'P') {
                end = i;
                break;
            }
        }
        if (end == text.length() && (text.endsWith("l") || text.endsWith("L"))) {
            end--;
        }
        String mantissa = text.substring(2, end);
        mantissa = digits == HexDigitCase.UPPER ? mantissa.toUpperCase(Locale.ROOT) : mantissa.toLowerCase(Locale.ROOT);
        return text.substring(0, 2) + mantissa + text.substring(end);
    }

    private static String withLongSuffix(String text, LongSuffix suffix) {
        return suffix == LongSuffix.UPPER && text.endsWith("l") ? text.substring(0, text.length() - 1) + "L" : text;
    }

    /**
     * Recases the {@code f} or {@code d} suffix of a floating-point literal. In a hex literal those
     * letters are digits unless a {@code p} exponent comes first.
     */
    private static String withFloatSuffix(String text, FloatSuffix suffix) {
        if (suffix == FloatSuffix.PRESERVE || !isFloatingSuffix(text)) {
            return text;
        }
        String last = text.substring(text.length() - 1);
        return text.substring(0, text.length() - 1) +
            (suffix == FloatSuffix.UPPER ? last.toUpperCase(Locale.ROOT) : last.toLowerCase(Locale.ROOT));
    }

    /** Groups a plain decimal integer in threes once it has {@code threshold} digits. */
    private static String withDigitGrouping(String text, int threshold) {
        if (threshold <= 0 || !text.matches("[1-9][0-9]*[lL]?")) {
            return text;
        }
        int digits = Character.isDigit(text.charAt(text.length() - 1)) ? text.length() : text.length() - 1;
        if (digits < threshold) {
            return text;
        }
        StringBuilder grouped = new StringBuilder(text.substring(digits));
        for (int i = digits - 1, count = 0; i >= 0; i--,count++) {
            if (count > 0 && count % 3 == 0) {
                grouped.insert(0, '_');
            }
            grouped.insert(0, text.charAt(i));
        }
        return grouped.toString();
    }

    @Override
    public String name() {
        return "literals";
    }

    @Override
    public boolean enabled(RewriteContext context) {
        return context.rule(LiteralRules.LONG_SUFFIX) == LongSuffix.UPPER ||
            context.rule(LiteralRules.HEX_DIGITS) != HexDigitCase.PRESERVE ||
            context.rule(LiteralRules.FLOAT_SUFFIX) != FloatSuffix.PRESERVE ||
            context.rule(LiteralRules.DIGIT_GROUPING_THRESHOLD) > 0;
    }

    @Override
    public GreenNode rewrite(GreenNode node, RewriteContext context) {
        List<GreenNode> children = node.children();
        List<GreenNode> rewritten = null;
        for (int i = 0; i < children.size(); i++) {
            GreenNode replacement = rewriteLeaf(children.get(i), context);
            if (replacement != children.get(i)) {
                if (rewritten == null) {
                    rewritten = new ArrayList<>(children);
                }
                rewritten.set(i, replacement);
            }
        }
        return rewritten == null ? node : GreenNode.branch(node.kind(), rewritten);
    }

    private GreenNode rewriteLeaf(GreenNode child, RewriteContext context) {
        if (!(child instanceof GreenNode.Leaf leaf)) {
            return child;
        }
        SyntaxToken syntax = leaf.token();
        Token token = syntax.token();
        if (token.kind() != TokenKind.NUMBER_LITERAL || token.hasUnicodeEscape()) {
            return child;
        }

        String original = token.text();
        String suffixed = withLongSuffix(original, context.rule(LiteralRules.LONG_SUFFIX));
        String hexed = withHexDigitCase(suffixed, context.rule(LiteralRules.HEX_DIGITS));
        String floated = withFloatSuffix(hexed, context.rule(LiteralRules.FLOAT_SUFFIX));
        String rewritten = withDigitGrouping(floated, context.rule(LiteralRules.DIGIT_GROUPING_THRESHOLD));
        Option<?> authority = !suffixed.equals(original)
            ? LiteralRules.LONG_SUFFIX
            : !hexed.equals(suffixed)
                ? LiteralRules.HEX_DIGITS
                : !floated.equals(hexed) ? LiteralRules.FLOAT_SUFFIX : LiteralRules.DIGIT_GROUPING_THRESHOLD;
        int position = context.firstPosition(child);
        if (rewritten.equals(original) || position < 0) {
            return child;
        }

        context.record(new TokenEdit(
            authority,
            "a numeric literal spelled the way the literal rules ask for",
            position,
            List.of(original),
            List.of(rewritten),
            TokenEdit.Bias.INNERMOST_FIRST
        ));
        return GreenNode.leaf(new SyntaxToken(
            syntax.leading(),
            Token.synthetic(TokenKind.NUMBER_LITERAL, rewritten),
            syntax.trailing()
        ));
    }

}
