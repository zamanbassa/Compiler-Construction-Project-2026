package spl.codegen;

import java.util.List;
import spl.semantic.SemanticType;
import spl.semantic.Symbol;
import spl.semantic.SymbolKind;
import spl.tree.TreeNode;
import java.util.ArrayList;

/** Phase 3a fragment translator: algorithms, terms, instructions, conditions and loops. */
public class AlgorithmCodeGenerator implements CodeGenerator {

    protected final CodeGenerationInput input;
    protected final CodeEmitter emitter;
    private final InliningContract.LexicalEnvironment environment;
    private final boolean ownsEmitter;

    public AlgorithmCodeGenerator(CodeGenerationInput input) {
        this(input, new CodeEmitter(input), new RootEnvironment(), true);
    }

    AlgorithmCodeGenerator(CodeGenerationInput input, CodeEmitter emitter,
                           InliningContract.LexicalEnvironment environment) {
        this(input, emitter, environment, false);
    }

    private AlgorithmCodeGenerator(CodeGenerationInput input, CodeEmitter emitter,
                                   InliningContract.LexicalEnvironment environment,
                                   boolean ownsEmitter) {
        if (input == null) {
            throw new CodeGenerationException("missing input");
        }
        if (emitter == null || !emitter.usesInput(input)) {
            throw new CodeGenerationException("emitter must use the accepted input");
        }
        if (environment == null) {
            throw new CodeGenerationException("missing lexical environment");
        }
        this.input = input;
        this.emitter = emitter;
        this.environment = environment;
        this.ownsEmitter = ownsEmitter;
    }

    /**
     * Explicit ALGO entry; independent calls reset all emission state.
     */
    public final String generate(TreeNode algo) {
        if (!ownsEmitter) {
            throw new CodeGenerationException("cannot reset a shared emitter; emit into it directly");
        }
        emitter.reset();
        input.requireNode(algo, "ALGO");
        input.requireNoCalls(algo);
        emitAlgo(algo);
        return emitter.render();
    }

    public CodeEmitter getEmitter() {
        return emitter;
    }

    /** Validate a term through the normal translator without mutating shared output. */
    final void validateTerm(TreeNode term) {
        CodeEmitter validationEmitter = new CodeEmitter(input);
        AlgorithmCodeGenerator validator =
                new AlgorithmCodeGenerator(input, validationEmitter, environment);
        validator.emitTerm(term, "spl_validation_sink");
        validationEmitter.render();
    }

    protected final void require(TreeNode node, String production) {
        input.requireNode(node, production);
        input.requireNoCalls(node);
    }

    private void unsupported(TreeNode node, String production) {
        require(node, production);
        throw new CodeGenerationException(node, production + " translator not installed (separate Phase 3a issue)");
    }

    @Override
    public void emitAlgo(TreeNode algo) {
        require(algo, "ALGO");
        // Iterative traversal avoids a Java stack frame per source instruction.
        TreeNode current = algo;
        while (true) {
            List<TreeNode> children = current.getChildren();
            if (children.isEmpty()) { emitter.emitComment("end"); return; }
            if (children.size() != 3 || !"INSTR".equals(children.get(0).getValue())
                    || !isToken(children.get(1), ";") || !"ALGO".equals(children.get(2).getValue()))
                throw new CodeGenerationException(current, "malformed ALGO production");
            emitInstruction(children.get(0));
            current = children.get(2);
        }
    }

    @Override
    public void emitInstruction(TreeNode instruction) {
        require(instruction, "INSTR");
        if (instruction.getType() != SemanticType.OK)
            throw new CodeGenerationException(instruction, "INSTR must have type OK");
        TreeNode branch = child(instruction, "BRANCH");
        TreeNode loop = child(instruction, "LOOP");
        if (branch != null) emitBranch(branch);
        else if (loop != null) emitLoop(loop);
        else emitSimpleInstruction(instruction);
    }

    @Override
    public void emitTerm(TreeNode term, String resultVariable) {
        require(term, "TERM");

        if (resultVariable == null
                || !resultVariable.matches("[A-Za-z_][A-Za-z0-9_]*")) {
            throw new CodeGenerationException(term, "invalid result variable");
        }
        if (term.getType() != SemanticType.NUMERIC) {
            throw new CodeGenerationException(term, "TERM must have type NUMERIC");
        }

        List<TreeNode> children = term.getChildren();

        if (children.size() == 1) {
            TreeNode leaf = children.get(0);

            if (!leaf.isTerminal()) {
                if ("CALL".equals(leaf.getValue())) {
                    throw new CodeGenerationException(
                            leaf, "CALL translation is deferred to Phase 3b");
                }
                throw new CodeGenerationException(term, "malformed TERM leaf");
            }

            String value = leaf.getValue();

            if (value.startsWith("#")) {
                Symbol symbol = leaf.getResolvedSymbol();
                if (symbol == null) {
                    throw new CodeGenerationException(leaf, "variable has no resolved symbol");
                }
                if (symbol.getKind() != SymbolKind.VARIABLE) {
                    throw new CodeGenerationException(leaf, "TERM reference must be a variable");
                }
                if (symbol.getType() != SemanticType.NUMERIC
                        || leaf.getType() != SemanticType.NUMERIC) {
                    throw new CodeGenerationException(leaf, "variable must have type NUMERIC");
                }

                String storage = environment.storageFor(symbol, leaf);
                emitter.emitStatement(resultVariable + " = " + storage);
                return;
            }

            if (value.matches("-?(?:0|[1-9][0-9]*)(?:\\.[0-9]+)?")) {
                if (leaf.getType() != SemanticType.NUMERIC) {
                    throw new CodeGenerationException(leaf, "numeric literal must have type NUMERIC");
                }
                emitter.emitStatement(resultVariable + " = " + value);
                return;
            }

            throw new CodeGenerationException(term, "unsupported TERM leaf: " + value);
        }

        if (children.isEmpty() || !children.get(0).isTerminal()) {
            throw new CodeGenerationException(term, "malformed TERM production");
        }

        String operator = children.get(0).getValue();

        switch (operator) {
            case "add":
            case "sub":
            case "mul":
            case "div":
            case "mod": {
                if (children.size() != 5
                        || !isToken(children.get(1), "(")
                        || !isTermNode(children.get(2))
                        || !isTermNode(children.get(3))
                        || !isToken(children.get(4), ")")) {
                    throw new CodeGenerationException(
                            term, "malformed binary TERM production for " + operator);
                }

                TreeNode left = children.get(2);
                TreeNode right = children.get(3);
                if (left.getType() != SemanticType.NUMERIC
                        || right.getType() != SemanticType.NUMERIC) {
                    throw new CodeGenerationException(
                            term, operator + " operands must have type NUMERIC");
                }

                String leftTemporary = emitter.newTemporary();
                String rightTemporary = emitter.newTemporary();

                emitTerm(left, leftTemporary);
                emitTerm(right, rightTemporary);

                String targetOperator;
                switch (operator) {
                    case "add":
                        targetOperator = "+";
                        break;
                    case "sub":
                        targetOperator = "-";
                        break;
                    case "mul":
                        targetOperator = "*";
                        break;
                    case "div":
                        targetOperator = "/";
                        break;
                    default:
                        targetOperator = "%";
                        break;
                }

                emitter.emitStatement(
                        resultVariable + " = " + leftTemporary
                        + " " + targetOperator + " " + rightTemporary);
                return;
            }

            case "neg": {
                if (children.size() != 4
                        || !isToken(children.get(1), "(")
                        || !isTermNode(children.get(2))
                        || !isToken(children.get(3), ")")) {
                    throw new CodeGenerationException(term, "malformed neg TERM production");
                }

                TreeNode operand = children.get(2);
                if (operand.getType() != SemanticType.NUMERIC) {
                    throw new CodeGenerationException(term, "neg operand must have type NUMERIC");
                }

                String operandTemporary = emitter.newTemporary();
                emitTerm(operand, operandTemporary);
                emitter.emitStatement(resultVariable + " = -" + operandTemporary);
                return;
            }

            default:
                throw new CodeGenerationException(term, "unsupported TERM operator: " + operator);
        }
    }

    @Override
    public void emitBool(TreeNode bool, String trueLabel, String falseLabel) {
        require(bool, "BOOL");

        if (bool.getType() != SemanticType.BOOLEAN) {
            throw new CodeGenerationException(bool, "BOOL must have type BOOLEAN");
        }

        List<TreeNode> children = bool.getChildren();
        if (children.isEmpty() || !children.get(0).isTerminal()) {
            throw new CodeGenerationException(bool, "malformed BOOL production");
        }

        String operator = children.get(0).getValue();

        switch (operator) {
            case "not": {
                if (children.size() != 4
                        || !isToken(children.get(1), "(")
                        || !isBoolNode(children.get(2))
                        || !isToken(children.get(3), ")")) {
                    throw new CodeGenerationException(bool, "malformed not BOOL production");
                }

                TreeNode operand = children.get(2);
                if (operand.getType() != SemanticType.BOOLEAN) {
                    throw new CodeGenerationException(
                            operand, "not operand must have type BOOLEAN");
                }

                emitBool(operand, falseLabel, trueLabel);
                return;
            }

            case "and":
            case "or": {
                if (children.size() != 5
                        || !isToken(children.get(1), "(")
                        || !isBoolNode(children.get(2))
                        || !isBoolNode(children.get(3))
                        || !isToken(children.get(4), ")")) {
                    throw new CodeGenerationException(
                            bool, "malformed " + operator + " BOOL production");
                }

                TreeNode left = children.get(2);
                TreeNode right = children.get(3);
                if (left.getType() != SemanticType.BOOLEAN
                        || right.getType() != SemanticType.BOOLEAN) {
                    throw new CodeGenerationException(
                            bool, operator + " operands must have type BOOLEAN");
                }

                String nextLabel = emitter.newLabel();
                if ("and".equals(operator)) {
                    emitBool(left, nextLabel, falseLabel);
                } else {
                    emitBool(left, trueLabel, nextLabel);
                }

                emitter.emitLabel(nextLabel);
                emitBool(right, trueLabel, falseLabel);
                return;
            }

            case "eq":
            case "larger":
            case "lesser": {
                if (children.size() != 5
                        || !isToken(children.get(1), "(")
                        || !isTermNode(children.get(2))
                        || !isTermNode(children.get(3))
                        || !isToken(children.get(4), ")")) {
                    throw new CodeGenerationException(
                            bool, "malformed " + operator + " BOOL production");
                }

                TreeNode left = children.get(2);
                TreeNode right = children.get(3);
                if (left.getType() != SemanticType.NUMERIC
                        || right.getType() != SemanticType.NUMERIC) {
                    throw new CodeGenerationException(
                            bool, operator + " operands must have type NUMERIC");
                }

                String leftTemporary = emitter.newTemporary();
                String rightTemporary = emitter.newTemporary();

                emitTerm(left, leftTemporary);
                emitTerm(right, rightTemporary);

                String comparison;
                switch (operator) {
                    case "eq":
                        comparison = "==";
                        break;
                    case "larger":
                        comparison = ">";
                        break;
                    default:
                        comparison = "<";
                        break;
                }

                emitter.emitConditionalGoto(
                        leftTemporary + " " + comparison + " " + rightTemporary,
                        trueLabel);
                emitter.emitGoto(falseLabel);
                return;
            }

            default:
                throw new CodeGenerationException(
                        bool, "unsupported BOOL operator: " + operator);
        }
    }

    protected void emitOutput(TreeNode output){

        require(output, "OUTP");
        TreeNode term = child(output, "TERM");

        if (term != null){

            String temp = emitter.newTemporary();
            emitTerm(term, temp);
            emitter.emitStatement("std::cout << " + temp);
            return;
        }

        if (output.getChildren().size() == 1 ){

            TreeNode string = output.getChildren().get(0);
            if (string.isTerminal() && string.getValue().startsWith("\"") && string.getValue().endsWith("\"")){
                emitter.emitStatement("std::cout << " + string.getValue());
                return;
            }
        }

        throw new CodeGenerationException(output, "malformed OUTP");
    }

    protected void emitAssignment(TreeNode assignment){

        require(assignment, "ASSIGN");

        if (assignment.getChildren().isEmpty()){
            throw new CodeGenerationException(assignment, "malformed ASSIGN");
        }

        TreeNode target = assignment.getChildren().get(0);
        Symbol symbol = target.getResolvedSymbol();

        if (symbol == null || symbol.getKind() != SymbolKind.VARIABLE){
            throw new CodeGenerationException(target, "assignment target must be a variable");
        }

        if (symbol.getType() != SemanticType.NUMERIC || target.getType() != SemanticType.NUMERIC){
            throw new CodeGenerationException(target, "assignment target must be NUMERIC");
        }

        TreeNode term = child(assignment, "TERM");
        if (term == null || term.getType() != SemanticType.NUMERIC){
            throw new CodeGenerationException(assignment, "assignment value must be be NUMERIC");
        }

        String temp = emitter.newTemporary();
        emitTerm(term, temp);
        String storage = environment.storageFor(symbol, target);
        emitter.emitStatement(storage + " = " + temp);

    }

    protected void emitSimpleInstruction(TreeNode instruction){

        require(instruction, "INSTR");

        TreeNode outp = child(instruction, "OUTP");

        if (outp != null){
            emitOutput(outp);
            return;
        }

        TreeNode assignment = child(instruction, "ASSIGN");

        if (assignment != null){
            emitAssignment(assignment);
            return;
        }

        if (hasToken(instruction, "nop")){
            emitter.emitComment("nop");
            return;
        }

        if (hasToken(instruction, "comment")){

            TreeNode string = null;

            for (TreeNode child : instruction.getChildren()){

                if (child.isTerminal() && child.getValue().startsWith("\"") && child.getValue().endsWith("\"")){
                    string = child;
                    break;
                }
            }

            if (string == null){
                throw new CodeGenerationException(instruction, "comment is missing its STRING");
            }

            emitter.emitComment(string.getValue());
            return;
        }

        throw new CodeGenerationException(instruction, "instruction is not handled by this translator");

    }

    public void emitBranch(TreeNode branch){

        require(branch, "BRANCH");

        TreeNode condition = child(branch, "BOOL");
        List<TreeNode> algorithms = new ArrayList<>();

        for (TreeNode child : branch.getChildren()){
            if ("ALGO".equals(child.getValue())){
                algorithms.add(child);
            }
        }

        if (condition == null || algorithms.size() != 2){
            throw new CodeGenerationException(branch, "malformed BRANCH");
        }

        TreeNode trueAlgo = algorithms.get(0);
        TreeNode falseAlgo = algorithms.get(1);

        String trueLabel = emitter.newLabel();
        String falseLabel = emitter.newLabel();
        String doneLabel = emitter.newLabel();

        emitter.emitComment("if");
        emitBool(condition, trueLabel, falseLabel);

        emitter.emitLabel(falseLabel);
        emitter.emitComment("else");
        emitAlgo(falseAlgo);
        emitter.emitGoto(doneLabel);

        emitter.emitLabel(trueLabel);
        emitter.emitComment("then");
        emitAlgo(trueAlgo);

        emitter.emitLabel(doneLabel);
        emitter.emitComment("continue");
    }

    // Translate one LOOP node into labelled jumps
    public void emitLoop(TreeNode loop) {
        require(loop, "LOOP");

        List<TreeNode> children = loop.getChildren();
        boolean preTest = !children.isEmpty()
                && "COND".equals(children.get(0).getValue());

        TreeNode cond = child(loop, "COND");
        TreeNode bool = child(loop, "BOOL");
        TreeNode algo = child(loop, "ALGO");
        if (cond == null || bool == null || algo == null) {
            throw new CodeGenerationException(loop, "malformed LOOP production");
        }
        if (bool.getType() != SemanticType.BOOLEAN) {
            throw new CodeGenerationException(bool, "loop condition must be BOOLEAN");
        }

        String kind = cond.getChildren().isEmpty()
                ? "" : cond.getChildren().get(0).getValue();
        if (!"while".equals(kind) && !"until".equals(kind)) {
            throw new CodeGenerationException(cond, "COND must be while or until");
        }

        if (preTest) {
            emitPreTest(kind, bool, algo);
        } else {
            emitPostTest(kind, bool, algo);
        }
    }

    // Pre-test until exit when true while: same skeleton, labels swapped
    private void emitPreTest(String kind, TreeNode bool, TreeNode algo) {
        String top  = emitter.newLabel();
        String body = emitter.newLabel();
        String exit = emitter.newLabel();

        emitter.emitLabel(top);
        if ("until".equals(kind)) {
            emitBool(bool, exit, body);
        } else {
            emitBool(bool, body, exit);
        }
        emitter.emitLabel(body);
        emitAlgo(algo);
        emitter.emitGoto(top);
        emitter.emitLabel(exit);
    }

    // Post-test do-while: repeat when true do-until: same skeleton, labels swapped
    private void emitPostTest(String kind, TreeNode bool, TreeNode algo) {
        String top  = emitter.newLabel();
        String exit = emitter.newLabel();

        emitter.emitLabel(top);
        emitAlgo(algo);
        if ("while".equals(kind)) {
            emitBool(bool, top, exit);
        } else {
            emitBool(bool, exit, top);
        }
        emitter.emitLabel(exit);
    }


    private TreeNode child(TreeNode node, String value){
        for (TreeNode n : node.getChildren()){
            if (value.equals(n.getValue())){
                return n;
            }
        }
        return null;
    }

    private boolean hasToken(TreeNode node, String value){
        for (TreeNode n : node.getChildren()){
            if (n.isTerminal() && value.equals(n.getValue())){
                return true;
            }
        }
        return false;
    }

    private boolean isToken(TreeNode node, String value) {
        return node != null && node.isTerminal() && value.equals(node.getValue());
    }

    private boolean isTermNode(TreeNode node) {
        return node != null && !node.isTerminal() && "TERM".equals(node.getValue());
    }

    private boolean isBoolNode(TreeNode node) {
        return node != null && !node.isTerminal() && "BOOL".equals(node.getValue());
    }
}
