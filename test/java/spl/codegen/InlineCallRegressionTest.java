package spl.codegen;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import spl.lexer.Lexer;
import spl.parser.Parser;
import spl.semantic.DeclarationResolver;
import spl.semantic.Phase2aResult;
import spl.semantic.Phase2bResult;
import spl.semantic.TypeAnalyzer;


public final class InlineCallRegressionTest {

    private static final class Case {
        final String name;
        final String source;
        final String expectedOutput;

        Case(String name, String source, String expectedOutput) {
            this.name = name;
            this.source = source;
            this.expectedOutput = expectedOutput;
        }
    }

    private static final String F_PLUS_ONE =
            "#r : num #f ( #x ) { : : return ( add ( #x 1 ) ) } ";

    public static void main(String[] args) throws Exception {
        List<Case> cases = new ArrayList<>();


        cases.add(new Case("same function nested in its own argument",
                F_PLUS_ONE + ": #r = #f ( #f ( 1 ) ) ; print ( #r ) ; "
                        + "#r = #f ( add ( #r #f ( #r ) ) ) ; print ( #r ) ; ",
                "38"));
        cases.add(new Case("same function twice in one expression",
                F_PLUS_ONE + ": #r = add ( #f ( 1 ) #f ( 2 ) ) ; print ( #r ) ; ",
                "5"));
        cases.add(new Case("void call updates outer variable once per call",
                "#r : void #p ( #x ) { : : #r = add ( #r #x ) ; print ( #r ) ; return } "
                        + ": #r = 0 ; #p ( 3 ) ; #p ( 4 ) ; print ( #r ) ; ",
                "377"));
        cases.add(new Case("three-level chain f g h with repeated child calls",
                "#r : num #f ( #x ) { : num #g ( #y ) { : num #h ( #z ) { : : return ( add ( #z 1 ) ) } "
                        + ": return ( add ( #h ( #y ) #h ( #y ) ) ) } : return ( #g ( #x ) ) } "
                        + ": #r = #f ( 5 ) ; print ( #r ) ; #r = #f ( #r ) ; print ( #r ) ; ",
                "1226"));
        cases.add(new Case("looping callee called twice has unique labels",
                "#r : num #cnt ( #n ) { #i #acc : : #i = 0 ; #acc = 0 ; "
                        + "until eq ( #i #n ) do { #acc = add ( #acc #i ) ; #i = add ( #i 1 ) ; } ; "
                        + "return ( #acc ) } : #r = add ( #cnt ( 4 ) #cnt ( 5 ) ) ; print ( #r ) ; ",
                "16"));

        cases.add(new Case("euclid-style repeated looping calls with parameter mutation",
                "#r : num #gcd ( #a #b ) { #t : : "
                        + "until eq ( #b 0 ) do { #t = mod ( #a #b ) ; #a = #b ; #b = #t ; } ; "
                        + "return ( #a ) } "
                        + ": #r = add ( #gcd ( 48 18 ) #gcd ( 35 14 ) ) ; print ( #r ) ; ",
                "13"));
        cases.add(new Case("sibling copies do not share locals or formals",
                "#a #b : num #inc ( #x ) { #t : : #t = add ( #x 1 ) ; return ( #t ) } "
                        + ": #a = #inc ( 1 ) ; #b = #inc ( 10 ) ; print ( #a ) ; print ( #b ) ; ",
                "211"));
        cases.add(new Case("nested function mutates parent local and parameter",
                "#r : num #f ( #x ) { #loc : void #bump ( ) { : : #loc = add ( #loc 10 ) ; "
                        + "#x = add ( #x 1 ) ; return } : #loc = 1 ; #bump ( ) ; #bump ( ) ; "
                        + "return ( add ( #loc #x ) ) } "
                        + ": #r = #f ( 5 ) ; print ( #r ) ; #r = #f ( 100 ) ; print ( #r ) ; ",
                "28123"));
        cases.add(new Case("call as argument, in arithmetic and in a condition",
                "#n #s : num #sq ( #a ) { : : return ( mul ( #a #a ) ) } "
                        + "num #sum3 ( #a #b #c ) { #t : : #t = add ( #a #b ) ; return ( add ( #t #c ) ) } "
                        + ": #n = 3 ; #s = #sum3 ( #sq ( #n ) #sq ( add ( #n 1 ) ) #sq ( 2 ) ) ; print ( #s ) ; "
                        + "if eq ( #sq ( 3 ) 9 ) then { print ( 1 ) ; } else { print ( 0 ) ; } ; ",
                "291"));

        // A call in a skipped Boolean operand must not run (side effect on #c).
        String bump = "#c : num #bump ( ) { : : #c = add ( #c 1 ) ; return ( #c ) } : #c = 0 ; ";
        cases.add(new Case("and skips call in right operand when left is false",
                bump + "if and ( eq ( 0 1 ) eq ( #bump ( ) 1 ) ) then { print \"t\" ; } else { print \"f\" ; } ; print ( #c ) ; ",
                "f0"));
        cases.add(new Case("or skips call in right operand when left is true",
                bump + "if or ( eq ( 1 1 ) eq ( #bump ( ) 1 ) ) then { print \"t\" ; } else { print \"f\" ; } ; print ( #c ) ; ",
                "t0"));
        cases.add(new Case("and runs call in right operand when left is true",
                bump + "if and ( eq ( 1 1 ) eq ( #bump ( ) 1 ) ) then { print \"t\" ; } else { print \"f\" ; } ; print ( #c ) ; ",
                "t1"));

        // 3b.7: loops in BOTH parent and child, parent invoked twice from main.
        cases.add(new Case("loops in parent and nested child, parent called twice",
                "#r : num #f ( #x ) { #i #acc : num #g ( #y ) { #j #s : : #j = 0 ; #s = 0 ; "
                        + "until eq ( #j #y ) do { #s = add ( #s #x ) ; #j = add ( #j 1 ) ; } ; return ( #s ) } "
                        + ": #i = 0 ; #acc = 0 ; "
                        + "until eq ( #i 2 ) do { #acc = add ( #acc #g ( #x ) ) ; #i = add ( #i 1 ) ; } ; "
                        + "return ( #acc ) } : #r = add ( #f ( 3 ) #f ( 2 ) ) ; print ( #r ) ; ",
                "26"));
        // 3b.7: three levels; the grandchild mutates variables of BOTH ancestors and a formal.
        cases.add(new Case("ancestor variables stay bound through three expansion levels",
                "#r : num #f ( #x ) { #a : num #g ( #y ) { #b : void #h ( #z ) { : : "
                        + "#a = add ( #a #z ) ; #b = add ( #b 1 ) ; #x = add ( #x 100 ) ; return } "
                        + ": #b = 0 ; #h ( #y ) ; #h ( 2 ) ; return ( add ( #b #y ) ) } "
                        + ": #a = 10 ; #a = add ( #a #g ( #x ) ) ; return ( add ( #a #x ) ) } "
                        + ": #r = #f ( 1 ) ; print ( #r ) ; #r = #f ( 5 ) ; print ( #r ) ; ",
                "214222"));
        // 3b.5: shadowing, names sharing a prefix (#x, #x1, #x10) and name-like text in a string.
        cases.add(new Case("shadowing, common-prefix names and name-like string text",
                "#x : num #f ( #x1 ) { #x10 : num #g ( #x ) { #x1 : : #x1 = add ( #x 1 ) ; return ( #x1 ) } "
                        + ": #x10 = #g ( #x1 ) ; return ( add ( #x10 #x1 ) ) } "
                        + ": #x = #f ( 4 ) ; print \"x1x10\" ; print ( #x ) ; ",
                "x1x109"));
        // 3b.5: many copies of one looping template in a single program (label/name stress).
        StringBuilder many = new StringBuilder("#r : num #cnt ( #n ) { #i : : #i = 0 ; "
                + "until eq ( #i #n ) do { #i = add ( #i 1 ) ; } ; return ( #i ) } : #r = 0 ; ");
        for (int k = 1; k <= 12; k++) {
            many.append("#r = add ( #r #cnt ( ").append(k).append(" ) ) ; ");
        }
        many.append("print ( #r ) ; ");
        cases.add(new Case("twelve copies of a looping function", many.toString(), "78"));

        cases.add(new Case("REAL mode numeric call",
                "#x : num #half ( #p ) { : : return ( mul ( #p 0.5 ) ) } : #x = #half ( 3.5 ) ; print ( #x ) ; ",
                "1.75"));

        List<String> generated = new ArrayList<>();
        for (Case c : cases) {
            String program = generate(c.source);
            check(program.equals(generate(c.source)), c.name + ": deterministic output");
            structural(c.name, program);
            generated.add(program);
        }

        expectRejected("argument count mismatch",
                "#r : num #f ( #a #b ) { : : return ( #a ) } : #r = #f ( 1 ) ; ");
        expectRejected("argument count mismatch",
                "#r : num #f ( #x ) { : num #g ( #y ) { : : return ( #y ) } "
                        + ": return ( #g ( #x #x ) ) } : #r = 1 ; ");
        expectRejected("argument count mismatch",
                "#r : num #f ( #x ) { : : return ( #x ) } : #r = #f ( 1 2 ) ; ");

        runNative(cases, generated);
        System.out.println("InlineCallRegressionTest: generation checks passed for " + cases.size() + " programs");
    }

    private static String generate(String source) throws Exception {
        Phase2aResult a = new DeclarationResolver().resolveWithResult(new Parser().parse(new Lexer().tokenize(source)));
        Phase2bResult b = new TypeAnalyzer(a.getSymbolTable()).analyzeWithResult(a);
        return new ProgramCodeGenerator(new CodeGenerationInput(b)).generate();
    }

    private static void expectRejected(String messagePart, String source) throws Exception {
        try {
            generate(source);
        } catch (CodeGenerationException expected) {
            check(expected.getMessage().contains(messagePart),
                    "rejected with '" + messagePart + "' but was: " + expected.getMessage());
            return;
        }
        throw new AssertionError("FAILED: expected rejection containing '" + messagePart + "'");
    }

    /** Code-mine stays a comment; executable section has one main, no calls, unique labels. */
    private static void structural(String name, String program) {
        int begin = program.indexOf("/* begin of executable C code */");
        check(begin > 0, name + ": executable section present");
        String mine = program.substring(0, begin);
        String executable = program.substring(begin);

        check(mine.trim().startsWith("/* begin of the non-executable code-mine")
                        && mine.trim().endsWith("end of the non-executable code-mine */"),
                name + ": code mine is one closed comment");
        check(mine.indexOf("*/") == mine.lastIndexOf("*/") || mine.trim().endsWith("*/"),
                name + ": code mine has no premature comment terminator");
        check(count(executable, "int main(") == 1, name + ": exactly one main");
        check(executable.contains("return 0;"), name + ": main returns 0");

        String code = executable.replaceAll("(?s)/\\*.*?\\*/", " ")
                .replaceAll("\"(?:\\\\.|[^\"\\\\])*\"", "\"\"");
        check(!Pattern.compile("\\b#[A-Za-z]").matcher(code).find(), name + ": no SPL names leak into target");
        check(!code.contains("__function"), name + ": no function names called or referenced in main");

        Set<String> labels = new HashSet<>();
        Matcher m = Pattern.compile("(?m)^([A-Za-z_][A-Za-z0-9_]*):(?!:)").matcher(code);
        while (m.find()) {
            check(labels.add(m.group(1)), name + ": duplicate label " + m.group(1));
        }
        m = Pattern.compile("\\bgoto ([A-Za-z_][A-Za-z0-9_]*)").matcher(code);
        while (m.find()) {
            check(labels.contains(m.group(1)), name + ": undefined jump target " + m.group(1));
        }
        Set<String> declared = new HashSet<>();
        m = Pattern.compile("(?m)^(?:int|float) ([A-Za-z_][A-Za-z0-9_]*);$").matcher(code);
        while (m.find()) {
            check(declared.add(m.group(1)), name + ": duplicate declaration " + m.group(1));
        }
    }

    private static void runNative(List<Case> cases, List<String> programs) throws Exception {
        String compiler = findCompiler();
        if (compiler == null) {
            System.out.println("SKIPPED native execution: no g++/clang++ found. "
                    + "This is NOT a pass; run on a compiler-enabled machine before sign-off.");
            return;
        }
        Path dir = Files.createTempDirectory("spl-inline-calls-");
        try {
            for (int i = 0; i < cases.size(); i++) {
                Case c = cases.get(i);
                Path src = dir.resolve("case" + i + ".cpp");
                Path exe = dir.resolve("case" + i + ".exe");
                Path log = dir.resolve("case" + i + ".log");
                Files.writeString(src, programs.get(i));
                Process build = new ProcessBuilder(compiler, "-std=c++11", src.toString(), "-o", exe.toString())
                        .redirectErrorStream(true).redirectOutput(log.toFile()).start();
                check(build.waitFor(60, TimeUnit.SECONDS), c.name + ": compile finished in time");
                check(build.exitValue() == 0, c.name + ": native compile succeeds: " + Files.readString(log));
                Process run = new ProcessBuilder(exe.toString())
                        .redirectErrorStream(true).redirectOutput(log.toFile()).start();
                if (!run.waitFor(30, TimeUnit.SECONDS)) {
                    run.destroyForcibly();
                    throw new AssertionError("FAILED: " + c.name + ": execution timed out");
                }
                check(run.exitValue() == 0, c.name + ": exit status 0");
                check(Files.readString(log).trim().equals(c.expectedOutput),
                        c.name + ": output is " + c.expectedOutput + " but was " + Files.readString(log).trim());
            }
            System.out.println("Native execution passed with " + compiler + ": " + cases.size() + " programs");
        } finally {
            try (var stream = Files.walk(dir)) {
                stream.sorted(java.util.Comparator.reverseOrder()).forEach(p -> p.toFile().delete());
            }
        }
    }

    private static String findCompiler() {
        String override = System.getenv("CXX");
        List<String> candidates = new ArrayList<>();
        if (override != null && !override.isBlank()) {
            candidates.add(override);
        }
        candidates.add("g++");
        candidates.add("clang++");
        for (String candidate : candidates) {
            try {
                Process p = new ProcessBuilder(candidate, "--version").redirectErrorStream(true).start();
                p.getInputStream().readAllBytes();
                if (p.waitFor(10, TimeUnit.SECONDS) && p.exitValue() == 0) {
                    return candidate;
                }
            } catch (Exception ignored) {
                // try the next candidate
            }
        }
        return null;
    }

    private static int count(String text, String needle) {
        int n = 0;
        int pos = 0;
        while ((pos = text.indexOf(needle, pos)) >= 0) {
            n++;
            pos += needle.length();
        }
        return n;
    }

    private static void check(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError("FAILED: " + message);
        }
    }
}
