package spl.codegen;

import java.nio.file.*;
import java.util.*;
import java.util.concurrent.TimeUnit;
import spl.lexer.Lexer;
import spl.parser.Parser;
import spl.semantic.*;
import spl.tree.TreeNode;

/**
 * Phase 3a.7 loop tests. Each loop body is STUBBED (via emitAlgo override) to
 * increment the counter #i by 1, so the FINAL value of #i is the iteration
 * count.
 *
 */
public final class LoopCodeGenerationTest {

    public static void main(String[] args) throws Exception {

        String[][] cases = {
            {"until larger ( #i 3 ) do { #i = add ( #i 1 ) ; }", "4"},
            {"while lesser ( #i 3 ) do { #i = add ( #i 1 ) ; }", "3"},
            {"while lesser ( #i 0 ) do { #i = add ( #i 1 ) ; }", "0"},
            {"do { #i = add ( #i 1 ) ; } while lesser ( #i 3 )", "3"},
            {"do { #i = add ( #i 1 ) ; } until larger ( #i 3 )", "4"}, 
            {"do { #i = add ( #i 1 ) ; } while lesser ( #i 0 )", "1"},
            // composite conditions:
            {"while or ( lesser ( #i 2 ) lesser ( #i 4 ) ) do { #i = add ( #i 1 ) ; }", "4"},
            {"while and ( lesser ( #i 5 ) lesser ( #i 3 ) ) do { #i = add ( #i 1 ) ; }", "3"} 
        };

        List<String> functions = new ArrayList<>();
        StringBuilder expected = new StringBuilder();

        for (int k = 0; k < cases.length; k++) {
            String program = "#i : : " + cases[k][0] + " ; ";
            expected.append(cases[k][1]);

            CodeGenerationInput input = accepted(program);
            TreeNode loop = find(input.getTree(), "LOOP");
            check(loop != null, "no LOOP node for: " + cases[k][0]);

            final String counter = firstVarName(input);

            LoopCodeGenerator gen = new LoopCodeGenerator(input) {
                @Override
                public void emitAlgo(TreeNode algo) {
                    require(algo, "ALGO");
                    emitter.emitStatement(counter + " = " + counter + " + 1");
                }
            };
            gen.emitLoop(loop);
            String fragment = gen.getEmitter().render();

            check(!fragment.contains("while") && !fragment.contains("do {")
                    && !fragment.contains("for ("),
                    "loops must use labels/gotos, not target loop keywords");

            functions.add(
                    "int spl_eval_" + k + "() {\n"
                    + "int " + counter + " = 0;\n"
                    + fragment
                    + "return " + counter + ";\n"
                    + "}\n");
        }

        nativeRun(functions, expected.toString());  

        branchInLoop();  
        nestedLoop();

        System.out.println("Loop code-generation (3a.7) tests passed.");
    }


    static void branchInLoop() throws Exception {
        CodeGenerationInput input = accepted(
                "#i : : while lesser ( #i 4 ) do { #i = add ( #i 1 ) ; } ; ");
        TreeNode loop = find(input.getTree(), "LOOP");
        final String c = firstVarName(input);

        LoopCodeGenerator gen = new LoopCodeGenerator(input) {
            @Override
            public void emitAlgo(TreeNode algo) {
                require(algo, "ALGO");
                String thenL = emitter.newLabel();
                String elseL = emitter.newLabel();
                String doneL = emitter.newLabel();
                emitter.emitConditionalGoto(c + " < 2", thenL); 
                emitter.emitGoto(elseL);
                emitter.emitLabel(elseL);
                emitter.emitStatement(c + " = " + c + " + 1");
                emitter.emitGoto(doneL);
                emitter.emitLabel(thenL);
                emitter.emitStatement(c + " = " + c + " + 2");
                emitter.emitLabel(doneL);
            }
        };
        gen.emitLoop(loop);
        String fragment = gen.getEmitter().render();
        List<String> fns = new ArrayList<>();
        fns.add("int spl_eval_0() {\nint " + c + " = 0;\n" + fragment + "return " + c + ";\n}\n");
        nativeRun(fns, "4");
    }

    static void nestedLoop() throws Exception {
        CodeGenerationInput input = accepted(
                "#i #j : : until larger ( #i 1 ) do { #i = add ( #i 1 ) ; } ; ");
        TreeNode loop = find(input.getTree(), "LOOP");
        final String i = varNamed(input, "#i");
        final String j = varNamed(input, "#j");

        LoopCodeGenerator gen = new LoopCodeGenerator(input) {
            @Override
            public void emitAlgo(TreeNode algo) {
                require(algo, "ALGO");
                String target = emitter.newLabel();
                String limit = emitter.newTemporary();
                String base = emitter.newTemporary();
                emitter.emitStatement(base + " = " + j);
                emitter.emitStatement(limit + " = " + base + " + 3");
                emitter.emitLabel(target);
                emitter.emitStatement(j + " = " + j + " + 1");
                emitter.emitConditionalGoto(j + " < " + limit, target);
                emitter.emitStatement(i + " = " + i + " + 1");
            }
        };
        gen.emitLoop(loop);
        String fragment = gen.getEmitter().render();
        List<String> fns = new ArrayList<>();
        fns.add("int spl_eval_0() {\nint " + i + " = 0;\nint " + j + " = 0;\n"
                + fragment + "return " + j + ";\n}\n");
        nativeRun(fns, "6");
    }

    static Phase2aResult resolve(String source) throws Exception {
        return new DeclarationResolver().resolveWithResult(new Parser().parse(new Lexer().tokenize(source)));
    }

    static CodeGenerationInput accepted(String source) throws Exception {
        Phase2aResult a = resolve(source);
        return new CodeGenerationInput(new TypeAnalyzer(a.getSymbolTable()).analyzeWithResult(a));
    }

    static TreeNode find(TreeNode n, String value) {
        if (value.equals(n.getValue())) {
            return n;
        }
        for (TreeNode c : n.getChildren()) {
            TreeNode f = find(c, value);
            if (f != null) {
                return f;
        
            }}
        return null;
    }

    static String firstVarName(CodeGenerationInput input) {
        for (Symbol s : input.getSymbolTable().getRootScope().getDeclarations().values()) {
            if (s.getKind() == SymbolKind.VARIABLE) {
                return s.getGeneratedName();
            }
        }
        throw new AssertionError("no variable declared");
    }

    static String varNamed(CodeGenerationInput input, String source) {
        Symbol s = input.getSymbolTable().getRootScope().getDeclarations().get(source);
        if (s == null) {
            throw new AssertionError("no var " + source);
        }
        return s.getGeneratedName();
    }

    static void nativeRun(List<String> functions, String expected) throws Exception {
        Path dir = Files.createTempDirectory("spl-loop-");
        Path cpp = dir.resolve("t.cpp"), exe = dir.resolve("t.exe"), log = dir.resolve("log.txt");
        try {
            StringBuilder src = new StringBuilder("#include <iostream>\n");
            for (String f : functions) {
                src.append(f);
            }
            src.append("int main() {\n");
            for (int i = 0; i < functions.size(); i++) {
                src.append("std::cout << spl_eval_").append(i).append("();\n");
            }
            src.append("return 0;\n}\n");
            Files.writeString(cpp, src.toString());
            List<String> compilers = new ArrayList<>();
            if (System.getenv("CXX") != null) {
                compilers.add(System.getenv("CXX"));
            }
            compilers.add("g++");
            compilers.add("clang++");
            boolean found = false;
            for (String c : compilers) {
                Process comp;
                try {
                    comp = new ProcessBuilder(c, "-std=c++11", cpp.toString(), "-o", exe.toString())
                            .redirectErrorStream(true).redirectOutput(log.toFile()).start();
                } catch (java.io.IOException miss) {
                    continue;
                }
                found = true;
                waitFor(comp);
                check(comp.exitValue() == 0, "C++ loop compile: " + Files.readString(log));
                Process run = new ProcessBuilder(exe.toString()).redirectErrorStream(true).redirectOutput(log.toFile()).start();
                waitFor(run);
                check(run.exitValue() == 0, "loop program failed");
                check(Files.readString(log).trim().equals(expected),
                        "wrong loop counts: got " + Files.readString(log).trim() + " want " + expected);
                System.out.println("Native loop run passed with " + c + ": " + expected);
                break;
            }
            if (!found) {
                System.out.println("SKIP native loop run: no C++ compiler.");
            }
        } finally {
            Files.deleteIfExists(cpp);
            Files.deleteIfExists(exe);
            Files.deleteIfExists(log);
            Files.deleteIfExists(dir);
        }
    }

    static void waitFor(Process p) throws Exception {
        if (!p.waitFor(10, TimeUnit.SECONDS)) {
            p.destroyForcibly();
            p.waitFor();
            throw new AssertionError("process timeout (infinite loop?)");
        }
    }

    static void check(boolean ok, String msg) {
        if (!ok) {
            throw new AssertionError(msg);
    
        }}
}
