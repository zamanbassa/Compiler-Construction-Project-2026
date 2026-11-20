package spl.codegen;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.TimeUnit;
import spl.lexer.Lexer;
import spl.parser.Parser;
import spl.semantic.DeclarationResolver;
import spl.semantic.Phase2aResult;
import spl.semantic.Phase2bResult;
import spl.semantic.TypeAnalyzer;

public final class InlineCallExecutionTest {
    private static final int PROCESS_TIMEOUT_SECONDS = 30;

    private InlineCallExecutionTest() {
    }

    public static void main(String[] args) throws Exception {
        List<Program> programs = programs();
        String compiler = findCompiler();
        if (compiler == null) {
            System.out.println("SKIP native inlining execution: no CXX, g++, or clang++ available.");
            return;
        }

        Path directory = Files.createTempDirectory("spl-inline-execution-");
        try {
            for (Program program : programs) {
                compileAndRun(compiler, program, directory);
            }
        } finally {
            deleteTemporaryTree(directory);
        }
        System.out.println("Native execution passed with " + compilerLabel(compiler)
                + ": " + programs.size() + " programs");
    }

    private static List<Program> programs() {
        return List.of(
                new Program("numeric-parameter-return",
                        ": num #inc ( #n ) { : : return ( add ( #n 1 ) ) } "
                        + ": print ( #inc ( 4 ) ) ; ",
                        "5"),
                new Program("void-call",
                        ": void #say ( ) { : : print \"called\" ; return } "
                        + ": #say ( ) ; ",
                        "called"),
                new Program("void-call-updates-global",
                        "#x : void #set ( ) { : : #x = 7 ; return } "
                        + ": #x = 0 ; #set ( ) ; print ( #x ) ; ",
                        "7"),
                new Program("function-local",
                        ": num #work ( #n ) { #local : : "
                        + "#local = mul ( #n 2 ) ; return ( add ( #local 1 ) ) } "
                        + ": print ( #work ( 3 ) ) ; ",
                        "7"),
                new Program("call-in-arithmetic",
                        ": num #inc ( #n ) { : : return ( add ( #n 1 ) ) } "
                        + ": print ( mul ( #inc ( 4 ) 2 ) ) ; ",
                        "10"),
                new Program("nested-calls-in-arguments",
                        ": num #inc ( #n ) { : : return ( add ( #n 1 ) ) } "
                        + "num #double ( #n ) { : : return ( mul ( #n 2 ) ) } "
                        + ": print ( #double ( #inc ( 3 ) ) ) ; ",
                        "8"),
                new Program("nested-call-in-function-return",
                        ": num #outer ( #n ) { : "
                        + "num #inner ( #m ) { : : return ( add ( #m 2 ) ) } "
                        + ": return ( #inner ( #n ) ) } "
                        + ": print ( #outer ( 5 ) ) ; ",
                        "7"),
                new Program("repeated-stateful-calls",
                        "#count #a #b : "
                        + "num #next ( ) { : : #count = add ( #count 1 ) ; "
                        + "return ( #count ) } "
                        + ": #count = 0 ; #a = #next ( ) ; #b = #next ( ) ; "
                        + "print ( #a ) ; print ( #b ) ; print ( #count ) ; ",
                        "122"),
                new Program("call-in-print",
                        ": num #value ( ) { : : return ( 42 ) } "
                        + ": print ( #value ( ) ) ; ",
                        "42"),
                new Program("call-in-condition",
                        ": num #score ( #n ) { : : return ( add ( #n 2 ) ) } "
                        + ": if larger ( #score ( 2 ) 3 ) then { print \"yes\" ; } "
                        + "else { print \"no\" ; } ; ",
                        "yes"),
                new Program("calls-in-loop-condition",
                        "#count : num #next ( ) { : : #count = add ( #count 1 ) ; "
                        + "return ( #count ) } "
                        + ": #count = 0 ; while lesser ( #next ( ) 4 ) do { "
                        + "print \"x\" ; } ; print ( #count ) ; ",
                        "xxx4"),
                new Program("left-to-right-argument-evaluation",
                        "#count #result : "
                        + "num #next ( ) { : : #count = add ( #count 1 ) ; "
                        + "return ( #count ) } "
                        + "num #pair ( #a #b ) { : : "
                        + "return ( add ( mul ( #a 10 ) #b ) ) } "
                        + ": #count = 0 ; #result = #pair ( #next ( ) #next ( ) ) ; "
                        + "print ( #result ) ; print \"-\" ; print ( #count ) ; ",
                        "12-2"),
                new Program("nested-function-lexical-capture",
                        ": num #outer ( #x ) { : "
                        + "num #inner ( #y ) { : : return ( add ( #x #y ) ) } "
                        + ": return ( #inner ( 3 ) ) } "
                        + ": print ( #outer ( 4 ) ) ; ",
                        "7"),
                new Program("multiple-nested-call-sites",
                        ": num #outer ( #x ) { : "
                        + "num #inner ( #y ) { : : return ( add ( #x #y ) ) } "
                        + ": return ( add ( #inner ( 2 ) #inner ( 3 ) ) ) } "
                        + ": print ( #outer ( 5 ) ) ; ",
                        "15"),
                new Program("void-call-inside-function",
                        "#x : void #run ( ) { : "
                        + "void #set ( ) { : : #x = 9 ; return } "
                        + ": #set ( ) ; return } "
                        + ": #x = 0 ; #run ( ) ; print ( #x ) ; ",
                        "9"),
                new Program("real-valued-function",
                        ": num #half ( ) { : : return ( div ( 7 2 ) ) } "
                        + ": print ( #half ( ) ) ; ",
                        "3.5"),
                new Program("call-in-branch-body",
                        "#x : num #inc ( #n ) { : : return ( add ( #n 1 ) ) } "
                        + ": #x = 0 ; if eq ( 1 1 ) then { #x = #inc ( 8 ) ; } "
                        + "else { #x = 0 ; } ; print ( #x ) ; ",
                        "9"));
    }

    private static void compileAndRun(String compiler, Program program, Path directory)
            throws Exception {
        CodeGenerationInput input;
        try {
            input = accepted(program.source);
        } catch (Exception failure) {
            throw new AssertionError("could not analyze " + program.name, failure);
        }
        String translationUnit = new ProgramCodeGenerator(input).generate();
        Path source = directory.resolve(program.name + ".cpp");
        Path executable = directory.resolve(program.name);
        Path log = directory.resolve(program.name + ".log");
        Files.writeString(source, translationUnit, StandardCharsets.UTF_8);

        Process compile = new ProcessBuilder(
                compiler, "-std=c++11", source.toString(), "-o", executable.toString())
                .redirectErrorStream(true)
                .redirectOutput(log.toFile())
                .start();
        waitFor(compile, "C++ compilation", program.name);
        String compileOutput = Files.readString(log, StandardCharsets.UTF_8);
        check(compile.exitValue() == 0,
                "C++ compilation failed for " + program.name + ":\n" + compileOutput);

        Process run = new ProcessBuilder(executable.toString())
                .redirectErrorStream(true)
                .redirectOutput(log.toFile())
                .start();
        waitFor(run, "native execution", program.name);
        String actual = Files.readString(log, StandardCharsets.UTF_8).trim();
        check(run.exitValue() == 0,
                "native execution failed for " + program.name + ":\n" + actual);
        check(actual.equals(program.expected),
                "wrong output for " + program.name + ": expected ["
                        + program.expected + "], got [" + actual + "]");
    }

    private static CodeGenerationInput accepted(String source) throws Exception {
        Phase2aResult resolved = new DeclarationResolver().resolveWithResult(
                new Parser().parse(new Lexer().tokenize(source)));
        Phase2bResult checked =
                new TypeAnalyzer(resolved.getSymbolTable()).analyzeWithResult(resolved);
        return new CodeGenerationInput(checked);
    }

    private static String findCompiler() throws Exception {
        List<String> candidates = new ArrayList<>();
        if (System.getenv("CXX") != null && !System.getenv("CXX").isBlank()) {
            candidates.add(System.getenv("CXX"));
        }
        candidates.add("g++");
        candidates.add("clang++");

        for (String candidate : candidates) {
            try {
                Process process = new ProcessBuilder(candidate, "--version")
                        .redirectErrorStream(true)
                        .start();
                if (process.waitFor(10, TimeUnit.SECONDS) && process.exitValue() == 0) {
                    return candidate;
                }
                if (process.isAlive()) {
                    process.destroyForcibly();
                }
            } catch (java.io.IOException unavailable) {
                // Try the next conventional compiler name.
            }
        }
        return null;
    }

    private static String compilerLabel(String compiler) {
        Path path = Path.of(compiler);
        return path.getFileName() == null ? compiler : path.getFileName().toString();
    }

    private static void waitFor(Process process, String operation, String program)
            throws Exception {
        if (!process.waitFor(PROCESS_TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
            process.destroyForcibly();
            process.waitFor();
            throw new AssertionError(operation + " timed out for " + program);
        }
    }

    private static void deleteTemporaryTree(Path root) throws Exception {
        try (java.util.stream.Stream<Path> paths = Files.walk(root)) {
            for (Path path : paths.sorted(Comparator.reverseOrder()).toArray(Path[]::new)) {
                Files.deleteIfExists(path);
            }
        }
    }

    private static void check(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }

    private static final class Program {
        private final String name;
        private final String source;
        private final String expected;

        private Program(String name, String source, String expected) {
            this.name = name;
            this.source = source;
            this.expected = expected;
        }
    }
}
