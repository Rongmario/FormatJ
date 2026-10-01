package zone.rong.formatj.core.rewrite;

import zone.rong.formatj.api.rules.SemicolonRules;
import zone.rong.formatj.core.cst.GreenNode;
import zone.rong.formatj.core.cst.ProgramTokens;
import zone.rong.formatj.core.cst.SyntaxKind;
import java.util.ArrayList;
import java.util.List;

/**
 * Removes stray semicolons, which the parser reads as empty declarations.
 *
 * <p>Only two places qualify: directly inside a type body, and directly after a top-level type.
 * Empty statements in a block, the {@code ;} ending an enum's constant list and the
 * {@code ;} of {@code for (;;)} are different nodes and are never reached. A semicolon carrying a
 * comment stays, so no comment is lost.
 */
public final class SemicolonRewrite implements Rewrite {

    @Override
    public String name() {
        return "semicolons";
    }

    @Override
    public boolean enabled(RewriteContext context) {
        return context.rule(SemicolonRules.REMOVE_REDUNDANT);
    }

    @Override
    public GreenNode rewrite(GreenNode node, RewriteContext context) {
        boolean body = node.kind() == SyntaxKind.CLASS_BODY;
        if (!body && node.kind() != SyntaxKind.COMPILATION_UNIT) {
            return node;
        }

        List<GreenNode> children = node.children();
        List<GreenNode> kept = new ArrayList<>(children.size());
        boolean removable = body && membersFollowConstants(children);
        boolean afterType = false;
        for (GreenNode child : children) {
            if (child.kind() == SyntaxKind.EMPTY_STATEMENT
                    && (body ? removable : afterType)
                    && !hasComments(child)
                    && context.firstPosition(child) >= 0) {
                context.record(TokenEdit.delete(
                        SemicolonRules.REMOVE_REDUNDANT,
                        "a stray semicolon",
                        context.firstPosition(child),
                        ";"));
                continue;
            }
            afterType = child.kind().isTypeDeclaration();
            kept.add(child);
        }
        return kept.size() == children.size() ? node : GreenNode.branch(node.kind(), kept);
    }

    /**
     * Whether stray semicolons in this body may go. An enum body whose only members are stray
     * semicolons keeps them, because then the {@code ;} ending the constants is the only one that
     * counts as a token, and which of them survives would change the token stream.
     */
    private static boolean membersFollowConstants(List<GreenNode> children) {
        boolean constants = false;
        for (GreenNode child : children) {
            if (child.kind() == SyntaxKind.ENUM_CONSTANTS) {
                constants = true;
            } else if (constants && child.kind().isMember()) {
                return true;
            }
        }
        return !constants;
    }

    private static boolean hasComments(GreenNode node) {
        return ProgramTokens.leaves(node).getFirst().token().hasComments();
    }

}
