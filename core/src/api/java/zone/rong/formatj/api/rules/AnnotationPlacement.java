package zone.rong.formatj.api.rules;

/** Whether an annotation shares a line with what it annotates. */
public enum AnnotationPlacement {

    /** Leave the author's choice alone. */
    PRESERVE,
    /** Annotation on its own line. */
    NEW_LINE,
    /** Annotation on the same line as the declaration. */
    SAME_LINE,
    /** Same line while the result fits the line length, own line otherwise. */
    SAME_LINE_WHEN_SHORT

}
