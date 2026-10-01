package zone.rong.formatj.core.lexer;

/** Unicode escape translation as defined by JLS 3.3. */
public final class UnicodeEscapes {

    private UnicodeEscapes() {}

    /**
     * End of a syntactically valid escape at {@code offset}, or {@code -1}. The caller checks
     * eligibility.
     */
    static int escapeEnd(String source, int offset) {
        if (source.charAt(offset) != '\\') {
            return -1;
        }
        int at = offset + 1;
        while (at < source.length() && source.charAt(at) == 'u') {
            at++;
        }
        if (at == offset + 1 || at + 4 > source.length()) {
            return -1;
        }
        for (int i = at; i < at + 4; i++) {
            if (Character.digit(source.charAt(i), 16) < 0) {
                return -1;
            }
        }
        return at + 4;
    }

    /** The UTF-16 code unit represented by an escape ending at {@code end}. */
    static char decodedChar(String source, int end) {
        return (char) Integer.parseInt(source, end - 4, end, 16);
    }

    /** Translates eligible Unicode escapes and leaves malformed or ineligible spellings untouched. */
    public static String decode(String text) {
        return TranslatedSource.translate(text).text();
    }

}
