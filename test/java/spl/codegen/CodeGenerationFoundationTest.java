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
        fails(() -> new CodeGenerationInput((Phase2bResult) null), "missing");
        fails(() -> new CodeGenerationInput(new TreeNode("P"), a.getSymbolTable(), NumericMode.WHOLE), "accepted");
        CodeGenerationInput real = accepted(": : print ( 0.5 ) ; ");
        fails(() -> new CodeGenerationInput(real.getTree(), real.getSymbolTable(), NumericMode.WHOLE), "contradicts");
        CodeGenerationInput mod = accepted(": : print ( mod ( 7 2 ) ) ; ");
        fails(() -> new CodeGenerationInput(mod.getTree(), mod.getSymbolTable(), NumericMode.REAL), "contradicts");
        TreeNode use = find(b.getTree(), "ASSIGN").getChildren().get(0);
        Symbol binding = use.getResolvedSymbol();
        use.setResolvedSymbol(null);
        fails(() -> new CodeGenerationInput(b), "resolved symbol");
        use.setResolvedSymbol(binding);
        fails(() -> new CodeGenerationInput(b.getTree(), new SymbolTable(), NumericMode.WHOLE), "resolved symbol");
        CodeGenerationInput call = accepted(": void #f ( ) { : : return } : #f ( ) ; ");
        fails(() -> new AlgorithmCodeGenerator(call).generate(rootAlgo(call)), "Phase 3b");
        AlgorithmCodeGenerator absent = new AlgorithmCodeGenerator(input);
        check(absent.generate(rootAlgo(input)).contains(" = 1;"), "integrated ALGO translation");
        fails(() -> absent.generate(new TreeNode("ALGO")), "accepted tree");
        fails(() -> absent.generate(input.getTree()), "expected ALGO");
        // A small test-only adapter proves the public entry point without implementing issue 8.
        CodeGenerationInput empty = accepted(": : ");
        AlgorithmCodeGenerator adapter = new AlgorithmCodeGenerator(empty) {
            @Override
            public void emitAlgo(TreeNode node) {
                require(node, "ALGO");
                emitter.emitComment("end");
            }
        };
        check(adapter.generate(rootAlgo(empty)).equals(adapter.generate(rootAlgo(empty))), "entry reset");
        check(adapter.generate(rootAlgo(empty)).equals("/* end */\n"), "entry output");
        namesAndFormatting(input);
        rejectedConditionalJumpPreservesState(input);
        check(new CodeEmitter(real).newTemporary().equals("spl_tmp_0"), "per-run names");
        CodeEmitter re = new CodeEmitter(real);
        re.newTemporary();
        check(re.render().equals("float spl_tmp_0;\n"), "float type");
        String fragment = nestedFragment(input);
        nativeCheck(fragment);
        termTranslation();
        booleanTranslation();
        extendedTermTranslation();
        shortCircuitExecution();
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
        if (value.equals(n.getValue())) {
            return n;
        }
        for (TreeNode c : n.getChildren()) {
            TreeNode found = find(c, value);
            if (found != null) {
                return found;

            }
        }
        return null;
    }

    static TreeNode rootAlgo(CodeGenerationInput input) {
        TreeNode p = input.getTree();
        if (p.getValue().equals("SPL_PROG")) {
            p = p.getChildren().get(0);
        }
        for (TreeNode c : p.getChildren()) {
            if (c.getValue().equals("ALGO")) {
                return c;
            }
        }
        throw new AssertionError("missing root ALGO");
    }

    static void namesAndFormatting(CodeGenerationInput input) {
        // Reserve colliding names in a descendant scope as well as the root.
        SymbolTable table = new SymbolTable();
        TreeNode tree = new TreeNode("ALGO");
        tree.setType(SemanticType.OK);
        table.declare("#a", SymbolKind.VARIABLE, "spl_tmp_0", null);
        table.enterScope();
        table.declare("#b", SymbolKind.VARIABLE, "spl_label_0", null);
        table.exitScope();
        CodeEmitter e = new CodeEmitter(new CodeGenerationInput(tree, table, NumericMode.WHOLE));
        Set<String> names = new HashSet<>();
        for (int i = 0; i < 600; i++) {
            String t = e.newTemporary(), l = e.newLabel();
            check(names.add(t) && names.add(l), "unique names");
            check(!t.equals("spl_tmp_0") && !l.equals("spl_label_0"), "reserved names");
        }
        e.reset();
        String t = e.newTemporary(), l = e.newLabel();
        check(t.equals("spl_tmp_1") && l.equals("spl_label_1"), "deterministic reset");
        e.emitStatement(t + " = 1");
        e.emitGoto(l);
        e.emitComment("safe */ text");
        e.emitLabel(l);
        String text = e.render();
        check(text.startsWith("int " + t + ";\n"), "declaration first");
        check(text.contains(l + ": ;\n"), "label syntax");
        check(text.contains("/* safe * / text */"), "safe comment");
        fails(() -> e.emitLabel(l), "duplicate");
        fails(() -> e.emitStatement("x = 1;"), "semicolon");
        fails(() -> e.emitGoto("missing"), "allocated");
        CodeEmitter dangling = new CodeEmitter(input);
        dangling.emitGoto(dangling.newLabel());
        fails(() -> dangling.render(), "undefined");
        CodeEmitter quoted = new CodeEmitter(input);
        quoted.emitStatement("std::cout << \"a;b\"");
        check(quoted.render().contains("a;b"), "quoted semicolon preserved");
        check(nestedFragment(input).equals(nestedFragment(input)), "independent output determinism");
    }

    static void rejectedConditionalJumpPreservesState(CodeGenerationInput input) {
        CodeEmitter emitter = new CodeEmitter(input);
        emitter.emitComment("existing output");
        String target = emitter.newLabel();
        String before = emitter.render();
        // Passes the comparison guard, but fails emitStatement's quote validation.
        fails(() -> emitter.emitConditionalGoto("x == \"unterminated", target), "unterminated");
        check(emitter.render().equals(before), "rejected jump must not change body or references");
        // A subsequent valid use must still track its target and render normally once defined.
        emitter.emitConditionalGoto("1 == 1", target);
        fails(() -> emitter.render(), "undefined");
        emitter.emitLabel(target);
        check(emitter.render().equals(before + "if (1 == 1) goto " + target + ";\n"
                + target + ": ;\n"), "valid emission after recovery");
        // A failed second jump must not discard an earlier valid target reference either.
        CodeEmitter pending = new CodeEmitter(input);
        String valid = pending.newLabel(), rejected = pending.newLabel();
        pending.emitConditionalGoto("1 == 1", valid);
        fails(() -> pending.emitConditionalGoto("x == \"unterminated", rejected), "unterminated");
        fails(() -> pending.render(), "undefined");
        pending.emitLabel(valid);
        check(pending.render().equals("if (1 == 1) goto " + valid + ";\n" + valid + ": ;\n"),
                "only successful jumps should register references");
    }

    static String nestedFragment(CodeGenerationInput input) {
        CodeEmitter e = new CodeEmitter(input);
        String start = e.newLabel(), inner = e.newLabel(), done = e.newLabel();
        String counter = e.newTemporary();
        e.emitStatement(counter + " = 0");
        e.emitLabel(start);
        e.emitConditionalGoto(counter + " >= 3", done);
        // Allocated after a jump in emission order, but rendered before every jump.
        String value = e.newTemporary();
        e.emitStatement(value + " = 2");
        e.emitConditionalGoto(value + " == 2", inner);
        e.emitGoto(done);
        e.emitLabel(inner);
        e.emitStatement(counter + " = " + counter + " + 1");
        e.emitGoto(start);
        e.emitLabel(done);
        e.emitStatement("std::cout << " + counter);
        return e.render();
    }

    static void nativeCheck(String fragment) throws Exception {
        Path dir = Files.createTempDirectory("spl-cpp-emitter-");
        Path cpp = dir.resolve("test.cpp"), exe = dir.resolve("test.exe"), log = dir.resolve("log.txt");
        try {
            Files.writeString(cpp, "#include <iostream>\nint main() {\n" + fragment + "return 0;\n}\n");
            boolean available = false;
            List<String> candidates = new ArrayList<>();
            if (System.getenv("CXX") != null) {
                candidates.add(System.getenv("CXX"));
            }
            candidates.add("g++");
            candidates.add("clang++");
            for (String compiler : candidates) {
                Process compile;
                try {
                    compile = new ProcessBuilder(compiler, "-std=c++11", cpp.toString(), "-o", exe.toString())
                            .redirectErrorStream(true).redirectOutput(log.toFile()).start();
                } catch (java.io.IOException missing) {
                    continue;
                }
                available = true;
                waitFor(compile);
                check(compile.exitValue() == 0, "C++ compilation: " + Files.readString(log));
                Process run = new ProcessBuilder(exe.toString()).redirectErrorStream(true).redirectOutput(log.toFile()).start();
                waitFor(run);
                check(run.exitValue() == 0 && Files.readString(log).trim().equals("3"), "C++ behavior");
                System.out.println("Native C++ compile/run passed with " + compiler);
                break;
            }
            if (!available) {
                System.out.println("SKIP native C++ compile/run: set CXX to a compiler executable or install g++/clang++ on PATH.");
            }
        } finally {
            Files.deleteIfExists(cpp);
            Files.deleteIfExists(exe);
            Files.deleteIfExists(log);
            Files.deleteIfExists(dir);
        }
    }

    static void waitFor(Process process) throws Exception {
        if (!process.waitFor(30, TimeUnit.SECONDS)) {
            process.destroyForcibly();
            process.waitFor();
            throw new AssertionError("process timeout");
        }
    }

    static void fails(Runnable action, String text) {
        try {
            action.run();
        } catch (CodeGenerationException expected) {
            check(expected.getMessage().contains(text), expected.getMessage());
            return;
        }
        throw new AssertionError("expected code-generation error containing " + text);
    }

    static void check(boolean ok, String message) {
        if (!ok) {
            throw new AssertionError(message);

        }
    }

    static void termTranslation() throws Exception {
        CodeGenerationInput input = accepted(
                "#x #y : : #x = add ( 2 mul ( #y 3 ) ) ; ");

        TreeNode assignment = find(input.getTree(), "ASSIGN");
        TreeNode term = find(assignment, "TERM");
        String result = assignment.getChildren().get(0)
                .getResolvedSymbol().getGeneratedName();

        AlgorithmCodeGenerator generator = new AlgorithmCodeGenerator(input);
        generator.emitTerm(term, result);

        check(generator.getEmitter().render().equals(
                "int spl_tmp_0;\n"
                + "int spl_tmp_1;\n"
                + "int spl_tmp_2;\n"
                + "int spl_tmp_3;\n"
                + "spl_tmp_0 = 2;\n"
                + "spl_tmp_2 = __variable1;\n"
                + "spl_tmp_3 = 3;\n"
                + "spl_tmp_1 = spl_tmp_2 * spl_tmp_3;\n"
                + "__variable0 = spl_tmp_0 + spl_tmp_1;\n"),
                "nested term translation and resolved target names");

        CodeGenerationInput callInput = accepted(
                "#x : num #f ( ) { : : return ( 1 ) } : #x = #f ( ) ; ");
        TreeNode callAssignment = find(callInput.getTree(), "ASSIGN");
        AlgorithmCodeGenerator callGenerator = new AlgorithmCodeGenerator(callInput);
        fails(() -> callGenerator.emitTerm(
                find(callAssignment, "TERM"), "__variable0"), "Phase 3b");

        fails(() -> generator.emitTerm(term, "not a target identifier"),
                "invalid result variable");
    }

    static void booleanTranslation() throws Exception {
        String[] expressions = {
            "eq ( 2 2 )", // true
            "eq ( 2 3 )", // false
            "larger ( 3 2 )", // true
            "larger ( 2 3 )", // false
            "lesser ( 2 3 )", // true
            "lesser ( 3 2 )", // false

            "and ( eq ( 1 1 ) eq ( 2 2 ) )", // true, true
            "and ( eq ( 1 1 ) eq ( 2 3 ) )", // true, false
            "and ( eq ( 1 2 ) eq ( 2 2 ) )", // false, true
            "and ( eq ( 1 2 ) eq ( 2 3 ) )", // false, false

            "or ( eq ( 1 1 ) eq ( 2 2 ) )", // true, true
            "or ( eq ( 1 1 ) eq ( 2 3 ) )", // true, false
            "or ( eq ( 1 2 ) eq ( 2 2 ) )", // false, true
            "or ( eq ( 1 2 ) eq ( 2 3 ) )", // false, false

            "not ( not ( eq ( 1 1 ) ) )", // double negation: true
            "not ( not ( eq ( 1 2 ) ) )", // double negation: false

            "or ( and ( eq ( 1 1 ) lesser ( 2 3 ) ) eq ( 4 5 ) )", // nested: true
            "and ( or ( eq ( 1 2 ) eq ( 3 3 ) ) not ( eq ( 4 4 ) ) )" // nested: false
        };

        StringBuilder expected = new StringBuilder();
        List<String> functions = new ArrayList<>();

        for (int i = 0; i < expressions.length; i++) {
            boolean result = i == 0 || i == 2 || i == 4
                    || i == 6 || i == 10 || i == 11 || i == 12
                    || i == 14 || i == 16;

            expected.append(result ? '1' : '0');
            functions.add(booleanFunction(i, booleanResultFragment(expressions[i])));
        }

        String andCode = booleanResultFragment(
                "and ( larger ( 3 0 ) lesser ( 3 10 ) )");
        check(andCode.contains(" > "), "larger must emit >");
        check(andCode.contains(" < "), "lesser must emit <");
        check(!andCode.contains("&&") && !andCode.contains("||")
                && !andCode.contains("!"),
                "Boolean operators must use jumps, not C++ logical operators");
        check(countOccurrences(andCode, "if (") == 2,
                "and must emit both conditional comparisons");
        check(countOccurrences(andCode, "goto ") >= 4,
                "and must emit explicit true/false/intermediate jumps");

        String orCode = booleanResultFragment(
                "or ( eq ( 1 1 ) eq ( 2 2 ) )");
        check(countOccurrences(orCode, "if (") == 2,
                "or must emit both conditional comparisons");
        check(countOccurrences(orCode, "goto ") >= 4,
                "or must emit explicit true/false/intermediate jumps");

        nativeBooleanCheck(functions, expected.toString());
    }

    static String booleanResultFragment(String expression) throws Exception {
        CodeGenerationInput input = accepted(
                ": : if " + expression + " then { } else { } ; ");

        TreeNode bool = find(input.getTree(), "BOOL");
        AlgorithmCodeGenerator generator = new AlgorithmCodeGenerator(input);
        CodeEmitter emitter = generator.getEmitter();

        String trueLabel = emitter.newLabel();
        String falseLabel = emitter.newLabel();
        String doneLabel = emitter.newLabel();

        generator.emitBool(bool, trueLabel, falseLabel);

        emitter.emitLabel(trueLabel);
        emitter.emitStatement("spl_bool_result = 1");
        emitter.emitGoto(doneLabel);

        emitter.emitLabel(falseLabel);
        emitter.emitStatement("spl_bool_result = 0");

        emitter.emitLabel(doneLabel);
        return emitter.render();
    }

    static String booleanFunction(int index, String fragment) {
        return "int spl_eval_" + index + "() {\n"
                + "int spl_bool_result = -1;\n"
                + fragment
                + "return spl_bool_result;\n"
                + "}\n";
    }

    static void nativeBooleanCheck(List<String> functions, String expected)
            throws Exception {
        Path dir = Files.createTempDirectory("spl-bool-codegen-");
        Path cpp = dir.resolve("test.cpp");
        Path exe = dir.resolve("test.exe");
        Path log = dir.resolve("log.txt");

        try {
            StringBuilder source = new StringBuilder("#include <iostream>\n");
            for (String function : functions) {
                source.append(function);
            }

            source.append("int main() {\n");
            for (int i = 0; i < functions.size(); i++) {
                source.append("std::cout << spl_eval_").append(i).append("();\n");
            }
            source.append("return 0;\n}\n");
            Files.writeString(cpp, source.toString());

            List<String> compilers = new ArrayList<>();
            if (System.getenv("CXX") != null) {
                compilers.add(System.getenv("CXX"));
            }
            compilers.add("g++");
            compilers.add("clang++");

            boolean compilerFound = false;
            for (String compiler : compilers) {
                Process compile;
                try {
                    compile = new ProcessBuilder(
                            compiler, "-std=c++11", cpp.toString(), "-o", exe.toString())
                            .redirectErrorStream(true)
                            .redirectOutput(log.toFile())
                            .start();
                } catch (java.io.IOException unavailable) {
                    continue;
                }

                compilerFound = true;
                waitFor(compile);
                check(compile.exitValue() == 0,
                        "C++ boolean compilation failed: " + Files.readString(log));

                Process run = new ProcessBuilder(exe.toString())
                        .redirectErrorStream(true)
                        .redirectOutput(log.toFile())
                        .start();
                waitFor(run);

                check(run.exitValue() == 0, "generated Boolean program failed");
                check(Files.readString(log).trim().equals(expected),
                        "wrong Boolean outcomes: " + Files.readString(log));
                break;
            }

            if (!compilerFound) {
                System.out.println(
                        "SKIP native Boolean outcomes: no C++ compiler found.");
            }
        } finally {
            Files.deleteIfExists(cpp);
            Files.deleteIfExists(exe);
            Files.deleteIfExists(log);
            Files.deleteIfExists(dir);
        }
    }

    static int countOccurrences(String text, String needle) {
        int count = 0;
        int index = 0;
        while ((index = text.indexOf(needle, index)) >= 0) {
            count++;
            index += needle.length();
        }
        return count;
    }

    static void extendedTermTranslation() throws Exception {
        String[][] cases = {
            {"sub ( 9 4 )", "5", "WHOLE"},
            {"mul ( 6 7 )", "42", "WHOLE"},
            {"mod ( 17 5 )", "2", "WHOLE"},
            {"div ( 7 2 )", "3.5", "REAL"},
            {"neg ( 8 )", "-8", "WHOLE"},
            {"add ( 1.25 2.5 )", "3.75", "REAL"},
            {"add ( neg ( 3 ) 8 )", "5", "WHOLE"},
            {"sub ( 2 9 )", "-7", "WHOLE"},
            {"add ( 6 6 )", "12", "WHOLE"},
            {"mul ( sub ( 10 4 ) add ( 2 3 ) )", "30", "WHOLE"}
        };

        List<String> functions = new ArrayList<>();

        for (int i = 0; i < cases.length; i++) {
            String expression = cases[i][0];
            String expected = cases[i][1];
            String mode = cases[i][2];

            CodeGenerationInput input = accepted(
                    "#x : : #x = " + expression + " ; ");

            check(input.getNumericMode().name().equals(mode),
                    "Incorrect numeric mode for: " + expression);

            TreeNode assignment = find(input.getTree(), "ASSIGN");
            TreeNode term = find(assignment, "TERM");

            String target = assignment.getChildren().get(0)
                    .getResolvedSymbol().getGeneratedName();

            AlgorithmCodeGenerator generator
                    = new AlgorithmCodeGenerator(input);

            generator.emitTerm(term, target);

            String generated = generator.getEmitter().render();

            String cppType = mode.equals("REAL") ? "float" : "int";

            double value = Double.parseDouble(expected);
            String lower = Double.toString(value - 0.001);
            String upper = Double.toString(value + 0.001);

            functions.add(
                    "int spl_eval_" + i + "() {\n"
                    + cppType + " " + target + " = 0;\n"
                    + generated
                    + "return (" + target + " > " + lower
                    + " && " + target + " < " + upper
                    + ") ? 1 : 0;\n"
                    + "}\n"
            );
        }

        nativeBooleanCheck(functions, "1".repeat(cases.length));

        System.out.println(
                "Extended TERM translation tests passed.");
    }

    static void shortCircuitExecution() throws Exception {
    
        String[] expressions = {
            "and ( eq ( 1 2 ) eq ( 3 3 ) )",
            "and ( eq ( 1 1 ) eq ( 3 3 ) )",
            "or ( eq ( 1 1 ) eq ( 3 3 ) )",
            "or ( eq ( 1 2 ) eq ( 3 3 ) )"
        };

        List<String> functions = new ArrayList<>();

        for (int i = 0; i < expressions.length; i++) {
            String generated
                    = booleanResultFragment(expressions[i]);

            
            int first = generated.indexOf("if (");
            int second = generated.indexOf("if (", first + 1);

            check(first >= 0 && second > first,
                    "Expected two comparison jumps");

            String instrumented
                    = generated.substring(0, second)
                    + "spl_rhs_hits++;\n"
                    + generated.substring(second);

            functions.add(
                    "int spl_eval_" + i + "() {\n"
                    + "int spl_bool_result = -1;\n"
                    + "int spl_rhs_hits = 0;\n"
                    + instrumented
                    + "return spl_rhs_hits;\n"
                    + "}\n"
            );
        }

        nativeBooleanCheck(functions, "0101");

        System.out.println(
                "Short-circuit execution tests passed.");
    }

}
