package spl.semantic;

import spl.tree.TreeNode;

/** Phase 2b failure, distinct from Phase 2a name-resolution diagnostics. */
public final class TypeAnalysisException extends RuntimeException {
    public TypeAnalysisException(String message) {
        super("Type analysis error: " + message);
    }

    /** Includes a syntax-node ID for diagnostics (not a source line number). */
    public TypeAnalysisException(TreeNode node, String message) {
        super("Type analysis error at node " + node.getNodeId()
                + " (" + node.getValue() + "): " + message);
    }
}
