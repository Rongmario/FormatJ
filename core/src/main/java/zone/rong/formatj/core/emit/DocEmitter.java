package zone.rong.formatj.core.emit;

import zone.rong.formatj.api.Style;
import zone.rong.formatj.api.rules.BlankLineRules;
import zone.rong.formatj.api.rules.BraceRules;
import zone.rong.formatj.api.rules.IndentRules;
import zone.rong.formatj.api.rules.RecordRules;
import zone.rong.formatj.api.rules.SpacingRules;
import zone.rong.formatj.api.rules.WrappingRules;
import zone.rong.formatj.core.cst.GreenNode;
import zone.rong.formatj.core.cst.SyntaxKind;
import zone.rong.formatj.core.cst.SyntaxNode;
import zone.rong.formatj.core.ir.Doc;
import java.util.ArrayList;
import java.util.List;

/**
 * Turns a concrete syntax tree into a {@link Doc}, consulting the {@link Style} at every decision.
 *
 * <p>Most tokens are laid out as the parser found them, comments travelling with the token they
 * are attached to. The optional semicolon after a no-argument enum constant list is a style choice
 * and is written or omitted here. Rules that would rewrite other code — braces on a one-line
 * {@code if}, lambda parentheses, import order — are not applied yet.
 */
public final class DocEmitter extends StatementEmitter {

    public DocEmitter(Style style) {
        super(style);
    }

    public Style style() {
        return style;
    }

    /** Emits a document for the given tree. */
    public Doc emit(SyntaxNode node) {
        return emit(node.green());
    }

    @Override
    protected Doc emit(GreenNode node) {
        if (node instanceof GreenNode.Leaf leaf) {
            return leaf(leaf);
        }
        return switch (node.kind()) {
            case COMPILATION_UNIT -> emitCompilationUnit(node);
            case UNPARSED, VERBATIM -> verbatim(node);

            case PACKAGE_DECLARATION, IMPORT_DECLARATION -> emitFlatDeclaration(node);

            case CLASS_DECLARATION, INTERFACE_DECLARATION, ENUM_DECLARATION, ANNOTATION_TYPE_DECLARATION ->
                    emitTypeDeclaration(node);
            case RECORD_DECLARATION -> emitTypeDeclaration(node);
            case CLASS_BODY -> emitClassBody(node);
            case ENUM_CONSTANTS -> emitEnumConstants(node, null);
            case ENUM_CONSTANT -> emitEnumConstant(node);
            case RECORD_HEADER ->
                    delimitedList(
                            node,
                            rule(RecordRules.COMPONENT_WRAPPING),
                            continuation(),
                            rule(SpacingRules.WITHIN_PARENTHESES));
            case RECORD_COMPONENT -> emitSpaced(node);
            case EXTENDS_CLAUSE, IMPLEMENTS_CLAUSE, PERMITS_CLAUSE -> emitTypeListClause(node);
            case THROWS_CLAUSE -> emitThrows(node);

            case MODIFIERS -> emitModifiers(node);
            case ANNOTATION -> emitAnnotation(node);
            case ANNOTATION_ARGUMENTS ->
                    delimitedList(
                            node,
                            rule(WrappingRules.ANNOTATION_ARGUMENTS),
                            continuation(),
                            rule(SpacingRules.WITHIN_PARENTHESES));
            case ANNOTATION_ELEMENT -> emitAnnotationElement(node);

            case CLASS_TYPE, PRIMITIVE_TYPE, VAR_TYPE, ARRAY_TYPE, WILDCARD_TYPE, QUALIFIED_NAME, NAME, LITERAL,
                    THIS_EXPRESSION, SUPER_EXPRESSION, CLASS_LITERAL -> emitTypeLike(node);
            case TYPE_ARGUMENTS -> emitTypeArguments(node);
            case TYPE_PARAMETERS -> emitTypeParameters(node);
            case TYPE_PARAMETER, TYPE_BOUND -> emitSpaced(node);

            case FIELD_DECLARATION -> emitDeclarationLine(node.children());
            case METHOD_DECLARATION, CONSTRUCTOR_DECLARATION, ANNOTATION_ELEMENT_DECLARATION ->
                    emitMethodDeclaration(node);
            case COMPACT_CONSTRUCTOR_DECLARATION -> emitCompactConstructor(node);
            case INITIALIZER_BLOCK -> emitInitializerBlock(node);
            case VARIABLE_DECLARATOR -> emitVariableDeclarator(node);
            case PARAMETERS ->
                    delimitedList(
                            node,
                            rule(WrappingRules.METHOD_PARAMETERS),
                            continuation(),
                            rule(SpacingRules.WITHIN_PARENTHESES));
            case PARAMETER -> emitParameter(node);
            case DEFAULT_VALUE -> emitSpaced(node);

            case BLOCK -> emitBlock(node);
            case LOCAL_VARIABLE_DECLARATION -> emitLocalVariableDeclaration(node);
            case EXPRESSION_STATEMENT -> emitStatementWithSemicolon(node);
            case IF_STATEMENT -> emitIf(node);
            case ELSE_CLAUSE -> emitElse(node);
            case FOR_STATEMENT -> emitFor(node);
            case ENHANCED_FOR_STATEMENT -> emitEnhancedFor(node);
            case WHILE_STATEMENT -> emitWhile(node);
            case DO_STATEMENT -> emitDo(node);
            case SWITCH_STATEMENT, SWITCH_EXPRESSION -> emitSwitchHeader(node);
            case SWITCH_BLOCK -> emitSwitchBlock(node);
            case SWITCH_CASE -> emitSwitchCase(node);
            case CASE_LABELS -> emitCaseLabels(node);
            case CASE_GUARD -> emitCaseGuard(node);
            case TRY_STATEMENT -> emitTry(node);
            case RESOURCES -> emitResources(node);
            case RESOURCE -> emitResource(node);
            case CATCH_CLAUSE -> emitCatch(node);
            case FINALLY_CLAUSE -> emitFinally(node);
            case RETURN_STATEMENT, THROW_STATEMENT, BREAK_STATEMENT, CONTINUE_STATEMENT, YIELD_STATEMENT,
                    ASSERT_STATEMENT -> emitKeywordStatement(node);
            case SYNCHRONIZED_STATEMENT -> emitSynchronized(node);
            case LABELED_STATEMENT -> emitLabeled(node);
            case EMPTY_STATEMENT -> emitConcatenated(node);

            case ASSIGNMENT_EXPRESSION -> emitAssignment(node);
            case TERNARY_EXPRESSION -> emitTernary(node);
            case BINARY_EXPRESSION -> emitBinary(node);
            case INSTANCEOF_EXPRESSION -> emitInstanceof(node);
            case UNARY_EXPRESSION -> emitUnary(node);
            case POSTFIX_EXPRESSION, ARRAY_ACCESS, METHOD_REFERENCE, DIMENSION -> emitConcatenated(node);
            case CAST_EXPRESSION -> emitCast(node);
            case LAMBDA_EXPRESSION -> emitLambda(node);
            case LAMBDA_PARAMETERS -> emitLambdaParameters(node);
            case METHOD_INVOCATION, MEMBER_ACCESS -> emitChain(node);
            case ARGUMENTS -> emitArguments(node);
            case PARENTHESIZED_EXPRESSION -> emitParenthesized(node);
            case OBJECT_CREATION, ARRAY_CREATION -> emitObjectCreation(node);
            case ARRAY_INITIALIZER -> emitArrayInitializer(node);
            case ANONYMOUS_CLASS_BODY -> emitClassBody(node);
            case WITH_EXPRESSION -> emitWithExpression(node);

            case TYPE_PATTERN -> emitTypePattern(node);
            case RECORD_PATTERN -> emitRecordPattern(node);
            case PATTERN_COMPONENTS -> emitPatternComponents(node);

            case TOKEN, LOCAL_TYPE_DECLARATION -> emitConcatenated(node);
        };
    }

    // ------------------------------------------------------ compilation unit

    private Doc emitCompilationUnit(GreenNode node) {
        List<GreenNode> children = node.children();
        List<Doc> parts = new ArrayList<>();
        GreenNode previous = null;
        for (int i = 0; i < children.size(); i++) {
            GreenNode child = children.get(i);
            boolean endOfFile = i == children.size() - 1 && isLeaf(child);
            if (endOfFile && !hasComments(child)) {
                // Nothing left but the file's trailing newline, which the pipeline normalises.
                continue;
            }
            if (previous != null) {
                parts.add(separatorBefore(child, minimumAtFileLevel(previous, child)));
            }
            parts.add(emit(child));
            previous = child;
        }
        return Doc.concat(parts);
    }

    private static boolean hasComments(GreenNode node) {
        return node instanceof GreenNode.Leaf leaf && leaf.token().hasComments();
    }

    private int minimumAtFileLevel(GreenNode previous, GreenNode next) {
        if (previous.kind() == SyntaxKind.PACKAGE_DECLARATION) {
            return rule(BlankLineRules.AFTER_PACKAGE);
        }
        if (previous.kind() == SyntaxKind.IMPORT_DECLARATION) {
            return next.kind() == SyntaxKind.IMPORT_DECLARATION ? 0 : rule(BlankLineRules.AFTER_IMPORTS);
        }
        if (next.kind().isTypeDeclaration()) {
            return rule(BlankLineRules.BEFORE_CLASS);
        }
        return 0;
    }

    /** A declaration whose parts are separated by single spaces, with punctuation kept tight. */
    private Doc emitFlatDeclaration(GreenNode node) {
        List<GreenNode> children = node.children();
        List<Doc> parts = new ArrayList<>();
        for (int i = 0; i < children.size(); i++) {
            GreenNode child = children.get(i);
            if (i > 0) {
                GreenNode previous = children.get(i - 1);
                boolean tight = is(child, ";") || is(child, ".") || is(previous, ".");
                parts.add(spaceIf(!tight));
            }
            parts.add(emit(child));
        }
        return Doc.concat(parts);
    }

    // --------------------------------------------------------- declarations

    private Doc emitTypeDeclaration(GreenNode node) {
        List<GreenNode> children = node.children();
        List<Doc> parts = new ArrayList<>();
        for (int i = 0; i < children.size(); i++) {
            GreenNode child = children.get(i);
            if (child.kind() == SyntaxKind.CLASS_BODY) {
                parts.add(braceLead(rule(BraceRules.CLASS_PLACEMENT)));
                parts.add(emit(child));
                continue;
            }
            if (i > 0) {
                GreenNode previous = children.get(i - 1);
                if (endsWithAnnotation(previous)) {
                    parts.add(annotationSeparator(child));
                } else if (needsSpaceInHeader(previous, child)) {
                    parts.add(space());
                }
            }
            parts.add(emit(child));
        }
        return Doc.concat(parts);
    }

    private boolean needsSpaceInHeader(GreenNode previous, GreenNode next) {
        if (next.kind() == SyntaxKind.TYPE_PARAMETERS
                || next.kind() == SyntaxKind.RECORD_HEADER
                || next.kind() == SyntaxKind.PARAMETERS
                || next.kind() == SyntaxKind.ARGUMENTS) {
            return false;
        }
        if (is(previous, "@") || is(next, "(")) {
            return false;
        }
        return true;
    }

    /**
     * Whether a node is, or ends with, an annotation.
     *
     * <p>The last annotation of a modifier list is followed by the declaration itself, so the decision
     * about the line break belongs to whoever emits the declaration, not to the modifier list.
     */
    private static boolean endsWithAnnotation(GreenNode node) {
        if (node.kind() == SyntaxKind.ANNOTATION) {
            return true;
        }
        return node.kind() == SyntaxKind.MODIFIERS
                && !node.children().isEmpty()
                && node.children().getLast().kind() == SyntaxKind.ANNOTATION;
    }

    private Doc emitClassBody(GreenNode node) {
        return emitBracedBody(
                node,
                rule(BraceRules.EMPTY_CLASS_BODY),
                rule(BlankLineRules.AFTER_CLASS_OPENING_BRACE),
                rule(BlankLineRules.BEFORE_CLASS_CLOSING_BRACE));
    }

    @Override
    protected Doc emitBodyChild(GreenNode statement, List<GreenNode> body, int index) {
        if (statement.kind() == SyntaxKind.ENUM_CONSTANTS) {
            return emitEnumConstants(statement, membersFollow(body, index));
        }
        return emit(statement);
    }

    private static boolean membersFollow(List<GreenNode> body, int enumConstantsIndex) {
        for (int i = enumConstantsIndex + 1; i < body.size(); i++) {
            if (body.get(i).kind().isMember()) {
                return true;
            }
        }
        return false;
    }

    @Override
    protected int minimumBetween(GreenNode previous, GreenNode next) {
        if (previous.kind() == SyntaxKind.ENUM_CONSTANTS) {
            return rule(BlankLineRules.AFTER_ENUM_CONSTANTS);
        }
        if (previous.kind() == SyntaxKind.INITIALIZER_BLOCK || next.kind() == SyntaxKind.INITIALIZER_BLOCK) {
            return rule(BlankLineRules.AROUND_INITIALIZER_BLOCK);
        }
        return switch (next.kind()) {
            case METHOD_DECLARATION, CONSTRUCTOR_DECLARATION, ANNOTATION_ELEMENT_DECLARATION ->
                    rule(BlankLineRules.BEFORE_METHOD);
            case COMPACT_CONSTRUCTOR_DECLARATION -> rule(BlankLineRules.BEFORE_RECORD_COMPACT_CONSTRUCTOR);
            case FIELD_DECLARATION -> rule(BlankLineRules.BEFORE_FIELD);
            case CLASS_DECLARATION, INTERFACE_DECLARATION, ENUM_DECLARATION, RECORD_DECLARATION,
                    ANNOTATION_TYPE_DECLARATION -> rule(BlankLineRules.BEFORE_CLASS);
            default -> 0;
        };
    }

    /**
     * @param membersFollow whether the enum body continues after the constant list; {@code null}
     *     when the caller has no neighbour context
     */
    private Doc emitEnumConstants(GreenNode node, Boolean membersFollow) {
        List<GreenNode> children = node.children();
        GreenNode semicolon = null;
        if (!children.isEmpty() && is(children.getLast(), ";")) {
            semicolon = children.getLast();
            children = children.subList(0, children.size() - 1);
        }
        Doc list = emitEnumConstantList(children);
        boolean parameterized = hasParameterizedConstant(children);
        boolean required = parameterized
                || Boolean.TRUE.equals(membersFollow)
                || rule(WrappingRules.REQUIRE_ENUM_CONSTANT_SEMICOLON);
        boolean mayOmit = Boolean.FALSE.equals(membersFollow)
                && !parameterized
                && !rule(WrappingRules.REQUIRE_ENUM_CONSTANT_SEMICOLON);

        if (semicolon != null) {
            if (mayOmit && !hasComments(semicolon)) {
                return list;
            }
            return Doc.concat(list, emit(semicolon));
        }
        if (required && hasEnumConstant(children)) {
            return Doc.concat(list, Doc.text(";"));
        }
        return list;
    }

    private static boolean hasEnumConstant(List<GreenNode> children) {
        for (GreenNode child : children) {
            if (child.kind() == SyntaxKind.ENUM_CONSTANT) {
                return true;
            }
        }
        return false;
    }

    private static boolean hasParameterizedConstant(List<GreenNode> children) {
        for (GreenNode child : children) {
            if (child.kind() != SyntaxKind.ENUM_CONSTANT) {
                continue;
            }
            for (GreenNode part : child.children()) {
                if (part.kind() == SyntaxKind.ARGUMENTS) {
                    return true;
                }
            }
        }
        return false;
    }

    private Doc emitEnumConstantList(List<GreenNode> children) {
        List<GreenNode> starts = new ArrayList<>();
        List<Doc> elements = new ArrayList<>();
        List<Doc> current = new ArrayList<>();
        GreenNode start = null;
        for (GreenNode child : children) {
            if (start == null) {
                start = child;
            }
            current.add(emit(child));
            if (is(child, ",")) {
                elements.add(Doc.concat(current));
                starts.add(start);
                start = null;
                current = new ArrayList<>();
            }
        }
        if (!current.isEmpty()) {
            elements.add(Doc.concat(current));
            starts.add(start);
        }

        if (authorBrokeInside(children)) {
            // Constants the author put on separate lines stay that way, blank lines and all;
            // collapsing them reads as churn in a diff.
            List<Doc> parts = new ArrayList<>();
            for (int i = 0; i < elements.size(); i++) {
                if (i > 0) {
                    parts.add(separatorBefore(starts.get(i), 0));
                }
                parts.add(elements.get(i));
            }
            return Doc.concat(parts);
        }

        Doc separator = rule(SpacingRules.AFTER_COMMA) ? Doc.line() : Doc.softLine();
        return switch (rule(WrappingRules.ENUM_CONSTANTS)) {
            case NEVER -> Doc.concat(elements);
            case CHOP_DOWN_ALWAYS -> Doc.breakingGroup(Doc.join(separator, elements));
            case WRAP_IF_LONG -> Doc.fill(withSeparators(elements, separator));
            default -> Doc.group(Doc.join(separator, elements));
        };
    }

    private static List<Doc> withSeparators(List<Doc> elements, Doc separator) {
        List<Doc> parts = new ArrayList<>();
        for (int i = 0; i < elements.size(); i++) {
            if (i > 0) {
                parts.add(separator);
            }
            parts.add(elements.get(i));
        }
        return parts;
    }

    private Doc emitEnumConstant(GreenNode node) {
        List<GreenNode> children = node.children();
        List<Doc> parts = new ArrayList<>();
        for (int i = 0; i < children.size(); i++) {
            GreenNode child = children.get(i);
            if (i > 0) {
                if (child.kind() == SyntaxKind.CLASS_BODY) {
                    parts.add(braceLead(rule(BraceRules.CLASS_PLACEMENT)));
                } else if (child.kind() != SyntaxKind.ARGUMENTS) {
                    parts.add(space());
                }
            }
            parts.add(emit(child));
        }
        return Doc.concat(parts);
    }

    private Doc emitTypeListClause(GreenNode node) {
        List<GreenNode> children = node.children();
        List<Doc> elements = new ArrayList<>();
        List<Doc> current = new ArrayList<>();
        for (GreenNode child : children.subList(1, children.size())) {
            current.add(emit(child));
            if (is(child, ",")) {
                elements.add(Doc.concat(current));
                current = new ArrayList<>();
            }
        }
        if (!current.isEmpty()) {
            elements.add(Doc.concat(current));
        }
        Doc separator = rule(SpacingRules.AFTER_COMMA) ? Doc.line() : Doc.softLine();
        return Doc.group(
                Doc.indent(
                        continuation(),
                        Doc.concat(emit(children.getFirst()), Doc.line(), Doc.join(separator, elements))));
    }

    private Doc emitThrows(GreenNode node) {
        List<GreenNode> children = node.children();
        List<Doc> parts = new ArrayList<>();
        for (int i = 1; i < children.size(); i++) {
            GreenNode child = children.get(i);
            if (i > 1 && !is(child, ",")) {
                parts.add(rule(SpacingRules.AFTER_COMMA) ? Doc.line() : Doc.softLine());
            }
            parts.add(emit(child));
        }
        return Doc.group(
                Doc.indent(
                        rule(IndentRules.THROWS_CLAUSE),
                        Doc.concat(Doc.line(), emit(children.getFirst()), space(), Doc.concat(parts))));
    }

    private Doc emitModifiers(GreenNode node) {
        List<GreenNode> children = node.children();
        List<Doc> parts = new ArrayList<>();
        for (int i = 0; i < children.size(); i++) {
            GreenNode child = children.get(i);
            if (i > 0) {
                GreenNode previous = children.get(i - 1);
                if (previous.kind() == SyntaxKind.ANNOTATION) {
                    parts.add(annotationSeparator(child));
                } else if (isNonSealedFragment(previous, child)) {
                    parts.add(Doc.EMPTY);
                } else {
                    parts.add(space());
                }
            }
            parts.add(emit(child));
        }
        return Doc.concat(parts);
    }

    /** {@code non-sealed} arrives as three tokens and must be printed back without spaces. */
    private static boolean isNonSealedFragment(GreenNode previous, GreenNode next) {
        return is(previous, "non") && is(next, "-") || is(previous, "-") && is(next, "sealed");
    }

    private Doc emitAnnotation(GreenNode node) {
        List<GreenNode> children = node.children();
        List<Doc> parts = new ArrayList<>();
        for (int i = 0; i < children.size(); i++) {
            GreenNode child = children.get(i);
            if (child.kind() == SyntaxKind.ANNOTATION_ARGUMENTS) {
                parts.add(spaceIf(rule(SpacingRules.BEFORE_ANNOTATION_PARENTHESIS)));
            }
            parts.add(emit(child));
        }
        return Doc.concat(parts);
    }

    private Doc emitAnnotationElement(GreenNode node) {
        List<GreenNode> children = node.children();
        boolean spaced = rule(SpacingRules.AROUND_ASSIGNMENT_OPERATORS);
        return Doc.concat(
                emit(children.get(0)),
                spaceIf(spaced),
                emit(children.get(1)),
                spaceIf(spaced),
                emit(children.get(2)));
    }

    private Doc emitMethodDeclaration(GreenNode node) {
        List<GreenNode> children = node.children();
        List<Doc> parts = new ArrayList<>();
        for (int i = 0; i < children.size(); i++) {
            GreenNode child = children.get(i);
            if (child.kind() == SyntaxKind.BLOCK) {
                parts.add(braceLead(rule(BraceRules.METHOD_PLACEMENT)));
                parts.add(emit(child));
                continue;
            }
            if (i > 0) {
                GreenNode previous = children.get(i - 1);
                if (endsWithAnnotation(previous)) {
                    parts.add(annotationSeparator(child));
                } else if (needsSpaceInSignature(previous, child)) {
                    parts.add(space());
                }
            }
            parts.add(emit(child));
        }
        return Doc.concat(parts);
    }

    private boolean needsSpaceInSignature(GreenNode previous, GreenNode next) {
        if (next.kind() == SyntaxKind.PARAMETERS) {
            return rule(SpacingRules.BEFORE_METHOD_DECLARATION_PARENTHESIS);
        }
        if (next.kind() == SyntaxKind.THROWS_CLAUSE) {
            return false;
        }
        if (is(next, ";") || is(next, "[") || is(next, "]")) {
            return false;
        }
        return true;
    }

    private Doc emitCompactConstructor(GreenNode node) {
        List<GreenNode> children = node.children();
        List<Doc> parts = new ArrayList<>();
        for (int i = 0; i < children.size(); i++) {
            GreenNode child = children.get(i);
            if (child.kind() == SyntaxKind.BLOCK) {
                parts.add(braceLead(rule(BraceRules.METHOD_PLACEMENT)));
            } else if (i > 0) {
                parts.add(space());
            }
            parts.add(emit(child));
        }
        return Doc.concat(parts);
    }

    private Doc emitInitializerBlock(GreenNode node) {
        List<GreenNode> children = node.children();
        List<Doc> parts = new ArrayList<>();
        for (int i = 0; i < children.size(); i++) {
            GreenNode child = children.get(i);
            if (i > 0) {
                if (child.kind() == SyntaxKind.BLOCK) {
                    parts.add(braceLead(rule(BraceRules.METHOD_PLACEMENT)));
                } else {
                    parts.add(endsWithAnnotation(children.get(i - 1)) ? annotationSeparator(child) : space());
                }
            }
            parts.add(emit(child));
        }
        return Doc.concat(parts);
    }

    // ---------------------------------------------------------------- misc

    /** Children separated by single spaces. */
    private Doc emitSpaced(GreenNode node) {
        List<GreenNode> children = node.children();
        List<Doc> parts = new ArrayList<>();
        for (int i = 0; i < children.size(); i++) {
            if (i > 0) {
                parts.add(space());
            }
            parts.add(emit(children.get(i)));
        }
        return Doc.concat(parts);
    }

    /** Children with nothing between them. */
    private Doc emitConcatenated(GreenNode node) {
        List<Doc> parts = new ArrayList<>();
        for (GreenNode child : node.children()) {
            parts.add(emit(child));
        }
        return Doc.concat(parts);
    }

    private Doc emitStatementWithSemicolon(GreenNode node) {
        return emitConcatenated(node);
    }

}
