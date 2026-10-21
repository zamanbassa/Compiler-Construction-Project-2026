package spl.codegen;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import spl.lexer.Lexer;
import spl.parser.Parser;
import spl.semantic.DeclarationResolver;
import spl.semantic.NumericMode;
import spl.semantic.Phase2aResult;
import spl.semantic.Phase2bResult;
import spl.semantic.Symbol;
import spl.semantic.SymbolKind;
import spl.semantic.TypeAnalyzer;
import spl.tree.TreeNode;

public final class ProgramCodeGeneratorTest {

    private static final String CRLF = "\r\n";

    private static final String CODE_MINE_BEGIN
            = "/* begin of the non-executable code-mine for later inlining */";
    private static final String CODE_MINE_END
            = "/* end of the non-executable code-mine */";
    private static final String EXECUTABLE_BEGIN
            = "/* begin of executable C code */";
    private static final String EXECUTABLE_END
            = "/* end of executable C */";
    private static final String DEFERRED_FUNCTION_TEMPLATES
            = "/* function templates deferred to a later Phase 3b issue */";

    private ProgramCodeGeneratorTest() {
    }

    public static void main(String[] args) throws Exception {
        testEmptyProgramAndEpsilonDeclarations();
        testWholeVariableDeclarationsAndNames();
        testRealVariableDeclarations();
        testDeferredFunctionTemplatesDoNotAddWrappers();
        testExistingAlgorithmTranslationAndTemporaryOrdering();
        testTargetFileOutputIntegration();
        testNativeCompilationAndExecution();

        System.out.println("Phase 3b.2 program-generation tests passed.");
    }

    private static void testEmptyProgramAndEpsilonDeclarations() throws Exception {
        CodeGenerationInput input = accepted(": : ");
        String output = new ProgramCodeGenerator(input).generate();

        check(input.getNumericMode() == NumericMode.WHOLE,
                "empty program should use the default WHOLE mode");
        check(output.contains(CODE_MINE_BEGIN + CRLF + " " + CRLF + CODE_MINE_END),
                "epsilon F_DECL should translate to one blank space");
        check(output.contains("int main() {" + CRLF + " " + CRLF),
                "epsilon V_DECL should translate to one blank space");
        check(output.contains("/* end */"),
                "empty ALGO should use the existing algorithm translator");
        checkWrapperStructure(output);
        checkLineEndings(output);
    }

    private static void testWholeVariableDeclarationsAndNames() throws Exception {
        CodeGenerationInput input = accepted("#first #second #third : : ");
        check(input.getNumericMode() == NumericMode.WHOLE,
                "integer-only program should use WHOLE mode");

        TreeNode declarations = child(input.getTree().getChildren().get(0), "V_DECL");
        String declarationText
                = new ProgramCodeGenerator(input).generateVariableDeclarations(declarations);

        List<Symbol> variables = rootVariables(input);
        check(variables.size() == 3, "expected three root variable symbols");

        Set<String> generatedNames = new HashSet<>();
        int previousPosition = -1;
        for (Symbol symbol : variables) {
            check(symbol.getKind() == SymbolKind.VARIABLE,
                    "root declarations should be variables");
            check(generatedNames.add(symbol.getGeneratedName()),
                    "generated variable names must be unique");

            String emitted = "int " + symbol.getGeneratedName() + ";";
            int position = declarationText.indexOf(emitted);
            check(position >= 0, "resolved generated name was not emitted: " + emitted);
            check(position > previousPosition,
                    "variable declarations must preserve source order");
            previousPosition = position;
        }

        check(declarationText.matches(
                "(?s)int [A-Za-z_][A-Za-z0-9_]*;\\r\\n"
                + "int [A-Za-z_][A-Za-z0-9_]*;\\r\\n"
                + "int [A-Za-z_][A-Za-z0-9_]*;\\r\\n "),
                "WHOLE declarations should use int and finish with the epsilon space");

        String output = new ProgramCodeGenerator(input).generate();
        assertUserDeclarationsUninitialized(output);
        checkWrapperStructure(output);
    }

    private static void testRealVariableDeclarations() throws Exception {
        CodeGenerationInput input = accepted(
                "#amount #total : : #amount = div ( 7 2 ) ; "
                + "#total = add ( #amount 0.5 ) ; "
                + "print ( #total ) ; ");

        check(input.getNumericMode() == NumericMode.REAL,
                "div or a decimal literal should select REAL mode");

        String output = new ProgramCodeGenerator(input).generate();
        List<Symbol> variables = rootVariables(input);

        check(variables.size() == 2, "expected two REAL source variables");
        for (Symbol symbol : variables) {
            check(output.contains("float " + symbol.getGeneratedName() + ";"),
                    "REAL declaration should use float for "
                    + symbol.getGeneratedName());
        }

        check(output.contains("float spl_tmp_"),
                "REAL algorithm temporaries should use float");
        assertUserDeclarationsUninitialized(output);
        checkWrapperStructure(output);
    }

    private static void testDeferredFunctionTemplatesDoNotAddWrappers()
            throws Exception {
        CodeGenerationInput input = accepted(
                ": void #unused ( ) { : : return } : print \"top-level\" ; ");

        String output = new ProgramCodeGenerator(input).generate();
        int codeMineStart = output.indexOf(CODE_MINE_BEGIN);
        int codeMineEnd = output.indexOf(CODE_MINE_END);

        check(codeMineStart >= 0 && codeMineEnd > codeMineStart,
                "code-mine markers should surround the deferred-template notice");
        String codeMine = output.substring(codeMineStart, codeMineEnd);
        check(codeMine.contains(DEFERRED_FUNCTION_TEMPLATES),
                "non-empty F_DECL should be explicitly marked as deferred");
        check(count(output, "#include <iostream>") == 1,
                "function P must not add a nested include");
        check(countMatches(output, "\\bint\\s+main\\s*\\(") == 1,
                "function P must not add a nested main");
        check(count(output, "return 0;") == 1,
                "function P must not add another executable return");
        check(output.contains("std::cout << \"top-level\";"),
                "top-level ALGO should still be translated");
        checkWrapperStructure(output);
    }

    private static void testExistingAlgorithmTranslationAndTemporaryOrdering()
            throws Exception {
        CodeGenerationInput arithmetic = accepted(
                "#x : : #x = add ( 2 mul ( 3 4 ) ) ; print ( #x ) ; ");
        String arithmeticOutput = new ProgramCodeGenerator(arithmetic).generate();
        check(arithmeticOutput.contains("std::cout <<"),
                "print instruction should be translated");
        check(arithmeticOutput.contains(" * ")
                && arithmeticOutput.contains(" + "),
                "arithmetic operators should be translated");
        check(arithmeticOutput.contains("spl_tmp_"),
                "arithmetic translation should retain generated temporaries");
        checkWrapperStructure(arithmeticOutput);

        CodeGenerationInput branch = accepted(
                "#x : : #x = 0 ; "
                + "if eq ( #x 1 ) then { print \"yes\" ; } "
                + "else { print \"no\" ; } ; ");
        String branchOutput = new ProgramCodeGenerator(branch).generate();
        check(branchOutput.contains("goto "),
                "branch should retain Phase 3a jump-based translation");
        check(branchOutput.contains("spl_label_"),
                "branch should retain Phase 3a labels");
        checkTemporariesBeforeJumps(branchOutput);
        checkWrapperStructure(branchOutput);

        CodeGenerationInput loop = accepted(
                "#i : : #i = 0 ; "
                + "while lesser ( #i 3 ) do { "
                + "#i = add ( #i 1 ) ; "
                + "} ; print ( #i ) ; ");
        String loopOutput = new ProgramCodeGenerator(loop).generate();
        check(loopOutput.contains("goto "),
                "loop should retain Phase 3a jump-based translation");
        check(loopOutput.contains("spl_label_"),
                "loop should retain Phase 3a labels");
        checkTemporariesBeforeJumps(loopOutput);
        checkWrapperStructure(loopOutput);
    }

    private static void testTargetFileOutputIntegration() throws Exception {
        Path directory = Files.createTempDirectory("spl-program-output-");
        try {
            Path source = directory.resolve("source.spl");
            Path xml = directory.resolve("tree.xml");
            Path target = directory.resolve("generated.cpp");
            Files.writeString(source, ": : ", StandardCharsets.UTF_8);
            Files.writeString(xml, "<tree/>", StandardCharsets.UTF_8);

            CodeGenerationInput input = accepted("#x : : #x = 4 ; ");
            String expected = new ProgramCodeGenerator(input).generate();

            new TargetFileOutput(source, xml, target).write(input);
            String actual = Files.readString(target, StandardCharsets.UTF_8);

            check(actual.equals(expected),
                    "TargetFileOutput should persist the complete generated unit");
            checkWrapperStructure(actual);
        } finally {
            deleteTemporaryTree(directory);
        }
    }

    private static void testNativeCompilationAndExecution() throws Exception {
        String compiler = findCppCompiler();
        if (compiler == null) {
            System.out.println(
                    "SKIP native Phase 3b.2 tests: no CXX, g++, or clang++ found.");
            return;
        }

        List<NativeCase> cases = List.of(
                new NativeCase("empty", ": : ", ""),
                new NativeCase("variable-only", "#x #y : : ", ""),
                new NativeCase(
                        "arithmetic",
                        "#x : : #x = add ( 2 mul ( 3 4 ) ) ; print ( #x ) ; ",
                        "14"),
                new NativeCase(
                        "print",
                        ": : print \"hello\" ; ",
                        "hello"),
                new NativeCase(
                        "branch",
                        "#x : : #x = 1 ; "
                        + "if eq ( #x 1 ) then { print \"yes\" ; } "
                        + "else { print \"no\" ; } ; ",
                        "yes"),
                new NativeCase(
                        "loop",
                        "#i : : #i = 0 ; "
                        + "while lesser ( #i 3 ) do { "
                        + "#i = add ( #i 1 ) ; "
                        + "} ; print ( #i ) ; ",
                        "3"),
                new NativeCase(
                        "real",
                        "#r : : #r = div ( 7 2 ) ; print ( #r ) ; ",
                        "3.5"));

        Path directory = Files.createTempDirectory("spl-program-native-");
        try {
            for (NativeCase testCase : cases) {
                compileAndRun(compiler, testCase, directory);
            }
        } finally {
            deleteTemporaryTree(directory);
        }

        System.out.println("Native C++ compile/run passed with " + compiler + ".");
    }

    private static void compileAndRun(
            String compiler, NativeCase testCase, Path directory) throws Exception {
        CodeGenerationInput input = accepted(testCase.source);
        String translationUnit = new ProgramCodeGenerator(input).generate();

        Path cpp = directory.resolve(testCase.name + ".cpp");
        Path executable = directory.resolve(
                isWindows() ? testCase.name + ".exe" : testCase.name);
        Path log = directory.resolve(testCase.name + ".log");

        Files.writeString(cpp, translationUnit, StandardCharsets.UTF_8);

        Process compile = new ProcessBuilder(
                compiler, "-std=c++11", cpp.toString(), "-o", executable.toString())
                .redirectErrorStream(true)
                .redirectOutput(log.toFile())
                .start();

        check(compile.waitFor(30, java.util.concurrent.TimeUnit.SECONDS),
                "C++ compile timed out for " + testCase.name);
        check(compile.exitValue() == 0,
                "C++ compilation failed for " + testCase.name + ":\n"
                + Files.readString(log, StandardCharsets.UTF_8));

        Process run = new ProcessBuilder(executable.toString())
                .redirectErrorStream(true)
                .redirectOutput(log.toFile())
                .start();

        check(run.waitFor(30, java.util.concurrent.TimeUnit.SECONDS),
                "native execution timed out for " + testCase.name);
        String actual = Files.readString(log, StandardCharsets.UTF_8).trim();

        check(run.exitValue() == 0,
                "native execution failed for " + testCase.name + ": " + actual);
        check(actual.equals(testCase.expected),
                "wrong output for " + testCase.name
                + ": expected [" + testCase.expected
                + "], got [" + actual + "]");
    }

    private static CodeGenerationInput accepted(String source) throws Exception {
        TreeNode parsed = new Parser().parse(new Lexer().tokenize(source));
        Phase2aResult names = new DeclarationResolver().resolveWithResult(parsed);
        Phase2bResult checked
                = new TypeAnalyzer(names.getSymbolTable()).analyzeWithResult(names);
        CodeGenerationInput input = new CodeGenerationInput(checked);

        check(input.getTree() == names.getResolvedTree(),
                "generation must preserve the accepted Phase 2a tree identity");
        check(input.getSymbolTable() == names.getSymbolTable(),
                "generation must preserve the Phase 2a symbol-table identity");
        check(input.getNumericMode() == checked.getNumericMode(),
                "generation must preserve the Phase 2b numeric mode");
        return input;
    }

    private static List<Symbol> rootVariables(CodeGenerationInput input) {
        List<Symbol> variables = new ArrayList<>();
        for (Symbol symbol : input.getSymbolTable()
                .getRootScope().getDeclarations().values()) {
            if (symbol.getKind() == SymbolKind.VARIABLE) {
                variables.add(symbol);
            }
        }
        return variables;
    }

    private static TreeNode child(TreeNode parent, String value) {
        for (TreeNode node : parent.getChildren()) {
            if (value.equals(node.getValue())) {
                return node;
            }
        }
        throw new AssertionError("missing child " + value);
    }

    private static void checkWrapperStructure(String output) {
        check(count(output, "#include <iostream>") == 1,
                "translation unit must contain exactly one iostream include");
        check(countMatches(output, "\\bint\\s+main\\s*\\(") == 1,
                "translation unit must contain exactly one main");
        check(count(output, "return 0;") == 1,
                "translation unit must contain exactly one return 0");

        int codeMineBegin = output.indexOf(CODE_MINE_BEGIN);
        int codeMineEnd = output.indexOf(CODE_MINE_END);
        int executableBegin = output.indexOf(EXECUTABLE_BEGIN);
        int include = output.indexOf("#include <iostream>");
        int executableEnd = output.indexOf(EXECUTABLE_END);

        check(codeMineBegin >= 0 && codeMineEnd > codeMineBegin,
                "code-mine markers must be present and ordered");
        check(executableBegin > codeMineEnd,
                "executable section must follow the code-mine section");
        check(include > executableBegin,
                "include must follow the executable-section marker");
        check(executableEnd > output.indexOf("return 0;"),
                "executable-section end marker must follow return and main");

        String codeMine = output.substring(codeMineBegin, codeMineEnd);
        check(!codeMine.contains("#include")
                && !codeMine.contains("int main")
                && !codeMine.contains("return 0;"),
                "code mine must not contain the executable wrapper");

        String code = stripCommentsAndStrings(output);
        int depth = 0;

        for (char c : code.toCharArray()) {
            if (c == '{') {
                depth++;
            } else if (c == '}') {
                depth--;

                check(depth >= 0,
                        "unexpected closing brace in generated C++");
            }
        }

        check(depth == 0,
                "unbalanced braces in generated C++");

        checkLineEndings(output);
    }

    private static void assertUserDeclarationsUninitialized(String output) {
        Pattern declaration = Pattern.compile(
                "(?m)^(?:int|float) __variable[0-9]+;\\r?$");
        Matcher matcher = declaration.matcher(output);
        int count = 0;
        while (matcher.find()) {
            check(!matcher.group().contains("="),
                    "user variable declaration must not be initialized implicitly");
            count++;
        }
        check(count > 0, "expected at least one emitted user variable declaration");
    }

    private static void checkTemporariesBeforeJumps(String output) {
        int firstJump = output.indexOf("goto ");
        check(firstJump >= 0, "test program should contain a jump");

        Pattern reference = Pattern.compile("\\bspl_tmp_[0-9]+\\b");
        Matcher matcher = reference.matcher(output);
        while (matcher.find()) {
            String name = matcher.group();
            int declaration = output.indexOf(" " + name + ";");
            if (declaration >= 0) {
                check(declaration < firstJump,
                        "temporary declaration must precede jump-bearing code: " + name);
            }
        }
    }

    private static void checkLineEndings(String output) {
        for (int i = 0; i < output.length(); i++) {
            char current = output.charAt(i);
            if (current == '\n') {
                check(i > 0 && output.charAt(i - 1) == '\r',
                        "generated output should use CRLF consistently");
            }
            if (current == '\r') {
                check(i + 1 < output.length() && output.charAt(i + 1) == '\n',
                        "generated output must not contain an isolated carriage return");
            }
        }
    }

    private static String stripCommentsAndStrings(String text) {
        return text.replaceAll("(?s)/\\*.*?\\*/", " ")
                .replaceAll("\"(?:\\\\.|[^\"\\\\])*\"", "\"\"");
    }

    private static int count(String text, String needle) {
        int matches = 0;
        int position = 0;
        while ((position = text.indexOf(needle, position)) >= 0) {
            matches++;
            position += needle.length();
        }
        return matches;
    }

    private static int countMatches(String text, String regex) {
        Matcher matcher = Pattern.compile(regex).matcher(text);
        int matches = 0;
        while (matcher.find()) {
            matches++;
        }
        return matches;
    }

    private static String findCppCompiler() {
        String configured = System.getenv("CXX");
        if (configured != null && !configured.isBlank()) {
            if (isExecutableAvailable(configured)) {
                return configured;
            }
        }

        for (String candidate : List.of("g++", "clang++")) {
            if (isExecutableAvailable(candidate)) {
                return candidate;
            }
        }
        return null;
    }

    private static boolean isExecutableAvailable(String executable) {
        try {
            Process process = new ProcessBuilder(executable, "--version")
                    .redirectErrorStream(true)
                    .start();
            return process.waitFor(5, java.util.concurrent.TimeUnit.SECONDS)
                    && process.exitValue() == 0;
        } catch (IOException | InterruptedException unavailable) {
            if (unavailable instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            return false;
        }
    }

    private static boolean isWindows() {
        return System.getProperty("os.name").toLowerCase().contains("win");
    }

    private static void deleteTemporaryTree(Path directory) throws IOException {
        if (!Files.exists(directory)) {
            return;
        }
        try (var paths = Files.walk(directory)) {
            for (Path path : paths.sorted(Comparator.reverseOrder()).toList()) {
                Files.deleteIfExists(path);
            }
        }
    }

    private static void check(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }

    private static final class NativeCase {

        private final String name;
        private final String source;
        private final String expected;

        private NativeCase(String name, String source, String expected) {
            this.name = name;
            this.source = source;
            this.expected = expected;
        }
    }
}
