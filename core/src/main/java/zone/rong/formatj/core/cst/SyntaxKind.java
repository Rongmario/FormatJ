package zone.rong.formatj.core.cst;

/**
 * The node kinds of the concrete syntax tree.
 *
 * <p>One kind per construct the emitter lays out differently. Constructs that share a layout share a
 * kind: there is one {@code BINARY_EXPRESSION} rather than one kind per operator, because the
 * operator is a token the emitter can read.
 */
public enum SyntaxKind {

    /** A leaf holding one significant token and its trivia. */
    TOKEN,

    /** A whole source file. */
    COMPILATION_UNIT,

    /** A region the parser could not interpret, emitted verbatim. */
    UNPARSED,

    /** A region between formatter-off and formatter-on markers, emitted verbatim. */
    VERBATIM,

    // Compilation unit level.
    PACKAGE_DECLARATION,
    IMPORT_DECLARATION,

    // Type declarations.
    CLASS_DECLARATION,
    INTERFACE_DECLARATION,
    ENUM_DECLARATION,
    RECORD_DECLARATION,
    ANNOTATION_TYPE_DECLARATION,
    CLASS_BODY,
    ENUM_CONSTANTS,
    ENUM_CONSTANT,
    RECORD_HEADER,
    RECORD_COMPONENT,
    EXTENDS_CLAUSE,
    IMPLEMENTS_CLAUSE,
    PERMITS_CLAUSE,

    // Modifiers and annotations.
    MODIFIERS,
    ANNOTATION,
    ANNOTATION_ARGUMENTS,
    ANNOTATION_ELEMENT,

    // Types.
    CLASS_TYPE,
    PRIMITIVE_TYPE,
    VAR_TYPE,
    ARRAY_TYPE,
    WILDCARD_TYPE,
    TYPE_ARGUMENTS,
    TYPE_PARAMETERS,
    TYPE_PARAMETER,
    TYPE_BOUND,

    // Members.
    FIELD_DECLARATION,
    METHOD_DECLARATION,
    CONSTRUCTOR_DECLARATION,
    COMPACT_CONSTRUCTOR_DECLARATION,
    ANNOTATION_ELEMENT_DECLARATION,
    INITIALIZER_BLOCK,
    VARIABLE_DECLARATOR,
    PARAMETERS,
    PARAMETER,
    THROWS_CLAUSE,
    DEFAULT_VALUE,

    // Statements.
    BLOCK,
    LOCAL_VARIABLE_DECLARATION,
    LOCAL_TYPE_DECLARATION,
    EXPRESSION_STATEMENT,
    IF_STATEMENT,
    ELSE_CLAUSE,
    FOR_STATEMENT,
    ENHANCED_FOR_STATEMENT,
    WHILE_STATEMENT,
    DO_STATEMENT,
    SWITCH_STATEMENT,
    SWITCH_BLOCK,
    SWITCH_CASE,
    CASE_LABELS,
    CASE_GUARD,
    TRY_STATEMENT,
    RESOURCES,
    RESOURCE,
    CATCH_CLAUSE,
    FINALLY_CLAUSE,
    RETURN_STATEMENT,
    THROW_STATEMENT,
    BREAK_STATEMENT,
    CONTINUE_STATEMENT,
    YIELD_STATEMENT,
    SYNCHRONIZED_STATEMENT,
    LABELED_STATEMENT,
    ASSERT_STATEMENT,
    EMPTY_STATEMENT,

    // Expressions.
    ASSIGNMENT_EXPRESSION,
    TERNARY_EXPRESSION,
    BINARY_EXPRESSION,
    INSTANCEOF_EXPRESSION,
    UNARY_EXPRESSION,
    POSTFIX_EXPRESSION,
    CAST_EXPRESSION,
    LAMBDA_EXPRESSION,
    LAMBDA_PARAMETERS,
    METHOD_REFERENCE,
    METHOD_INVOCATION,
    ARGUMENTS,
    MEMBER_ACCESS,
    ARRAY_ACCESS,
    PARENTHESIZED_EXPRESSION,
    OBJECT_CREATION,
    ARRAY_CREATION,
    ARRAY_INITIALIZER,
    DIMENSION,
    CLASS_LITERAL,
    SWITCH_EXPRESSION,
    LITERAL,
    NAME,
    QUALIFIED_NAME,
    THIS_EXPRESSION,
    SUPER_EXPRESSION,
    ANONYMOUS_CLASS_BODY,
    WITH_EXPRESSION,

    // Patterns.
    TYPE_PATTERN,
    RECORD_PATTERN,
    PATTERN_COMPONENTS;

    /** Whether nodes of this kind are emitted exactly as they were written. */
    public boolean isVerbatim() {
        return this == UNPARSED || this == VERBATIM;
    }

    /** Whether nodes of this kind declare a type. */
    public boolean isTypeDeclaration() {
        return this == CLASS_DECLARATION
                || this == INTERFACE_DECLARATION
                || this == ENUM_DECLARATION
                || this == RECORD_DECLARATION
                || this == ANNOTATION_TYPE_DECLARATION;
    }

    /** Whether nodes of this kind are members of a type body. */
    public boolean isMember() {
        return isTypeDeclaration()
                || this == FIELD_DECLARATION
                || this == METHOD_DECLARATION
                || this == CONSTRUCTOR_DECLARATION
                || this == COMPACT_CONSTRUCTOR_DECLARATION
                || this == ANNOTATION_ELEMENT_DECLARATION
                || this == INITIALIZER_BLOCK;
    }

}
