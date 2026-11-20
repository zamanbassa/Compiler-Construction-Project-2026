package spl.codegen;

import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.LinkedHashSet;
import java.util.Set;
import spl.semantic.*;
import spl.tree.TreeNode;

/** Read-only handoff validation, not name resolution or type inference.
 * Caller must not mutate the tree/table during a generation run.
 */
public final class CodeGenerationInput {
    private final TreeNode tree;
    private final SymbolTable symbols;
    private final NumericMode mode;
    private final Set<TreeNode> nodes = Collections.newSetFromMap(new IdentityHashMap<>());
    private final Set<Symbol> declarations = Collections.newSetFromMap(new IdentityHashMap<>());
    private final Set<String> reserved = new LinkedHashSet<>();

    public CodeGenerationInput(Phase2bResult result) {
        this(requireResult(result).getTree(), result.getSymbolTable(), result.getNumericMode());
    }
    private static Phase2bResult requireResult(Phase2bResult r) {
        if (r == null) throw new CodeGenerationException("missing Phase 2b result");
        return r;
    }
    /** For integrations already holding successful Phase 2b output and an explicit mode. */
    public CodeGenerationInput(TreeNode tree, SymbolTable symbols, NumericMode mode) {
        if (tree == null || symbols == null || mode == null)
            throw new CodeGenerationException("accepted tree, original symbol table and numeric mode are required");
        this.tree = tree; this.symbols = symbols; this.mode = mode;
        if (tree.getType() != SemanticType.OK)
            throw new CodeGenerationException(tree, "expected an accepted Phase 2b tree of type OK");
        collect(symbols.getRootScope());
        validate(tree);
    }
    private void collect(Scope scope) {
        for (Symbol symbol : scope.getDeclarations()) {
            declarations.add(symbol);
            if (!symbol.getGeneratedName().matches("[A-Za-z_][A-Za-z0-9_]*"))
                throw new CodeGenerationException(symbol.getDeclarationNode(), "invalid generated target identifier");
            if (!reserved.add(symbol.getGeneratedName()))
                throw new CodeGenerationException(symbol.getDeclarationNode(), "duplicate generated target name");
        }
        for (Scope child : scope.getChildScopes()) collect(child);
    }
    private void validate(TreeNode node) {
        if (!nodes.add(node)) throw new CodeGenerationException(node, "cycle or shared node in input tree");
        if (node.getType() == SemanticType.UNKNOWN)
            throw new CodeGenerationException(node, "missing Phase 2b type annotation");
        String v = node.getValue();
        if (node.isTerminal() && v.startsWith("#")) {
            Symbol binding = node.getResolvedSymbol();
            if (binding == null || !declarations.contains(binding))
                throw new CodeGenerationException(node, "missing resolved symbol in original table");
            if (binding.getType() == SemanticType.UNKNOWN || binding.getType() != node.getType())
                throw new CodeGenerationException(node, "inconsistent resolved symbol/type annotation");
        }
        if (node.isTerminal() && ((mode == NumericMode.REAL && "mod".equals(v))
                || (mode == NumericMode.WHOLE && ("div".equals(v)
                    || v.matches("-?[0-9]+\\.[0-9]+")))))
            throw new CodeGenerationException(node, "numeric mode contradicts accepted program");
        for (TreeNode child : node.getChildren()) validate(child);
    }
    public TreeNode getTree() { return tree; }
    public SymbolTable getSymbolTable() { return symbols; }
    public NumericMode getNumericMode() { return mode; }
    public Set<String> getReservedNames() { return Collections.unmodifiableSet(reserved); }
    public void requireNode(TreeNode node, String production) {
        if (node == null || !nodes.contains(node)) throw new CodeGenerationException(node, "node is not in the accepted tree");
        if (!production.equals(node.getValue())) throw new CodeGenerationException(node, "expected " + production);
    }
    /** Reject only the selected translation subtree; unused function bodies are not generated. */
    public void requireNoCalls(TreeNode node) {
        if ("CALL".equals(node.getValue())) throw new CodeGenerationException(node, "CALL translation is deferred to Phase 3b");
        for (TreeNode child : node.getChildren()) requireNoCalls(child);
    }
}
