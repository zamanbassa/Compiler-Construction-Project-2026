package spl;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import javax.xml.parsers.DocumentBuilderFactory;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;
import spl.lexer.Lexer;
import spl.lexer.LexicalException;
import spl.lexer.Token;
import spl.lexer.TokenType;
import spl.parser.Parser;
import spl.parser.SyntaxError;
import spl.semantic.DuplicateDeclarationException;
import spl.semantic.Scope;
import spl.semantic.ScopeException;
import spl.semantic.SemanticType;
import spl.semantic.Symbol;
import spl.semantic.SymbolKind;
import spl.semantic.SymbolTable;
import spl.tree.TreeBuilder;
import spl.tree.TreeNode;
import spl.tree.XMLWriter;

/**
 * Broad dependency-free regression test for the current Phase 1 and Phase 2a system.
 *
 * Run from the project root with assertions enabled:
 * java -ea -cp out\\classes spl.ComprehensiveSystemTest
 */
public final class ComprehensiveSystemTest {
    private ComprehensiveSystemTest() {
    }

    public static void main(String[] args) throws Exception {
        testLexerRecognisesEveryKeywordAndTokenFamily();
        testLexerTracksLocationsAcrossNewlines();
        testLexerAcceptsAllSupportedSeparators();
        testLexerRejectsMalformedInput();
        testParserAcceptsBroadPrograms();
        testParserRejectsInvalidProgramsAndArguments();
        testTreeNodeMaintainsParentChildInvariants();
        testXmlWriterSerialisesStructureEscapingAndEmptyChildren();
        testTreeBuilderValidatesItsRoot();
        testMainRunsWithCustomPaths();
        testSymbolTableScopesAndResolution();
        testSymbolTableRejectsInvalidDeclarationsAndScopeOperations();
        System.out.println("Comprehensive system tests passed.");
    }

    private static void testLexerRecognisesEveryKeywordAndTokenFamily() {
        String[] keywords = {
                "void", "num", "return", "print", "nop", "comment", "if", "then", "else",
                "mod", "add", "sub", "mul", "div", "neg", "not", "and", "or", "eq",
                "larger", "lesser", "while", "until", "do"
        };
        String input = String.join(" ", keywords)
                + " #name1 0 -0.5 42 -42 42.11 -42.11 \"abc,.-:?!09\""
                + " : ; ( ) { } = ";

        List<Token> tokens = new Lexer().tokenize(input);
        assert tokens.size() == keywords.length + 1 + 6 + 1 + 7 + 1
                : "unexpected token count: " + tokens.size();

        for (int i = 0; i < keywords.length; i++) {
            assertToken(tokens, i, TokenType.KEYWORD, keywords[i]);
        }
        int index = keywords.length;
        assertToken(tokens, index++, TokenType.IDENTIFIER, "#name1");
        assertToken(tokens, index++, TokenType.NUMBER, "0");
        assertToken(tokens, index++, TokenType.NUMBER, "-0.5");
        assertToken(tokens, index++, TokenType.NUMBER, "42");
        assertToken(tokens, index++, TokenType.NUMBER, "-42");
        assertToken(tokens, index++, TokenType.NUMBER, "42.11");
        assertToken(tokens, index++, TokenType.NUMBER, "-42.11");
        assertToken(tokens, index++, TokenType.STRING, "\"abc,.-:?!09\"");
        assertToken(tokens, index++, TokenType.SYMBOL, ":");
        assertToken(tokens, index++, TokenType.SYMBOL, ";");
        assertToken(tokens, index++, TokenType.SYMBOL, "(");
        assertToken(tokens, index++, TokenType.SYMBOL, ")");
        assertToken(tokens, index++, TokenType.SYMBOL, "{");
        assertToken(tokens, index++, TokenType.SYMBOL, "}");
        assertToken(tokens, index++, TokenType.SYMBOL, "=");
        assertToken(tokens, index, TokenType.EOF, "");
    }

    private static void testLexerTracksLocationsAcrossNewlines() {
        List<Token> tokens = new Lexer().tokenize("print\r\n\"ok\" \n #x ");
        assert tokens.get(0).getLine() == 1;
        assert tokens.get(0).getColumn() == 1;
        assert tokens.get(1).getLine() == 2;
        assert tokens.get(1).getColumn() == 1;
        assert tokens.get(2).getLine() == 3;
        assert tokens.get(2).getColumn() == 2;
        assert tokens.get(3).getLine() == 3;
        assert tokens.get(3).getColumn() == 5;
    }

    private static void testLexerAcceptsAllSupportedSeparators() {
        List<Token> tokens = new Lexer().tokenize("print \r\n ");
        assert tokens.size() == 2;
        assertToken(tokens, 0, TokenType.KEYWORD, "print");
        assertToken(tokens, 1, TokenType.EOF, "");

        List<Token> unixTokens = new Lexer().tokenize("nop\n");
        assertToken(unixTokens, 0, TokenType.KEYWORD, "nop");
        assertToken(unixTokens, 1, TokenType.EOF, "");
    }

    private static void testLexerRejectsMalformedInput() {
        assertLexicalError("print", "must be followed by a blank space");
        assertLexicalError("#x_ ", "must be followed by a blank space");
        assertLexicalError("007 ", "invalid number");
        assertLexicalError("-0 ", "invalid number");
        assertLexicalError("0.0 ", "invalid number");
        assertLexicalError("1.0 ", "invalid number");
        assertLexicalError("\"UPPER\" ", "invalid character inside a string");
        assertLexicalError("\"unterminated", "unterminated string");
        assertLexicalError("wat ", "unknown word");
        assertLexicalError("print\t", "unknown word");
        assertNullPointer(() -> new Lexer().tokenize(null), "input");
    }

    private static void testParserAcceptsBroadPrograms() throws Exception {
        assertParsed(": : ");
        assertParsed(": : print \"hello\" ; nop ; comment \"note\" ; ");
        assertParsed(": : #x = add ( 1 2 ) ; #f ( 1 #x ) ; ");
        assertParsed(": : if eq ( #x 1 ) then { nop ; } else { print \"no\" ; } ; ");
        assertParsed(": : while larger ( #x 0 ) do { #x = sub ( #x 1 ) ; } ; ");
        assertParsed(": : do { nop ; } until lesser ( #x 1 ) ; ");
        assertParsed(": void #f ( #arg ) { : : return } : ");
        assertParsed(": num #double ( #value ) { : : return ( add ( #value #value ) ) } : ");
    }

    private static void testParserRejectsInvalidProgramsAndArguments() {
        assertSyntaxError(": print \"hello\" ; ");
        assertSyntaxError(": : print \"hello\" ");
        assertSyntaxError(": : if eq ( 1 1 ) then { nop ; } ");
        assertSyntaxError(": : #x = add ( 1 ) ; ");

        try {
            new Parser().parse(null);
            throw new AssertionError("expected null parser input to fail");
        } catch (SyntaxError expected) {
            assert expected.getMessage().contains("no tokens");
        }

        try {
            new Parser().parse(List.of());
            throw new AssertionError("expected empty parser input to fail");
        } catch (SyntaxError expected) {
            assert expected.getMessage().contains("no tokens");
        }
    }

    private static void testTreeNodeMaintainsParentChildInvariants() {
        TreeNode.resetIdCounter();
        TreeNode root = new TreeNode("root");
        TreeNode first = new TreeNode("first", true);
        TreeNode second = new TreeNode("second");
        TreeNode grandchild = new TreeNode("grandchild", true);

        root.addChild(first);
        root.addChild(second);
        second.addChild(grandchild);

        assert root.getParent() == null;
        assert first.getParent() == root;
        assert second.getParent() == root;
        assert grandchild.getParent() == second;
        assert root.getChildren().size() == 2;
        assert second.getChildren().size() == 1;
        assert root.getChildrenIds().get(0) == first.getNodeId();
        assert root.getChildrenIds().get(1) == second.getNodeId();
        assert second.getChildrenIds().get(0) == grandchild.getNodeId();
        assert root.getNodeId() == 1;
        assert grandchild.getNodeId() == 4;

        root.addChild(null);
        assert root.getChildren().size() == 2 : "null children must be ignored";
    }

    private static void testXmlWriterSerialisesStructureEscapingAndEmptyChildren() throws Exception {
        TreeNode.resetIdCounter();
        TreeNode root = new TreeNode("root<&>\"'");
        TreeNode terminal = new TreeNode("leaf<&>\"'", true);
        TreeNode emptyNonTerminal = new TreeNode("epsilon");
        root.addChild(terminal);
        root.addChild(emptyNonTerminal);

        Path directory = Files.createTempDirectory("spl-comprehensive-xml-");
        Path output = directory.resolve("nested").resolve("tree.xml");
        try {
            new XMLWriter().write(root, output.toString());
            assert Files.exists(output);
            String rawXml = Files.readString(output);
            assert rawXml.contains("&lt;");
            assert rawXml.contains("&gt;");
            assert rawXml.contains("&amp;");
            assert rawXml.contains("&quot;");
            assert rawXml.contains("&apos;");

            Document document = parseXml(output);
            assert "tree".equals(document.getDocumentElement().getTagName());
            assert document.getElementsByTagName("node").getLength() == 3;
            assert document.getElementsByTagName("children").getLength() == 2;
            assert document.getElementsByTagName("child").getLength() == 2;
            assert document.getElementsByTagName("parent").getLength() == 2;
            assert containsContents(document.getElementsByTagName("contents"), "root<&>\"'");
            assert containsContents(document.getElementsByTagName("contents"), "leaf<&>\"'");
            assert containsContents(document.getElementsByTagName("contents"), "epsilon");

            NodeList childrenElements = document.getElementsByTagName("children");
            boolean foundEmptyChildren = false;
            for (int i = 0; i < childrenElements.getLength(); i++) {
                Element children = (Element) childrenElements.item(i);
                if (children.getElementsByTagName("child").getLength() == 0) {
                    foundEmptyChildren = true;
                }
            }
            assert foundEmptyChildren : "epsilon node should have an empty children element";
        } finally {
            Files.deleteIfExists(output);
            Files.deleteIfExists(output.getParent());
            Files.deleteIfExists(directory);
        }

        assertThrows(IllegalArgumentException.class, () -> new XMLWriter().write(null, "x.xml"));
        assertThrows(IllegalArgumentException.class, () -> new XMLWriter().write(root, ""));
    }

    private static void testTreeBuilderValidatesItsRoot() {
        TreeBuilder builder = new TreeBuilder();
        assertThrows(IllegalStateException.class, builder::buildTree);
        assertThrows(IllegalStateException.class, () -> builder.buildTree("unused.xml"));
    }

    private static void testMainRunsWithCustomPaths() throws Exception {
        Path directory = Files.createTempDirectory("spl-comprehensive-main-");
        Path input = directory.resolve("program.spl");
        Path output = directory.resolve("deep").resolve("result.xml");
        try {
            Files.writeString(input, ": : print \"custom-smoke\" ; #x = mul ( 6 7 ) ; ");
            Main.main(new String[] {input.toString(), output.toString()});

            assert Files.exists(output) : "Main did not create the custom output";
            Document document = parseXml(output);
            assert "tree".equals(document.getDocumentElement().getTagName());
            assert document.getElementsByTagName("node").getLength() > 1;
            assert containsContents(document.getElementsByTagName("contents"), "SPL_PROG");
            assert containsContents(document.getElementsByTagName("contents"), "print");
            assert containsContents(document.getElementsByTagName("contents"), "mul");
        } finally {
            Files.deleteIfExists(output);
            Files.deleteIfExists(output.getParent());
            Files.deleteIfExists(input);
            Files.deleteIfExists(directory);
        }
    }

    private static void testSymbolTableScopesAndResolution() {
        TreeNode.resetIdCounter();
        TreeNode rootNode = new TreeNode("SPL_PROG");
        TreeNode variableNode = new TreeNode("#x", true);
        TreeNode functionNode = new TreeNode("#f", true);
        SymbolTable table = new SymbolTable();
        Scope root = table.getRootScope();

        Symbol variable = table.declare("#x", SymbolKind.VARIABLE, variableNode);
        Symbol function = table.declare("#f", SymbolKind.FUNCTION, "__entry", functionNode);
        assert variable.getOriginalName().equals("#x");
        assert variable.getGeneratedName().equals("__variable0");
        assert variable.getKind() == SymbolKind.VARIABLE;
        assert variable.getDeclaringScope() == root;
        assert variable.getDeclarationNode() == variableNode;
        assert variable.getType() == SemanticType.UNKNOWN;
        assert function.getGeneratedName().equals("__entry");
        assert function.getKind() == SymbolKind.FUNCTION;
        assert root.getDeclarations().size() == 2;
        assert table.lookupLocal("#x").orElseThrow() == variable;
        assert table.resolve("#f").orElseThrow() == function;
        assert table.lookupLocal("#missing").isEmpty();
        assert table.resolve("#missing").isEmpty();

        variable.setType(SemanticType.NUMERIC);
        assert variable.getType() == SemanticType.NUMERIC;

        TreeNode childOwner = new TreeNode("ALGO");
        Scope child = table.enterScope(childOwner);
        assert table.getCurrentScope() == child;
        assert child.getParent() == root;
        assert child.getOwnerNode() == childOwner;
        assert root.getChildScopes().contains(child);
        assert child.getScopeId() != root.getScopeId();
        assert table.lookupLocal("#x").isEmpty();
        assert table.resolve("#x").orElseThrow() == variable;

        Symbol shadow = table.declare("#x", SymbolKind.VARIABLE, new TreeNode("#x", true));
        assert table.lookupLocal("#x").orElseThrow() == shadow;
        assert table.resolve("#x").orElseThrow() == shadow;
        assert !shadow.getGeneratedName().equals(variable.getGeneratedName());

        Scope exited = table.exitScope();
        assert exited == child;
        assert table.getCurrentScope() == root;
        assert table.resolve("#x").orElseThrow() == variable;
    }

    private static void testSymbolTableRejectsInvalidDeclarationsAndScopeOperations() {
        SymbolTable table = new SymbolTable();
        table.declare("#duplicate", SymbolKind.VARIABLE, null);
        assertThrows(DuplicateDeclarationException.class,
                () -> table.declare("#duplicate", SymbolKind.FUNCTION, null));
        assertThrows(NullPointerException.class,
                () -> table.declare("#null-kind", null, null));
        assertThrows(NullPointerException.class,
                () -> new Symbol(null, "__name", SymbolKind.VARIABLE, table.getRootScope(), null));
        assertThrows(IllegalArgumentException.class,
                () -> new Symbol("#name", "", SymbolKind.VARIABLE, table.getRootScope(), null));
        assertThrows(NullPointerException.class, () -> table.lookupLocal(null));
        assertThrows(NullPointerException.class, () -> table.resolve(null));
        assertThrows(ScopeException.class, table::exitScope);

        Scope scope = table.enterScope();
        Symbol foreign = new Symbol("#foreign", "__foreign", SymbolKind.VARIABLE,
                table.getRootScope(), null);
        assertThrows(IllegalArgumentException.class, () -> scope.declare(foreign));
        table.exitScope();
    }

    private static void assertParsed(String source) throws Exception {
        List<Token> tokens = new Lexer().tokenize(source);
        TreeNode root = new Parser().parse(tokens);
        assert root != null;
        assert "SPL_PROG".equals(root.getValue());
        assert root.getChildren().size() == 1 : "parser root should contain the program node";
        assert root.getChildren().get(0).getParent() == root;
    }

    private static void assertSyntaxError(String source) {
        try {
            new Parser().parse(new Lexer().tokenize(source));
            throw new AssertionError("expected syntax error for: " + source);
        } catch (SyntaxError expected) {
            assert expected.getMessage().contains("Syntax error")
                    : "unexpected syntax error message: " + expected.getMessage();
        }
    }

    private static void assertToken(List<Token> tokens, int index, TokenType type, String lexeme) {
        Token token = tokens.get(index);
        assert token.getType() == type
                : "expected " + type + " at index " + index + ", got " + token;
        assert token.getLexeme().equals(lexeme)
                : "expected '" + lexeme + "' at index " + index + ", got '"
                        + token.getLexeme() + "'";
    }

    private static void assertLexicalError(String input, String expectedMessagePart) {
        try {
            new Lexer().tokenize(input);
            throw new AssertionError("expected lexical error for: " + input);
        } catch (LexicalException expected) {
            assert expected.getMessage().contains(expectedMessagePart)
                    : "unexpected lexical error: " + expected.getMessage();
            assert expected.getLine() > 0 : "lexical error should include a line";
            assert expected.getColumn() > 0 : "lexical error should include a column";
        }
    }

    private static Document parseXml(Path path) throws Exception {
        return DocumentBuilderFactory.newInstance()
                .newDocumentBuilder()
                .parse(path.toFile());
    }

    private static boolean containsContents(NodeList nodes, String expected) {
        for (int i = 0; i < nodes.getLength(); i++) {
            if (expected.equals(nodes.item(i).getTextContent())) {
                return true;
            }
        }
        return false;
    }

    private static void assertThrows(Class<? extends Throwable> expectedType, Runnable action) {
        try {
            action.run();
            throw new AssertionError("expected " + expectedType.getSimpleName());
        } catch (Throwable actual) {
            assert expectedType.isInstance(actual)
                    : "expected " + expectedType.getSimpleName() + ", got " + actual;
        }
    }

    private static void assertNullPointer(Runnable action, String expectedMessagePart) {
        try {
            action.run();
            throw new AssertionError("expected NullPointerException");
        } catch (NullPointerException expected) {
            assert expected.getMessage().contains(expectedMessagePart)
                    : "unexpected null message: " + expected.getMessage();
        }
    }
}
