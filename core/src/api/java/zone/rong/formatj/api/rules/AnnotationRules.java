package zone.rong.formatj.api.rules;

import zone.rong.formatj.api.Option;

/** Where annotations sit relative to what they annotate. */
public final class AnnotationRules {

    public static final Option<AnnotationPlacement> DECLARATION_PLACEMENT = Option.ofEnum(
        "annotations.declaration-placement",
        AnnotationPlacement.NEW_LINE,
        "Placement of an annotation on a type, method or constructor declaration"
    );

    public static final Option<AnnotationPlacement> FIELD_PLACEMENT = Option.ofEnum(
        "annotations.field-placement",
        AnnotationPlacement.SAME_LINE,
        "Placement of an annotation on a field declaration"
    );

    public static final Option<AnnotationPlacement> PARAMETER_PLACEMENT = Option.ofEnum(
        "annotations.parameter-placement",
        AnnotationPlacement.SAME_LINE,
        "Placement of an annotation on a parameter or local variable"
    );

    public static final Option<AnnotationPlacement> PACKAGE_PLACEMENT = Option.ofEnum(
        "annotations.package-placement",
        AnnotationPlacement.NEW_LINE,
        "Placement of an annotation on a package declaration"
    );

    public static final Option<Boolean> SINGLE_MARKER_INLINE = Option.ofBoolean(
        "annotations.single-marker-inline",
        false,
        "Keep a lone marker annotation on the line of its declaration"
    );

    private AnnotationRules() { }

}
