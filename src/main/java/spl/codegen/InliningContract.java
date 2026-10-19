package spl.codegen;

import java.util.List;
import java.util.Optional;
import spl.semantic.Symbol;
import spl.tree.TreeNode;

/** Shared Phase 3b contracts. Metadata refers to accepted objects; no emission occurs here. */
public final class InliningContract {
    private InliningContract() { }
    public interface FunctionTemplate {
        Symbol getSymbol();
        TreeNode getDeclaration();
        List<Symbol> getParameters();
        List<Symbol> getLocals();
        List<TreeNode> getNestedDeclarations();
        TreeNode getBodyProgram();
        TreeNode getAlgorithm();
        Optional<TreeNode> getReturnTerm();
    }
    /** Instance-specific storage, keyed by ORIGINAL Symbol identity, never source spelling.
     * Own formals/locals get new slots. Free variables delegate to the enclosing environment.
     * Missing storage must produce CodeGenerationException, not an invented new binding.
     */
    public interface LexicalEnvironment {
        String storageFor(Symbol symbol, TreeNode useSite);
        Optional<LexicalEnvironment> getParent();
    }
    /** One allocator shared across the entire output; reserve original generated names.
     * Variable and label names must be mutually collision-free and deterministic per run.
     */
    public interface TargetNames {
        String newVariable();
        String newLabel();
    }
    public interface InlineInstance {
        FunctionTemplate getTemplate();
        LexicalEnvironment getEnvironment();
        /** Stable within this instance; fresh across instances, including copied child labels. */
        String labelFor(String templateLabel);
        ReturnResult getResult();
    }
    public interface ReturnResult {
        /** Empty for void; otherwise typed per-instance return storage. */
        Optional<String> getValueSlot();
    }
}
