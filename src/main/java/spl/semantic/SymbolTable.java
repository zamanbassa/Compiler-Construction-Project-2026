package spl.semantic;

import java.util.Objects;
import java.util.Optional;
import spl.tree.TreeNode;

public final class SymbolTable {//MAIN INTERFACE
    private final Scope rootScope;
    private final GeneratedNameGenerator generatedNameGenerator;
    private Scope currentScope;

    /** Creates a table with an empty root scope. */
    public SymbolTable() {
        rootScope = new Scope(null, null);
        currentScope = rootScope;
        generatedNameGenerator = new GeneratedNameGenerator();
    }

    public Scope getRootScope() {
        return rootScope;
    }

    public Scope getCurrentScope() {
        return currentScope;
    }

    /** Enters an anonymous child scope. */
    public Scope enterScope() {
        return enterScope(null);
    }

    /** Enters a child scope owned by the supplied syntax-tree node. */
    public Scope enterScope(TreeNode ownerNode) {
        currentScope = new Scope(currentScope, ownerNode);
        return currentScope;
    }

    /**
     * Exits the current scope and returns the scope that was exited.
     * The root scope cannot be exited.
     */
    public Scope exitScope() {
        if (currentScope.getParent() == null) {
            throw new ScopeException("cannot exit the root scope");
        }
        Scope exited = currentScope;
        currentScope = currentScope.getParent();
        return exited;
    }

    /** Declares a symbol and generates its internal name automatically. */
    public Symbol declare(String originalName, SymbolKind kind, TreeNode declarationNode) {
        return declare(originalName, kind, null, declarationNode);
    }

    /** Declares a symbol, using the supplied generated name when non-null. */
    public Symbol declare(
            String originalName,
            SymbolKind kind,
            String generatedName,
            TreeNode declarationNode) {
        Objects.requireNonNull(kind, "kind");
        String internalName = generatedName == null || generatedName.isBlank()
                ? generatedNameGenerator.generate(kind)
                : generatedName;
        Symbol symbol = new Symbol(
                originalName,
                internalName,
                kind,
                currentScope,
                declarationNode);
        currentScope.declare(symbol);
        return symbol;
    }

    /** Looks up a source name in the current scope only. */
    public Optional<Symbol> lookupLocal(String sourceName) {
        return currentScope.lookupLocal(sourceName);
    }

    /** Resolves a source name in the current scope and then its ancestors. */
    public Optional<Symbol> resolve(String sourceName) {
        Objects.requireNonNull(sourceName, "sourceName");
        for (Scope scope = currentScope; scope != null; scope = scope.getParent()) {
            Optional<Symbol> symbol = scope.lookupLocal(sourceName);
            if (symbol.isPresent()) {
                return symbol;
            }
        }
        return Optional.empty();
    }
}
