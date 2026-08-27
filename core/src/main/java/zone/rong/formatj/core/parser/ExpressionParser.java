package zone.rong.formatj.core.parser;

import zone.rong.formatj.api.LanguageLevel;
import zone.rong.formatj.core.cst.GreenNode;
import zone.rong.formatj.core.cst.SyntaxKind;
import zone.rong.formatj.core.lexer.Token;
import zone.rong.formatj.core.lexer.TokenKind;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Types, patterns and expressions.
 *
 * <p>Binary expressions are parsed by precedence climbing. Operators beginning with {@code >} are
 * assembled here from adjacent single {@code >} tokens, because the lexer must not merge them: the
 * same characters close a generic type, and a token that spans both roles cannot be split again
 * without losing the source text.
 */
abstract class ExpressionParser extends ParserBase {

    private static final Set<String> PRIMITIVE_TYPES =
            Set.of("boolean", "byte", "char", "short", "int", "long", "float", "double", "void");

    private static final Set<String> ASSIGNMENT_OPERATORS =
            Set.of("=", "+=", "-=", "*=", "/=", "%=", "&=", "|=", "^=", "<<=", ">>=", ">>>=");

    private static final Set<String> LITERAL_KEYWORDS = Set.of("true", "false", "null");

    ExpressionParser(List<Token> tokens, LanguageLevel languageLevel, boolean previewFeatures) {
        super(tokens, languageLevel, previewFeatures);
    }

    // ----------------------------------------------------------------- types

    protected boolean atPrimitiveType() {
        return peek().kind() == TokenKind.KEYWORD && PRIMITIVE_TYPES.contains(peek().text());
    }

    /** Whether a type can start here, used to tell declarations from expressions. */
    protected boolean atTypeStart() {
        return atPrimitiveType() || atIdentifier() || at("@");
    }

    protected GreenNode parseType() {
        List<GreenNode> children = new ArrayList<>();
        while (at("@")) {
            children.add(parseAnnotation());
        }
        GreenNode base;
        if (atPrimitiveType()) {
            children.add(advance());
            base = branch(SyntaxKind.PRIMITIVE_TYPE, children);
        } else if (atContextual("var") && (peek(1).kind() == TokenKind.IDENTIFIER || atUnnamed(1))) {
            children.add(advance());
            base = branch(SyntaxKind.VAR_TYPE, children);
        } else {
            children.add(parseTypeName());
            base = branch(SyntaxKind.CLASS_TYPE, children);
        }
        return parseArraySuffix(base);
    }

    /** A possibly qualified name with type arguments on any segment: {@code a.b.C<D>.E}. */
    private GreenNode parseTypeName() {
        List<GreenNode> children = new ArrayList<>();
        children.add(identifier());
        if (at("<")) {
            children.add(parseTypeArguments());
        }
        while (at(".") && peek(1).kind() == TokenKind.IDENTIFIER) {
            children.add(advance());
            children.add(identifier());
            if (at("<")) {
                children.add(parseTypeArguments());
            }
        }
        return children.size() == 1 ? children.getFirst() : branch(SyntaxKind.QUALIFIED_NAME, children);
    }

    private GreenNode parseArraySuffix(GreenNode base) {
        GreenNode current = base;
        while (at("[") && peek(1).is("]")) {
            List<GreenNode> children = new ArrayList<>();
            children.add(current);
            children.add(advance());
            children.add(advance());
            current = branch(SyntaxKind.ARRAY_TYPE, children);
        }
        if (at("...")) {
            List<GreenNode> children = new ArrayList<>();
            children.add(current);
            children.add(advance());
            current = branch(SyntaxKind.ARRAY_TYPE, children);
        }
        return current;
    }

    protected GreenNode parseTypeArguments() {
        List<GreenNode> children = new ArrayList<>();
        children.add(expect("<"));
        if (!at(">")) {
            children.add(parseTypeArgument());
            while (at(",")) {
                children.add(advance());
                children.add(parseTypeArgument());
            }
        }
        children.add(expect(">"));
        return branch(SyntaxKind.TYPE_ARGUMENTS, children);
    }

    private GreenNode parseTypeArgument() {
        if (at("?")) {
            List<GreenNode> children = new ArrayList<>();
            children.add(advance());
            if (at("extends") || at("super")) {
                children.add(advance());
                children.add(parseType());
            }
            return branch(SyntaxKind.WILDCARD_TYPE, children);
        }
        return parseType();
    }

    protected GreenNode parseTypeParameters() {
        List<GreenNode> children = new ArrayList<>();
        children.add(expect("<"));
        children.add(parseTypeParameter());
        while (at(",")) {
            children.add(advance());
            children.add(parseTypeParameter());
        }
        children.add(expect(">"));
        return branch(SyntaxKind.TYPE_PARAMETERS, children);
    }

    private GreenNode parseTypeParameter() {
        List<GreenNode> children = new ArrayList<>();
        while (at("@")) {
            children.add(parseAnnotation());
        }
        children.add(identifier());
        if (at("extends")) {
            List<GreenNode> bound = new ArrayList<>();
            bound.add(advance());
            bound.add(parseType());
            while (at("&")) {
                bound.add(advance());
                bound.add(parseType());
            }
            children.add(branch(SyntaxKind.TYPE_BOUND, bound));
        }
        return branch(SyntaxKind.TYPE_PARAMETER, children);
    }

    // ----------------------------------------------------------- annotations

    protected GreenNode parseAnnotation() {
        List<GreenNode> children = new ArrayList<>();
        children.add(expect("@"));
        children.add(parseQualifiedName());
        if (at("(")) {
            children.add(parseAnnotationArguments());
        }
        return branch(SyntaxKind.ANNOTATION, children);
    }

    private GreenNode parseAnnotationArguments() {
        List<GreenNode> children = new ArrayList<>();
        children.add(expect("("));
        if (!at(")")) {
            children.add(parseAnnotationElement());
            while (at(",")) {
                children.add(advance());
                children.add(parseAnnotationElement());
            }
        }
        children.add(expect(")"));
        return branch(SyntaxKind.ANNOTATION_ARGUMENTS, children);
    }

    private GreenNode parseAnnotationElement() {
        if (atIdentifier() && peek(1).is("=")) {
            List<GreenNode> children = new ArrayList<>();
            children.add(identifier());
            children.add(advance());
            children.add(parseElementValue());
            return branch(SyntaxKind.ANNOTATION_ELEMENT, children);
        }
        return parseElementValue();
    }

    private GreenNode parseElementValue() {
        if (at("{")) {
            return parseArrayInitializer();
        }
        if (at("@")) {
            return parseAnnotation();
        }
        return parseExpression();
    }

    protected GreenNode parseQualifiedName() {
        List<GreenNode> children = new ArrayList<>();
        children.add(identifier());
        while (at(".") && peek(1).kind() == TokenKind.IDENTIFIER) {
            children.add(advance());
            children.add(identifier());
        }
        return children.size() == 1 ? children.getFirst() : branch(SyntaxKind.QUALIFIED_NAME, children);
    }

    // ---------------------------------------------------------- expressions

    protected GreenNode parseExpression() {
        if (atLambda()) {
            return parseLambda();
        }
        GreenNode left = parseTernary();
        String operator = peekOperator();
        if (ASSIGNMENT_OPERATORS.contains(operator)) {
            List<GreenNode> children = new ArrayList<>();
            children.add(left);
            consumeOperator(children);
            children.add(parseExpression());
            return branch(SyntaxKind.ASSIGNMENT_EXPRESSION, children);
        }
        return left;
    }

    private GreenNode parseTernary() {
        GreenNode condition = parseBinary(1);
        if (!at("?")) {
            return condition;
        }
        List<GreenNode> children = new ArrayList<>();
        children.add(condition);
        children.add(advance());
        children.add(parseExpression());
        children.add(expect(":"));
        children.add(atLambda() ? parseLambda() : parseTernary());
        return branch(SyntaxKind.TERNARY_EXPRESSION, children);
    }

    private static int precedenceOf(String operator) {
        return switch (operator) {
            case "||" -> 1;
            case "&&" -> 2;
            case "|" -> 3;
            case "^" -> 4;
            case "&" -> 5;
            case "==", "!=" -> 6;
            case "<", ">", "<=", ">=", "instanceof" -> 7;
            case "<<", ">>", ">>>" -> 8;
            case "+", "-" -> 9;
            case "*", "/", "%" -> 10;
            default -> 0;
        };
    }

    private GreenNode parseBinary(int minimumPrecedence) {
        GreenNode left = parseUnary();
        while (true) {
            String operator = peekOperator();
            int precedence = precedenceOf(operator);
            if (precedence == 0 || precedence < minimumPrecedence || ASSIGNMENT_OPERATORS.contains(operator)) {
                return left;
            }
            if (operator.equals("instanceof")) {
                List<GreenNode> children = new ArrayList<>();
                children.add(left);
                children.add(advance());
                if (at("final")) {
                    children.add(advance());
                }
                children.add(parsePattern());
                left = branch(SyntaxKind.INSTANCEOF_EXPRESSION, children);
                continue;
            }
            List<GreenNode> children = new ArrayList<>();
            children.add(left);
            consumeOperator(children);
            children.add(parseBinary(precedence + 1));
            left = branch(SyntaxKind.BINARY_EXPRESSION, children);
        }
    }

    /**
     * The operator at the cursor, assembling any run of adjacent {@code >} tokens.
     *
     * @return the operator text, or an empty string when the next token is not an operator
     */
    protected String peekOperator() {
        Token token = peek();
        if (token.kind() == TokenKind.KEYWORD && token.is("instanceof")) {
            return "instanceof";
        }
        if (token.kind() != TokenKind.OPERATOR) {
            return "";
        }
        if (!token.is(">")) {
            return token.text();
        }
        StringBuilder operator = new StringBuilder(">");
        Token previous = token;
        for (int ahead = 1; ahead <= 3; ahead++) {
            Token next = peek(ahead);
            if (next.start() != previous.end() || next.kind() != TokenKind.OPERATOR) {
                break;
            }
            if (next.is(">") && operator.length() < 3 && operator.indexOf("=") < 0) {
                operator.append('>');
            } else if (next.is("=")) {
                operator.append('=');
                return operator.toString();
            } else {
                break;
            }
            previous = next;
        }
        return operator.toString();
    }

    /** Consumes the operator {@link #peekOperator()} reported, which may be several tokens. */
    protected void consumeOperator(List<GreenNode> children) {
        String operator = peekOperator();
        int tokens = operator.startsWith(">") ? operator.length() : 1;
        for (int i = 0; i < tokens; i++) {
            children.add(advance());
        }
    }

    private GreenNode parseUnary() {
        if (atAny("+", "-", "!", "~", "++", "--")) {
            List<GreenNode> children = new ArrayList<>();
            children.add(advance());
            children.add(parseUnary());
            return branch(SyntaxKind.UNARY_EXPRESSION, children);
        }
        if (at("(") && atCast()) {
            List<GreenNode> children = new ArrayList<>();
            children.add(advance());
            children.add(parseType());
            while (at("&")) {
                children.add(advance());
                children.add(parseType());
            }
            children.add(expect(")"));
            children.add(atLambda() ? parseLambda() : parseUnary());
            return branch(SyntaxKind.CAST_EXPRESSION, children);
        }
        return parsePostfix(parsePrimary());
    }

    /** Decides whether a parenthesis opens a cast rather than a parenthesized expression. */
    private boolean atCast() {
        int start = mark();
        try {
            advance();
            if (!atTypeStart()) {
                return false;
            }
            boolean primitive = atPrimitiveType();
            parseType();
            while (at("&")) {
                advance();
                parseType();
            }
            if (!at(")")) {
                return false;
            }
            advance();
            Token next = peek();
            if (next.kind() == TokenKind.END_OF_FILE) {
                return false;
            }
            if (primitive) {
                return !next.is(")") && !next.is(";") && !next.is(",");
            }
            // For a reference type only an operand may follow; (a) + b is addition, not a cast.
            return next.kind() == TokenKind.IDENTIFIER
                    || next.kind() == TokenKind.STRING_LITERAL
                    || next.kind() == TokenKind.TEXT_BLOCK
                    || next.kind() == TokenKind.CHAR_LITERAL
                    || next.kind() == TokenKind.NUMBER_LITERAL
                    || next.is("(")
                    || next.is("!")
                    || next.is("~")
                    || next.is("this")
                    || next.is("super")
                    || next.is("new")
                    || next.is("switch")
                    || LITERAL_KEYWORDS.contains(next.text());
        } catch (ParseFailure failure) {
            return false;
        } finally {
            reset(start);
        }
    }

    private GreenNode parsePostfix(GreenNode start) {
        GreenNode current = start;
        while (true) {
            if (at(".")) {
                List<GreenNode> children = new ArrayList<>();
                children.add(current);
                children.add(advance());
                if (at("new")) {
                    children.add(parseObjectCreation());
                    current = branch(SyntaxKind.MEMBER_ACCESS, children);
                    continue;
                }
                if (at("<")) {
                    children.add(parseTypeArguments());
                }
                if (at("this")) {
                    children.add(advance());
                    current = branch(SyntaxKind.MEMBER_ACCESS, children);
                    continue;
                }
                if (at("class")) {
                    children.add(advance());
                    current = branch(SyntaxKind.CLASS_LITERAL, children);
                    continue;
                }
                children.add(identifier());
                if (at("(")) {
                    children.add(parseArguments());
                    current = branch(SyntaxKind.METHOD_INVOCATION, children);
                } else {
                    current = branch(SyntaxKind.MEMBER_ACCESS, children);
                }
                continue;
            }
            if (at("[")) {
                List<GreenNode> children = new ArrayList<>();
                children.add(current);
                children.add(advance());
                children.add(parseExpression());
                children.add(expect("]"));
                current = branch(SyntaxKind.ARRAY_ACCESS, children);
                continue;
            }
            if (at("::")) {
                List<GreenNode> children = new ArrayList<>();
                children.add(current);
                children.add(advance());
                if (at("<")) {
                    children.add(parseTypeArguments());
                }
                children.add(at("new") ? advance() : identifier());
                current = branch(SyntaxKind.METHOD_REFERENCE, children);
                continue;
            }
            if (atAny("++", "--")) {
                List<GreenNode> children = new ArrayList<>();
                children.add(current);
                children.add(advance());
                current = branch(SyntaxKind.POSTFIX_EXPRESSION, children);
                continue;
            }
            if (previewFeatures && atContextual("with") && peek(1).is("{")) {
                // Derived record creation: point with { x = 1; }
                List<GreenNode> children = new ArrayList<>();
                children.add(current);
                children.add(advance());
                children.add(parseWithBlock());
                current = branch(SyntaxKind.WITH_EXPRESSION, children);
                continue;
            }
            return current;
        }
    }

    /**
     * The block of a derived record creation, whose contents are component assignments.
     *
     * <p>Each assignment and its semicolon form one {@code EXPRESSION_STATEMENT}, the same shape an
     * ordinary block has. The emitter lays a block out by walking its children as statements, so a
     * loose semicolon sitting beside its expression would be spaced as though it were a statement of
     * its own.
     */
    private GreenNode parseWithBlock() {
        List<GreenNode> children = new ArrayList<>();
        children.add(expect("{"));
        while (!at("}") && !atEnd()) {
            List<GreenNode> assignment = new ArrayList<>();
            assignment.add(parseExpression());
            assignment.add(expect(";"));
            children.add(branch(SyntaxKind.EXPRESSION_STATEMENT, assignment));
        }
        children.add(expect("}"));
        return branch(SyntaxKind.BLOCK, children);
    }

    private GreenNode parsePrimary() {
        Token token = peek();
        return switch (token.kind()) {
            case NUMBER_LITERAL, STRING_LITERAL, CHAR_LITERAL, TEXT_BLOCK ->
                    branch(SyntaxKind.LITERAL, List.of(advance()));
            case IDENTIFIER -> parseIdentifierPrimary();
            case KEYWORD -> parseKeywordPrimary(token);
            case SEPARATOR -> parseSeparatorPrimary(token);
            default -> throw fail("Expected an expression");
        };
    }

    private GreenNode parseIdentifierPrimary() {
        GreenNode name = identifier();
        if (at("(")) {
            List<GreenNode> children = new ArrayList<>();
            children.add(name);
            children.add(parseArguments());
            return branch(SyntaxKind.METHOD_INVOCATION, children);
        }
        return branch(SyntaxKind.NAME, List.of(name));
    }

    private GreenNode parseKeywordPrimary(Token token) {
        if (LITERAL_KEYWORDS.contains(token.text())) {
            return branch(SyntaxKind.LITERAL, List.of(advance()));
        }
        if (token.is("this")) {
            GreenNode keyword = advance();
            if (at("(")) {
                return branch(SyntaxKind.METHOD_INVOCATION, List.of(keyword, parseArguments()));
            }
            return branch(SyntaxKind.THIS_EXPRESSION, List.of(keyword));
        }
        if (token.is("super")) {
            GreenNode keyword = advance();
            if (at("(")) {
                return branch(SyntaxKind.METHOD_INVOCATION, List.of(keyword, parseArguments()));
            }
            return branch(SyntaxKind.SUPER_EXPRESSION, List.of(keyword));
        }
        if (token.is("new")) {
            return parseObjectCreation();
        }
        if (token.is("switch")) {
            return parseSwitchExpression();
        }
        if (PRIMITIVE_TYPES.contains(token.text())) {
            // int.class, int[].class
            GreenNode type = parseType();
            List<GreenNode> children = new ArrayList<>();
            children.add(type);
            children.add(expect("."));
            children.add(expect("class"));
            return branch(SyntaxKind.CLASS_LITERAL, children);
        }
        throw fail("Expected an expression");
    }

    private GreenNode parseSeparatorPrimary(Token token) {
        if (token.is("(")) {
            List<GreenNode> children = new ArrayList<>();
            children.add(advance());
            children.add(parseExpression());
            children.add(expect(")"));
            return branch(SyntaxKind.PARENTHESIZED_EXPRESSION, children);
        }
        if (token.is("{")) {
            return parseArrayInitializer();
        }
        if (token.is("@")) {
            return parseAnnotation();
        }
        throw fail("Expected an expression");
    }

    protected GreenNode parseArguments() {
        List<GreenNode> children = new ArrayList<>();
        children.add(expect("("));
        if (!at(")")) {
            children.add(parseExpression());
            while (at(",")) {
                children.add(advance());
                children.add(parseExpression());
            }
        }
        children.add(expect(")"));
        return branch(SyntaxKind.ARGUMENTS, children);
    }

    protected GreenNode parseArrayInitializer() {
        List<GreenNode> children = new ArrayList<>();
        children.add(expect("{"));
        while (!at("}") && !atEnd()) {
            children.add(at("{") ? parseArrayInitializer() : parseExpression());
            if (at(",")) {
                children.add(advance());
                continue;
            }
            break;
        }
        children.add(expect("}"));
        return branch(SyntaxKind.ARRAY_INITIALIZER, children);
    }

    private GreenNode parseObjectCreation() {
        List<GreenNode> children = new ArrayList<>();
        children.add(expect("new"));
        if (at("<")) {
            children.add(parseTypeArguments());
        }
        GreenNode type = atPrimitiveType() ? advance() : parseTypeNameForCreation();
        children.add(type);
        if (at("[")) {
            while (at("[")) {
                List<GreenNode> dimension = new ArrayList<>();
                dimension.add(advance());
                if (!at("]")) {
                    dimension.add(parseExpression());
                }
                dimension.add(expect("]"));
                children.add(branch(SyntaxKind.DIMENSION, dimension));
            }
            if (at("{")) {
                children.add(parseArrayInitializer());
            }
            return branch(SyntaxKind.ARRAY_CREATION, children);
        }
        children.add(parseArguments());
        if (at("{")) {
            children.add(parseAnonymousClassBody());
        }
        return branch(SyntaxKind.OBJECT_CREATION, children);
    }

    /** The type of a creation expression, where {@code [} starts a dimension rather than an array type. */
    private GreenNode parseTypeNameForCreation() {
        List<GreenNode> children = new ArrayList<>();
        children.add(parseTypeName());
        return branch(SyntaxKind.CLASS_TYPE, children);
    }

    /** Implemented by the declaration layer, which owns class bodies. */
    protected abstract GreenNode parseAnonymousClassBody();

    // ------------------------------------------------------------- lambdas

    protected boolean atLambda() {
        if (atName() && peek(1).is("->")) {
            return true;
        }
        if (!at("(")) {
            return false;
        }
        int depth = 0;
        for (int ahead = 0; ahead < 512; ahead++) {
            Token token = peek(ahead);
            if (token.kind() == TokenKind.END_OF_FILE) {
                return false;
            }
            if (token.is("(")) {
                depth++;
            } else if (token.is(")")) {
                depth--;
                if (depth == 0) {
                    return peek(ahead + 1).is("->");
                }
            }
        }
        return false;
    }

    private GreenNode parseLambda() {
        List<GreenNode> children = new ArrayList<>();
        children.add(parseLambdaParameters());
        children.add(expect("->"));
        children.add(at("{") ? parseLambdaBody() : parseExpression());
        return branch(SyntaxKind.LAMBDA_EXPRESSION, children);
    }

    /** Implemented by the statement layer, which owns blocks. */
    protected abstract GreenNode parseLambdaBody();

    private GreenNode parseLambdaParameters() {
        List<GreenNode> children = new ArrayList<>();
        if (atName()) {
            children.add(name());
            return branch(SyntaxKind.LAMBDA_PARAMETERS, children);
        }
        children.add(expect("("));
        if (!at(")")) {
            children.add(parseLambdaParameter());
            while (at(",")) {
                children.add(advance());
                children.add(parseLambdaParameter());
            }
        }
        children.add(expect(")"));
        return branch(SyntaxKind.LAMBDA_PARAMETERS, children);
    }

    private GreenNode parseLambdaParameter() {
        // Either a bare name, or a typed parameter with optional final and annotations.
        if (atName() && (peek(1).is(",") || peek(1).is(")"))) {
            return branch(SyntaxKind.PARAMETER, List.of(name()));
        }
        List<GreenNode> children = new ArrayList<>();
        while (at("final") || at("@")) {
            children.add(at("@") ? parseAnnotation() : advance());
        }
        children.add(parseType());
        children.add(name());
        return branch(SyntaxKind.PARAMETER, children);
    }

    // ------------------------------------------------------------ patterns

    /** A type pattern, record pattern, {@code var} pattern, or unnamed pattern {@code _}. */
    protected GreenNode parsePattern() {
        if (atUnnamed()) {
            return branch(SyntaxKind.TYPE_PATTERN, List.of(advance()));
        }
        List<GreenNode> children = new ArrayList<>();
        while (at("final") || at("@")) {
            children.add(at("@") ? parseAnnotation() : advance());
        }
        children.add(parseType());
        if (at("(")) {
            List<GreenNode> components = new ArrayList<>();
            components.add(advance());
            if (!at(")")) {
                components.add(parsePattern());
                while (at(",")) {
                    components.add(advance());
                    components.add(parsePattern());
                }
            }
            components.add(expect(")"));
            children.add(branch(SyntaxKind.PATTERN_COMPONENTS, components));
            return branch(SyntaxKind.RECORD_PATTERN, children);
        }
        if (atName()) {
            children.add(name());
        }
        return branch(SyntaxKind.TYPE_PATTERN, children);
    }

    /** Whether a case label at the cursor is a pattern rather than a constant expression. */
    protected boolean atPatternLabel() {
        int start = mark();
        try {
            if (atUnnamed() || at("final")) {
                return true;
            }
            if (!atTypeStart()) {
                return false;
            }
            parseType();
            return at("(") || atName();
        } catch (ParseFailure failure) {
            return false;
        } finally {
            reset(start);
        }
    }

    // ------------------------------------------------------------- switches

    protected GreenNode parseSwitchExpression() {
        List<GreenNode> children = new ArrayList<>();
        children.add(expect("switch"));
        children.add(expect("("));
        children.add(parseExpression());
        children.add(expect(")"));
        children.add(parseSwitchBlock());
        return branch(SyntaxKind.SWITCH_EXPRESSION, children);
    }

    /** Implemented by the statement layer: case bodies are statements. */
    protected abstract GreenNode parseSwitchBlock();

}
