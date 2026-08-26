package zone.rong.formatj.core.emit;

import zone.rong.formatj.api.Style;
import zone.rong.formatj.api.rules.AnnotationPlacement;
import zone.rong.formatj.api.rules.AnnotationRules;
import zone.rong.formatj.api.rules.BlankLineRules;
import zone.rong.formatj.api.rules.BraceRules;
import zone.rong.formatj.api.rules.ClosingDelimiter;
import zone.rong.formatj.api.rules.EmptyBodyStyle;
import zone.rong.formatj.api.rules.IndentRules;
import zone.rong.formatj.api.rules.PreservationRules;
import zone.rong.formatj.api.rules.SpacingRules;
import zone.rong.formatj.api.rules.SwitchRules;
import zone.rong.formatj.api.rules.WrapPolicy;
import zone.rong.formatj.api.rules.WrappingRules;
import zone.rong.formatj.core.cst.GreenNode;
import zone.rong.formatj.core.cst.SyntaxKind;
import zone.rong.formatj.core.ir.AlignmentSite;
import zone.rong.formatj.core.ir.Doc;
import java.util.ArrayList;
import java.util.List;

/** Layout for blocks, statements and switch bodies. */
abstract class StatementEmitter extends ExpressionEmitter {

    StatementEmitter(Style style) {
        super(style);
    }

    @Override
    protected Doc emitBlockLike(GreenNode node) {
        return emitBlock(node);
    }

    @Override
    protected Doc emitBlockLike(GreenNode node, EmptyBodyStyle emptyStyle) {
        return emitBracedBody(node, emptyStyle, 0, 0, false);
    }

    @Override
    protected Doc emitBlockLike(GreenNode node, boolean mayInline) {
        return emitBracedBody(node, rule(BraceRules.EMPTY_CONTROL_BODY), 0, 0, mayInline);
    }

    /** A lambda's block body, which has a keep-on-one-line rule of its own. */
    @Override
    protected Doc emitLambdaBody(GreenNode node, EmptyBodyStyle emptyStyle) {
        return emitBracedBody(
                node,
                emptyStyle,
                0,
                0,
                keepsOnOneLine(node, WrappingRules.KEEP_SIMPLE_LAMBDAS_ON_ONE_LINE));
    }

    protected Doc emitBlock(GreenNode node) {
        return emitBracedBody(
                node,
                rule(BraceRules.EMPTY_CONTROL_BODY),
                0,
                0,
                keepsOnOneLine(node, PreservationRules.KEEP_SIMPLE_BLOCKS_INLINE));
    }

    /** The block that forms a method, constructor or initializer body. */
    protected Doc emitMethodBody(GreenNode node) {
        return emitBracedBody(
                node,
                rule(BraceRules.EMPTY_METHOD_BODY),
                0,
                0,
                keepsOnOneLine(node, WrappingRules.KEEP_SIMPLE_METHODS_ON_ONE_LINE));
    }

    /**
     * A braced body: the braces themselves plus its indented contents.
     *
     * @param blankLinesAfterOpen minimum blank lines just inside the opening brace
     * @param blankLinesBeforeClose minimum blank lines just before the closing brace
     */
    protected Doc emitBracedBody(
            GreenNode node,
            EmptyBodyStyle emptyStyle,
            int blankLinesAfterOpen,
            int blankLinesBeforeClose) {
        return emitBracedBody(node, emptyStyle, blankLinesAfterOpen, blankLinesBeforeClose, false);
    }

    /**
     * @param mayInline whether this body is allowed to print on one line, because the author wrote it
     *     that way and the rule governing the construct permits it. The single line is only ever an
     *     option: the body is one group, and the layout engine still breaks it if it does not fit, or
     *     if anything inside it forces a break. What it prints when broken is byte for byte what the
     *     ordinary path prints, which is what keeps formatting a fixed point — a body that was too
     *     long to stay inline comes back on the next pass as an ordinary multi-line body and lays out
     *     identically.
     */
    protected Doc emitBracedBody(
            GreenNode node,
            EmptyBodyStyle emptyStyle,
            int blankLinesAfterOpen,
            int blankLinesBeforeClose,
            boolean mayInline) {
        List<GreenNode> children = node.children();
        GreenNode open = children.getFirst();
        GreenNode close = children.getLast();
        List<GreenNode> body = children.subList(1, children.size() - 1);

        if (body.isEmpty() && !hasLeadingComments(close)) {
            return switch (emptyStyle) {
                case COMPACT -> Doc.concat(emit(open), emit(close));
                case SPACED -> Doc.concat(emit(open), space(), emit(close));
                case EXPANDED -> Doc.concat(emit(open), Doc.hardLine(), closeBrace(close));
            };
        }

        // A comment before the closing brace has to be indented with the body, which needs a line of
        // its own; and a formatter-off region is copied through with its own line structure. Neither
        // can be squeezed onto one line, so both fall back to the ordinary path.
        boolean inline = mayInline && !hasLeadingComments(close) && !turnsFormattingOff(body);

        List<Doc> parts = new ArrayList<>();
        int i = 0;
        while (i < body.size()) {
            GreenNode statement = body.get(i);
            int minimum =
                    i == 0
                    ? minimumAfterOpen(statement, blankLinesAfterOpen)
                    : minimumBetween(body.get(i - 1), statement);
            parts.add(optional(separatorBefore(statement, minimum), inline));
            int off = formatterOffIndex(statement);
            if (off >= 0) {
                int end = i + 1;
                while (end < body.size() && !turnsFormattingOn(body.get(end))) {
                    end++;
                }
                parts.add(formatterOffRegion(body.subList(i, end), off));
                i = end;
                continue;
            }
            HoistedLeading hoisted = hoistLeadingTrivia(statement);
            parts.add(hoisted.leading());
            parts.add(emitBodyChild(hoisted.node(), body, i));
            i++;
        }
        if (hasLeadingComments(close)) {
            // A comment on the closing line belongs to the body, indented with it, not to the brace.
            parts.add(lineBreaks(blankLinesBefore(close, blankLinesBeforeClose)));
            parts.add(commentsBefore(close));
            Doc contents = Doc.indent(indentSize(), Doc.concat(parts));
            return Doc.concat(emit(open), contents, Doc.hardLine(), closeBrace(close));
        }
        Doc contents = Doc.indent(indentSize(), Doc.concat(parts));
        Doc closing =
                Doc.concat(optional(lineBreaks(blankLinesBefore(close, blankLinesBeforeClose)), inline), emit(close));
        Doc braced = Doc.concat(emit(open), contents, closing);
        return inline ? Doc.group(braced) : braced;
    }

    /**
     * A hard break, or the choice between it and a space.
     *
     * <p>{@link Doc.IfBreak} is what lets one body serve both shapes: its two branches are never
     * scanned for forced breaks, so the hard breaks of the ordinary layout can sit inside it without
     * forcing the group they are in to break. Whatever else in the body does force a break — a nested
     * multi-line statement, a text block, a comment that ends a line — breaks the group, and the
     * ordinary branch is what prints.
     */
    private static Doc optional(Doc broken, boolean inline) {
        return inline ? Doc.ifBreak(broken, Doc.line()) : broken;
    }

    /** Whether any statement of this body turns formatting off. */
    private boolean turnsFormattingOff(List<GreenNode> body) {
        for (GreenNode statement : body) {
            if (formatterOffIndex(statement) >= 0) {
                return true;
            }
        }
        return false;
    }

    /** One child of a braced body. Override to pass neighbour context into a layout rule. */
    protected Doc emitBodyChild(GreenNode statement, List<GreenNode> body, int index) {
        return emit(statement);
    }

    /** The closing brace itself, without the comments that lead it. */
    protected Doc closeBrace(GreenNode close) {
        return close instanceof GreenNode.Leaf leaf ? leaf(leaf, false) : emit(close);
    }

    /** Minimum blank lines a rule demands between two neighbours in a body. */
    protected int minimumBetween(GreenNode previous, GreenNode next) {
        return 0;
    }

    /** Minimum blank lines before the first child of a body. Override where the child has its own rule. */
    protected int minimumAfterOpen(GreenNode first, int fallback) {
        return fallback;
    }

    // ---------------------------------------------------------- statements

    protected Doc emitStatementSequence(List<GreenNode> children) {
        List<Doc> parts = new ArrayList<>();
        for (int i = 0; i < children.size(); i++) {
            if (i > 0) {
                parts.add(separatorBefore(children.get(i), 0));
            }
            HoistedLeading hoisted = hoistLeadingTrivia(children.get(i));
            parts.add(hoisted.leading());
            parts.add(emit(hoisted.node()));
        }
        return Doc.concat(parts);
    }

    /** The body of a control statement: a block on the same line, or an indented single statement. */
    protected Doc controlBody(GreenNode body) {
        if (body.kind() == SyntaxKind.BLOCK) {
            return Doc.concat(braceLead(rule(BraceRules.CONTROL_PLACEMENT)), emitBlock(body));
        }
        HoistedLeading hoisted = hoistLeadingTrivia(body);
        return Doc.indent(indentSize(), Doc.concat(Doc.hardLine(), hoisted.leading(), emit(hoisted.node())));
    }

    protected Doc emitIf(GreenNode node) {
        List<GreenNode> children = node.children();
        List<Doc> parts = new ArrayList<>();
        parts.add(emit(children.get(0)));
        parts.add(spaceIf(rule(SpacingRules.BEFORE_IF_PARENTHESIS)));
        parts.add(emitCondition(children.get(1), children.get(2), children.get(3)));
        parts.add(controlBody(children.get(4)));
        if (children.size() > 5) {
            parts.add(emitElse(children.get(5), children.get(4).kind() != SyntaxKind.BLOCK));
        }
        return Doc.concat(parts);
    }

    protected Doc emitElse(GreenNode node) {
        return emitElse(node, false);
    }

    /**
     * An else clause.
     *
     * @param afterUnbracedBody whether the body this else follows was a bare statement rather than a
     *     block. There is no closing brace to sit beside in that case, so the else has to start a
     *     line of its own however {@code braces.else-on-new-line} is set; trailing it after the
     *     statement would read as part of that statement.
     */
    protected Doc emitElse(GreenNode node, boolean afterUnbracedBody) {
        List<GreenNode> children = node.children();
        GreenNode body = children.get(1);
        Doc keyword = emit(children.get(0));
        Doc lead = afterUnbracedBody || rule(BraceRules.ELSE_ON_NEW_LINE) ? Doc.hardLine() : space();
        if (body.kind() == SyntaxKind.IF_STATEMENT) {
            return Doc.concat(lead, keyword, space(), emit(body));
        }
        return Doc.concat(lead, keyword, controlBody(body));
    }

    /**
     * A parenthesised condition.
     *
     * <p>The parentheses never break away from the condition. A long condition wraps inside them,
     * which is what Java code looks like everywhere; hanging the closing parenthesis on its own line
     * is a JavaScript habit that reads badly next to a brace.
     */
    protected Doc emitCondition(GreenNode open, GreenNode expression, GreenNode close) {
        boolean inside = rule(SpacingRules.WITHIN_PARENTHESES);
        return Doc.concat(
                emit(open),
                spaceIf(inside),
                authorGroup(expression, emit(expression)),
                spaceIf(inside),
                emit(close));
    }

    protected Doc emitWhile(GreenNode node) {
        List<GreenNode> children = node.children();
        return Doc.concat(
                emit(children.get(0)),
                spaceIf(rule(SpacingRules.BEFORE_WHILE_PARENTHESIS)),
                emitCondition(children.get(1), children.get(2), children.get(3)),
                controlBody(children.get(4)));
    }

    protected Doc emitDo(GreenNode node) {
        List<GreenNode> children = node.children();
        // An unbraced body has no closing brace for the while to sit beside.
        boolean whileOnNewLine = children.get(1).kind() != SyntaxKind.BLOCK || rule(BraceRules.ELSE_ON_NEW_LINE);
        return Doc.concat(
                emit(children.get(0)),
                controlBody(children.get(1)),
                whileOnNewLine ? Doc.hardLine() : space(),
                emit(children.get(2)),
                spaceIf(rule(SpacingRules.BEFORE_WHILE_PARENTHESIS)),
                emitCondition(children.get(3), children.get(4), children.get(5)),
                semicolonLead(),
                emit(children.get(6)));
    }

    protected Doc emitSynchronized(GreenNode node) {
        List<GreenNode> children = node.children();
        return Doc.concat(
                emit(children.get(0)),
                spaceIf(rule(SpacingRules.BEFORE_SYNCHRONIZED_PARENTHESIS)),
                emitCondition(children.get(1), children.get(2), children.get(3)),
                controlBody(children.get(4)));
    }

    protected Doc emitFor(GreenNode node) {
        List<GreenNode> children = node.children();
        int closing = lastIndexOfLexeme(children, ")");
        List<Doc> header = new ArrayList<>();
        boolean afterSemicolon = false;
        for (int i = 2; i < closing; i++) {
            GreenNode child = children.get(i);
            if (afterSemicolon) {
                header.add(rule(SpacingRules.AFTER_SEMICOLON_IN_FOR) ? Doc.line() : Doc.softLine());
                afterSemicolon = false;
            }
            header.add(emit(child));
            if (is(child, ";")) {
                afterSemicolon = true;
            }
        }
        Doc inner =
                rule(WrappingRules.FOR_STATEMENT) == WrapPolicy.NEVER
                ? Doc.concat(header)
                : authorGroup(node, Doc.indent(continuation(), Doc.concat(header)));
        return Doc.concat(
                emit(children.get(0)),
                spaceIf(rule(SpacingRules.BEFORE_FOR_PARENTHESIS)),
                emit(children.get(1)),
                inner,
                emit(children.get(closing)),
                controlBody(children.get(closing + 1)));
    }

    protected Doc emitEnhancedFor(GreenNode node) {
        List<GreenNode> children = node.children();
        return Doc.concat(
                emit(children.get(0)),
                spaceIf(rule(SpacingRules.BEFORE_FOR_PARENTHESIS)),
                emit(children.get(1)),
                emit(children.get(2)),
                emit(children.get(3)),
                controlBody(children.get(4)));
    }

    /** The {@code Type name : values} header of an enhanced for. */
    protected Doc emitForEachHeader(GreenNode node) {
        List<GreenNode> children = node.children();
        List<Doc> parts = new ArrayList<>();
        for (int i = 0; i < children.size(); i++) {
            GreenNode child = children.get(i);
            if (i > 0) {
                if (is(child, ":")) {
                    parts.add(spaceIf(rule(SpacingRules.BEFORE_COLON_IN_ENHANCED_FOR)));
                } else if (is(children.get(i - 1), ":")) {
                    parts.add(spaceIf(rule(SpacingRules.AFTER_COLON_IN_ENHANCED_FOR)));
                } else {
                    parts.add(space());
                }
            }
            parts.add(emit(child));
        }
        return Doc.concat(parts);
    }

    protected Doc emitTry(GreenNode node) {
        List<GreenNode> children = node.children();
        List<Doc> parts = new ArrayList<>();
        parts.add(emit(children.get(0)));
        int index = 1;
        if (children.get(index).kind() == SyntaxKind.RESOURCES) {
            parts.add(space());
            parts.add(emit(children.get(index++)));
        }
        parts.add(braceLead(rule(BraceRules.CONTROL_PLACEMENT)));
        parts.add(emit(children.get(index++)));
        for (int i = index; i < children.size(); i++) {
            parts.add(emit(children.get(i)));
        }
        return Doc.concat(parts);
    }

    protected Doc emitResources(GreenNode node) {
        List<GreenNode> children = node.children();
        GreenNode open = children.getFirst();
        GreenNode close = children.getLast();
        WrapPolicy policy = rule(WrappingRules.TRY_RESOURCES);
        boolean inside = rule(SpacingRules.WITHIN_PARENTHESES);
        Doc separator = policy == WrapPolicy.NEVER ? space() : Doc.line();

        List<Doc> parts = new ArrayList<>();
        boolean afterSemicolon = false;
        for (GreenNode child : children.subList(1, children.size() - 1)) {
            if (afterSemicolon) {
                parts.add(separator);
                afterSemicolon = false;
            }
            parts.add(emit(child));
            if (is(child, ";")) {
                afterSemicolon = true;
            }
        }
        Doc resources = Doc.concat(parts);

        if (policy == WrapPolicy.NEVER) {
            return Doc.concat(emit(open), spaceIf(inside), resources, spaceIf(inside), emit(close));
        }
        Doc edge = inside ? Doc.line() : Doc.softLine();
        Doc closingEdge = rule(WrappingRules.CLOSING_DELIMITER) == ClosingDelimiter.OWN_LINE ? edge : spaceIf(inside);
        Doc body =
                Doc.concat(
                        emit(open),
                        Doc.indentIfBreak(continuation(), Doc.concat(edge, resources)),
                        closingEdge,
                        emit(close));
        return policy == WrapPolicy.CHOP_DOWN_ALWAYS ? Doc.breakingGroup(body) : authorGroup(node, body);
    }

    protected Doc emitResource(GreenNode node) {
        List<Doc> parts = new ArrayList<>();
        List<GreenNode> children = node.children();
        for (int i = 0; i < children.size(); i++) {
            GreenNode child = children.get(i);
            if (i > 0) {
                boolean assignment = is(child, "=") || is(children.get(i - 1), "=");
                parts.add(spaceIf(!assignment || rule(SpacingRules.AROUND_ASSIGNMENT_OPERATORS)));
            }
            parts.add(emit(child));
        }
        return Doc.concat(parts);
    }

    protected Doc emitCatch(GreenNode node) {
        List<GreenNode> children = node.children();
        Doc lead = rule(BraceRules.CATCH_ON_NEW_LINE) ? Doc.hardLine() : space();
        return Doc.concat(
                lead,
                emit(children.get(0)),
                spaceIf(rule(SpacingRules.BEFORE_CATCH_PARENTHESIS)),
                emitCondition(children.get(1), children.get(2), children.get(3)),
                braceLead(rule(BraceRules.CONTROL_PLACEMENT)),
                emit(children.get(4)));
    }

    protected Doc emitFinally(GreenNode node) {
        List<GreenNode> children = node.children();
        Doc lead = rule(BraceRules.FINALLY_ON_NEW_LINE) ? Doc.hardLine() : space();
        return Doc.concat(
                lead,
                emit(children.get(0)),
                braceLead(rule(BraceRules.CONTROL_PLACEMENT)),
                emit(children.get(1)));
    }

    /** A parameter or catch parameter: types, name, and any multi-catch alternatives. */
    protected Doc emitParameter(GreenNode node) {
        List<GreenNode> children = node.children();
        if (indexOf(children, ":") >= 0) {
            return emitForEachHeader(node);
        }
        List<Doc> parts = new ArrayList<>();
        for (int i = 0; i < children.size(); i++) {
            GreenNode child = children.get(i);
            if (i > 0) {
                GreenNode previous = children.get(i - 1);
                boolean bracket = is(child, "[") || is(child, "]");
                if (previous.kind() == SyntaxKind.ANNOTATION) {
                    parts.add(annotationSeparator(child, rule(AnnotationRules.PARAMETER_PLACEMENT), children));
                } else {
                    parts.add(spaceIf(!bracket));
                }
            }
            parts.add(emit(child));
        }
        return Doc.concat(parts);
    }

    /** A statement made of a keyword, an optional operand and a semicolon. */
    protected Doc emitKeywordStatement(GreenNode node) {
        List<GreenNode> children = node.children();
        List<Doc> parts = new ArrayList<>();
        for (int i = 0; i < children.size(); i++) {
            GreenNode child = children.get(i);
            if (i > 0) {
                parts.add(is(child, ";") ? semicolonLead() : space());
            }
            if (is(child, ":") && rule(SpacingRules.AROUND_TERNARY_OPERATORS)) {
                parts.add(Doc.EMPTY);
            }
            parts.add(emit(child));
        }
        return Doc.concat(parts);
    }

    protected Doc emitLabeled(GreenNode node) {
        List<GreenNode> children = node.children();
        Doc separator = authorBrokeBefore(children.get(2)) ? Doc.hardLine() : space();
        return Doc.concat(emit(children.get(0)), emit(children.get(1)), separator, emit(children.get(2)));
    }

    protected Doc emitLocalVariableDeclaration(GreenNode node) {
        return emitDeclarationLine(
                node.children(),
                rule(AnnotationRules.PARAMETER_PLACEMENT),
                AlignmentSite.VARIABLE_NAME);
    }

    /** Modifiers, a type, declarators and a semicolon, in one line unless something wraps. */
    protected Doc emitDeclarationLine(List<GreenNode> children) {
        return emitDeclarationLine(children, rule(AnnotationRules.DECLARATION_PLACEMENT), AlignmentSite.FIELD_NAME);
    }

    /**
     * As {@link #emitDeclarationLine(List)}, with the placement rule the caller's context asks for.
     *
     * @param nameSite which alignment rule, if any, may line up the declared name with its neighbours.
     *     Only the first declarator is marked: the second name on a line has no column of its own, and
     *     the {@code =} of the first is what the assignment rule lines up.
     */
    protected Doc emitDeclarationLine(List<GreenNode> children, AnnotationPlacement placement, AlignmentSite nameSite) {
        List<Doc> parts = new ArrayList<>();
        boolean firstDeclarator = true;
        for (int i = 0; i < children.size(); i++) {
            GreenNode child = children.get(i);
            if (i > 0 && is(child, ";")) {
                parts.add(semicolonLead());
            } else if (i > 0 && !is(child, ",")) {
                GreenNode previous = children.get(i - 1);
                boolean afterAnnotation = previous.kind() == SyntaxKind.ANNOTATION
                        || previous.kind() == SyntaxKind.MODIFIERS
                                && !previous.children().isEmpty()
                                && previous.children().getLast().kind() == SyntaxKind.ANNOTATION;
                parts.add(afterAnnotation ? annotationSeparator(child, placement, children) : space());
            }
            if (child.kind() == SyntaxKind.VARIABLE_DECLARATOR && firstDeclarator) {
                firstDeclarator = false;
                if (i > 0 && nameSite != null) {
                    parts.add(alignmentMark(nameSite));
                }
                parts.add(emitVariableDeclarator(child, true));
                continue;
            }
            parts.add(emit(child));
        }
        return Doc.concat(parts);
    }

    protected Doc emitVariableDeclarator(GreenNode node) {
        return emitVariableDeclarator(node, false);
    }

    /**
     * @param alignable whether this declarator's {@code =} is one
     *     {@code alignment.consecutive-assignments} may line up. An initialiser is an assignment; one
     *     buried in a second declarator on the same line is not one with a column of its own.
     */
    protected Doc emitVariableDeclarator(GreenNode node, boolean alignable) {
        List<GreenNode> children = node.children();
        int equals = indexOf(children, "=");
        if (equals < 0) {
            List<Doc> parts = new ArrayList<>();
            for (GreenNode child : children) {
                parts.add(emit(child));
            }
            return Doc.concat(parts);
        }
        List<Doc> target = new ArrayList<>();
        for (int i = 0; i < equals; i++) {
            target.add(emit(children.get(i)));
        }
        boolean spaced = rule(SpacingRules.AROUND_ASSIGNMENT_OPERATORS);
        GreenNode valueNode = children.get(equals + 1);
        return authorGroup(
                node,
                Doc.concat(
                        Doc.concat(target),
                        alignable ? alignmentMark(AlignmentSite.ASSIGNMENT) : Doc.EMPTY,
                        spaceIf(spaced),
                        emit(children.get(equals)),
                        assignedValue(valueNode, emit(valueNode), spaced)));
    }

    // ------------------------------------------------------------- switches

    protected Doc emitSwitchHeader(GreenNode node) {
        List<GreenNode> children = node.children();
        return Doc.concat(
                emit(children.get(0)),
                spaceIf(rule(SpacingRules.BEFORE_SWITCH_PARENTHESIS)),
                emitCondition(children.get(1), children.get(2), children.get(3)),
                braceLead(rule(BraceRules.CONTROL_PLACEMENT)),
                emit(children.get(4)));
    }

    protected Doc emitSwitchBlock(GreenNode node) {
        List<GreenNode> children = node.children();
        GreenNode open = children.getFirst();
        GreenNode close = children.getLast();
        List<GreenNode> cases = children.subList(1, children.size() - 1);
        if (cases.isEmpty()) {
            return Doc.concat(emit(open), emit(close));
        }
        List<Doc> parts = new ArrayList<>();
        for (int i = 0; i < cases.size(); i++) {
            int minimum = i == 0 ? 0 : rule(BlankLineRules.BETWEEN_SWITCH_CASES);
            parts.add(separatorBefore(cases.get(i), minimum));
            parts.add(emit(cases.get(i)));
        }
        Doc body = Doc.concat(parts);
        Doc contents = rule(IndentRules.SWITCH_CASE_LABELS) ? Doc.indent(indentSize(), body) : body;
        return Doc.concat(emit(open), contents, Doc.hardLine(), emit(close));
    }

    protected Doc emitSwitchCase(GreenNode node) {
        List<GreenNode> children = node.children();
        boolean arrow = children.size() > 1 && is(children.get(1), "->");
        Doc labels =
                arrow
                ? Doc.concat(emit(children.getFirst()), alignmentMark(AlignmentSite.SWITCH_ARROW))
                : emit(children.getFirst());
        if (arrow) {
            boolean spaced = rule(SpacingRules.AROUND_CASE_ARROW);
            GreenNode body = children.get(2);
            Doc bodyDoc = emit(body);
            if (body.kind() == SyntaxKind.BLOCK) {
                return Doc.concat(labels, spaceIf(spaced), emit(children.get(1)), space(), bodyDoc);
            }
            // The question is asked of the labels rather than the whole case: a body that turned out
            // too long to sit beside them would otherwise answer it differently on the second pass,
            // and formatting has to be a fixed point.
            GreenNode labelNode = children.getFirst();
            boolean onOneLine = isNullDefault(labelNode.children())
                    && keepsOnOneLine(labelNode, SwitchRules.NULL_DEFAULT_ON_ONE_LINE);
            Doc tail =
                    rule(SwitchRules.ARROW_BODY_ON_NEW_LINE_WHEN_LONG) && !onOneLine
                    ? Doc.group(Doc.indent(continuation(), Doc.concat(spaced ? Doc.line() : Doc.softLine(), bodyDoc)))
                    : Doc.concat(spaceIf(spaced), bodyDoc);
            return Doc.concat(labels, spaceIf(spaced), emit(children.get(1)), tail);
        }
        // Colon style: the label, then its statements indented under it.
        List<Doc> statements = new ArrayList<>();
        for (int i = 2; i < children.size(); i++) {
            statements.add(separatorBefore(children.get(i), 0));
            statements.add(emit(children.get(i)));
        }
        Doc colon = children.size() > 1 ? emit(children.get(1)) : Doc.EMPTY;
        Doc body = Doc.concat(statements);
        return Doc.concat(
                labels,
                spaceIf(rule(SpacingRules.BEFORE_COLON_IN_CASE_LABEL)),
                colon,
                rule(IndentRules.SWITCH_CASE_BODY) ? Doc.indent(indentSize(), body) : body);
    }

    protected Doc emitCaseLabels(GreenNode node) {
        List<GreenNode> children = node.children();
        if (children.size() == 1) {
            return emit(children.getFirst());
        }
        // The keyword, then the labels themselves, which may be a long list that has to wrap.
        Doc keyword = emit(children.getFirst());
        List<Doc> elements = new ArrayList<>();
        List<Doc> current = new ArrayList<>();
        Doc guard = Doc.EMPTY;
        for (GreenNode child : children.subList(1, children.size())) {
            if (child.kind() == SyntaxKind.CASE_GUARD) {
                guard = Doc.concat(space(), emit(child));
                continue;
            }
            if (is(child, ",")) {
                current.add(spaceIf(rule(SpacingRules.BEFORE_COMMA)));
                current.add(emit(child));
                elements.add(Doc.concat(current));
                current = new ArrayList<>();
                continue;
            }
            current.add(emit(child));
        }
        if (!current.isEmpty()) {
            elements.add(Doc.concat(current));
        }
        if (isNullDefault(children) && keepsOnOneLine(node, SwitchRules.NULL_DEFAULT_ON_ONE_LINE)) {
            // Two words that mean one thing. Splitting them across lines reads as two separate cases.
            Doc pair = Doc.join(spaceIf(rule(SpacingRules.AFTER_COMMA)), elements);
            return Doc.concat(keyword, space(), pair, guard);
        }
        Doc separator = rule(SpacingRules.AFTER_COMMA) ? Doc.line() : Doc.softLine();
        Doc labels =
                rule(SwitchRules.MULTI_LABEL_WRAPPING) == WrapPolicy.WRAP_IF_LONG
                ? Doc.fill(withSeparator(elements, separator))
                : Doc.join(separator, elements);
        return authorGroup(node, Doc.concat(keyword, space(), Doc.indent(continuation(), labels), guard));
    }

    /**
     * Whether these case labels are exactly {@code null} and {@code default}.
     *
     * <p>The pair is one idea rather than a list of two, which is why it has a rule of its own.
     */
    private static boolean isNullDefault(List<GreenNode> children) {
        boolean nullLabel = false;
        boolean defaultLabel = false;
        for (GreenNode child : children.subList(1, children.size())) {
            if (is(child, ",") || child.kind() == SyntaxKind.CASE_GUARD) {
                continue;
            }
            if (is(child, "default")) {
                defaultLabel = true;
            } else if (lexeme(child).equals("null") || child.kind() != SyntaxKind.TOKEN && isNullLiteral(child)) {
                nullLabel = true;
            } else {
                return false;
            }
        }
        return nullLabel && defaultLabel;
    }

    /** Whether a label node is the literal {@code null}. */
    private static boolean isNullLiteral(GreenNode node) {
        return node.kind() == SyntaxKind.LITERAL
                && node.children().size() == 1
                && is(node.children().getFirst(), "null");
    }

    private static List<Doc> withSeparator(List<Doc> elements, Doc separator) {
        List<Doc> parts = new ArrayList<>();
        for (int i = 0; i < elements.size(); i++) {
            if (i > 0) {
                parts.add(separator);
            }
            parts.add(elements.get(i));
        }
        return parts;
    }

    protected Doc emitCaseGuard(GreenNode node) {
        List<GreenNode> children = node.children();
        Doc guard = Doc.concat(emit(children.get(0)), space(), emit(children.get(1)));
        return rule(SwitchRules.GUARD_ON_SAME_LINE)
               ? guard
               : Doc.group(Doc.indent(continuation(), Doc.concat(Doc.line(), guard)));
    }

    protected static int lastIndexOfLexeme(List<GreenNode> children, String lexeme) {
        for (int i = children.size() - 1; i >= 0; i--) {
            if (is(children.get(i), lexeme)) {
                return i;
            }
        }
        return -1;
    }

}
