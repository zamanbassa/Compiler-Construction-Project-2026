package spl.codegen;

import spl.tree.TreeNode;

/** Shared translator contract. All methods append to one run's CodeEmitter.
 * emitTerm assigns to an already declared result variable.
 * emitBool transfers control to exactly one destination; no implicit fall-through.
 */
public interface CodeGenerator {
    void emitAlgo(TreeNode algo);
    void emitInstruction(TreeNode instruction);
    void emitTerm(TreeNode term, String resultVariable);
    void emitBool(TreeNode bool, String trueLabel, String falseLabel);
}
