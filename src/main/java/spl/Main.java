package spl;

import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.List;
import spl.lexer.Lexer;
import spl.lexer.LexicalException;
import spl.lexer.Token;
import spl.parser.Parser;
import spl.parser.SyntaxError;
import spl.semantic.DeclarationResolver;
import spl.semantic.DuplicateDeclarationException;
import spl.semantic.NameResolutionException;
import spl.semantic.Phase2aResult;
import spl.tree.TreeBuilder;
import spl.tree.TreeNode;

public class Main {
    public static void main(String[] args) {
        try {
            if (args.length > 2) {
                throw new IllegalArgumentException(
                        "Usage: java spl.Main [input-file] [output-xml-file]");
            }

            String inputPath = args.length > 0 ? args[0] : "input/sample.spl";
            String outputPath = args.length > 1 ? args[1] : "output/tree.xml";
            String sourceCode = Files.readString(Paths.get(inputPath));
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
            Parser parser = new Parser();
            TreeNode parseTree = parser.parse(tokens);
            System.out.println("✓ Parsing successful. Parse tree generated.");
            System.out.println();

            System.out.println("=== Semantic Analysis ===");
                Phase2aResult phase2aResult =
                    new DeclarationResolver().resolveWithResult(parseTree);
            System.out.println("✓ Declaration analysis successful.");
                System.out.println("✓ Symbol table ready for Phase 2b: "
                    + phase2aResult.getSymbolTable().getRootScope().getDeclarations().size()
                    + " root declarations.");
            System.out.println();

            System.out.println("=== XML Output ===");
            TreeBuilder treeBuilder = new TreeBuilder();
            treeBuilder.setRoot(phase2aResult.getResolvedTree());
            treeBuilder.buildTree(outputPath);
            System.out.println("✓ XML tree written to " + outputPath);
            System.out.println();

            System.out.println("=== Compilation Successful ===");

        } catch (LexicalException e) {
            System.err.println("✗ Lexical Error: " + e.getMessage());
            e.printStackTrace();
            System.exit(1);
        } catch (SyntaxError e) {
            System.err.println("✗ Syntax Error: " + e.getMessage());
            e.printStackTrace();
            System.exit(1);
        } catch (DuplicateDeclarationException e) {
            System.err.println("✗ Semantic Error: " + e.getMessage());
            e.printStackTrace();
            System.exit(1);
        } catch (NameResolutionException e) {
            System.err.println("✗ Semantic Error: " + e.getMessage());
            e.printStackTrace();
            System.exit(1);
        } catch (Exception e) {
            System.err.println("✗ Error: " + e.getMessage());
            e.printStackTrace();
            System.exit(1);
        }
    }
}
