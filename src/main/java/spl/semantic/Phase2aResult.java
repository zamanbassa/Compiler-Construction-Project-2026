package spl.semantic;

import java.util.Objects;
import spl.tree.TreeNode;

/** Result passed from Phase 2a to later semantic-analysis phases. */
public final class Phase2aResult {
    private final TreeNode resolvedTree;
    private final SymbolTable symbolTable;

    public Phase2aResult(TreeNode resolvedTree, SymbolTable symbolTable) {
        this.resolvedTree = Objects.requireNonNull(resolvedTree, "resolvedTree");
        this.symbolTable = Objects.requireNonNull(symbolTable, "symbolTable");
    }

    public TreeNode getResolvedTree() {
        return resolvedTree;
    }

    public SymbolTable getSymbolTable() {
        return symbolTable;
    }
}