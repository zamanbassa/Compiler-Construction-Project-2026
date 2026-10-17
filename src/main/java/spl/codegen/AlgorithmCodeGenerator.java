package spl.codegen;

import spl.tree.TreeNode;

/** Foundation for issues 3a.3–3a.8. Override hooks as translators are implemented.
 * This class deliberately does not pretend to translate unimplemented productions.
 */
public class AlgorithmCodeGenerator implements CodeGenerator {
    protected final CodeGenerationInput input;
    protected final CodeEmitter emitter;
    public AlgorithmCodeGenerator(CodeGenerationInput input) {
        if (input == null) throw new CodeGenerationException("missing input");
        this.input = input; this.emitter = new CodeEmitter(input);
    }
    /** Explicit ALGO entry; independent calls reset all emission state. */
    public final String generate(TreeNode algo) {
        emitter.reset();
        input.requireNode(algo, "ALGO");
        input.requireNoCalls(algo);
        emitAlgo(algo);
        return emitter.render();
    }
    public CodeEmitter getEmitter() { return emitter; }
    protected final void require(TreeNode node, String production) {
        input.requireNode(node, production); input.requireNoCalls(node);
    }
    private void unsupported(TreeNode node, String production) {
        require(node, production);
        throw new CodeGenerationException(node, production + " translator not installed (separate Phase 3a issue)");
    }
    @Override public void emitAlgo(TreeNode algo) { unsupported(algo, "ALGO"); }
    @Override public void emitInstruction(TreeNode instruction) { unsupported(instruction, "INSTR"); }
    @Override public void emitTerm(TreeNode term, String resultVariable) { unsupported(term, "TERM"); }
    @Override public void emitBool(TreeNode bool, String trueLabel, String falseLabel) { unsupported(bool, "BOOL"); }
}
