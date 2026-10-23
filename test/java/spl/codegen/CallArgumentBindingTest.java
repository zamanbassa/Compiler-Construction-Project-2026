package spl.codegen;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import spl.lexer.Lexer;
import spl.parser.Parser;
import spl.semantic.DeclarationResolver;
import spl.semantic.NumericMode;
import spl.semantic.Phase2aResult;
import spl.semantic.Phase2bResult;
import spl.semantic.Symbol;
import spl.semantic.TypeAnalyzer;
import spl.tree.TreeNode;

public final class CallArgumentBindingTest {
    private static boolean nativeCompilerAvailable;
    private static boolean nativeCompilerChecked;
    private static List<String> selectedCompiler;

    public static void main(String[] args) throws Exception {
        zeroArguments();
        orderedCapturesPrecedeBindings();
        callerAndCalleeBindingsUseSymbolIdentity();
        inlineCallerEnvironmentIsPreserved();
        numericModesDeclareCaptureAndFormalStorage();
        declarationRegistrationChecksCollisions();
        failuresAreAtomic();
        nativeFragmentsCompileAndRun();
        System.out.println("CallArgumentBindingTest: all checks passed");
    }

    private static void zeroArguments() throws Exception {
        Context context = context(": void #f ( ) { : : return } : #f ( ) ; ");
        List<String> captures = context.bind();
        check(captures.isEmpty(), "zero arguments produce no captures");
        check(context.emitter.render().isEmpty(), "zero-argument void call emits no binding code");
    }

    private static void orderedCapturesPrecedeBindings() throws Exception {
        Context context = context(
                "#x #y : void #f ( #a #b ) { : : return } : "
                + "#f ( add ( #x 1 ) #y ) ; ");
        List<String> captures = context.bind();
        check(captures.size() == 2, "two direct INPUT terms produce two captures");

        String output = context.emitter.render();
        String firstCapture = captures.get(0) + " = ";
        String secondCapture = captures.get(1) + " = ";
        String firstFormalAssignment = context.formalStorage(0) + " = " + captures.get(0) + ";";
        String secondFormalAssignment = context.formalStorage(1) + " = " + captures.get(1) + ";";
        int firstCaptureAt = output.indexOf(firstCapture);
        int secondCaptureAt = output.indexOf(secondCapture);
        int firstBindingAt = output.indexOf(firstFormalAssignment);
        int secondBindingAt = output.indexOf(secondFormalAssignment);
        check(firstCaptureAt >= 0 && secondCaptureAt > firstCaptureAt,
                "actuals are captured in source order");
        check(firstBindingAt > secondCaptureAt && secondBindingAt > secondCaptureAt,
                "all actual evaluations precede every formal assignment");
        check(count(output, "int " + context.formalStorage(0) + ";\n") == 1
                        && count(output, "int " + context.formalStorage(1) + ";\n") == 1,
                "each formal slot is declared once");

        context.binder.bind(context.call, context.catalogue, context.instance, context.generator);
        check(count(context.emitter.render(), "int " + context.formalStorage(0) + ";\n") == 1,
                "re-registering the same instance storage is idempotent");
    }

    private static void callerAndCalleeBindingsUseSymbolIdentity() throws Exception {
        Context context = context(
                "#x : void #f ( #x ) { : : return } : #f ( #x ) ; ");
        Symbol caller = findTerminal(context.call, "#x").getResolvedSymbol();
        Symbol formal = context.function.getParameters().get(0);
        check(caller != formal, "same-spelled caller and formal are distinct resolved symbols");

        List<String> captures = context.bind();
        String output = context.emitter.render();
        check(output.contains(captures.get(0) + " = " + caller.getGeneratedName() + ";"),
                "actual reads the caller's resolved storage");
        check(output.contains(context.formalStorage(0) + " = " + captures.get(0) + ";"),
                "captured value is written to this instance's formal storage");
        check(!context.formalStorage(0).equals(caller.getGeneratedName()),
                "formal storage is distinct from the same-spelled caller variable");
    }

    private static void inlineCallerEnvironmentIsPreserved() throws Exception {
        String source =
                ": void #outer ( #x ) { : void #inner ( #x ) { : : return } "
                + ": #inner ( #x ) ; return } : ";
        Phase2aResult resolved =
                new DeclarationResolver().resolveWithResult(
                        new Parser().parse(new Lexer().tokenize(source)));
        Phase2bResult analyzed =
                new TypeAnalyzer(resolved.getSymbolTable()).analyzeWithResult(resolved);
        CodeGenerationInput input = new CodeGenerationInput(analyzed);
        FunctionCatalogue catalogue = new FunctionCatalogue(input);
        FunctionCatalogue.Function outer = catalogue.getFunctions().get(0);
        TreeNode call = find(input.getTree(), "CALL");
        FunctionCatalogue.Function inner =
                catalogue.requireFunction(call.getChildren().get(0).getResolvedSymbol(), call);
        OutputNames names = new OutputNames(input);
        FunctionInstance outerInstance =
                new FunctionInstance(outer, new RootEnvironment(), names);
        FunctionInstance innerInstance =
                new FunctionInstance(inner, outerInstance.getEnvironment(), names);
        Symbol outerFormal = outer.getParameters().get(0);
        Symbol innerFormal = inner.getParameters().get(0);
        Symbol actual = findTerminal(call, "#x").getResolvedSymbol();
        CodeEmitter emitter = new CodeEmitter(input);
        AlgorithmCodeGenerator caller =
                new AlgorithmCodeGenerator(input, emitter, outerInstance.getEnvironment());

        new CallArgumentBinder().bind(call, catalogue, innerInstance, caller);

        String output = emitter.render();
        String outerStorage = outerInstance.getEnvironment().storageFor(outerFormal, call);
        String innerStorage = innerInstance.getEnvironment().storageFor(innerFormal, call);
        check(actual == outerFormal && actual != innerFormal,
                "nested same-spelled argument resolves to the caller's original symbol");
        check(output.contains(" = " + outerStorage + ";"),
                "argument read uses the caller instance's lexical storage");
        check(output.contains(innerStorage + " = "),
                "binding writes the callee instance's distinct formal storage");
    }

    private static void numericModesDeclareCaptureAndFormalStorage() throws Exception {
        Context whole = context(
                "#x : void #f ( #p ) { : : return } : #f ( #x ) ; ");
        whole.bind();
        check(whole.input.getNumericMode() == NumericMode.WHOLE, "integer source selects WHOLE mode");
        check(whole.emitter.render().startsWith("int " + whole.formalStorage(0) + ";\nint spl_tmp_"),
                "WHOLE mode declares formal and capture storage as int");

        Context real = context(
                "#x : void #f ( #p ) { : : return } : #f ( add ( #x 0.5 ) ) ; ");
        real.bind();
        check(real.input.getNumericMode() == NumericMode.REAL, "decimal source selects REAL mode");
        check(real.emitter.render().startsWith("float " + real.formalStorage(0) + ";\nfloat spl_tmp_"),
                "REAL mode declares formal and capture storage as float");
    }

    private static void declarationRegistrationChecksCollisions() throws Exception {
        Context context = context(
                "#x : void #f ( #p ) { : : return } : #f ( #x ) ; ");
        Symbol sourceVariable = findTerminal(context.call, "#x").getResolvedSymbol();
        CodeEmitter emitter = new CodeEmitter(context.input);

        try {
            emitter.declareVariables(List.of(sourceVariable.getGeneratedName()));
            throw new AssertionError("expected reserved-name collision");
        } catch (CodeGenerationException expected) {
            check(expected.getMessage().contains("duplicate, reserved"),
                    "source generated names cannot be redeclared as inline storage");
        }
        check(emitter.render().isEmpty(), "reserved-name rejection is atomic");

        try {
            emitter.declareVariables(List.of("inline_slot", "inline_slot"));
            throw new AssertionError("expected duplicate declaration collision");
        } catch (CodeGenerationException expected) {
            check(expected.getMessage().contains("duplicate, reserved"),
                    "duplicate new inline names are rejected");
        }
        check(emitter.render().isEmpty(), "duplicate-name rejection is atomic");

        emitter.declareVariables(List.of("inline_slot"));
        emitter.declareVariables(List.of("inline_slot"));
        check(count(emitter.render(), "int inline_slot;\n") == 1,
                "repeated registration of an existing inline slot emits one declaration");
    }

    private static void failuresAreAtomic() throws Exception {
        assertFailureAtomic(
                ": void #f ( #p ) { : : return } : #f ( ) ; ",
                "expected 1, got 0");
        assertFailureAtomic(
                ": void #f ( ) { : : return } : #f ( 1 ) ; ",
                "expected 0, got 1");
        assertFailureAtomic(
                ": num #g ( ) { : : return ( 1 ) } "
                + "void #f ( #p ) { : : return } : #f ( #g ( ) ) ; ",
                "CALL translation is deferred");
    }

    private static void assertFailureAtomic(String source, String expectedMessage) throws Exception {
        Context context = context(source);
        context.emitter.emitComment("existing output");
        String before = context.emitter.render();
        try {
            context.bind();
            throw new AssertionError("expected code-generation failure containing " + expectedMessage);
        } catch (CodeGenerationException expected) {
            check(expected.getMessage().contains(expectedMessage),
                    "failure reports " + expectedMessage + ": " + expected.getMessage());
        }
        check(context.emitter.render().equals(before),
                "preflight failure leaves declarations and emitted statements unchanged");
    }

    private static void nativeFragmentsCompileAndRun() throws Exception {
        Context whole = context(
                "#x : void #f ( #p ) { : : return } : #f ( #x ) ; ");
        whole.bind();
        compileAndRun(whole, "41", "int", "41");

        Context real = context(
                "#x : void #f ( #p ) { : : return } : #f ( add ( #x 0.5 ) ) ; ");
        real.bind();
        compileAndRun(real, "4.5f", "float", "5");
        if (!nativeCompilerAvailable) {
            System.out.println("SKIP native C++ compile/run: no CXX, g++, or clang++ available.");
        }
    }

    private static void compileAndRun(Context context, String initialValue,
                                      String targetType, String expectedOutput) throws Exception {
        List<String> compiler = findCompiler();
        if (compiler == null) {
            return;
        }
        boolean usingWsl = compiler.size() > 1 && "wsl.exe".equalsIgnoreCase(compiler.get(0));

        Symbol caller = findTerminal(context.call, "#x").getResolvedSymbol();
        String formal = context.formalStorage(0);
        Path directory = Files.createTempDirectory("spl-call-arguments-");
        Path source = directory.resolve("test.cpp");
        Path executable = directory.resolve("test.exe");
        Path log = directory.resolve("compiler.log");
        try {
            String cpp = "#include <iostream>\nint main() {\n"
                    + targetType + " " + caller.getGeneratedName() + " = " + initialValue + ";\n"
                    + context.emitter.render()
                    + "std::cout << " + formal + ";\nreturn 0;\n}\n";
            Files.writeString(source, cpp);
            String sourcePath = usingWsl ? wslPath(source) : source.toString();
            String executablePath = usingWsl ? wslPath(executable) : executable.toString();
            List<String> buildCommand = new ArrayList<>(compiler);
            buildCommand.addAll(List.of("-std=c++11", sourcePath, "-o", executablePath));
            Process build = new ProcessBuilder(buildCommand)
                    .redirectErrorStream(true).redirectOutput(log.toFile()).start();
            if (!build.waitFor(30, java.util.concurrent.TimeUnit.SECONDS)) {
                build.destroyForcibly();
                throw new AssertionError("native C++ compilation timed out");
            }
            check(build.exitValue() == 0,
                    "native C++ compilation succeeds: " + Files.readString(log));
            List<String> runCommand = usingWsl
                    ? List.of("wsl.exe", "--exec", executablePath)
                    : List.of(executable.toString());
            Process run = new ProcessBuilder(runCommand)
                    .redirectErrorStream(true).redirectOutput(log.toFile()).start();
            if (!run.waitFor(30, java.util.concurrent.TimeUnit.SECONDS)) {
                run.destroyForcibly();
                throw new AssertionError("native C++ execution timed out");
            }
            check(run.exitValue() == 0 && Files.readString(log).trim().equals(expectedOutput),
                    "native fragment execution produces " + expectedOutput);
            System.out.println("Native C++ compile/run passed with "
                    + String.join(" ", compiler)
                    + " (" + context.input.getNumericMode() + ").");
        } finally {
            Files.deleteIfExists(source);
            Files.deleteIfExists(executable);
            Files.deleteIfExists(log);
            Files.deleteIfExists(directory);
        }
    }

    private static List<String> findCompiler() throws Exception {
        if (nativeCompilerChecked) {
            return selectedCompiler;
        }
        nativeCompilerChecked = true;
        List<List<String>> candidates = new ArrayList<>();
        if (System.getenv("CXX") != null) {
            candidates.add(List.of(System.getenv("CXX")));
        }
        candidates.add(List.of("g++"));
        candidates.add(List.of("clang++"));
        candidates.add(List.of("wsl.exe", "--exec", "g++"));
        for (List<String> candidate : candidates) {
            List<String> command = new ArrayList<>(candidate);
            command.add("--version");
            try {
                Process process = new ProcessBuilder(command)
                        .redirectErrorStream(true).start();
                if (process.waitFor(10, java.util.concurrent.TimeUnit.SECONDS)
                        && process.exitValue() == 0) {
                    nativeCompilerAvailable = true;
                    selectedCompiler = List.copyOf(candidate);
                    return selectedCompiler;
                }
            } catch (java.io.IOException unavailable) {
                // Try the next conventional compiler name.
            }
        }
        return null;
    }

    private static String wslPath(Path path) throws Exception {
        Process process = new ProcessBuilder("wsl.exe", "--exec", "wslpath", "-a", path.toString())
                .redirectErrorStream(true).start();
        String converted = new String(process.getInputStream().readAllBytes()).trim();
        if (!process.waitFor(10, java.util.concurrent.TimeUnit.SECONDS)
                || process.exitValue() != 0 || converted.isEmpty()) {
            throw new AssertionError("could not convert path for WSL: " + path);
        }
        return converted;
    }

    private static Context context(String source) throws Exception {
        Phase2aResult resolved =
                new DeclarationResolver().resolveWithResult(
                        new Parser().parse(new Lexer().tokenize(source)));
        Phase2bResult analyzed =
                new TypeAnalyzer(resolved.getSymbolTable()).analyzeWithResult(resolved);
        CodeGenerationInput input = new CodeGenerationInput(analyzed);
        FunctionCatalogue catalogue = new FunctionCatalogue(input);
        TreeNode call = find(input.getTree(), "CALL");
        if (call == null) {
            throw new AssertionError("accepted program has no call");
        }
        FunctionCatalogue.Function function =
                catalogue.requireFunction(call.getChildren().get(0).getResolvedSymbol(), call);
        OutputNames names = new OutputNames(input);
        FunctionInstance instance = new FunctionInstance(function, new RootEnvironment(), names);
        CodeEmitter emitter = new CodeEmitter(input);
        AlgorithmCodeGenerator generator =
                new AlgorithmCodeGenerator(input, emitter, new RootEnvironment());
        return new Context(input, catalogue, call, function, instance, emitter, generator);
    }

    private static TreeNode find(TreeNode node, String value) {
        if (value.equals(node.getValue())) {
            return node;
        }
        for (TreeNode child : node.getChildren()) {
            TreeNode found = find(child, value);
            if (found != null) {
                return found;
            }
        }
        return null;
    }

    private static TreeNode findTerminal(TreeNode node, String value) {
        if (node.isTerminal() && value.equals(node.getValue())) {
            return node;
        }
        for (TreeNode child : node.getChildren()) {
            TreeNode found = findTerminal(child, value);
            if (found != null) {
                return found;
            }
        }
        return null;
    }

    private static int count(String text, String token) {
        int matches = 0;
        for (int at = text.indexOf(token); at >= 0; at = text.indexOf(token, at + token.length())) {
            matches++;
        }
        return matches;
    }

    private static void check(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError("FAILED: " + message);
        }
    }

    private static final class Context {
        private final CodeGenerationInput input;
        private final FunctionCatalogue catalogue;
        private final TreeNode call;
        private final FunctionCatalogue.Function function;
        private final FunctionInstance instance;
        private final CodeEmitter emitter;
        private final AlgorithmCodeGenerator generator;
        private final CallArgumentBinder binder = new CallArgumentBinder();

        private Context(CodeGenerationInput input, FunctionCatalogue catalogue, TreeNode call,
                        FunctionCatalogue.Function function, FunctionInstance instance,
                        CodeEmitter emitter, AlgorithmCodeGenerator generator) {
            this.input = input;
            this.catalogue = catalogue;
            this.call = call;
            this.function = function;
            this.instance = instance;
            this.emitter = emitter;
            this.generator = generator;
        }

        private List<String> bind() {
            return binder.bind(call, catalogue, instance, generator);
        }

        private String formalStorage(int index) {
            return instance.getEnvironment().storageFor(function.getParameters().get(index), call);
        }
    }
}
