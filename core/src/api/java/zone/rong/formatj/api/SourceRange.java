package zone.rong.formatj.api;

/**
 * A half-open character range of the source, used to format a selection rather than a whole file.
 *
 * @param startOffset first character of the range, zero-based and inclusive
 * @param endOffset character after the range, zero-based and exclusive
 */
public record SourceRange(int startOffset, int endOffset) {

    public SourceRange {
        if (startOffset < 0) {
            throw new IllegalArgumentException("startOffset must not be negative: " + startOffset);
        }
        if (endOffset < startOffset) {
            throw new IllegalArgumentException("endOffset " + endOffset + " precedes startOffset " + startOffset);
        }
    }

    public int length() {
        return endOffset - startOffset;
    }

    public boolean contains(int offset) {
        return offset >= startOffset && offset < endOffset;
    }

}
