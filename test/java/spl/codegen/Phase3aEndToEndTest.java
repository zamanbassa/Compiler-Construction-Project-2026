package spl.codegen;

import java.nio.file.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.regex.*;
import javax.xml.parsers.DocumentBuilderFactory;
import spl.lexer.Lexer;
import spl.parser.Parser;
import spl.semantic.*;
import spl.tree.TreeNode;

/** Actual production translator, not test overrides. Native execution is explicitly optional. */
public final class Phase3aEndToEndTest {
    static final class Case {
        final String name, source, expected;
        Case(String name, String source, String expected) { this.name=name; this.source=source; this.expected=expected; }
    }
    public static void main(String[] args) throws Exception {
        List<Case> cases = new ArrayList<>();
        cases.add(new Case("empty", ": : ", ""));
        cases.add(new Case("simple", "#x : : nop ; comment \"note\" ; #x = 7 ; print \"value:\" ; print ( #x ) ; print \"\" ; ", "value:7"));
        cases.add(new Case("arithmetic", "#x : : #x = add ( 2 mul ( 3 4 ) ) ; print ( #x ) ; print \",\" ; print ( sub ( 9 2 ) ) ; print \",\" ; print ( neg ( neg ( -3 ) ) ) ; print \",\" ; print ( mod ( 7 2 ) ) ; ", "14,7,-3,1"));
        cases.add(new Case("real", "#x : : #x = div ( 7 2 ) ; print ( #x ) ; print \",\" ; print ( add ( 0.5 1 ) ) ; ", "3.5,1.5"));
        cases.add(new Case("unused functions", ": void #f ( ) { : void #g ( ) { : : return } : #g ( ) ; return } : print \"main\" ; ", "main"));
        for (String op : new String[]{"eq", "larger", "lesser"}) {
            for (int x=0;x<2;x++) for(int y=0;y<2;y++) {
                boolean result=op.equals("eq")?x==y:op.equals("larger")?x>y:x<y;
                cases.add(branch(op+x+y,op+" ( "+x+" "+y+" )",result));
            }
        }
        for(int a=0;a<2;a++) for(int b=0;b<2;b++) for(int c=0;c<2;c++) {
            String A="eq ( "+a+" 1 )", B="eq ( "+b+" 1 )", C="eq ( "+c+" 1 )";
            cases.add(branch("nested-or"+a+b+c,"or ( and ( "+A+" "+B+" ) "+C+" )",(a==1&&b==1)||c==1));
            cases.add(branch("nested-and"+a+b+c,"and ( or ( "+A+" "+B+" ) not ( "+C+" ) )",(a==1||b==1)&&c!=1));
        }
        cases.add(branch("double-not","not ( not ( eq ( 1 1 ) ) )",true));
        cases.add(branch("short-and","and ( eq ( 0 1 ) eq ( mod ( 1 0 ) 0 ) )",false));
        cases.add(branch("short-or","or ( eq ( 1 1 ) eq ( mod ( 1 0 ) 0 ) )",true));
        for (String kind : new String[]{"while","until"}) {
            String cond=kind.equals("while")?"lesser ( #x 3 )":"eq ( #x 3 )";
            String body="#x = add ( #x 1 ) ;";
            cases.add(new Case("pre-"+kind,"#x : : #x = 0 ; "+kind+" "+cond+" do { "+body+" } ; print ( #x ) ; ","3"));
            cases.add(new Case("post-"+kind,"#x : : #x = 0 ; do { "+body+" } "+kind+" "+cond+" ; print ( #x ) ; ","3"));
            String stop=kind.equals("while")?"eq ( 0 1 )":"eq ( 1 1 )";
            cases.add(new Case("zero-"+kind,"#x : : #x = 0 ; "+kind+" "+stop+" do { "+body+" } ; print ( #x ) ; ","0"));
            cases.add(new Case("one-"+kind,"#x : : #x = 0 ; do { "+body+" } "+kind+" "+stop+" ; print ( #x ) ; ","1"));
        }
        cases.add(new Case("nested-flow","#x #y : : #x = 0 ; #y = 0 ; while lesser ( #x 2 ) do { #x = add ( #x 1 ) ; do { #y = add ( #y 1 ) ; } until eq ( mod ( #y 2 ) 0 ) ; if eq ( #x 1 ) then { print \"a\" ; } else { print \"b\" ; } ; } ; print ( #y ) ; ","ab4"));
        List<String> fragments=new ArrayList<>(), wrappers=new ArrayList<>();
        for (Case test : cases) {
            CodeGenerationInput input=accepted(test.source);
            AlgorithmCodeGenerator generator=new AlgorithmCodeGenerator(input);
            String fragment=generator.generate(TargetFileOutput.mainAlgorithm(input));
            check(fragment.equals(generator.generate(TargetFileOutput.mainAlgorithm(input))),test.name+" reset determinism");
            check(fragment.equals(new AlgorithmCodeGenerator(input).generate(TargetFileOutput.mainAlgorithm(input))),test.name+" new instance determinism");
            try { structural(fragment); } catch (AssertionError failure) { throw new AssertionError(test.name + ": " + failure.getMessage() + "\n" + fragment, failure); }
            fragments.add(fragment);
            StringBuilder wrapper=new StringBuilder("void case_"+wrappers.size()+"() {\n");
            // Source-variable declarations are deliberately test-only (production deferred to 3b).
            for(Symbol symbol:input.getSymbolTable().getRootScope().getDeclarations().values())
                if(symbol.getKind()==SymbolKind.VARIABLE) wrapper.append(input.getNumericMode().getTargetType()).append(' ').append(symbol.getGeneratedName()).append(" = 0;\n");
            wrappers.add(wrapper.append(fragment).append("}\n").toString());
        }
        check(fragments.get(0).equals("/* end */\n"),"empty snapshot");
        String simple=fragments.get(1);
        check(simple.indexOf("/* nop */")<simple.indexOf("/* \"note\" */"),"instruction order");
        check(count(simple,"std::cout <<")==3,"print once each");
        cliTests();
        nativeRun(cases,wrappers);
        System.out.println("Phase 3a end-to-end Java checks passed: "+cases.size()+" production-translator cases and CLI regressions.");
    }
    static Case branch(String name,String condition,boolean result) {
        return new Case(name,": : if "+condition+" then { print \"t\" ; } else { print \"f\" ; } ; print \"!\" ; ",result?"t!":"f!");
    }
    static CodeGenerationInput accepted(String source)throws Exception {
        Phase2aResult a=new DeclarationResolver().resolveWithResult(new Parser().parse(new Lexer().tokenize(source)));
        return new CodeGenerationInput(new TypeAnalyzer(a.getSymbolTable()).analyzeWithResult(a));
    }
    static void structural(String fragment) {
        String code=fragment.replaceAll("(?s)/\\*.*?\\*/", " ").replaceAll("\"(?:\\\\.|[^\"\\\\])*\"", "\"\"");
        check(!Pattern.compile("\\b(?:else|and|or|not|while|do)\\b|&&|\\|\\||!(?!=)").matcher(code).find(),"forbidden target control/operator");
        Set<String> labels=new HashSet<>(); Matcher m=Pattern.compile("(?m)^([A-Za-z_][A-Za-z0-9_]*):(?!:)").matcher(code);
        while(m.find())check(labels.add(m.group(1)),"duplicate label");
        m=Pattern.compile("\\bgoto ([A-Za-z_][A-Za-z0-9_]*)").matcher(code);while(m.find())check(labels.contains(m.group(1)),"undefined target");
        Map<String,Integer> temps=new HashMap<>();m=Pattern.compile("(?m)^(?:int|float) (spl_tmp_[0-9]+);$").matcher(code);
        while(m.find())check(temps.put(m.group(1),m.start())==null,"duplicate temporary");
        m=Pattern.compile("\\bspl_tmp_[0-9]+\\b").matcher(code);while(m.find())check(temps.containsKey(m.group())&&temps.get(m.group())<=m.start(),"temporary before declaration");
        check(!code.contains("; "),"newline after semicolon");
    }
    static int count(String text,String needle){int n=0,pos=0;while((pos=text.indexOf(needle,pos))>=0){n++;pos+=needle.length();}return n;}
    static void cliTests()throws Exception {
        Path dir=Files.createTempDirectory("spl-phase3-cli-");
        try {
            Path source=dir.resolve("source.spl"), xml=dir.resolve("tree.xml"), target=dir.resolve("nested/out.cpp");
            Files.writeString(source,"#x : : #x = 2 ; print ( #x ) ; ");
            cli(dir,0,"Compilation Successful",source,xml,target);
            check(Files.readString(target,StandardCharsets.UTF_8).contains("std::cout"),"UTF8 custom target");
            DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(xml.toFile());
            String xmlWithTarget=Files.readString(xml);
            cli(dir,0,"Compilation Successful",source,xml);
            check(Files.readString(xml).equals(xmlWithTarget),"XML unchanged with target option");
            String bytes=Files.readString(target);
            cli(dir,0,"Compilation Successful",source,xml,target);check(bytes.equals(Files.readString(target)),"file determinism");
            String[] bad={"@ ",": : print ; ",": : #x = 1 ; ",": void #f ( ) { : : return } : print ( #f ( ) ) ; ","#x : : #x = mod ( 4 2 ) ; #x = div ( 4 2 ) ; ",": void #f ( ) { : : return } : #f ( ) ; ",": num #f ( ) { : : return ( 1 ) } : print ( #f ( ) ) ; "};
            String[] phase={"Lexical Analysis","Syntax Analysis","Declaration Resolution","Type Checking","Type Checking","Code Generation","Code Generation"};
            for(int i=0;i<bad.length;i++){
                Files.writeString(source,bad[i]);Files.writeString(target,"stale target");
                cli(dir,1,phase[i],source,xml,target);check(!Files.exists(target),"stale target removed on rejection");
            }
            Files.writeString(source,": : ");
            cli(dir,0,"Compilation Successful",source,xml); // original two-argument XML mode
            String original=Files.readString(source);
            cli(dir,1,"paths must be different",source,xml,source);check(Files.readString(source).equals(original),"source alias preserved");
            String originalXml=Files.readString(xml);
            cli(dir,1,"paths must be different",source,xml,xml);check(Files.readString(xml).equals(originalXml),"XML alias preserved");
            Path blocked=dir.resolve("blocked");Files.writeString(blocked,"file not directory");
            cli(dir,1,"File I/O",source,xml,blocked.resolve("out.cpp"));
            cli(dir,1,"File I/O",source,blocked.resolve("tree.xml"),target);
            check(!Files.exists(target),"XML failure leaves no target");
            cli(dir,1,"File I/O",source,xml,dir); // do not delete an output directory
            Files.createDirectories(dir.resolve("input"));Files.writeString(dir.resolve("input/sample.spl"),": : ");
            cli(dir,0,"Compilation Successful");check(Files.exists(dir.resolve("output/tree.xml")),"default XML behavior");
        } finally { cleanup(dir); }
    }
    static void cli(Path dir,int exit,String message,Path...args)throws Exception {
        String cp=Arrays.stream(System.getProperty("java.class.path").split(Pattern.quote(System.getProperty("path.separator"))))
                .map(x->Path.of(x).toAbsolutePath().toString()).collect(java.util.stream.Collectors.joining(System.getProperty("path.separator")));
        List<String> cmd=new ArrayList<>();cmd.add(Path.of(System.getProperty("java.home"),"bin",System.getProperty("os.name").startsWith("Windows")?"java.exe":"java").toString());
        cmd.add("-cp");cmd.add(cp);cmd.add("spl.Main");for(Path arg:args)cmd.add(arg.toString());
        Path log=dir.resolve("cli.log");Process process=new ProcessBuilder(cmd).directory(dir.toFile()).redirectErrorStream(true).redirectOutput(log.toFile()).start();waitFor(process);
        String text=Files.readString(log);check(process.exitValue()==exit&&text.contains(message),"CLI diagnostic: "+text);
        check(!text.contains("Internal Compiler Error"),"expected failure mislabeled internal");
        if(exit!=0)check(!text.contains("=== Compilation Successful ==="),"false successful compilation");
    }
    static void nativeRun(List<Case> cases,List<String> wrappers)throws Exception {
        Path dir=Files.createTempDirectory("spl-phase3-native-");
        try {
            StringBuilder src=new StringBuilder("#include <iostream>\n");for(String wrapper:wrappers)src.append(wrapper);
            src.append("int main() {\n");StringBuilder expected=new StringBuilder();
            for(int i=0;i<cases.size();i++){src.append("case_").append(i).append("(); std::cout << \"|\";\n");expected.append(cases.get(i).expected).append('|');}
            src.append("return 0;\n}\n");Path cpp=dir.resolve("cases.cpp"),exe=dir.resolve("cases.exe"),log=dir.resolve("native.log");Files.writeString(cpp,src);
            List<String> compilers=new ArrayList<>();if(System.getenv("CXX")!=null)compilers.add(System.getenv("CXX"));compilers.add("g++");compilers.add("clang++");
            for(String compiler:compilers){
                Process build;try{build=new ProcessBuilder(compiler,"-std=c++11",cpp.toString(),"-o",exe.toString()).redirectErrorStream(true).redirectOutput(log.toFile()).start();}catch(java.io.IOException missing){continue;}
                waitFor(build);check(build.exitValue()==0,"native compilation: "+Files.readString(log));
                Process run=new ProcessBuilder(exe.toString()).redirectErrorStream(true).redirectOutput(log.toFile()).start();waitFor(run);
                String actual=Files.readString(log);check(run.exitValue()==0&&actual.equals(expected.toString()),"native behavior mismatch: "+actual+" expected "+expected);
                System.out.println("PASS native Phase 3a execution: "+cases.size()+" cases with "+compiler);return;
            }
            System.out.println("SKIP native Phase 3a execution: no CXX/g++/clang++ compiler available; behavior expectations NOT executed.");
        }finally{cleanup(dir);}
    }
    static void waitFor(Process p)throws Exception {if(!p.waitFor(30,TimeUnit.SECONDS)){p.destroyForcibly();p.waitFor();throw new AssertionError("process timeout");}}
    static void cleanup(Path root)throws Exception {try(java.util.stream.Stream<Path> paths=Files.walk(root)){for(Path path:paths.sorted(Comparator.reverseOrder()).toArray(Path[]::new))Files.deleteIfExists(path);}}
    static void check(boolean condition,String message){if(!condition)throw new AssertionError(message);}
}
