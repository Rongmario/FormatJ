package zone.rong.formatj.core.emit;

import zone.rong.formatj.api.Style;
import zone.rong.formatj.api.rules.AlignmentRules;
import zone.rong.formatj.api.rules.BracePlacement;
import zone.rong.formatj.api.rules.BraceRules;
import zone.rong.formatj.api.rules.ChainPolicy;
import zone.rong.formatj.api.rules.ClosingDelimiter;
import zone.rong.formatj.api.rules.EmptyBodyStyle;
import zone.rong.formatj.api.rules.IndentRules;
import zone.rong.formatj.api.rules.LambdaRules;
import zone.rong.formatj.api.rules.OperatorWrap;
import zone.rong.formatj.api.rules.PatternRules;
import zone.rong.formatj.api.rules.PreservationRules;
import zone.rong.formatj.api.rules.RecordRules;
import zone.rong.formatj.api.rules.SpacingRules;
import zone.rong.formatj.api.rules.WrapPolicy;
import zone.rong.formatj.api.rules.WrappingRules;
import zone.rong.formatj.core.cst.GreenNode;
import zone.rong.formatj.core.cst.SyntaxKind;
import zone.rong.formatj.core.cst.SyntaxToken;
import zone.rong.formatj.core.ir.AlignmentSite;
import zone.rong.formatj.core.ir.Doc;
import java.util.ArrayList;
import java.util.List;

/** Layout for types, expressions and patterns. */
abstract class ExpressionEmitter extends EmitSupport {

    ExpressionEmitter(Style style) {
        super(style);
    }

    /** Implemented by the statement layer. */
    protected abstract Doc emitBlockLike(GreenNode node);

    /** Implemented by the statement layer; a block whose empty form follows {@code emptyStyle}. */
    protected abstract Doc emitBlockLike(GreenNode node, EmptyBodyStyle emptyStyle);

    /** Implemented by the statement layer; a block that may print on one line when it fits. */
    protected abstract Doc emitBlockLike(GreenNode node, boolean mayInline);

    /** Implemented by the statement layer; a lambda's block body, which may stay on one line. */
    protected abstract Doc emitLambdaBody(GreenNode node, EmptyBodyStyle emptyStyle);

    // ---------------------------------------------------------------- lists

    /**
     * A delimited, comma separated list.
     *
     * <p>Every token is emitted from the tree rather than synthesised, including the commas, so a
     * comment the author attached to one cannot go missing.
     *
     * @param node the node whose first and last children are the delimiters
     * @param policy how the list wraps
     * @param indentColumns columns to indent wrapped elements by
     * @param spaceInside whether a space is kept just inside the delimiters when flat
     */
    protected Doc delimitedList(GreenNode node, WrapPolicy policy, int indentColumns, boolean spaceInside) {
        return delimitedList(node, policy, indentColumns, spaceInside, Doc.GroupKind.IF_NEEDED);
    }

    /**
     * @param groupKind how the list decides whether its own optional breaks fit; a trailing argument
     *     that brings hard breaks uses {@link Doc.GroupKind#FIRST_LINE}
     */
    protected Doc delimitedList(
            GreenNode node,
            WrapPolicy policy,
            int indentColumns,
            boolean spaceInside,
            Doc.GroupKind groupKind) {
        List<GreenNode> children = node.children();
        if (children.size() < 2) {
            return Doc.concat(children.stream().map(this::emit).toList());
        }
        GreenNode open = children.getFirst();
        GreenNode close = children.getLast();
        List<GreenNode> middle = children.subList(1, children.size() - 1);
        if (middle.isEmpty()) {
            return Doc.concat(emit(open), emit(close));
        }

        List<Doc> elements = new ArrayList<>();
        List<Doc> current = new ArrayList<>();
        for (GreenNode child : middle) {
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
        Doc inner =
                policy == WrapPolicy.WRAP_IF_LONG && mayJoin(node)
                        ? Doc.fill(interleave(elements, separator))
                        : Doc.join(separator, elements);
        Doc edge = spaceInside ? Doc.line() : Doc.softLine();
        // Braces of an initializer carry their own answer; a parenthesis follows the file-wide rule.
        boolean ownLine = is(close, "}")
                || is(close, ")") && rule(WrappingRules.CLOSING_DELIMITER) == ClosingDelimiter.OWN_LINE;
        Doc closingEdge = ownLine ? edge : spaceIf(spaceInside);
        // This indentation belongs to the list breaking. A first-line group can stay flat around hard
        // breaks brought by its last child, and those lines must not receive an indent the list did not
        // take. For every ordinary group, IndentIfBreak prints identically to Indent.
        Doc body = Doc.concat(Doc.indentIfBreak(indentColumns, Doc.concat(edge, inner)), closingEdge);
        Doc content = Doc.concat(emit(open), body, emit(close));

        // The author's break after the opening delimiter is the one this rule is named for; it is the
        // same break the PRESERVE policy reads, so a list under either policy keeps it.
        boolean keepOpenBreak =
                rule(PreservationRules.KEEP_LINE_BREAK_AFTER_OPEN_PAREN) && AuthorLines.brokeAfterFirstToken(node);

        return switch (policy) {
            case NEVER -> Doc.concat(emit(open), spaceIf(spaceInside), inner, spaceIf(spaceInside), emit(close));
            case CHOP_DOWN_ALWAYS -> Doc.breakingGroup(content);
            case PRESERVE ->
                    authorBrokeBefore(middle.getFirst())
                            ? Doc.breakingGroup(content)
                            : Doc.group(content, groupKind);
            default ->
                    keepOpenBreak ? Doc.breakingGroup(content) : authorGroup(node, content, groupKind);
        };
    }

    private static List<Doc> interleave(List<Doc> elements, Doc separator) {
        List<Doc> parts = new ArrayList<>(elements.size() * 2);
        for (int i = 0; i < elements.size(); i++) {
            if (i > 0) {
                parts.add(separator);
            }
            parts.add(elements.get(i));
        }
        return parts;
    }

    /** Whether the author started a new line before this node. */
    protected static boolean authorBrokeBefore(GreenNode node) {
        SyntaxToken token = firstToken(node);
        return token != null && token.startsNewLine();
    }

    // ---------------------------------------------------------------- types

    protected Doc emitTypeLike(GreenNode node) {
        List<Doc> parts = new ArrayList<>();
        List<GreenNode> children = node.children();
        for (int i = 0; i < children.size(); i++) {
            GreenNode child = children.get(i);
            if (i > 0 && needsSpaceInType(children.get(i - 1), child)) {
                parts.add(space());
            }
            parts.add(emit(child));
        }
        return Doc.concat(parts);
    }

    private boolean needsSpaceInType(GreenNode previous, GreenNode next) {
        if (previous.kind() == SyntaxKind.ANNOTATION) {
            return true;
        }
        if (is(previous, "...")) {
            return rule(SpacingRules.AFTER_VARARGS_ELLIPSIS);
        }
        if (is(next, "[") || is(previous, "[") || is(next, "]")) {
            return is(next, "[") && rule(SpacingRules.BEFORE_ARRAY_BRACKETS);
        }
        if (isAny(previous, "extends", "super", "&") || isAny(next, "extends", "super", "&")) {
            return true;
        }
        return false;
    }

    protected Doc emitTypeArguments(GreenNode node) {
        return delimitedList(node, WrapPolicy.WRAP_IF_LONG, continuation(), rule(SpacingRules.WITHIN_ANGLE_BRACKETS));
    }

    protected Doc emitTypeParameters(GreenNode node) {
        return delimitedList(
                node,
                rule(WrappingRules.TYPE_PARAMETERS),
                continuation(),
                rule(SpacingRules.WITHIN_ANGLE_BRACKETS));
    }

    // ----------------------------------------------------------- expressions

    /**
     * A run of binary operators of the same precedence, laid out as one unit.
     *
     * <p>Flattening matters: {@code a + b + c + d} parses left-nested, and emitting each level on its
     * own would put every operand on its own line as soon as the outermost one broke. Treating the run
     * as a list lets it fill lines instead, which is what a reader of a long concatenation wants.
     */
    protected Doc emitBinary(GreenNode node) {
        List<GreenNode> operands = new ArrayList<>();
        List<Doc> operators = new ArrayList<>();
        flattenBinary(node, operands, operators);

        boolean spaced = rule(SpacingRules.AROUND_BINARY_OPERATORS);
        WrapPolicy policy = rule(WrappingRules.BINARY_OPERATORS);
        boolean operatorFirst = rule(WrappingRules.OPERATOR_POSITION) == OperatorWrap.BEFORE_OPERATOR;

        if (policy == WrapPolicy.NEVER) {
            List<Doc> flat = new ArrayList<>();
            flat.add(emit(operands.getFirst()));
            for (int i = 0; i < operators.size(); i++) {
                flat.add(spaceIf(spaced));
                flat.add(operators.get(i));
                flat.add(spaceIf(spaced));
                flat.add(emit(operands.get(i + 1)));
            }
            return Doc.concat(flat);
        }

        List<Doc> parts = new ArrayList<>();
        parts.add(emit(operands.getFirst()));
        for (int i = 0; i < operators.size(); i++) {
            Doc operand = emit(operands.get(i + 1));
            if (operatorFirst) {
                parts.add(Doc.line());
                parts.add(Doc.concat(operators.get(i), spaceIf(spaced), operand));
            } else {
                parts.add(Doc.concat(spaceIf(spaced), operators.get(i)));
                parts.add(Doc.line());
                parts.add(operand);
            }
        }
        // A run of && or || reads as a list of conditions, so it breaks all at once; arithmetic and
        // string concatenation read as prose and fill the line instead. A run whose breaks have to
        // survive gets neither: a fill decides each break by what fits and a nested group is measured
        // on its own, so either would quietly rejoin it. It is left bare for the group around it.
        boolean logical = !operators.isEmpty() && isLogicalRun(node);
        Doc run;
        if (!mayJoin(node)) {
            run = Doc.concat(parts);
        } else {
            run = policy == WrapPolicy.WRAP_IF_LONG && !logical ? Doc.fill(parts) : Doc.group(Doc.concat(parts));
        }
        return authorGroup(node, Doc.indent(continuation(), run));
    }

    private static boolean isLogicalRun(GreenNode node) {
        String operator = operatorText(node.children());
        return operator.equals("&&") || operator.equals("||");
    }

    /** Splits a left-nested run of same-precedence operators into operands and operator documents. */
    private void flattenBinary(GreenNode node, List<GreenNode> operands, List<Doc> operators) {
        List<GreenNode> children = node.children();
        GreenNode left = children.getFirst();
        List<Doc> operator = new ArrayList<>();
        for (GreenNode child : children.subList(1, children.size() - 1)) {
            operator.add(emit(child));
        }
        String text = operatorText(children);
        if (left.kind() == SyntaxKind.BINARY_EXPRESSION && samePrecedence(operatorText(left.children()), text)) {
            flattenBinary(left, operands, operators);
        } else {
            operands.add(left);
        }
        operators.add(Doc.concat(operator));
        operands.add(children.getLast());
    }

    private static String operatorText(List<GreenNode> children) {
        StringBuilder text = new StringBuilder();
        for (GreenNode child : children.subList(1, children.size() - 1)) {
            text.append(lexeme(child));
        }
        return text.toString();
    }

    private static boolean samePrecedence(String left, String right) {
        return precedenceOf(left) == precedenceOf(right);
    }

    private static int precedenceOf(String operator) {
        return switch (operator) {
            case "||" -> 1;
            case "&&" -> 2;
            case "|" -> 3;
            case "^" -> 4;
            case "&" -> 5;
            case "==", "!=" -> 6;
            case "<", ">", "<=", ">=" -> 7;
            case "<<", ">>", ">>>" -> 8;
            case "+", "-" -> 9;
            case "*", "/", "%" -> 10;
            default -> 0;
        };
    }

    protected Doc emitAssignment(GreenNode node) {
        return emitAssignment(node, false);
    }

    /**
     * @param alignable whether this assignment's operator is one
     *     {@code alignment.consecutive-assignments} may line up. An assignment nested inside a larger
     *     expression is not: it shares its line with whatever encloses it, so the column it sits at
     *     says nothing about the lines above and below.
     */
    protected Doc emitAssignment(GreenNode node, boolean alignable) {
        List<GreenNode> children = node.children();
        Doc target = emit(children.getFirst());
        List<Doc> operator = new ArrayList<>();
        for (GreenNode child : children.subList(1, children.size() - 1)) {
            operator.add(emit(child));
        }
        Doc value = emit(children.getLast());
        boolean spaced = rule(SpacingRules.AROUND_ASSIGNMENT_OPERATORS);
        return authorGroup(
                node,
                Doc.concat(
                        target,
                        alignable ? alignmentMark(AlignmentSite.ASSIGNMENT) : Doc.EMPTY,
                        spaceIf(spaced),
                        Doc.concat(operator),
                        assignedValue(children.getLast(), value, spaced)));
    }

    /**
     * The right hand side of an assignment or initialiser.
     *
     * <p>When the line is too long there are two ways out: break after the {@code =} and give the
     * value a fresh line, or leave the value in place and break inside it. Which one reads better
     * depends on the value. A call or a creation is one thing and prefers a line of its own; an
     * arithmetic run, a lambda or an array initializer already has its own line structure, so
     * breaking after the {@code =} as well would just indent it twice.
     */
    protected Doc assignedValue(GreenNode valueNode, Doc value, boolean spaced) {
        WrapPolicy policy = rule(WrappingRules.ASSIGNMENT);
        if (policy == WrapPolicy.NEVER || !prefersItsOwnLine(valueNode)) {
            return Doc.concat(spaceIf(spaced), value);
        }
        Doc broken = Doc.indent(continuation(), Doc.concat(spaced ? Doc.line() : Doc.softLine(), value));
        return policy == WrapPolicy.CHOP_DOWN_ALWAYS ? Doc.breakingGroup(broken) : Doc.group(broken);
    }

    private static boolean prefersItsOwnLine(GreenNode valueNode) {
        return switch (valueNode.kind()) {
            // A chain wraps at its dots, so it stays on the line of the = and hangs from there.
            case METHOD_INVOCATION, MEMBER_ACCESS -> chainLength(valueNode) < 2;
            case OBJECT_CREATION, ARRAY_CREATION, CAST_EXPRESSION, SWITCH_EXPRESSION, TERNARY_EXPRESSION -> true;
            default -> false;
        };
    }

    /** How many dotted links a chain has; 1 for a plain call. */
    private static int chainLength(GreenNode node) {
        int length = 0;
        GreenNode current = node;
        while (isChainLink(current)) {
            length++;
            GreenNode receiver = current.children().getFirst();
            if (!isChainLink(receiver)) {
                break;
            }
            current = receiver;
        }
        return length;
    }

    protected Doc emitTernary(GreenNode node) {
        List<GreenNode> children = node.children();
        boolean spaced = rule(SpacingRules.AROUND_TERNARY_OPERATORS);
        Doc condition = emit(children.get(0));
        Doc question = emit(children.get(1));
        Doc whenTrue = emit(children.get(2));
        Doc colon = emit(children.get(3));
        Doc whenFalse = emit(children.get(4));
        if (rule(WrappingRules.TERNARY) == WrapPolicy.NEVER) {
            return Doc.concat(
                    condition,
                    spaceIf(spaced),
                    question,
                    spaceIf(spaced),
                    whenTrue,
                    spaceIf(spaced),
                    colon,
                    spaceIf(spaced),
                    whenFalse);
        }
        Doc branches = Doc.concat(
                Doc.line(), question, spaceIf(spaced), whenTrue, Doc.line(), colon, spaceIf(spaced), whenFalse);
        if (alignsOnColumn(AlignmentRules.TERNARY_BRANCHES)) {
            // Aligning the branches means hanging them under the condition rather than at a fixed
            // indent, so the alignment has to start where the condition starts, not where it ends.
            return authorGroup(node, Doc.align(Doc.concat(condition, branches)));
        }
        return authorGroup(node, Doc.concat(condition, Doc.indent(rule(IndentRules.TERNARY), branches)));
    }

    /**
     * An {@code instanceof} test and the pattern it binds.
     *
     * <p>{@code patterns.keep-simple-pattern-inline} decides whether the pattern is tied to the line
     * of the test it belongs to. Tied, the whole thing is one unbreakable run, and a long condition
     * has to wrap somewhere else; untied, the pattern may take a line of its own when the test does
     * not fit, indented under it. A pattern the author had already put on its own line is untied
     * whatever the rule says, because there is no single line left to keep.
     */
    protected Doc emitInstanceof(GreenNode node) {
        List<GreenNode> children = node.children();
        if (children.size() < 3 || keepsOnOneLine(node, PatternRules.KEEP_SIMPLE_PATTERN_INLINE)) {
            List<Doc> parts = new ArrayList<>();
            for (int i = 0; i < children.size(); i++) {
                if (i > 0) {
                    parts.add(space());
                }
                parts.add(emit(children.get(i)));
            }
            return Doc.concat(parts);
        }
        // The children are the operand, the instanceof keyword, then the type or pattern it tests for.
        List<Doc> pattern = new ArrayList<>();
        for (int i = 2; i < children.size(); i++) {
            if (i > 2) {
                pattern.add(space());
            }
            pattern.add(emit(children.get(i)));
        }
        return authorGroup(
                node,
                Doc.concat(
                        emit(children.get(0)),
                        space(),
                        emit(children.get(1)),
                        Doc.indent(continuation(), Doc.concat(Doc.line(), Doc.concat(pattern)))));
    }

    protected Doc emitUnary(GreenNode node) {
        List<GreenNode> children = node.children();
        return Doc.concat(
                emit(children.getFirst()),
                spaceIf(rule(SpacingRules.AROUND_UNARY_OPERATORS)),
                emit(children.getLast()));
    }

    protected Doc emitCast(GreenNode node) {
        List<GreenNode> children = node.children();
        List<Doc> parts = new ArrayList<>();
        int closing = indexOf(children, ")");
        for (int i = 0; i <= closing; i++) {
            GreenNode child = children.get(i);
            if (i > 0 && !is(child, ")") && !is(children.get(i - 1), "(")) {
                parts.add(space());
            }
            parts.add(emit(child));
        }
        parts.add(spaceIf(rule(SpacingRules.AFTER_TYPE_CAST)));
        for (int i = closing + 1; i < children.size(); i++) {
            parts.add(emit(children.get(i)));
        }
        return Doc.concat(parts);
    }

    protected Doc emitParenthesized(GreenNode node) {
        List<GreenNode> children = node.children();
        boolean inside = rule(SpacingRules.WITHIN_PARENTHESES);
        return Doc.concat(
                emit(children.getFirst()),
                spaceIf(inside),
                emit(children.get(1)),
                spaceIf(inside),
                emit(children.getLast()));
    }

    protected Doc emitArguments(GreenNode node) {
        List<GreenNode> children = node.children();
        if (children.size() == 3 && hugsItsArgument(children.get(1))) {
            // A lone lambda or anonymous class brings its own line structure: wrapping the argument
            // list around it would indent the body twice and buy nothing.
            return Doc.concat(emit(children.getFirst()), emit(children.get(1)), emit(children.getLast()));
        }
        // The same argument, with others in front of it, is the same argument: the lines its body
        // brings are not the list overflowing, so the list is judged by the line it actually prints.
        // Only the last one may hug, because an argument that ends mid-line leaves the ones after it
        // stranded against a closing brace.
        GreenNode last = lastArgument(children);
        boolean hugsLast = last != null && hugsItsArgument(last);
        return delimitedList(
                node,
                rule(WrappingRules.METHOD_ARGUMENTS),
                continuation(),
                rule(SpacingRules.WITHIN_PARENTHESES),
                hugsLast ? Doc.GroupKind.FIRST_LINE : Doc.GroupKind.IF_NEEDED);
    }

    /** The last argument of a non-empty argument list. */
    private static GreenNode lastArgument(List<GreenNode> children) {
        for (int i = children.size() - 2; i > 0; i--) {
            GreenNode child = children.get(i);
            if (!is(child, ",")) {
                return child;
            }
        }
        return null;
    }

    private static boolean hugsItsArgument(GreenNode argument) {
        return switch (argument.kind()) {
            case LAMBDA_EXPRESSION, ARRAY_INITIALIZER, SWITCH_EXPRESSION -> true;
            case OBJECT_CREATION ->
                    argument.children()
                            .stream()
                            .anyMatch(child -> child.kind() == SyntaxKind.CLASS_BODY);
            default -> false;
        };
    }

    protected Doc emitArrayInitializer(GreenNode node) {
        WrapPolicy policy = rule(WrappingRules.ARRAY_INITIALIZERS);
        if (rule(PreservationRules.KEEP_ARRAY_INITIALIZER_LAYOUT) && authorBrokeInside(node)) {
            policy = WrapPolicy.CHOP_DOWN_ALWAYS;
        }
        return delimitedList(
                node,
                policy,
                rule(IndentRules.ARRAY_INITIALIZER),
                rule(SpacingRules.WITHIN_ARRAY_INITIALIZER_BRACES));
    }

    /** Whether the author spread this node over more than one line. */
    protected static boolean authorBrokeInside(GreenNode node) {
        return authorBrokeInside(node.children());
    }

    /** Whether the author spread these siblings over more than one line. */
    protected static boolean authorBrokeInside(List<GreenNode> children) {
        for (int i = 1; i < children.size(); i++) {
            if (authorBrokeBefore(children.get(i))) {
                return true;
            }
        }
        return false;
    }

    // ---------------------------------------------------------------- chains

    /** Whether this node is the top of a call chain the emitter should lay out as a unit. */
    protected static boolean isChainLink(GreenNode node) {
        return node.kind() == SyntaxKind.METHOD_INVOCATION || node.kind() == SyntaxKind.MEMBER_ACCESS;
    }

    protected Doc emitChain(GreenNode node) {
        List<GreenNode> links = new ArrayList<>();
        GreenNode base = flatten(node, links);
        if (links.size() <= 1) {
            return emitLinkFlat(node);
        }

        List<Doc> linkDocs = new ArrayList<>(links.size());
        boolean authorBroke = false;
        for (GreenNode link : links) {
            linkDocs.add(emitLinkTail(link));
            authorBroke |= authorBrokeBeforeDot(link);
        }

        // A plain receiver keeps its first call: people.stream() reads as one thing, and breaking
        // after the name alone wastes a line.
        boolean alignDots = alignsOnColumn(AlignmentRules.METHOD_CHAINS);
        Doc head = emit(base);
        Doc attached = Doc.EMPTY;
        if (isPlainReceiver(base) && linkDocs.size() > 1 && !authorBrokeBeforeDot(links.getFirst())) {
            // Aligning the dots wants the same link kept on the receiver's line, but not folded into
            // the receiver: the column the later dots hang from is this link's dot, so the alignment
            // has to be measured in front of it rather than behind it.
            attached = linkDocs.removeFirst();
            if (!alignDots) {
                head = Doc.concat(head, attached);
                attached = Doc.EMPTY;
            }
        }

        ChainPolicy policy = rule(WrappingRules.CHAINED_CALLS);
        int threshold = rule(WrappingRules.CHAIN_THRESHOLD);
        boolean forceBreak = authorBroke && (rule(PreservationRules.RESPECT_EXISTING_CHAIN_BREAKS) || !mayJoin(node));
        Doc baseDoc = head;
        if (policy == ChainPolicy.PRESERVE) {
            int firstRemaining = links.size() - linkDocs.size();
            List<Doc> preserved = new ArrayList<>();
            preserved.add(attached);
            for (int i = 0; i < linkDocs.size(); i++) {
                GreenNode link = links.get(firstRemaining + i);
                preserved.add(authorBrokeBeforeDot(link) ? Doc.hardLine() : Doc.EMPTY);
                preserved.add(linkDocs.get(i));
            }
            Doc tails = Doc.concat(preserved);
            return Doc.concat(
                    baseDoc,
                    alignDots
                            ? Doc.align(tails)
                            : Doc.indent(rule(IndentRules.CHAINED_CALL), tails));
        }
        if (policy == ChainPolicy.NEVER_BREAK || links.size() < threshold && !forceBreak) {
            return Doc.concat(baseDoc, attached, Doc.concat(linkDocs));
        }

        List<Doc> parts = new ArrayList<>();
        for (Doc link : linkDocs) {
            parts.add(Doc.softLine());
            parts.add(link);
        }
        Doc hanging = alignDots
                ? Doc.align(Doc.concat(attached, Doc.concat(parts)))
                : Doc.indentIfBreak(rule(IndentRules.CHAINED_CALL), Doc.concat(parts));
        Doc content = Doc.concat(baseDoc, hanging);
        if (policy == ChainPolicy.BREAK_WHEN_TOO_LONG) {
            if (alignDots) {
                List<Doc> filled = new ArrayList<>();
                filled.add(attached);
                filled.addAll(parts);
                return Doc.concat(baseDoc, Doc.align(Doc.fill(filled)));
            }
            List<Doc> filled = new ArrayList<>();
            filled.add(baseDoc);
            filled.addAll(parts);
            return Doc.indent(rule(IndentRules.CHAINED_CALL), Doc.fill(filled));
        }
        if (forceBreak) {
            return Doc.breakingGroup(content);
        }
        if (policy == ChainPolicy.BREAK_ALL_WHEN_TOO_LONG) {
            return Doc.firstLineGroup(content);
        }
        return Doc.group(content);
    }

    private static boolean isPlainReceiver(GreenNode base) {
        return switch (base.kind()) {
            case NAME, QUALIFIED_NAME, THIS_EXPRESSION, SUPER_EXPRESSION, LITERAL, CLASS_TYPE -> true;
            default -> false;
        };
    }

    /** Splits a chain into its base expression and the links hanging off it. */
    private GreenNode flatten(GreenNode node, List<GreenNode> links) {
        if (!isChainLink(node)) {
            return node;
        }
        GreenNode receiver = node.children().getFirst();
        if (is(receiver, ".") || isLeaf(receiver) && !isChainLink(receiver)) {
            return node;
        }
        if (!isChainLink(receiver)) {
            links.addFirst(node);
            return receiver;
        }
        GreenNode base = flatten(receiver, links);
        links.add(node);
        return base;
    }

    /** Everything of a chain link from its dot onwards. */
    private Doc emitLinkTail(GreenNode link) {
        List<Doc> parts = new ArrayList<>();
        List<GreenNode> children = link.children();
        for (int i = 1; i < children.size(); i++) {
            parts.add(emit(children.get(i)));
        }
        return Doc.concat(parts);
    }

    private Doc emitLinkFlat(GreenNode node) {
        List<Doc> parts = new ArrayList<>();
        for (GreenNode child : node.children()) {
            parts.add(emit(child));
        }
        return Doc.concat(parts);
    }

    private static boolean authorBrokeBeforeDot(GreenNode link) {
        List<GreenNode> children = link.children();
        return children.size() > 1 && authorBrokeBefore(children.get(1));
    }

    // --------------------------------------------------------------- lambdas

    protected Doc emitLambda(GreenNode node) {
        List<GreenNode> children = node.children();
        Doc parameters = emit(children.get(0));
        Doc arrow = emit(children.get(1));
        GreenNode bodyNode = children.get(2);
        boolean spaced = rule(SpacingRules.AROUND_LAMBDA_ARROW);
        if (bodyNode.kind() == SyntaxKind.BLOCK) {
            BracePlacement placement = rule(BraceRules.LAMBDA_PLACEMENT);
            // On the same line the arrow's own spacing rule decides the gap; a placed brace brings its own.
            Doc lead = placement == BracePlacement.END_OF_LINE ? spaceIf(spaced) : braceLead(placement);
            return Doc.concat(
                    parameters,
                    spaceIf(spaced),
                    arrow,
                    lead,
                    emitLambdaBody(bodyNode, rule(BraceRules.EMPTY_METHOD_BODY)));
        }
        Doc body = emit(bodyNode);
        if (rule(LambdaRules.KEEP_SINGLE_EXPRESSION_INLINE)) {
            return Doc.concat(parameters, spaceIf(spaced), arrow, spaceIf(spaced), body);
        }
        return Doc.group(
                Doc.concat(
                        parameters,
                        spaceIf(spaced),
                        arrow,
                        Doc.indent(continuation(), Doc.concat(spaced ? Doc.line() : Doc.softLine(), body))));
    }

    protected Doc emitLambdaParameters(GreenNode node) {
        List<GreenNode> children = node.children();
        if (children.size() == 1) {
            return emit(children.getFirst());
        }
        return delimitedList(
                node,
                rule(WrappingRules.METHOD_PARAMETERS),
                continuation(),
                rule(SpacingRules.WITHIN_PARENTHESES));
    }

    // --------------------------------------------------------- object creation

    protected Doc emitObjectCreation(GreenNode node) {
        List<Doc> parts = new ArrayList<>();
        List<GreenNode> children = node.children();
        for (int i = 0; i < children.size(); i++) {
            GreenNode child = children.get(i);
            if (i > 0 && needsSpaceInCreation(children.get(i - 1), child)) {
                parts.add(space());
            }
            parts.add(emit(child));
        }
        return Doc.concat(parts);
    }

    private boolean needsSpaceInCreation(GreenNode previous, GreenNode next) {
        if (is(previous, "new")) {
            return true;
        }
        if (next.kind() == SyntaxKind.CLASS_BODY) {
            return true;
        }
        if (next.kind() == SyntaxKind.ARGUMENTS) {
            return rule(SpacingRules.BEFORE_METHOD_CALL_PARENTHESIS);
        }
        if (next.kind() == SyntaxKind.ARRAY_INITIALIZER) {
            return true;
        }
        return false;
    }

    /**
     * Derived record creation, {@code point with { x = 1; }}.
     *
     * <p>{@code records.with-style} is the one rule in this area that changes no tokens, so it is
     * decided here rather than in the rewrite stage: a with-block on one line and the same block
     * spread over several are the same program. The single line is only ever offered — the block is
     * one group, and the layout engine still breaks it when it does not fit or when something inside
     * forces a break — which is what keeps formatting a fixed point under every one of the three
     * values.
     */
    protected Doc emitWithExpression(GreenNode node) {
        List<GreenNode> children = node.children();
        GreenNode block = children.get(2);
        boolean mayInline =
                switch (rule(RecordRules.WITH_STYLE)) {
                    case ALWAYS_BLOCK -> false;
                    case INLINE_WHEN_SHORT -> true;
                    case PRESERVE -> AuthorLines.onOneLine(block);
                };
        return Doc.concat(
                emit(children.get(0)),
                space(),
                emit(children.get(1)),
                spaceIf(rule(RecordRules.SPACE_BEFORE_WITH_BLOCK)),
                emitBlockLike(block, mayInline));
    }

    // -------------------------------------------------------------- patterns

    protected Doc emitTypePattern(GreenNode node) {
        List<Doc> parts = new ArrayList<>();
        List<GreenNode> children = node.children();
        for (int i = 0; i < children.size(); i++) {
            if (i > 0) {
                parts.add(space());
            }
            parts.add(emit(children.get(i)));
        }
        return Doc.concat(parts);
    }

    protected Doc emitRecordPattern(GreenNode node) {
        List<Doc> parts = new ArrayList<>();
        List<GreenNode> children = node.children();
        for (int i = 0; i < children.size(); i++) {
            GreenNode child = children.get(i);
            if (i > 0 && child.kind() != SyntaxKind.PATTERN_COMPONENTS) {
                parts.add(space());
            }
            parts.add(emit(child));
        }
        return Doc.concat(parts);
    }

    protected Doc emitPatternComponents(GreenNode node) {
        return delimitedList(
                node,
                rule(PatternRules.DECONSTRUCTION_WRAPPING),
                rule(PatternRules.NESTED_INDENT),
                rule(SpacingRules.WITHIN_PARENTHESES));
    }

    /** An index or dimension expression, with the spaces the bracket rule asks for inside it. */
    protected Doc emitBracketed(GreenNode node) {
        List<GreenNode> children = node.children();
        boolean inside = rule(SpacingRules.WITHIN_BRACKETS);
        List<Doc> parts = new ArrayList<>();
        for (int i = 0; i < children.size(); i++) {
            GreenNode child = children.get(i);
            if (i > 0) {
                boolean afterOpen = is(children.get(i - 1), "[");
                boolean beforeClose = is(child, "]");
                // An empty pair such as new int[] keeps no space of its own.
                boolean empty = afterOpen && beforeClose;
                parts.add(spaceIf(inside && !empty && (afterOpen || beforeClose)));
            }
            parts.add(emit(child));
        }
        return Doc.concat(parts);
    }

    protected static int indexOf(List<GreenNode> children, String lexeme) {
        for (int i = 0; i < children.size(); i++) {
            if (is(children.get(i), lexeme)) {
                return i;
            }
        }
        return -1;
    }

}
