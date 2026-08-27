package zone.rong.formatj.core.ir;

/**
 * A place in the output where a column alignment rule may pad.
 *
 * <p>Alignment is the one layout decision that cannot be made while the document is being printed.
 * Every other decision is about one construct and what is left of its line; alignment is about a run
 * of neighbouring lines and where each of them happened to end up, which is not known until they have
 * all been printed. So the emitter marks the places instead, the printer records where each mark
 * landed, and {@link zone.rong.formatj.core.layout.ColumnAligner} pads them afterwards.
 *
 * <p>The order of the constants is the order the padding is applied in, which has to be left to right
 * across a line: padding at an earlier mark moves every later one, so a trailing comment can only be
 * aligned once everything in front of it has stopped moving.
 */
public enum AlignmentSite {

    /** Before the name of the first declarator of a field declaration. */
    FIELD_NAME,
    /** Before the name of the first declarator of a local variable declaration. */
    VARIABLE_NAME,
    /** Before the {@code =} of an assignment or an initialiser. */
    ASSIGNMENT,
    /** Before the {@code =} of an annotation element. */
    ANNOTATION_VALUE,
    /** Before the {@code ->} of a switch case. */
    SWITCH_ARROW,
    /**
     * At the start of a comment trailing a line of code, padded independently to
     * {@code comments.trailing-comment-column}. Applied before {@link #TRAILING_COMMENT} so a run
     * that still wants to share a column can move further out.
     */
    TRAILING_COMMENT_COLUMN,
    /** At the start of a comment trailing a line of code. */
    TRAILING_COMMENT

}
