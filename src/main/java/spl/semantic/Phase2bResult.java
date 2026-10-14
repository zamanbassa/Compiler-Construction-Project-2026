package spl.semantic;

import java.util.Objects;
import spl.tree.TreeNode;

/** Successful Phase 2b handoff. Retains original objects, not copies. */
public final class Phase2bResult {
    private final TreeNode tree;
    private final SymbolTable symbols;
    private final NumericMode numericMode;
    Phase2bResult(TreeNode tree, SymbolTable symbols, NumericMode numericMode) {
        this.tree = Objects.requireNonNull(tree);
        this.symbols = Objects.requireNonNull(symbols);
        this.numericMode = Objects.requireNonNull(numericMode);
    }
    public TreeNode getTree() { return tree; }
    public SymbolTable getSymbolTable() { return symbols; }
    public NumericMode getNumericMode() { return numericMode; }
}
