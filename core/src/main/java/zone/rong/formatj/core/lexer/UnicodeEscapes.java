package zone.rong.formatj.core.lexer;

/**
 * Unicode escape translation (JLS 3.3): a backslash preceded by an even number of backslashes,
 * followed by one or more {@code u}'s and exactly four hex digits, stands for the character those
 * digits encode. A backslash preceded by an odd number of backslashes belongs to the literal pair
 * before it and can never start an escape, which is why {@code \\u0041} (two backslashes) is not one.
 *
 * <p>An escape that fails to parse, such as non-hex digits after the {@code u}'s, is a compile-time
 * error in {@code javac}; here it is left as ordinary characters so the lexer stays lossless.
 */
final class UnicodeEscapes {

    private UnicodeEscapes() { }

    /** Length of the Unicode escape starting at {@code offset}, or 0 if none starts there. */
    static int lengthAt(String source, int offset) {
        if (source.charAt(offset) != '\\' || isPrecededByOddBackslashes(source, offset)) {
            return 0;
        }
        int at = offset + 1;
        while (at < source.length() && source.charAt(at) == 'u') {
            at++;
        }
        if (at == offset + 1 || at + 4 > source.length()) {
            return 0;
        }
        for (int i = at; i < at + 4; i++) {
            if (Character.digit(source.charAt(i), 16) < 0) {
                return 0;
            }
        }
        return at + 4 - offset;
    }

    /** The character the Unicode escape of the given {@code length}, starting at {@code offset}, decodes to. */
    static char decodedCharAt(String source, int offset, int length) {
        return (char) Integer.parseInt(source, offset + length - 4, offset + length, 16);
    }

    /** Decodes every Unicode escape in {@code text}, leaving everything else untouched. */
    static String decode(String text) {
        StringBuilder decoded = new StringBuilder(text.length());
        int i = 0;
        while (i < text.length()) {
            int length = lengthAt(text, i);
            if (length > 0) {
                decoded.append(decodedCharAt(text, i, length));
                i += length;
            } else {
                decoded.append(text.charAt(i));
                i++;
            }
        }
        return decoded.toString();
    }

    private static boolean isPrecededByOddBackslashes(String source, int offset) {
        int count = 0;
        while (offset - count - 1 >= 0 && source.charAt(offset - count - 1) == '\\') {
            count++;
        }
        return count % 2 != 0;
    }

}
