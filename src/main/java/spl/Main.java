package spl;

import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.List;

import spl.lexer.Lexer;
import spl.lexer.LexicalException;
import spl.lexer.Token;
import spl.parser.SLRDriver;
import spl.parser.SLRGenerator;
import spl.parser.SLRTables;
import spl.parser.SyntaxError;
import spl.tree.TreeBuilder;
import spl.tree.TreeNode;

public class Main {
    public static void main(String[] args) {
        try {
            String sourceCode = Files.readString(Paths.get("input/sample.spl"));
            System.out.println("=== Source Code ===");
            System.out.println(sourceCode);
            System.out.println();

            System.out.println("=== Lexical Analysis ===");
            Lexer lexer = new Lexer();
            List<Token> tokens = lexer.tokenize(sourceCode);
            System.out.println("✓ Tokenization successful. Tokens: " + tokens.size());
            for (Token token : tokens) {
                System.out.println("  " + token.getType() + ": " + token.getLexeme());
            }
            System.out.println();
            System.out.println("=== Syntax Analysis ===");
            SLRTables tables = new SLRGenerator().generateSLRTables();
            SLRDriver parser = new SLRDriver(tables);
            TreeNode parseTree = parser.parse(tokens);
            System.out.println("✓ Parsing successful. Parse tree generated.");
            System.out.println();

            System.out.println("=== XML Output ===");
            TreeBuilder treeBuilder = new TreeBuilder();
            treeBuilder.setRoot(parseTree);
            treeBuilder.buildTree("output/tree.xml");
            System.out.println("✓ XML tree written to output/tree.xml");
            System.out.println();

            System.out.println("=== Compilation Successful ===");

        } catch (LexicalException e) {
            System.err.println("✗ Lexical Error: " + e.getMessage());
            e.printStackTrace();
        } catch (SyntaxError e) {
            System.err.println("✗ Syntax Error: " + e.getMessage());
            e.printStackTrace();
        } catch (Exception e) {
            System.err.println("✗ Error: " + e.getMessage());
            e.printStackTrace();
        }
    }
}
