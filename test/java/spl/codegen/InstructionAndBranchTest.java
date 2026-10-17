package spl.codegen;
import java.util.List;
import java.util.ArrayList;
import spl.semantic.Symbol;
import spl.tree.TreeNode;

public final class InstructionAndBranchTest {

    public static void main(String[] args) throws Exception {

        outputTests();
        assignmentTests();
        shadowedAssignmentTest();
        commentAndNopTest();
        branchExecutionTests();

        System.out.println("3a.4 and 3a.6 tests passed!");
    }

    private static void outputTests() throws Exception {

        CodeGenerationInput emptyString = CodeGenerationFoundationTest.accepted(": : print \"\" ; ");
        String emptyOutput = new TestGenerator(emptyString).generate(CodeGenerationFoundationTest.rootAlgo(emptyString));
        check(emptyOutput.contains("std::cout << \"\";\n"), "empty string should be printed with one pair of quotes");

        CodeGenerationInput text = CodeGenerationFoundationTest.accepted(": : print \"hello\" ; ");
        String textOutput = new TestGenerator(text).generate(CodeGenerationFoundationTest.rootAlgo(text));
        check(textOutput.contains("std::cout << \"hello\";\n"), "string literal should be preserved");

        CodeGenerationInput whole = CodeGenerationFoundationTest.accepted(": : print ( 3 ) ; ");
        String wholeOutput = new TestGenerator(whole).generate(CodeGenerationFoundationTest.rootAlgo(whole));
        check(wholeOutput.contains("int spl_tmp_0;\n") && wholeOutput.contains("std::cout << spl_tmp_0;\n"), "whole number output should declare and print a type temporary");

        CodeGenerationInput real = CodeGenerationFoundationTest.accepted(": : print ( 0.5 ) ; ");
        String realOutput = new TestGenerator(real).generate(CodeGenerationFoundationTest.rootAlgo(real));
        check(realOutput.contains("float spl_tmp_0;\n") && realOutput.contains("std::cout << spl_tmp_0;\n"), "real number output should use a float temporary");

    }

    private static void assignmentTests() throws Exception{

        CodeGenerationInput input = CodeGenerationFoundationTest.accepted(
            "#x #y : : #x = #7 ; #y = #x ; " + "#x = add ( #x mul ( #y 2 ) ) ; "
        );

        String generated = new TestGenerator(input).generate(CodeGenerationFoundationTest.rootAlgo(input));
        String x = input.getSymbolTable().getRootScope().lookupLocal("#x").orElseThrow().getGeneratedName();
        String y = input.getSymbolTable().getRootScope().lookupLocal("#y").orElseThrow().getGeneratedName();

        check(generated.contains(x + " = spl_tmp_"), "assignment should use resolved x name");
        check(generated.contains(y + " = spl_tmp_"), "assignment should use resolved y name");
        check(generated.contains(" = 7;\n"), "literal term should be generated");
        check(generated.contains(" = " + x + ";\n"), "name term should use resolved name");
        check(generated.contains(" * ") && generated.contains(" + "), "nested term should reuse the term generator");
    }

    private static void shadowedAssignmentTest() throws Exception{

        CodeGenerationInput input = CodeGenerationFoundationTest.accepted(
            "#x : void #f ( #x ) { : : #x = 2 ; return } : #x = 1 ; "
        );

        List<TreeNode> assignments = new ArrayList<>();
        collect(input.getTree(), "ASSIGN", assignments);
        check(assignments.size() == 2, "expected assignments in both scopes");

        String firstTarget = assignments.get(0).getChildren().get(0).getResolvedSymbol().getGeneratedName();
        String secondTarget = assignments.get(1).getChildren().get(1).getResolvedSymbol().getGeneratedName();

        check(!firstTarget.equals(secondTarget), "shadowed variables should resolve to distinct names");

        TestGenerator generator = new TestGenerator(input);
        generator.assignment(assignments.get(0));
        generator.assignment(assignments.get(1));
        String generated = generator.getEmitter().render();

        check(generated.contains(firstTarget + " = "), "each assignment should use its own resolved symbol name");
    }

    private static void commentAndNopTest() throws Exception {
        CodeGenerationInput input = CodeGenerationFoundationTest.accepted(": : nop ; comment \"note\" ; ");

        String generated = new TestGenerator(input).generate(CodeGenerationFoundationTest.rootAlgo(input));

        check(generated.contains("/* nop */\n"), "nop should emit a comment");
        check(generated.contains("/* \"note\" */\n"), "comment instruction should emit a target comment");
        check(!generated.contains("nop;") && !generated.contains("comment;"), "nop and comment should not emit executable behaviour");
    }

    private static void branchExecutionTests() throws Exception {
        String source = "#x : : " + "if eq ( #x 1 ) then { #x = 2 ; } else { #x = 3 ; } ; " + "#x = add ( #x 1 ) ; ";

        CodeGenerationInput input = CodeGenerationFoundationTest.accepted(source);
        Symbol x = input.getSymbolTable().getRootScope().lookupLocal("#x").orElseThrow();

        String fragment = new TestGenerator(input).generate(CodeGenerationFoundationTest.rootAlgo(input));

        check(fragment.contains("/* else */"), "else should only appear as a comment");
        check(fragment.contains("else {"), "target code must not contain an else statement");
        check(count(fragment,"goto") >= 2, "branch needs explicit jumps");

        String functionTrue = function("spl_eval_0", x.getGeneratedName(), 1, fragment);
        String functionFalse = function("spl_eval_1", x.getGeneratedName(), 0, fragment);

        CodeGenerationFoundationTest.nativeBooleanCheck(
            List.of(functionTrue, functionFalse), "34"
        );

        nestedBranchExecutionTest();
    }

    private static void nestedBranchExecutionTest() throws Exception {
        String source = "#x : : " + "if eq ( #x 1 ) then { " + 
        "if eq ( #x 1 ) then { #x = 5 ; } else { #x = 6 ; } ; " + 
        "} else { #x = 7 ; } ; " + "#x = add ( #x 1 ) ; ";

        CodeGenerationInput input = CodeGenerationFoundationTest.accepted(source);
        String x = input.getSymbolTable().getRootScope().lookupLocal("#x").orElseThrow().getGeneratedName();
        String fragment = new TestGenerator(input).generate(CodeGenerationFoundationTest.rootAlgo(input));

        List<String> labels = new ArrayList<>();
        for (String line : fragment.split("\\R")){
            if (line.matches("spl_label_[0-9]+: ;")){
                labels.add(line.substring(0, line.indexOf(':')));
            }
        }
        check(labels.size() == 6, "nested branches should define six distinct labels");
        check(labels.stream().distinct().count() == labels.size(), "nested branch labels must be unique");

        CodeGenerationFoundationTest.nativeBooleanCheck(
            List.of(function("spl_eval_0",x,1,fragment), function("spl_eval_eval_1",x,0,fragment)), "68"
        );
    }

    private static String function(
        String name, String variable, int initialValue, String body
    ){
        return "int " + name + "() {\n" + "int " + variable + " = " + initialValue + ";\n" + body + "return " + variable + ";\n" + "}\n";
    }

    private static void collect(TreeNode node, String value, List<TreeNode> result){

        if (value.equals(node.getValue())){
            result.add(node);
        }
        for (TreeNode child : node.getChildren()){
            collect(child, value, result);
        }
    }

    private static int count(String text, String value){

        int count = 0;
        int i = 0;

        while ((i = text.indexOf(value, i)) >= 0){
            count++;
            i += value.length();
        }
        return count;
    }

    private static void check(boolean cond, String message){
        if (!cond){
            throw new AssertionError(message);
        }
    }

    //ALGO traversal only for tests (Phase 3a.8 work)
    private static final class TestGenerator extends AlgorithmicCodeGenerator{
        TestGenerator(CodeGenerationInput input){
            super(input);
        }

        @Override
        public void emitAlgo(TreeNode instruction){

            require(algo, "ALGO");

            if (algo.getChildren().isEmpty()){
                emitter.emitComment("end");
                return;
            }

            for (TreeNode child : algo.getChildren()){

                if ("INSTR".equals(child.getValue())){
                    emitInstruction(child);
                }
                else if ("ALGO".equals(child.getValue())){
                    emitAlgo(child);
                }
            }
        }

        @Override
        public void emitInstruction(TreeNode instruction){

            require(instruction, "INSTR");

            TreeNode branch = child(instruction, "BRANCH");

            if (branch != null){
                emitBranch(branch);
            }
            else{
                emitSimpleInstruction(instruction);
            }
        }

        void assignment(TreeNode node){
            emitAssignment(node);
        }

        private TreeNode child(TreeNode node, String value){
            for (TreeNode child: node.getChildren()){
                if (value.equals(child.getValue())){
                    return child;
                }
            }
            return null;
        }
    }
}