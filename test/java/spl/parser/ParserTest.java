package spl.parser;

import java.util.List;
import spl.lexer.Lexer;
import spl.lexer.Token;
import spl.tree.TreeNode;

/** Dependency-free lexer-to-parser integration tests. */
public final class ParserTest {
    private ParserTest() {
    }

    public static void main(String[] args) throws Exception {
        acceptsEmptyProgram();
        acceptsPrintProgram();
        acceptsAssignmentProgram();
        acceptsVoidFunctionProgram();
        rejectsMissingDeclarationSeparator();
        rejectsMissingInstructionTerminator();
        System.out.println("Parser tests passed.");
    }

    private static void acceptsEmptyProgram() throws Exception {
        assertRoot(": : ");
    }

    private static void acceptsPrintProgram() throws Exception {
        assertRoot(": : print \"hello\" ; ");
    }

    private static void acceptsAssignmentProgram() throws Exception {
        assertRoot(": : #x_ = 0 ; ");
    }

    private static void acceptsVoidFunctionProgram() throws Exception {
        assertRoot(": void #f_ ( ) { : : return } : ");
    }

    private static void rejectsMissingDeclarationSeparator() throws Exception {
        assertSyntaxError(": print \"hello\" ; ");
    }

    private static void rejectsMissingInstructionTerminator() throws Exception {
        assertSyntaxError(": : print \"hello\" ");
    }

    private static void assertRoot(String source) throws Exception {
        List<Token> tokens = new Lexer().tokenize(source);
        TreeNode root = new Parser().parse(tokens);
        assert root != null : "parser returned a null tree";
        assert "SPL_PROG".equals(root.getValue())
                : "expected parser root to be SPL_PROG, got " + root.getValue();
    }

    private static void assertSyntaxError(String source) throws Exception {
        try {
            List<Token> tokens = new Lexer().tokenize(source);
            new Parser().parse(tokens);
            throw new AssertionError("expected a syntax error for: " + source);
        } catch (SyntaxError expected) {
            assert expected.getMessage().contains("Syntax error")
                    : "unexpected syntax-error message: " + expected.getMessage();
        }
    }
}
