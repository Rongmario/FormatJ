package zone.rong.formatj.api.rules;

/** Arrow ({@code case A -> ...}) versus colon ({@code case A:}) switch labels. */
public enum SwitchCaseStyle {

    /** Leave each switch as the author wrote it. */
    PRESERVE,

    /** Rewrite to arrow labels where the switch allows it. */
    ARROW,

    /** Rewrite to colon labels. */
    COLON

}
