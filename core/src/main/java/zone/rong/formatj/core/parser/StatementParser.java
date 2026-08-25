package zone.rong.formatj.core.parser;

import zone.rong.formatj.api.Diagnostic;
import zone.rong.formatj.api.LanguageLevel;
import zone.rong.formatj.core.cst.GreenNode;
import zone.rong.formatj.core.cst.SyntaxKind;
import zone.rong.formatj.core.lexer.Token;
import zone.rong.formatj.core.lexer.TokenKind;
import java.util.ArrayList;
import java.util.List;

/**
 * Blocks, statements and switch bodies.
 *
 * <p>A statement that fails to parse is wrapped in an unparsed region and the cursor moves to the
 * next recovery point, so one construct the parser does not understand costs that statement its
 * formatting and nothing else.
 */
abstract class StatementParser extends ExpressionParser {

    StatementParser(List<Token> tokens, LanguageLevel languageLevel, boolean previewFeatures) {
        super(tokens, languageLevel, previewFeatures);
    }

    @Override
    protected GreenNode parseLambdaBody() {
        return parseBlock();
    }

    protected GreenNode parseBlock() {
        List<GreenNode> children = new ArrayList<>();
        children.add(expect("{"));
        while (!at("}") && !atEnd()) {
            children.add(parseStatementWithRecovery());
        }
        children.add(expect("}"));
        return branch(SyntaxKind.BLOCK, children);
    }

    /** Parses one statement, degrading to a verbatim region when it cannot. */
    protected GreenNode parseStatementWithRecovery() {
        int start = mark();
        try {
            return parseStatement();
        } catch (ParseFailure failure) {
            reset(start);
            skipToRecoveryPoint();
            if (mark() == start) {
                // No progress would loop forever; take one token verbatim and carry on.
                advance();
            }
            report(
                    Diagnostic.warning(
                            failure.getMessage() + "; statement left unformatted",
                            failure.token().line(),
                            failure.token().column()));
            return unparsedFrom(start);
        }
    }

    protected GreenNode parseStatement() {
        Token token = peek();
        if (token.kind() == TokenKind.KEYWORD) {
            switch (token.text()) {
                case "if":
                    return parseIf();
                case "for":
                    return parseFor();
                case "while":
                    return parseWhile();
                case "do":
                    return parseDo();
                case "switch":
                    return parseSwitchStatement();
                case "try":
                    return parseTry();
                case "return":
                    return parseSimpleStatement(SyntaxKind.RETURN_STATEMENT, true);
                case "throw":
                    return parseThrow();
                case "break":
                    return parseBreakOrContinue(SyntaxKind.BREAK_STATEMENT);
                case "continue":
                    return parseBreakOrContinue(SyntaxKind.CONTINUE_STATEMENT);
                case "synchronized":
                    return parseSynchronized();
                case "assert":
                    return parseAssert();
                case "class":
                case "interface":
                case "enum":
                    return parseLocalTypeDeclaration();
                default:
                    break;
            }
        }
        if (at("{")) {
            return parseBlock();
        }
        if (at(";")) {
            return branch(SyntaxKind.EMPTY_STATEMENT, List.of(advance()));
        }
        if (atContextual("yield") && !peek(1).is("=") && !peek(1).is(".") && !peek(1).is("(")) {
            return parseSimpleStatement(SyntaxKind.YIELD_STATEMENT, true);
        }
        if (atIdentifier() && peek(1).is(":")) {
            List<GreenNode> children = new ArrayList<>();
            children.add(identifier());
            children.add(advance());
            children.add(parseStatement());
            return branch(SyntaxKind.LABELED_STATEMENT, children);
        }
        if (atLocalTypeDeclaration()) {
            return parseLocalTypeDeclaration();
        }
        if (atLocalVariableDeclaration()) {
            return parseLocalVariableDeclaration(true);
        }
        List<GreenNode> children = new ArrayList<>();
        children.add(parseExpression());
        children.add(expect(";"));
        return branch(SyntaxKind.EXPRESSION_STATEMENT, children);
    }

    private boolean atLocalTypeDeclaration() {
        int start = mark();
        try {
            while (at("final") || at("static") || at("abstract") || at("@")) {
                if (at("@")) {
                    parseAnnotation();
                } else {
                    advance();
                }
            }
            return at("class")
                    || at("interface")
                    || at("enum")
                    || (atContextual("record") && peek(1).kind() == TokenKind.IDENTIFIER)
                    || (atContextual("sealed") || atContextual("non"));
        } catch (ParseFailure failure) {
            return false;
        } finally {
            reset(start);
        }
    }

    /** Implemented by the declaration layer. */
    protected abstract GreenNode parseLocalTypeDeclaration();

    private boolean atLocalVariableDeclaration() {
        if (at("final") || at("@")) {
            return true;
        }
        if (!atTypeStart()) {
            return false;
        }
        int start = mark();
        try {
            parseType();
            if (!atIdentifier()) {
                return false;
            }
            advance();
            return at("=") || at(";") || at(",") || (at("[") && peek(1).is("]"));
        } catch (ParseFailure failure) {
            return false;
        } finally {
            reset(start);
        }
    }

    /**
     * @param terminated whether the declaration ends with a semicolon; a for header's does not
     */
    protected GreenNode parseLocalVariableDeclaration(boolean terminated) {
        List<GreenNode> children = new ArrayList<>();
        while (at("final") || at("@")) {
            children.add(at("@") ? parseAnnotation() : advance());
        }
        children.add(parseType());
        children.add(parseVariableDeclarator());
        while (at(",")) {
            children.add(advance());
            children.add(parseVariableDeclarator());
        }
        if (terminated) {
            children.add(expect(";"));
        }
        return branch(SyntaxKind.LOCAL_VARIABLE_DECLARATION, children);
    }

    protected GreenNode parseVariableDeclarator() {
        List<GreenNode> children = new ArrayList<>();
        children.add(identifier());
        while (at("[") && peek(1).is("]")) {
            children.add(advance());
            children.add(advance());
        }
        if (at("=")) {
            children.add(advance());
            children.add(at("{") ? parseArrayInitializer() : parseExpression());
        }
        return branch(SyntaxKind.VARIABLE_DECLARATOR, children);
    }

    private GreenNode parseIf() {
        List<GreenNode> children = new ArrayList<>();
        children.add(expect("if"));
        children.add(expect("("));
        children.add(parseExpression());
        children.add(expect(")"));
        children.add(parseStatementWithRecovery());
        if (at("else")) {
            List<GreenNode> elseChildren = new ArrayList<>();
            elseChildren.add(advance());
            elseChildren.add(parseStatementWithRecovery());
            children.add(branch(SyntaxKind.ELSE_CLAUSE, elseChildren));
        }
        return branch(SyntaxKind.IF_STATEMENT, children);
    }

    private GreenNode parseFor() {
        List<GreenNode> children = new ArrayList<>();
        children.add(expect("for"));
        children.add(expect("("));
        if (atEnhancedFor()) {
            children.add(parseEnhancedForHeader());
            children.add(expect(")"));
            children.add(parseStatementWithRecovery());
            return branch(SyntaxKind.ENHANCED_FOR_STATEMENT, children);
        }
        if (!at(";")) {
            children.add(atLocalVariableDeclaration() ? parseLocalVariableDeclaration(false) : parseForInit());
        }
        children.add(expect(";"));
        if (!at(";")) {
            children.add(parseExpression());
        }
        children.add(expect(";"));
        if (!at(")")) {
            children.add(parseExpression());
            while (at(",")) {
                children.add(advance());
                children.add(parseExpression());
            }
        }
        children.add(expect(")"));
        children.add(parseStatementWithRecovery());
        return branch(SyntaxKind.FOR_STATEMENT, children);
    }

    private GreenNode parseForInit() {
        List<GreenNode> children = new ArrayList<>();
        children.add(parseExpression());
        while (at(",")) {
            children.add(advance());
            children.add(parseExpression());
        }
        return branch(SyntaxKind.EXPRESSION_STATEMENT, children);
    }

    private boolean atEnhancedFor() {
        int start = mark();
        try {
            while (at("final") || at("@")) {
                if (at("@")) {
                    parseAnnotation();
                } else {
                    advance();
                }
            }
            if (!atTypeStart()) {
                return false;
            }
            parseType();
            if (!atIdentifier()) {
                return false;
            }
            advance();
            return at(":");
        } catch (ParseFailure failure) {
            return false;
        } finally {
            reset(start);
        }
    }

    private GreenNode parseEnhancedForHeader() {
        List<GreenNode> children = new ArrayList<>();
        while (at("final") || at("@")) {
            children.add(at("@") ? parseAnnotation() : advance());
        }
        children.add(parseType());
        children.add(identifier());
        children.add(expect(":"));
        children.add(parseExpression());
        return branch(SyntaxKind.PARAMETER, children);
    }

    private GreenNode parseWhile() {
        List<GreenNode> children = new ArrayList<>();
        children.add(expect("while"));
        children.add(expect("("));
        children.add(parseExpression());
        children.add(expect(")"));
        children.add(parseStatementWithRecovery());
        return branch(SyntaxKind.WHILE_STATEMENT, children);
    }

    private GreenNode parseDo() {
        List<GreenNode> children = new ArrayList<>();
        children.add(expect("do"));
        children.add(parseStatementWithRecovery());
        children.add(expect("while"));
        children.add(expect("("));
        children.add(parseExpression());
        children.add(expect(")"));
        children.add(expect(";"));
        return branch(SyntaxKind.DO_STATEMENT, children);
    }

    private GreenNode parseSwitchStatement() {
        List<GreenNode> children = new ArrayList<>();
        children.add(expect("switch"));
        children.add(expect("("));
        children.add(parseExpression());
        children.add(expect(")"));
        children.add(parseSwitchBlock());
        return branch(SyntaxKind.SWITCH_STATEMENT, children);
    }

    @Override
    protected GreenNode parseSwitchBlock() {
        List<GreenNode> children = new ArrayList<>();
        children.add(expect("{"));
        while (!at("}") && !atEnd()) {
            children.add(parseSwitchCase());
        }
        children.add(expect("}"));
        return branch(SyntaxKind.SWITCH_BLOCK, children);
    }

    private GreenNode parseSwitchCase() {
        List<GreenNode> children = new ArrayList<>();
        children.add(parseCaseLabels());
        if (at("->")) {
            children.add(advance());
            children.add(parseArrowCaseBody());
            return branch(SyntaxKind.SWITCH_CASE, children);
        }
        children.add(expect(":"));
        while (!at("}") && !at("case") && !at("default") && !atEnd()) {
            children.add(parseStatementWithRecovery());
        }
        return branch(SyntaxKind.SWITCH_CASE, children);
    }

    private GreenNode parseArrowCaseBody() {
        if (at("{")) {
            return parseBlock();
        }
        if (at("throw")) {
            return parseThrow();
        }
        List<GreenNode> children = new ArrayList<>();
        children.add(parseExpression());
        children.add(expect(";"));
        return branch(SyntaxKind.EXPRESSION_STATEMENT, children);
    }

    private GreenNode parseCaseLabels() {
        List<GreenNode> children = new ArrayList<>();
        if (at("default")) {
            children.add(advance());
            return branch(SyntaxKind.CASE_LABELS, children);
        }
        children.add(expect("case"));
        children.add(parseCaseLabel());
        while (at(",")) {
            children.add(advance());
            children.add(parseCaseLabel());
        }
        if (atContextual("when")) {
            List<GreenNode> guard = new ArrayList<>();
            guard.add(advance());
            guard.add(parseExpression());
            children.add(branch(SyntaxKind.CASE_GUARD, guard));
        }
        return branch(SyntaxKind.CASE_LABELS, children);
    }

    private GreenNode parseCaseLabel() {
        if (at("default")) {
            return advance();
        }
        if (atIdentifier() && (peek(1).is("->") || peek(1).is(",") || peek(1).is(":"))) {
            // A bare constant label; without this it would parse as the start of a lambda.
            return branch(SyntaxKind.NAME, List.of(identifier()));
        }
        if (atPatternLabel()) {
            return parsePattern();
        }
        return parseExpression();
    }

    private GreenNode parseTry() {
        List<GreenNode> children = new ArrayList<>();
        children.add(expect("try"));
        if (at("(")) {
            children.add(parseResources());
        }
        children.add(parseBlock());
        while (at("catch")) {
            List<GreenNode> catchChildren = new ArrayList<>();
            catchChildren.add(advance());
            catchChildren.add(expect("("));
            catchChildren.add(parseCatchParameter());
            catchChildren.add(expect(")"));
            catchChildren.add(parseBlock());
            children.add(branch(SyntaxKind.CATCH_CLAUSE, catchChildren));
        }
        if (at("finally")) {
            List<GreenNode> finallyChildren = new ArrayList<>();
            finallyChildren.add(advance());
            finallyChildren.add(parseBlock());
            children.add(branch(SyntaxKind.FINALLY_CLAUSE, finallyChildren));
        }
        return branch(SyntaxKind.TRY_STATEMENT, children);
    }

    private GreenNode parseCatchParameter() {
        List<GreenNode> children = new ArrayList<>();
        while (at("final") || at("@")) {
            children.add(at("@") ? parseAnnotation() : advance());
        }
        children.add(parseType());
        while (at("|")) {
            children.add(advance());
            children.add(parseType());
        }
        children.add(identifier());
        return branch(SyntaxKind.PARAMETER, children);
    }

    private GreenNode parseResources() {
        List<GreenNode> children = new ArrayList<>();
        children.add(expect("("));
        children.add(parseResource());
        while (at(";")) {
            children.add(advance());
            if (at(")")) {
                break;
            }
            children.add(parseResource());
        }
        children.add(expect(")"));
        return branch(SyntaxKind.RESOURCES, children);
    }

    private GreenNode parseResource() {
        List<GreenNode> children = new ArrayList<>();
        if (atLocalVariableDeclaration()) {
            while (at("final") || at("@")) {
                children.add(at("@") ? parseAnnotation() : advance());
            }
            children.add(parseType());
            children.add(identifier());
            children.add(expect("="));
            children.add(parseExpression());
        } else {
            children.add(parseExpression());
        }
        return branch(SyntaxKind.RESOURCE, children);
    }

    private GreenNode parseThrow() {
        List<GreenNode> children = new ArrayList<>();
        children.add(expect("throw"));
        children.add(parseExpression());
        children.add(expect(";"));
        return branch(SyntaxKind.THROW_STATEMENT, children);
    }

    private GreenNode parseSynchronized() {
        List<GreenNode> children = new ArrayList<>();
        children.add(expect("synchronized"));
        children.add(expect("("));
        children.add(parseExpression());
        children.add(expect(")"));
        children.add(parseBlock());
        return branch(SyntaxKind.SYNCHRONIZED_STATEMENT, children);
    }

    private GreenNode parseAssert() {
        List<GreenNode> children = new ArrayList<>();
        children.add(expect("assert"));
        children.add(parseExpression());
        if (at(":")) {
            children.add(advance());
            children.add(parseExpression());
        }
        children.add(expect(";"));
        return branch(SyntaxKind.ASSERT_STATEMENT, children);
    }

    private GreenNode parseBreakOrContinue(SyntaxKind kind) {
        List<GreenNode> children = new ArrayList<>();
        children.add(advance());
        if (atIdentifier()) {
            children.add(identifier());
        }
        children.add(expect(";"));
        return branch(kind, children);
    }

    /**
     * @param hasValue whether the keyword may be followed by an expression
     */
    private GreenNode parseSimpleStatement(SyntaxKind kind, boolean hasValue) {
        List<GreenNode> children = new ArrayList<>();
        children.add(advance());
        if (hasValue && !at(";")) {
            children.add(parseExpression());
        }
        children.add(expect(";"));
        return branch(kind, children);
    }

}
