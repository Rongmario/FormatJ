package zone.rong.formatj.core.rewrite;

import zone.rong.formatj.api.Option;
import java.util.List;

/**
 * One declared change to the program's token stream.
 *
 * <p>A rewrite does not merely produce a new tree; it states, edit by edit, exactly what it did. That
 * statement is what makes the result checkable. Replaying every edit against the original token
 * stream yields the token stream the output is required to have, so an edit that was made but not
 * declared and an edit that was declared but not made both fail the same comparison, and anything
 * the rewrite disturbed by accident fails it too.
 *
 * <p>A splice at {@code position} removes {@code removed} and puts {@code inserted} in its place.
 * Either list may be empty, but not both.
 *
 * @param authority the rule that permits this edit; the verifier checks the edit against that rule's
 *     own law, so an edit cannot launder itself by claiming the wrong authority
 * @param reason human readable, for the diagnostic when verification fails
 * @param position index into the program tokens of the tree as it was before any rewriting
 * @param removed lexemes expected at {@code position}, which the edit deletes
 * @param inserted lexemes the edit puts at {@code position}
 * @param bias how to order this edit against others recorded at the same position
 */
public record TokenEdit(
        Option<?> authority,
        String reason,
        int position,
        List<String> removed,
        List<String> inserted,
        TokenEdit.Bias bias) {

    /**
     * Which of two edits at one position comes first.
     *
     * <p>Rewriting runs innermost-first, so the order edits were recorded in is the order the inner
     * construct wanted. A closing delimiter agrees with that: the inner {@code }} closes before the
     * outer one. An opening delimiter does not: the outer {@code {} opens before the inner one, so
     * its edit has to sort ahead of an inner edit recorded earlier at the same position.
     */
    public enum Bias {

        /** Order by the sequence edits were recorded in. */
        INNERMOST_FIRST,

        /** Order against the sequence edits were recorded in. */
        OUTERMOST_FIRST

    }

    public TokenEdit {
        removed = List.copyOf(removed);
        inserted = List.copyOf(inserted);
        if (removed.isEmpty() && inserted.isEmpty()) {
            throw new IllegalArgumentException("an edit must remove or insert something");
        }
        if (position < 0) {
            throw new IllegalArgumentException("edit position must not be negative: " + position);
        }
    }

    /** An edit that inserts tokens without removing any. */
    public static TokenEdit insert(Option<?> authority, String reason, int position, Bias bias, String... tokens) {
        return new TokenEdit(authority, reason, position, List.of(), List.of(tokens), bias);
    }

    /** An edit that removes tokens without inserting any. */
    public static TokenEdit delete(Option<?> authority, String reason, int position, String... tokens) {
        return new TokenEdit(authority, reason, position, List.of(tokens), List.of(), Bias.INNERMOST_FIRST);
    }

    @Override
    public String toString() {
        return authority.key() + " at token " + position + ": " + removed + " -> " + inserted;
    }

}
