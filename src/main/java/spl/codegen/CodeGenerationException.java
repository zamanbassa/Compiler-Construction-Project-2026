package spl.codegen;

import spl.tree.TreeNode;

/** A backend contract/translation error, distinct from Phase 2 diagnostics. */
public final class CodeGenerationException extends RuntimeException {
    public CodeGenerationException(String message) { super("Code generation error: " + message); }
    public CodeGenerationException(TreeNode node, String message) {
        super("Code generation error at node " + (node == null ? "<missing>" :
                node.getNodeId() + " (" + node.getValue() + ")") + ": " + message);
    }
}
