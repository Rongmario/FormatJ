package zone.rong.formatj.core.parser;

import zone.rong.formatj.api.Diagnostic;
import zone.rong.formatj.api.LanguageLevel;
import zone.rong.formatj.core.cst.GreenNode;
import zone.rong.formatj.core.cst.SyntaxKind;
import zone.rong.formatj.core.cst.SyntaxNode;
import zone.rong.formatj.core.lexer.JavaLexer;
import zone.rong.formatj.core.lexer.Token;
import zone.rong.formatj.core.lexer.TokenKind;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Recursive-descent parser for Java source, producing a lossless concrete syntax tree.
 *
 * <p>Error tolerant by design: a construct that does not parse becomes an unparsed region, which the
 * emitter reproduces verbatim. A formatter that threw on a half-typed file would be useless in an
 * editor, and one that guessed would be dangerous, so it does neither.
 */
public final class JavaParser extends StatementParser {

    private static final Set<String> MODIFIER_KEYWORDS =
            Set.of(
                    "public",
                    "protected",
                    "private",
                    "static",
                    "final",
                    "abstract",
                    "native",
                    "synchronized",
                    "transient",
                    "volatile",
                    "strictfp",
                    "default");

    private JavaParser(List<Token> tokens, LanguageLevel languageLevel, boolean previewFeatures) {
        super(tokens, languageLevel, previewFeatures);
    }

    /** Parses source text into a lossless tree. */
    public static ParseResult parse(String source, LanguageLevel languageLevel, boolean previewFeatures) {
        return parse(JavaLexer.tokenize(source), languageLevel, previewFeatures);
    }

    /** Parses an already-lexed token stream into a lossless tree. */
    public static ParseResult parse(List<Token> tokens, LanguageLevel languageLevel, boolean previewFeatures) {
        JavaParser parser = new JavaParser(tokens, languageLevel, previewFeatures);
        return parser.parseCompilationUnit();
    }

    private ParseResult parseCompilationUnit() {
        List<GreenNode> children = new ArrayList<>();
        boolean complete = true;

        while (!atEnd()) {
            int start = mark();
            try {
                children.add(parseTopLevelDeclaration());
            } catch (ParseFailure failure) {
                reset(start);
                skipToRecoveryPoint();
                if (mark() == start) {
                    advance();
                }
                report(
                        Diagnostic.warning(
                                failure.getMessage() + "; declaration left unformatted",
                                failure.token().line(),
                                failure.token().column()));
                children.add(unparsedFrom(start));
                complete = false;
            }
        }
        // The end-of-file token carries the file's trailing trivia, so it must be consumed.
        children.add(advance());

        GreenNode unit = branch(SyntaxKind.COMPILATION_UNIT, children);
        return new ParseResult(SyntaxNode.root(unit), diagnostics(), complete);
    }

    private GreenNode parseTopLevelDeclaration() {
        if (at("package")) {
            return parsePackageDeclaration(new ArrayList<>());
        }
        if (at("import")) {
            return parseImportDeclaration();
        }
        if (at(";")) {
            return branch(SyntaxKind.EMPTY_STATEMENT, List.of(advance()));
        }
        List<GreenNode> modifiers = parseModifierList();
        if (at("package")) {
            return parsePackageDeclaration(modifiers);
        }
        return parseTypeDeclaration(modifiers);
    }

    private GreenNode parsePackageDeclaration(List<GreenNode> modifiers) {
        List<GreenNode> children = new ArrayList<>();
        if (!modifiers.isEmpty()) {
            children.add(branch(SyntaxKind.MODIFIERS, modifiers));
        }
        children.add(expect("package"));
        children.add(parseQualifiedName());
        children.add(expect(";"));
        return branch(SyntaxKind.PACKAGE_DECLARATION, children);
    }

    private GreenNode parseImportDeclaration() {
        List<GreenNode> children = new ArrayList<>();
        children.add(expect("import"));
        if (at("static")) {
            children.add(advance());
        } else if (atContextual("module")) {
            // Module import declaration: import module java.base;
            children.add(advance());
        }
        children.add(identifier());
        while (at(".")) {
            children.add(advance());
            if (at("*")) {
                children.add(advance());
                break;
            }
            children.add(identifier());
        }
        children.add(expect(";"));
        return branch(SyntaxKind.IMPORT_DECLARATION, children);
    }

    // --------------------------------------------------------- declarations

    private List<GreenNode> parseModifierList() {
        List<GreenNode> modifiers = new ArrayList<>();
        while (true) {
            if (at("@") && !peek(1).is("interface")) {
                modifiers.add(parseAnnotation());
                continue;
            }
            if (peek().kind() == TokenKind.KEYWORD && MODIFIER_KEYWORDS.contains(peek().text())) {
                modifiers.add(advance());
                continue;
            }
            if (atContextual("sealed") && startsTypeDeclarationAfterModifier(1)) {
                modifiers.add(advance());
                continue;
            }
            if (atContextual("non") && peek(1).is("-") && peek(2).is("sealed")) {
                // non-sealed lexes as three tokens; it is one modifier.
                modifiers.add(advance());
                modifiers.add(advance());
                modifiers.add(advance());
                continue;
            }
            return modifiers;
        }
    }

    /** Whether what follows the cursor eventually reaches a type declaration keyword. */
    private boolean startsTypeDeclarationAfterModifier(int ahead) {
        for (int position = ahead; position < ahead + 8; position++) {
            Token token = peek(position);
            if (token.is("class") || token.is("interface") || token.is("enum") || token.is("record")) {
                return true;
            }
            if (token.kind() == TokenKind.KEYWORD && MODIFIER_KEYWORDS.contains(token.text())) {
                continue;
            }
            if (token.kind() == TokenKind.IDENTIFIER && (token.is("sealed") || token.is("non") || token.is("record"))) {
                continue;
            }
            if (token.is("-") || token.is("@")) {
                continue;
            }
            return false;
        }
        return false;
    }

    @Override
    protected GreenNode parseLocalTypeDeclaration() {
        return parseTypeDeclaration(parseModifierList());
    }

    private GreenNode parseTypeDeclaration(List<GreenNode> modifiers) {
        if (at("class")) {
            return parseClassLike(modifiers, SyntaxKind.CLASS_DECLARATION);
        }
        if (at("interface")) {
            return parseClassLike(modifiers, SyntaxKind.INTERFACE_DECLARATION);
        }
        if (at("@") && peek(1).is("interface")) {
            return parseAnnotationTypeDeclaration(modifiers);
        }
        if (at("enum")) {
            return parseEnumDeclaration(modifiers);
        }
        if (atContextual("record") && peek(1).kind() == TokenKind.IDENTIFIER) {
            return parseRecordDeclaration(modifiers);
        }
        throw fail("Expected a type declaration");
    }

    private GreenNode parseClassLike(List<GreenNode> modifiers, SyntaxKind kind) {
        List<GreenNode> children = withModifiers(modifiers);
        children.add(advance());
        GreenNode name = identifier();
        children.add(name);
        if (at("<")) {
            children.add(parseTypeParameters());
        }
        if (at("extends")) {
            children.add(parseTypeList(SyntaxKind.EXTENDS_CLAUSE));
        }
        if (at("implements")) {
            children.add(parseTypeList(SyntaxKind.IMPLEMENTS_CLAUSE));
        }
        if (atContextual("permits")) {
            children.add(parseTypeList(SyntaxKind.PERMITS_CLAUSE));
        }
        children.add(parseClassBody(nameOf(name), false));
        return branch(kind, children);
    }

    private GreenNode parseRecordDeclaration(List<GreenNode> modifiers) {
        List<GreenNode> children = withModifiers(modifiers);
        children.add(advance());
        GreenNode name = identifier();
        children.add(name);
        if (at("<")) {
            children.add(parseTypeParameters());
        }
        children.add(parseRecordHeader());
        if (at("implements")) {
            children.add(parseTypeList(SyntaxKind.IMPLEMENTS_CLAUSE));
        }
        children.add(parseClassBody(nameOf(name), true));
        return branch(SyntaxKind.RECORD_DECLARATION, children);
    }

    private GreenNode parseRecordHeader() {
        List<GreenNode> children = new ArrayList<>();
        children.add(expect("("));
        if (!at(")")) {
            children.add(parseRecordComponent());
            while (at(",")) {
                children.add(advance());
                children.add(parseRecordComponent());
            }
        }
        children.add(expect(")"));
        return branch(SyntaxKind.RECORD_HEADER, children);
    }

    private GreenNode parseRecordComponent() {
        List<GreenNode> children = new ArrayList<>();
        while (at("@")) {
            children.add(parseAnnotation());
        }
        children.add(parseType());
        children.add(identifier());
        return branch(SyntaxKind.RECORD_COMPONENT, children);
    }

    private GreenNode parseEnumDeclaration(List<GreenNode> modifiers) {
        List<GreenNode> children = withModifiers(modifiers);
        children.add(advance());
        GreenNode name = identifier();
        children.add(name);
        if (at("implements")) {
            children.add(parseTypeList(SyntaxKind.IMPLEMENTS_CLAUSE));
        }
        children.add(parseEnumBody(nameOf(name)));
        return branch(SyntaxKind.ENUM_DECLARATION, children);
    }

    private GreenNode parseEnumBody(String enclosingName) {
        List<GreenNode> children = new ArrayList<>();
        children.add(expect("{"));
        List<GreenNode> constants = new ArrayList<>();
        if (!at(";") && !at("}")) {
            constants.add(parseEnumConstant(enclosingName));
            while (at(",")) {
                constants.add(advance());
                if (at(";") || at("}")) {
                    break;
                }
                constants.add(parseEnumConstant(enclosingName));
            }
        }
        boolean bodyDeclarations = false;
        if (at(";")) {
            // The terminator belongs to the constant list so the emitter can glue it to the last
            // constant instead of treating it as a body member that always starts a new line.
            constants.add(advance());
            bodyDeclarations = true;
        }
        if (!constants.isEmpty()) {
            children.add(branch(SyntaxKind.ENUM_CONSTANTS, constants));
        }
        if (bodyDeclarations) {
            while (!at("}") && !atEnd()) {
                children.add(parseMemberWithRecovery(enclosingName, false));
            }
        }
        children.add(expect("}"));
        return branch(SyntaxKind.CLASS_BODY, children);
    }

    private GreenNode parseEnumConstant(String enclosingName) {
        List<GreenNode> children = new ArrayList<>();
        while (at("@")) {
            children.add(parseAnnotation());
        }
        children.add(identifier());
        if (at("(")) {
            children.add(parseArguments());
        }
        if (at("{")) {
            children.add(parseClassBody(enclosingName, false));
        }
        return branch(SyntaxKind.ENUM_CONSTANT, children);
    }

    private GreenNode parseAnnotationTypeDeclaration(List<GreenNode> modifiers) {
        List<GreenNode> children = withModifiers(modifiers);
        children.add(expect("@"));
        children.add(expect("interface"));
        GreenNode name = identifier();
        children.add(name);
        children.add(parseClassBody(nameOf(name), false));
        return branch(SyntaxKind.ANNOTATION_TYPE_DECLARATION, children);
    }

    private GreenNode parseTypeList(SyntaxKind kind) {
        List<GreenNode> children = new ArrayList<>();
        children.add(advance());
        children.add(parseType());
        while (at(",")) {
            children.add(advance());
            children.add(parseType());
        }
        return branch(kind, children);
    }

    // -------------------------------------------------------------- bodies

    @Override
    protected GreenNode parseAnonymousClassBody() {
        return parseClassBody("", false);
    }

    private GreenNode parseClassBody(String enclosingName, boolean isRecord) {
        List<GreenNode> children = new ArrayList<>();
        children.add(expect("{"));
        while (!at("}") && !atEnd()) {
            children.add(parseMemberWithRecovery(enclosingName, isRecord));
        }
        children.add(expect("}"));
        return branch(SyntaxKind.CLASS_BODY, children);
    }

    private GreenNode parseMemberWithRecovery(String enclosingName, boolean isRecord) {
        int start = mark();
        try {
            return parseMember(enclosingName, isRecord);
        } catch (ParseFailure failure) {
            reset(start);
            skipToRecoveryPoint();
            if (mark() == start) {
                advance();
            }
            report(
                    Diagnostic.warning(
                            failure.getMessage() + "; member left unformatted",
                            failure.token().line(),
                            failure.token().column()));
            return unparsedFrom(start);
        }
    }

    private GreenNode parseMember(String enclosingName, boolean isRecord) {
        if (at(";")) {
            return branch(SyntaxKind.EMPTY_STATEMENT, List.of(advance()));
        }
        List<GreenNode> modifiers = parseModifierList();

        if (at("{")) {
            List<GreenNode> children = withModifiers(modifiers);
            children.add(parseBlock());
            return branch(SyntaxKind.INITIALIZER_BLOCK, children);
        }
        if (at("class")
                || at("interface")
                || at("enum")
                || (at("@") && peek(1).is("interface"))
                || (atContextual("record") && peek(1).kind() == TokenKind.IDENTIFIER)) {
            return parseTypeDeclaration(modifiers);
        }
        if (atIdentifier() && peek().is(enclosingName) && peek(1).is("(")) {
            return parseConstructor(modifiers, false);
        }
        if (isRecord && atIdentifier() && peek().is(enclosingName) && peek(1).is("{")) {
            return parseConstructor(modifiers, true);
        }

        List<GreenNode> children = withModifiers(modifiers);
        if (at("<")) {
            children.add(parseTypeParameters());
        }
        children.add(parseType());
        GreenNode name = identifier();
        children.add(name);

        if (at("(")) {
            children.add(parseParameters());
            while (at("[") && peek(1).is("]")) {
                children.add(advance());
                children.add(advance());
            }
            if (at("throws")) {
                children.add(parseTypeList(SyntaxKind.THROWS_CLAUSE));
            }
            if (at("default")) {
                List<GreenNode> defaultValue = new ArrayList<>();
                defaultValue.add(advance());
                defaultValue.add(at("{") ? parseArrayInitializer() : parseExpression());
                children.add(branch(SyntaxKind.DEFAULT_VALUE, defaultValue));
                children.add(expect(";"));
                return branch(SyntaxKind.ANNOTATION_ELEMENT_DECLARATION, children);
            }
            if (at("{")) {
                children.add(parseBlock());
            } else {
                children.add(expect(";"));
            }
            return branch(SyntaxKind.METHOD_DECLARATION, children);
        }

        children.add(finishVariableDeclarator(name, children));
        while (at(",")) {
            children.add(advance());
            children.add(parseVariableDeclarator());
        }
        children.add(expect(";"));
        return branch(SyntaxKind.FIELD_DECLARATION, children);
    }

    /**
     * Turns the already-consumed name into the first declarator of a field declaration.
     *
     * @param children the member's children, from which the name is removed and re-wrapped
     */
    private GreenNode finishVariableDeclarator(GreenNode name, List<GreenNode> children) {
        children.remove(children.size() - 1);
        List<GreenNode> declarator = new ArrayList<>();
        declarator.add(name);
        while (at("[") && peek(1).is("]")) {
            declarator.add(advance());
            declarator.add(advance());
        }
        if (at("=")) {
            declarator.add(advance());
            declarator.add(at("{") ? parseArrayInitializer() : parseExpression());
        }
        return branch(SyntaxKind.VARIABLE_DECLARATOR, declarator);
    }

    private GreenNode parseConstructor(List<GreenNode> modifiers, boolean compact) {
        List<GreenNode> children = withModifiers(modifiers);
        children.add(identifier());
        if (compact) {
            children.add(parseBlock());
            return branch(SyntaxKind.COMPACT_CONSTRUCTOR_DECLARATION, children);
        }
        children.add(parseParameters());
        if (at("throws")) {
            children.add(parseTypeList(SyntaxKind.THROWS_CLAUSE));
        }
        children.add(parseBlock());
        return branch(SyntaxKind.CONSTRUCTOR_DECLARATION, children);
    }

    private GreenNode parseParameters() {
        List<GreenNode> children = new ArrayList<>();
        children.add(expect("("));
        if (!at(")")) {
            children.add(parseParameter());
            while (at(",")) {
                children.add(advance());
                children.add(parseParameter());
            }
        }
        children.add(expect(")"));
        return branch(SyntaxKind.PARAMETERS, children);
    }

    private GreenNode parseParameter() {
        List<GreenNode> children = new ArrayList<>();
        while (at("final") || at("@")) {
            children.add(at("@") ? parseAnnotation() : advance());
        }
        children.add(parseType());
        if (at("this")) {
            // A receiver parameter has no name of its own.
            children.add(advance());
            return branch(SyntaxKind.PARAMETER, children);
        }
        children.add(identifier());
        while (at("[") && peek(1).is("]")) {
            children.add(advance());
            children.add(advance());
        }
        return branch(SyntaxKind.PARAMETER, children);
    }

    private static List<GreenNode> withModifiers(List<GreenNode> modifiers) {
        List<GreenNode> children = new ArrayList<>();
        if (!modifiers.isEmpty()) {
            children.add(branch(SyntaxKind.MODIFIERS, modifiers));
        }
        return children;
    }

    private static String nameOf(GreenNode name) {
        return name instanceof GreenNode.Leaf leaf ? leaf.lexeme() : "";
    }

}
