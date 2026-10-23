package spl;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import spl.codegen.AlgorithmCodeGenerator;
import spl.codegen.CallArgumentBinder;
import spl.codegen.CodeGenerationException;
import spl.codegen.CodeGenerationInput;
import spl.codegen.FunctionCatalogue;
import spl.codegen.FunctionInstance;
import spl.codegen.OutputNames;
import spl.codegen.RootEnvironment;
import spl.codegen.TargetFileOutput;
import spl.lexer.Lexer;
import spl.lexer.LexicalException;
import spl.lexer.Token;
import spl.parser.Parser;
import spl.parser.SyntaxError;
import spl.semantic.DeclarationResolver;
import spl.semantic.DuplicateDeclarationException;
import spl.semantic.FloatIntegerConflictException;
import spl.semantic.NameResolutionException;
import spl.semantic.Phase2aResult;
import spl.semantic.Phase2bResult;
import spl.semantic.SemanticType;
import spl.semantic.TypeAnalysisException;
import spl.semantic.TypeAnalyzer;
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

            if (Boolean.getBoolean("spl.debug.3b4")) {
                debugArgumentBinding(checked);
                System.out.println();
            }

            System.out.println("=== XML Output ===");
            TreeBuilder treeBuilder = new TreeBuilder();
            treeBuilder.setRoot(resolvedTree);
            treeBuilder.buildTree(outputPath);
            System.out.println("XML tree written to " + outputPath);
            System.out.println();

            if (target != null) {
                System.out.println("=== Code Generation: Phase 3 ===");
                target.write(new CodeGenerationInput(checked));
                System.out.println("C++ translation unit written to " + args[2]);
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

    private static void debugArgumentBinding(Phase2bResult checked) {
        CodeGenerationInput input = new CodeGenerationInput(checked);
        FunctionCatalogue catalogue = new FunctionCatalogue(input);
        OutputNames names = new OutputNames(input);
        CallArgumentBinder binder = new CallArgumentBinder();
        RootEnvironment rootEnvironment = new RootEnvironment();
        List<TreeNode> calls = new ArrayList<>();

        TreeNode program = input.getTree();
        if ("SPL_PROG".equals(program.getValue())) {
            if (program.getChildren().size() != 1) {
                throw new CodeGenerationException(program, "SPL_PROG must contain one P");
            }
            program = program.getChildren().get(0);
        }
        input.requireNode(program, "P");
        TreeNode algorithm = null;
        for (TreeNode child : program.getChildren()) {
            if ("ALGO".equals(child.getValue())) {
                algorithm = child;
                break;
            }
        }
        if (algorithm == null) {
            throw new CodeGenerationException(program, "P is missing ALGO");
        }
        collectTopLevelCalls(algorithm, calls);

        System.out.println("=== Phase 3b.4 Argument-Binding Debug (top-level calls only) ===");
        if (calls.isEmpty()) {
            System.out.println("No top-level CALL nodes found in the program ALGO.");
        }
        for (TreeNode call : calls) {
            TreeNode calleeName = call.getChildren().get(0);
            try {
                FunctionCatalogue.Function function =
                        catalogue.requireFunction(calleeName.getResolvedSymbol(), calleeName);
                FunctionInstance instance =
                        new FunctionInstance(function, rootEnvironment, names);
                AlgorithmCodeGenerator caller = new AlgorithmCodeGenerator(input);
                binder.bind(call, catalogue, instance, caller);
                System.out.println("Argument binding succeeded for "
                        + function.getSymbol().getGeneratedName()
                        + " at CALL node " + call.getNodeId() + ".");
                System.out.println("=== BEGIN ARGUMENT-BINDING C++ FRAGMENT ===");
                System.out.print(caller.getEmitter().render());
                System.out.println("=== END ARGUMENT-BINDING C++ FRAGMENT ===");
            } catch (CodeGenerationException failure) {
                System.err.println("Argument binding failed at CALL node "
                        + call.getNodeId() + ": " + failure.getMessage());
            }
        }
        System.out.println("Nested function caller environments are not tested by this "
                + "Main-level debug path; it uses RootEnvironment only.");
        List<TreeNode> nestedCalls = new ArrayList<>();
        for (TreeNode child : program.getChildren()) {
            if ("F_DECL".equals(child.getValue())) {
                collectTopLevelCalls(child, nestedCalls);
            }
        }
        for (TreeNode call : nestedCalls) {
            System.out.println("Skipped nested-function CALL node " + call.getNodeId()
                    + ": Main cannot construct the package-private caller generator "
                    + "with that function's lexical environment.");
        }
    }

    private static void collectTopLevelCalls(TreeNode node, List<TreeNode> calls) {
        if ("CALL".equals(node.getValue())) {
            calls.add(node);
            return;
        }
        for (TreeNode child : node.getChildren()) {
            collectTopLevelCalls(child, calls);
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
