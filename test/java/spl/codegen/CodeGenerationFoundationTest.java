package spl.codegen;

import java.nio.file.*;
import java.util.*;
import java.util.concurrent.TimeUnit;
import spl.lexer.Lexer;
import spl.parser.Parser;
import spl.semantic.*;
import spl.tree.TreeNode;

public final class CodeGenerationFoundationTest {
    public static void main(String[] args) throws Exception {
        Phase2aResult a = resolve("#x : : #x = 1 ; ");
        Phase2bResult b = new TypeAnalyzer(a.getSymbolTable()).analyzeWithResult(a);
        CodeGenerationInput input = new CodeGenerationInput(b);
        check(input.getTree() == a.getResolvedTree(), "original tree");
        check(input.getSymbolTable() == a.getSymbolTable(), "original table");
        check(b.getNumericMode() == NumericMode.WHOLE, "integer default");
        check(accepted(": : print ( div ( 7 2 ) ) ; ").getNumericMode() == NumericMode.REAL, "div mode");
        check(accepted(": : print ( 0.5 ) ; ").getNumericMode() == NumericMode.REAL, "decimal mode");
        check(accepted(": : print ( mod ( 7 2 ) ) ; ").getNumericMode() == NumericMode.WHOLE, "mod mode");
        fails(() -> new CodeGenerationInput(null, a.getSymbolTable(), NumericMode.WHOLE), "required");
        fails(() -> new CodeGenerationInput(b.getTree(), null, NumericMode.WHOLE), "required");
        fails(() -> new CodeGenerationInput(b.getTree(), b.getSymbolTable(), null), "required");
        fails(() -> new CodeGenerationInput((Phase2bResult)null), "missing");
        fails(() -> new CodeGenerationInput(new TreeNode("P"), a.getSymbolTable(), NumericMode.WHOLE), "accepted");
        CodeGenerationInput real = accepted(": : print ( 0.5 ) ; ");
        fails(() -> new CodeGenerationInput(real.getTree(), real.getSymbolTable(), NumericMode.WHOLE), "contradicts");
        CodeGenerationInput mod = accepted(": : print ( mod ( 7 2 ) ) ; ");
        fails(() -> new CodeGenerationInput(mod.getTree(), mod.getSymbolTable(), NumericMode.REAL), "contradicts");
        TreeNode use = find(b.getTree(), "ASSIGN").getChildren().get(0);
        Symbol binding = use.getResolvedSymbol(); use.setResolvedSymbol(null);
        fails(() -> new CodeGenerationInput(b), "resolved symbol"); use.setResolvedSymbol(binding);
        fails(() -> new CodeGenerationInput(b.getTree(), new SymbolTable(), NumericMode.WHOLE), "resolved symbol");
        CodeGenerationInput call = accepted(": void #f ( ) { : : return } : #f ( ) ; ");
        fails(() -> new AlgorithmCodeGenerator(call).generate(rootAlgo(call)), "Phase 3b");
        AlgorithmCodeGenerator absent = new AlgorithmCodeGenerator(input);
        fails(() -> absent.generate(rootAlgo(input)), "translator not installed");
        fails(() -> absent.generate(new TreeNode("ALGO")), "accepted tree");
        fails(() -> absent.generate(input.getTree()), "expected ALGO");
        // A small test-only adapter proves the public entry point without implementing issue 8.
        CodeGenerationInput empty = accepted(": : ");
        AlgorithmCodeGenerator adapter = new AlgorithmCodeGenerator(empty) {
            @Override public void emitAlgo(TreeNode node) { require(node, "ALGO"); emitter.emitComment("end"); }
        };
        check(adapter.generate(rootAlgo(empty)).equals(adapter.generate(rootAlgo(empty))), "entry reset");
        check(adapter.generate(rootAlgo(empty)).equals("/* end */\n"), "entry output");
        namesAndFormatting(input);
        check(new CodeEmitter(real).newTemporary().equals("spl_tmp_0"), "per-run names");
        CodeEmitter re = new CodeEmitter(real); re.newTemporary(); check(re.render().equals("float spl_tmp_0;\n"), "float type");
        String fragment = nestedFragment(input);
        nativeCheck(fragment);
        System.out.println("Code-generation foundation tests passed.");
    }
    static Phase2aResult resolve(String source) throws Exception {
        return new DeclarationResolver().resolveWithResult(new Parser().parse(new Lexer().tokenize(source)));
    }
    static CodeGenerationInput accepted(String source) throws Exception {
        Phase2aResult a = resolve(source);
        return new CodeGenerationInput(new TypeAnalyzer(a.getSymbolTable()).analyzeWithResult(a));
    }
    static TreeNode find(TreeNode n, String value) {
        if (value.equals(n.getValue())) return n;
        for (TreeNode c : n.getChildren()) { TreeNode found = find(c, value); if (found != null) return found; }
        return null;
    }
    static TreeNode rootAlgo(CodeGenerationInput input) {
        TreeNode p = input.getTree(); if (p.getValue().equals("SPL_PROG")) p = p.getChildren().get(0);
        for (TreeNode c : p.getChildren()) if (c.getValue().equals("ALGO")) return c;
        throw new AssertionError("missing root ALGO");
    }
    static void namesAndFormatting(CodeGenerationInput input) {
        // Reserve colliding names in a descendant scope as well as the root.
        SymbolTable table = new SymbolTable();
        TreeNode tree = new TreeNode("ALGO"); tree.setType(SemanticType.OK);
        table.declare("#a", SymbolKind.VARIABLE, "spl_tmp_0", null);
        table.enterScope(); table.declare("#b", SymbolKind.VARIABLE, "spl_label_0", null); table.exitScope();
        CodeEmitter e = new CodeEmitter(new CodeGenerationInput(tree, table, NumericMode.WHOLE));
        Set<String> names = new HashSet<>();
        for (int i = 0; i < 600; i++) {
            String t = e.newTemporary(), l = e.newLabel();
            check(names.add(t) && names.add(l), "unique names");
            check(!t.equals("spl_tmp_0") && !l.equals("spl_label_0"), "reserved names");
        }
        e.reset(); String t = e.newTemporary(), l = e.newLabel();
        check(t.equals("spl_tmp_1") && l.equals("spl_label_1"), "deterministic reset");
        e.emitStatement(t + " = 1"); e.emitGoto(l); e.emitComment("safe */ text"); e.emitLabel(l);
        String text = e.render();
        check(text.startsWith("int " + t + ";\n"), "declaration first");
        check(text.contains(l + ": ;\n"), "label syntax");
        check(text.contains("/* safe * / text */"), "safe comment");
        fails(() -> e.emitLabel(l), "duplicate");
        fails(() -> e.emitStatement("x = 1;"), "semicolon");
        fails(() -> e.emitGoto("missing"), "allocated");
        CodeEmitter dangling = new CodeEmitter(input); dangling.emitGoto(dangling.newLabel());
        fails(() -> dangling.render(), "undefined");
        CodeEmitter quoted = new CodeEmitter(input); quoted.emitStatement("std::cout << \"a;b\"");
        check(quoted.render().contains("a;b"), "quoted semicolon preserved");
        check(nestedFragment(input).equals(nestedFragment(input)), "independent output determinism");
    }
    static String nestedFragment(CodeGenerationInput input) {
        CodeEmitter e = new CodeEmitter(input);
        String start = e.newLabel(), inner = e.newLabel(), done = e.newLabel();
        String counter = e.newTemporary(); e.emitStatement(counter + " = 0"); e.emitLabel(start);
        e.emitConditionalGoto(counter + " >= 3", done);
        // Allocated after a jump in emission order, but rendered before every jump.
        String value = e.newTemporary(); e.emitStatement(value + " = 2");
        e.emitConditionalGoto(value + " == 2", inner); e.emitGoto(done);
        e.emitLabel(inner); e.emitStatement(counter + " = " + counter + " + 1"); e.emitGoto(start);
        e.emitLabel(done); e.emitStatement("std::cout << " + counter);
        return e.render();
    }
    static void nativeCheck(String fragment) throws Exception {
        Path dir = Files.createTempDirectory("spl-cpp-emitter-");
        Path cpp = dir.resolve("test.cpp"), exe = dir.resolve("test.exe"), log = dir.resolve("log.txt");
        try {
            Files.writeString(cpp, "#include <iostream>\nint main() {\n" + fragment + "return 0;\n}\n");
            boolean available = false;
            List<String> candidates = new ArrayList<>();
            if (System.getenv("CXX") != null) candidates.add(System.getenv("CXX"));
            candidates.add("g++"); candidates.add("clang++");
            for (String compiler : candidates) {
                Process compile;
                try { compile = new ProcessBuilder(compiler, "-std=c++11", cpp.toString(), "-o", exe.toString())
                        .redirectErrorStream(true).redirectOutput(log.toFile()).start(); }
                catch (java.io.IOException missing) { continue; }
                available = true;
                waitFor(compile); check(compile.exitValue() == 0, "C++ compilation: " + Files.readString(log));
                Process run = new ProcessBuilder(exe.toString()).redirectErrorStream(true).redirectOutput(log.toFile()).start();
                waitFor(run); check(run.exitValue() == 0 && Files.readString(log).trim().equals("3"), "C++ behavior");
                System.out.println("Native C++ compile/run passed with " + compiler); break;
            }
            if (!available) System.out.println("SKIP native C++ compile/run: set CXX to a compiler executable or install g++/clang++ on PATH.");
        } finally {
            Files.deleteIfExists(cpp); Files.deleteIfExists(exe); Files.deleteIfExists(log); Files.deleteIfExists(dir);
        }
    }
    static void waitFor(Process process) throws Exception {
        if (!process.waitFor(30, TimeUnit.SECONDS)) { process.destroyForcibly(); process.waitFor(); throw new AssertionError("process timeout"); }
    }
    static void fails(Runnable action, String text) {
        try { action.run(); } catch (CodeGenerationException expected) { check(expected.getMessage().contains(text), expected.getMessage()); return; }
        throw new AssertionError("expected code-generation error containing " + text);
    }
    static void check(boolean ok, String message) { if (!ok) throw new AssertionError(message); }
}
