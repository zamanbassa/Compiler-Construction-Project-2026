package spl;

import java.nio.file.Files;
import java.io.IOException;
import java.io.UncheckedIOException;
import spl.codegen.CodeGenerationException;
import spl.codegen.CodeGenerationInput;
import spl.codegen.TargetFileOutput;
import spl.semantic.Phase2bResult;
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
import spl.semantic.TypeAnalyzer;
import spl.semantic.SemanticType;
import spl.semantic.TypeAnalysisException;
import spl.semantic.FloatIntegerConflictException;

import spl.tree.TreeBuilder;
import spl.tree.TreeNode;

public class Main {
    public static void main(String[] args) {
        try {
            if (args.length > 3) {
                throw new IllegalArgumentException(
                        "Usage: java spl.Main [input-file] [output-xml-file] [output-target-file]");
            }

            String inputPath = args.length > 0 ? args[0] : "input/sample.spl";
            String outputPath = args.length > 1 ? args[1] : "output/tree.xml";
            TargetFileOutput target = args.length > 2
                    ? new TargetFileOutput(Paths.get(inputPath), Paths.get(outputPath), Paths.get(args[2])) : null;
            String sourceCode = Files.readString(Paths.get(inputPath));
            System.out.println("=== Source Code ===");
            System.out.println(sourceCode);
            System.out.println();

            System.out.println("=== Lexical Analysis ===");
            Lexer lexer = new Lexer();
            List<Token> tokens = lexer.tokenize(sourceCode);
            System.out.println("Tokenization successful. Tokens: " + tokens.size());
            for (Token token : tokens) {
                System.out.println("  " + token.getType() + ": " + token.getLexeme());
            }
            System.out.println();
            System.out.println("=== Syntax Analysis ===");
            Parser parser = new Parser();
            TreeNode parseTree = parser.parse(tokens);
            System.out.println("Parsing successful. Parse tree generated.");
            System.out.println();

            System.out.println("=== Semantic Analysis ===");
                Phase2aResult phase2aResult =
                    new DeclarationResolver().resolveWithResult(parseTree);
            
            TreeNode resolvedTree = phase2aResult.getResolvedTree();
            
            System.out.println("Declaration analysis successful.");
                System.out.println("Symbol table ready for Phase 2b: "
                    + phase2aResult.getSymbolTable().getRootScope().getDeclarations().size()
                    + " root declarations.");
            System.out.println();

             System.out.println("=== Semantic Analysis: Phase 2b ===");

            TypeAnalyzer typeAnalyzer =
                new TypeAnalyzer(phase2aResult.getSymbolTable());

            Phase2bResult checked = typeAnalyzer.analyzeWithResult(phase2aResult);
            SemanticType programType = checked.getTree().getType();

            if (programType != SemanticType.OK) {
                throw new TypeAnalysisException(
                    "Program type analysis did not return OK: "
                    + programType
                );
            }

            System.out.println("Type analysis successful.");
            System.out.println("Program type: " + programType);

            System.out.println();

            System.out.println("=== XML Output ===");
            TreeBuilder treeBuilder = new TreeBuilder();
            treeBuilder.setRoot(resolvedTree);
            treeBuilder.buildTree(outputPath);
            System.out.println("XML tree written to " + outputPath);
            System.out.println();

            if (target != null) {
                System.out.println("=== Code Generation: Phase 3a ===");
                target.write(new CodeGenerationInput(checked));
                System.out.println("Target fragment written to " + args[2]);
            }
            System.out.println("=== Compilation Successful ===");

        }  catch (LexicalException e) {
            printError("Lexical Analysis", e);

        } catch (SyntaxError e) {
            printError("Syntax Analysis", e);

        } catch (DuplicateDeclarationException e) {
            printError("Semantic Analysis - Declaration Resolution", e);

        } catch (NameResolutionException e) {
            printError("Semantic Analysis - Declaration Resolution", e);

        } catch (FloatIntegerConflictException e) {
            printError("Semantic Analysis - Type Checking", e);

        } catch (TypeAnalysisException e) {
            printError("Semantic Analysis - Type Checking", e);

        } catch (CodeGenerationException e) {
            printError("Code Generation", e);

        } catch (UncheckedIOException e) {
            printError("File I/O", e);

        } catch (IOException e) {
            printError("File I/O", e);

        } catch (IllegalArgumentException e) {
            printError("Command Line", e);

        } catch (Exception e) {
            System.err.println();
            System.err.println("=== Internal Compiler Error ===");
            System.err.println(e.getMessage());
            e.printStackTrace();
            System.exit(2);
        }
    }

    private static void printError(String phase, Exception e) {
        System.err.println();
        System.err.println("=== Compilation Failed ===");
        System.err.println("Phase: " + phase);
        System.err.println("Error: " + e.getMessage());
        System.exit(1);
    }
}
