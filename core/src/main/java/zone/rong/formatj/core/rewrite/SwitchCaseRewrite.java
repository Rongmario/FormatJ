package zone.rong.formatj.core.rewrite;

import zone.rong.formatj.api.rules.SwitchCaseStyle;
import zone.rong.formatj.api.rules.SwitchRules;
import zone.rong.formatj.core.cst.GreenNode;
import zone.rong.formatj.core.cst.SyntaxKind;
import java.util.ArrayList;
import java.util.List;

/**
 * Colon case labels turned into arrow ones, and back.
 *
 * <p>Every other rewrite the formatter ships is safe by construction and checked afterwards. This
 * one cannot be: whether {@code case A: f(); break;} means the same thing as {@code case A -> f();}
 * is a question about fall-through, about the one scope a colon switch shares between its groups, and
 * about where a bare {@code break} binds — none of which is visible in the tokens the edit changed,
 * and so none of which a law over that edit could ever check. Verification after the fact would be
 * checking the wrong thing and reporting that all was well.
 *
 * <p>So the guarantee is moved to the front. The switch is read whole before a single token is
 * changed, and unless every question below has the safe answer the switch is left exactly as the
 * author wrote it. Nothing is converted half way: mixing arrow and colon cases in one switch does not
 * compile, so a switch is either wholly convertible or wholly left alone.
 *
 * <h2>The preconditions for {@code arrow}</h2>
 *
 * <ul>
 *   <li>Every group ends in a way that cannot fall through — an unlabelled {@code break} the rewrite
 *       then removes, or a {@code return}, {@code throw}, {@code yield} or {@code continue} it
 *       keeps. The last group needs no terminator, having nothing to fall into.
 *   <li>No {@code break} anywhere else in the group belongs to the switch. Nested loops and switches
 *       are not searched, because a {@code break} inside one binds to that and is unaffected; one
 *       found anywhere else would silently change from leaving the switch to leaving nothing.
 *   <li>No group declares a local variable or a local type at its own level. A colon switch's groups
 *       share one scope and arrow cases do not, so a declaration another group reads would stop
 *       compiling. The declaration is not traced to its readers: a rule that has to be right about a
 *       question it cannot see all of should decline it instead.
 *   <li>A group with no statements merges its labels into the next, which is what {@code case A:}
 *       above {@code case B:} already meant. A {@code default} on either side of such a merge is
 *       refused: {@code default} may only join a label list next to {@code null}.
 *   <li>Nothing that would be deleted carries a comment, here as everywhere else.
 * </ul>
 *
 * <h2>The precondition for {@code colon}</h2>
 *
 * <p>Every arrow body is an expression or a {@code throw}. A block body is refused, because the colon
 * form needs a {@code break} after it and whether a block can complete normally — a block whose
 * every branch returns must not be followed by one — is exactly the flow question this rewrite has
 * decided not to guess at.
 */
public final class SwitchCaseRewrite implements Rewrite {

    @Override
    public String name() {
        return "switch.case-style";
    }

    @Override
    public boolean enabled(RewriteContext context) {
        return context.rule(SwitchRules.CASE_STYLE) != SwitchCaseStyle.PRESERVE;
    }

    @Override
    public GreenNode rewrite(GreenNode node, RewriteContext context) {
        boolean value = node.kind() == SyntaxKind.SWITCH_EXPRESSION;
        if (!value && node.kind() != SyntaxKind.SWITCH_STATEMENT) {
            return node;
        }
        List<GreenNode> children = node.children();
        int blockIndex = children.size() - 1;
        GreenNode block = children.get(blockIndex);
        if (block.kind() != SyntaxKind.SWITCH_BLOCK || block.children().size() < 2) {
            return node;
        }

        List<GreenNode> cases = block.children().subList(1, block.children().size() - 1);
        if (cases.isEmpty()) {
            return node;
        }
        List<GreenNode> converted =
                context.rule(SwitchRules.CASE_STYLE) == SwitchCaseStyle.ARROW
                ? toArrow(cases, value, context)
                : toColon(cases, value, context);
        if (converted == null) {
            return node;
        }

        List<GreenNode> rebuiltBlock = new ArrayList<>();
        rebuiltBlock.add(block.children().getFirst());
        rebuiltBlock.addAll(converted);
        rebuiltBlock.add(block.children().getLast());

        List<GreenNode> rebuilt = new ArrayList<>(children);
        rebuilt.set(blockIndex, GreenNode.branch(SyntaxKind.SWITCH_BLOCK, rebuiltBlock));
        return GreenNode.branch(node.kind(), rebuilt);
    }

    // ------------------------------------------------------------ colon to arrow

    /** The cases as arrow cases, or null when any one of them fails a precondition. */
    private List<GreenNode> toArrow(List<GreenNode> cases, boolean value, RewriteContext context) {
        List<Group> groups = groups(cases, context);
        if (groups == null) {
            return null;
        }

        List<Plan> plans = new ArrayList<>(groups.size());
        for (int i = 0; i < groups.size(); i++) {
            Plan plan = plan(groups.get(i), value, i == groups.size() - 1, context);
            if (plan == null) {
                return null;
            }
            plans.add(plan);
        }

        List<GreenNode> converted = new ArrayList<>(plans.size());
        for (Plan plan : plans) {
            converted.add(plan.apply(context));
        }
        return converted;
    }

    /**
     * The cases gathered into the groups they already are.
     *
     * <p>A colon case with no statements is not a case of its own; it is another label on the one
     * below it, and that is what it has to become. Refusing to see that would leave
     * {@code case A:} above {@code case B:} unconvertible, which is the commonest shape there is.
     */
    private List<Group> groups(List<GreenNode> cases, RewriteContext context) {
        List<Group> groups = new ArrayList<>();
        List<GreenNode> pending = new ArrayList<>();
        for (GreenNode switchCase : cases) {
            if (switchCase.kind() != SyntaxKind.SWITCH_CASE || switchCase.children().size() < 2) {
                return null;
            }
            GreenNode separator = switchCase.children().get(1);
            if (!(separator instanceof GreenNode.Leaf leaf) || !leaf.lexeme().equals(":")) {
                return null;
            }
            if (Synthetic.carriesComments(separator)) {
                return null;
            }
            if (switchCase.children().size() == 2) {
                if (isDefault(switchCase) || isGuarded(switchCase)) {
                    return null;
                }
                pending.add(switchCase);
                continue;
            }
            if (!pending.isEmpty() && (isDefault(switchCase) || isGuarded(switchCase))) {
                // A guard belongs to one pattern, and default may share a label list only with null;
                // neither can absorb the labels of the empty cases above it.
                return null;
            }
            groups.add(new Group(List.copyOf(pending), switchCase));
            pending.clear();
        }
        return pending.isEmpty() ? groups : null;
    }

    /** A run of empty cases and the case whose statements they all share. */
    private record Group(List<GreenNode> merged, GreenNode owner) { }

    /** What one group becomes, worked out before anything is recorded. */
    private record Plan(Group group, List<GreenNode> statements, GreenNode dropped, boolean brace, boolean yielded) {

        GreenNode apply(RewriteContext context) {
            return SwitchCaseRewrite.build(this, context);
        }

    }

    /**
     * How one group converts, or null when it does not.
     *
     * <p>Worked out for every group before any of them is recorded, because a switch converts whole
     * or not at all and a ledger holding half a conversion is worse than one holding none.
     */
    private Plan plan(Group group, boolean value, boolean last, RewriteContext context) {
        List<GreenNode> statements =
                new ArrayList<>(group.owner().children().subList(2, group.owner().children().size()));
        GreenNode dropped = null;

        GreenNode terminator = statements.getLast();
        if (isPlainBreak(terminator)) {
            if (Synthetic.carriesComments(terminator)) {
                return null;
            }
            dropped = terminator;
            statements.removeLast();
        } else if (!leavesTheSwitch(terminator) && !last) {
            return null;
        }

        for (GreenNode statement : statements) {
            if (statement.kind() == SyntaxKind.LOCAL_VARIABLE_DECLARATION
                    || statement.kind() == SyntaxKind.LOCAL_TYPE_DECLARATION) {
                return null;
            }
            if (holdsSwitchBreak(statement)) {
                return null;
            }
        }

        boolean yielded = value && statements.size() == 1 && isSimpleYield(statements.getFirst());
        if (yielded && Synthetic.carriesComments(statements.getFirst().children().getFirst())) {
            return null;
        }
        boolean brace = !yielded && !(statements.size() == 1 && standsAlone(statements.getFirst()));
        if (brace && !statements.isEmpty() && context.firstPosition(statements.getFirst()) < 0) {
            return null;
        }
        return new Plan(group, List.copyOf(statements), dropped, brace, yielded);
    }

    /** Records a group's edits and builds the arrow case they describe. */
    private static GreenNode build(Plan plan, RewriteContext context) {
        String reason = "a colon case written as an arrow case";
        List<GreenNode> labels = new ArrayList<>();

        // Every empty case above this one is another label on it: its colon and the following
        // `case` keyword become the comma that joins the two label lists.
        for (GreenNode merged : plan.group().merged()) {
            GreenNode colon = merged.children().get(1);
            int position = context.firstPosition(colon);
            context.record(
                    new TokenEdit(
                            SwitchRules.CASE_STYLE,
                            reason,
                            position,
                            List.of(":", "case"),
                            List.of(","),
                            TokenEdit.Bias.INNERMOST_FIRST));
            labels.addAll(labels.isEmpty() ? labelsOf(merged) : labelsOf(merged).subList(1, labelsOf(merged).size()));
            labels.add(Synthetic.separator(","));
        }

        GreenNode owner = plan.group().owner();
        GreenNode colon = owner.children().get(1);
        int colonPosition = context.firstPosition(colon);
        context.record(
                new TokenEdit(
                        SwitchRules.CASE_STYLE,
                        reason,
                        colonPosition,
                        List.of(":"),
                        List.of("->"),
                        TokenEdit.Bias.INNERMOST_FIRST));

        if (plan.dropped() != null) {
            context.record(
                    TokenEdit.delete(
                            SwitchRules.CASE_STYLE,
                            reason,
                            context.firstPosition(plan.dropped()),
                            "break",
                            ";"));
        }

        List<GreenNode> statements = plan.statements();
        GreenNode body;
        if (plan.yielded()) {
            GreenNode yield = statements.getFirst();
            context.record(
                    TokenEdit.delete(
                            SwitchRules.CASE_STYLE,
                            reason,
                            context.firstPosition(yield.children().getFirst()),
                            "yield"));
            body =
                    GreenNode.branch(
                            SyntaxKind.EXPRESSION_STATEMENT,
                            List.of(yield.children().get(1), yield.children().getLast()));
        } else if (!plan.brace()) {
            body = statements.getFirst();
        } else {
            body = braced(statements, colonPosition, context, reason);
        }

        List<GreenNode> ownLabels = labelsOf(owner);
        labels.addAll(labels.isEmpty() ? ownLabels : ownLabels.subList(1, ownLabels.size()));
        List<GreenNode> children = new ArrayList<>();
        children.add(GreenNode.branch(SyntaxKind.CASE_LABELS, labels));
        children.add(Synthetic.operator("->"));
        children.add(body);
        return GreenNode.branch(SyntaxKind.SWITCH_CASE, children);
    }

    /** A block round the statements of a group that cannot stand as a lone arrow body. */
    private static GreenNode braced(
            List<GreenNode> statements,
            int colonPosition,
            RewriteContext context,
            String reason) {
        int start = statements.isEmpty() ? colonPosition + 1 : context.firstPosition(statements.getFirst());
        int end = statements.isEmpty() ? colonPosition + 1 : context.endPosition(statements.getLast());
        if (statements.isEmpty()) {
            context.record(
                    TokenEdit.insert(SwitchRules.CASE_STYLE, reason, start, TokenEdit.Bias.OUTERMOST_FIRST, "{", "}"));
        } else {
            context.record(
                    TokenEdit.insert(SwitchRules.CASE_STYLE, reason, start, TokenEdit.Bias.OUTERMOST_FIRST, "{"));
            context.record(TokenEdit.insert(SwitchRules.CASE_STYLE, reason, end, TokenEdit.Bias.INNERMOST_FIRST, "}"));
        }
        List<GreenNode> children = new ArrayList<>();
        children.add(Synthetic.separator("{"));
        children.addAll(statements);
        children.add(Synthetic.separator("}"));
        return GreenNode.branch(SyntaxKind.BLOCK, children);
    }

    // ------------------------------------------------------------ arrow to colon

    /** The cases as colon cases, or null when any one of them fails the precondition. */
    private List<GreenNode> toColon(List<GreenNode> cases, boolean value, RewriteContext context) {
        for (GreenNode switchCase : cases) {
            if (switchCase.kind() != SyntaxKind.SWITCH_CASE || switchCase.children().size() != 3) {
                return null;
            }
            GreenNode arrow = switchCase.children().get(1);
            if (!(arrow instanceof GreenNode.Leaf leaf) || !leaf.lexeme().equals("->")) {
                return null;
            }
            if (Synthetic.carriesComments(arrow) || context.firstPosition(arrow) < 0) {
                return null;
            }
            GreenNode body = switchCase.children().get(2);
            if (body.kind() != SyntaxKind.THROW_STATEMENT
                    && !(body.kind() == SyntaxKind.EXPRESSION_STATEMENT && body.children().size() == 2)) {
                return null;
            }
            if (context.firstPosition(body) < 0 || context.endPosition(body) < 0) {
                return null;
            }
        }

        String reason = "an arrow case written as a colon case";
        List<GreenNode> converted = new ArrayList<>(cases.size());
        for (GreenNode switchCase : cases) {
            GreenNode arrow = switchCase.children().get(1);
            GreenNode body = switchCase.children().get(2);
            context.record(
                    new TokenEdit(
                            SwitchRules.CASE_STYLE,
                            reason,
                            context.firstPosition(arrow),
                            List.of("->"),
                            List.of(":"),
                            TokenEdit.Bias.INNERMOST_FIRST));

            List<GreenNode> children = new ArrayList<>();
            children.add(switchCase.children().getFirst());
            children.add(Synthetic.separator(":"));
            if (body.kind() == SyntaxKind.THROW_STATEMENT) {
                children.add(body);
            } else if (value) {
                // The body of an expression switch is a value, so the colon form has to yield it.
                context.record(
                        TokenEdit.insert(
                                SwitchRules.CASE_STYLE,
                                reason,
                                context.firstPosition(body),
                                TokenEdit.Bias.OUTERMOST_FIRST,
                                "yield"));
                children.add(
                        GreenNode.branch(
                                SyntaxKind.YIELD_STATEMENT,
                                List.of(
                                        Synthetic.contextualKeyword("yield"),
                                        body.children().getFirst(),
                                        body.children().getLast())));
            } else {
                context.record(
                        TokenEdit.insert(
                                SwitchRules.CASE_STYLE,
                                reason,
                                context.endPosition(body),
                                TokenEdit.Bias.INNERMOST_FIRST,
                                "break",
                                ";"));
                children.add(body);
                children.add(
                        GreenNode.branch(
                                SyntaxKind.BREAK_STATEMENT,
                                List.of(Synthetic.keyword("break"), Synthetic.separator(";"))));
            }
            converted.add(GreenNode.branch(SyntaxKind.SWITCH_CASE, children));
        }
        return converted;
    }

    // ----------------------------------------------------------------- queries

    /** The label list of a case, without the {@code CASE_LABELS} node round it. */
    private static List<GreenNode> labelsOf(GreenNode switchCase) {
        return switchCase.children().getFirst().children();
    }

    /** Whether a case carries a {@code when} guard, which belongs to one pattern and cannot be shared. */
    private static boolean isGuarded(GreenNode switchCase) {
        for (GreenNode label : labelsOf(switchCase)) {
            if (label.kind() == SyntaxKind.CASE_GUARD) {
                return true;
            }
        }
        return false;
    }

    private static boolean isDefault(GreenNode switchCase) {
        List<GreenNode> labels = labelsOf(switchCase);
        return labels.size() == 1
                && labels.getFirst() instanceof GreenNode.Leaf leaf
                && leaf.lexeme().equals("default");
    }

    /** An unlabelled {@code break;}, which is the terminator an arrow case does not need. */
    private static boolean isPlainBreak(GreenNode statement) {
        return statement.kind() == SyntaxKind.BREAK_STATEMENT && statement.children().size() == 2;
    }

    /** Whether a statement cannot fall out of the bottom of its group. */
    private static boolean leavesTheSwitch(GreenNode statement) {
        return statement.kind() == SyntaxKind.RETURN_STATEMENT
                || statement.kind() == SyntaxKind.THROW_STATEMENT
                || statement.kind() == SyntaxKind.YIELD_STATEMENT
                || statement.kind() == SyntaxKind.CONTINUE_STATEMENT
                || statement.kind() == SyntaxKind.BREAK_STATEMENT;
    }

    /** {@code yield e;} and nothing else, which an arrow body can be written as directly. */
    private static boolean isSimpleYield(GreenNode statement) {
        return statement.kind() == SyntaxKind.YIELD_STATEMENT && statement.children().size() == 3;
    }

    /** Whether a statement may be an arrow body without braces round it. */
    private static boolean standsAlone(GreenNode statement) {
        return statement.kind() == SyntaxKind.EXPRESSION_STATEMENT || statement.kind() == SyntaxKind.THROW_STATEMENT;
    }

    /**
     * Whether a {@code break} belonging to this switch is buried somewhere in the statement.
     *
     * <p>Loops and nested switches are not searched: a {@code break} inside one binds to that, so it
     * means the same thing before and after. One anywhere else is the fall-through this rewrite has
     * no way to express, and the switch is left alone.
     */
    private static boolean holdsSwitchBreak(GreenNode node) {
        if (isPlainBreak(node)) {
            return true;
        }
        if (bindsBreak(node.kind())) {
            return false;
        }
        for (GreenNode child : node.children()) {
            if (holdsSwitchBreak(child)) {
                return true;
            }
        }
        return false;
    }

    private static boolean bindsBreak(SyntaxKind kind) {
        return kind == SyntaxKind.FOR_STATEMENT
                || kind == SyntaxKind.ENHANCED_FOR_STATEMENT
                || kind == SyntaxKind.WHILE_STATEMENT
                || kind == SyntaxKind.DO_STATEMENT
                || kind == SyntaxKind.SWITCH_STATEMENT
                || kind == SyntaxKind.SWITCH_EXPRESSION
                || kind == SyntaxKind.LAMBDA_EXPRESSION
                || kind == SyntaxKind.ANONYMOUS_CLASS_BODY
                || kind == SyntaxKind.CLASS_BODY;
    }

}
