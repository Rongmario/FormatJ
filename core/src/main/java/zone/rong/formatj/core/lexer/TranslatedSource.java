package zone.rong.formatj.core.lexer;

/**
 * The character stream after Unicode escape translation, with every UTF-16 code unit mapped to its
 * half-open span in the raw source.
 */
final class TranslatedSource {

    private final String raw;
    private final char[] characters;
    private final int[] rawStarts;
    private final int[] rawEnds;

    private TranslatedSource(String raw, char[] characters, int[] rawStarts, int[] rawEnds) {
        this.raw = raw;
        this.characters = characters;
        this.rawStarts = rawStarts;
        this.rawEnds = rawEnds;
    }

    static TranslatedSource translate(String raw) {
        char[] characters = new char[raw.length()];
        int[] rawStarts = new int[raw.length()];
        int[] rawEnds = new int[raw.length()];
        int size = 0;
        int rawOffset = 0;
        int contiguousBackslashes = 0;
        boolean previousWasTranslated = false;

        while (rawOffset < raw.length()) {
            char current = raw.charAt(rawOffset);
            // An escape-produced character makes the next raw backslash eligible. Otherwise the
            // translated stream's contiguous backslash count decides eligibility.
            boolean eligible = current == '\\' && (previousWasTranslated || contiguousBackslashes % 2 == 0);
            int escapeEnd = eligible ? UnicodeEscapes.escapeEnd(raw, rawOffset) : -1;
            if (escapeEnd >= 0) {
                char translated = UnicodeEscapes.decodedChar(raw, escapeEnd);
                characters[size] = translated;
                rawStarts[size] = rawOffset;
                rawEnds[size] = escapeEnd;
                size++;
                rawOffset = escapeEnd;
                previousWasTranslated = true;
                contiguousBackslashes = translated == '\\' ? contiguousBackslashes + 1 : 0;
                continue;
            }

            characters[size] = current;
            rawStarts[size] = rawOffset;
            rawEnds[size] = rawOffset + 1;
            size++;
            rawOffset++;
            previousWasTranslated = false;
            contiguousBackslashes = current == '\\' ? contiguousBackslashes + 1 : 0;
        }

        return new TranslatedSource(
                raw,
                java.util.Arrays.copyOf(characters, size),
                java.util.Arrays.copyOf(rawStarts, size),
                java.util.Arrays.copyOf(rawEnds, size));
    }

    int length() {
        return characters.length;
    }

    char charAt(int index) {
        return characters[index];
    }

    int codePointAt(int index) {
        return Character.codePointAt(characters, index, characters.length);
    }

    boolean startsWith(String expected, int offset) {
        if (offset + expected.length() > characters.length) {
            return false;
        }
        for (int i = 0; i < expected.length(); i++) {
            if (characters[offset + i] != expected.charAt(i)) {
                return false;
            }
        }
        return true;
    }

    int rawOffset(int translatedOffset) {
        return translatedOffset == characters.length ? raw.length() : rawStarts[translatedOffset];
    }

    String rawText(int start, int end) {
        int rawStart = rawOffset(start);
        int rawEnd = end == start ? rawStart : rawEnds[end - 1];
        return raw.substring(rawStart, rawEnd);
    }

    String text(int start, int end) {
        return new String(characters, start, end - start);
    }

    String text() {
        return new String(characters);
    }

}
