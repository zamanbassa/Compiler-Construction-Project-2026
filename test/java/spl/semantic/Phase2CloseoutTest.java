package spl.semantic;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.TimeUnit;
import spl.lexer.Lexer;
import spl.parser.Parser;
import spl.tree.TreeNode;

/** Specification regressions and child-process CLI diagnostics; no repository outputs. */
public final class Phase2CloseoutTest {
    public static void main(String[] args) throws Exception {
        rejectName(": void #f ( ) { : : #f ( ) ; return } : ");
        rejectName(": void #f ( ) { : : #g ( ) ; return } void #g ( ) { : : #f ( ) ; return } : ");
        rejectName(": void #f ( ) { : : #g ( ) ; return } void #g ( ) { : : return } : ");
        rejectName(": void #f ( ) { #x : : return } void #g ( ) { : : print ( #x ) ; return } : ");
        analyze(": void #f ( ) { : void #g ( ) { : : return } : #g ( ) ; return } : #f ( ) ; ");
        analyze("#x : void #f ( ) { : : print ( #x ) ; return } : #f ( ) ; ");
        // All functions directly in a program's F_DECL are callable from that program's ALGO.
        analyze(": void #f ( ) { : : return } void #g ( ) { : : return } : #f ( ) ; #g ( ) ; ");
        String both = "#x : : #x = mod ( 6 2 ) ; #x = div ( 6 2 ) ; ";
        String decimal = "#x : : #x = mod ( 6 2 ) ; print ( 0.5 ) ; ";
        rejectConflict(both);
        rejectConflict(decimal);
        analyze("#x : : #x = mod ( 7 2 ) ; ");
        analyze("#x : : #x = div ( 7 2 ) ; ");
        analyze(": : print ( 0.5 ) ; ");
        TreeNode tree = analyze("#x : void #f ( ) { : : return } num #n ( ) { : : return ( 1 ) } : #x = #n ( ) ; #f ( ) ; print ( #x ) ; print \"ok\" ; ");
        checkNames(tree);
        cli(": : #x = add ( 6 7 ) ; ", 1, "Semantic Analysis - Declaration Resolution", "undefined variable");
        cli("#x : : #x = add ( 6 7 ) ; ", 0, "Compilation Successful", "");
        cli(both, 1, "Semantic Analysis - Type Checking", FloatIntegerConflictException.MESSAGE);
        cli(decimal, 1, "Semantic Analysis - Type Checking", FloatIntegerConflictException.MESSAGE);
        cli(": void #f ( ) { : : #f ( ) ; return } : ", 1, "Semantic Analysis - Declaration Resolution", "undefined function");
        cli(": void #f ( ) { : : return } : print ( #f ( ) ) ; ", 1, "Semantic Analysis - Type Checking", "expected NUMERIC");
        System.out.println("Phase 2 closeout tests passed.");
    }

    private static TreeNode analyze(String source) throws Exception {
        TreeNode tree = new Parser().parse(new Lexer().tokenize(source));
        Phase2aResult result = new DeclarationResolver().resolveWithResult(tree);
        check(new TypeAnalyzer(result.getSymbolTable()).analyze(tree) == SemanticType.OK, "program type");
        return tree;
    }

    private static void rejectName(String source) throws Exception {
        try { analyze(source); }
        catch (NameResolutionException expected) { return; }
        throw new AssertionError("expected name-resolution failure");
    }

    private static void rejectConflict(String source) throws Exception {
        try { analyze(source); }
        catch (FloatIntegerConflictException expected) {
            check(FloatIntegerConflictException.MESSAGE.equals(expected.getMessage()), "exact conflict message");
            return;
        }
        throw new AssertionError("expected numeric conflict from TypeAnalyzer");
    }

    private static void checkNames(TreeNode node) {
        if (node.getResolvedSymbol() != null) {
            check(node.getType() == node.getResolvedSymbol().getType(), "name annotation: " + node.getValue());
        }
        for (TreeNode child : node.getChildren()) checkNames(child);
    }

    private static void cli(String source, int code, String phase, String message) throws Exception {
        Path directory = Files.createTempDirectory("spl-closeout-");
        Path input = directory.resolve("input.spl");
        Path output = directory.resolve("tree.xml");
        Path log = directory.resolve("process.log");
        Process process = null;
        try {
            Files.writeString(input, source);
            String executable = System.getProperty("os.name").startsWith("Windows") ? "java.exe" : "java";
            String java = Path.of(System.getProperty("java.home"), "bin", executable).toString();
            process = new ProcessBuilder(java, "-cp", System.getProperty("java.class.path"),
                    "spl.Main", input.toString(), output.toString())
                    .redirectErrorStream(true).redirectOutput(log.toFile()).start();
            check(process.waitFor(30, TimeUnit.SECONDS), "CLI timeout");
            String text = Files.readString(log);
            check(process.exitValue() == code, "exit code: " + text);
            check(text.contains(phase) && text.contains(message), "diagnostic: " + text);
            check(!text.contains("Internal Compiler Error"), "expected error mislabeled internal");
            check(Files.exists(output) == (code == 0), "output presence");
        } finally {
            if (process != null && process.isAlive()) {
                process.destroyForcibly();
                process.waitFor(5, TimeUnit.SECONDS);
            }
            Files.deleteIfExists(input);
            Files.deleteIfExists(output);
            Files.deleteIfExists(log);
            Files.deleteIfExists(directory);
        }
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
