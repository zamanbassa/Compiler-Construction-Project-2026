package spl.semantic;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import spl.tree.TreeNode;

/**
 * One lexical scope in the Phase 2a scope tree.
 *
 * <p>Declarations belong directly to one scope. Lookup through ancestors is
 * deliberately implemented by {@link SymbolTable#resolve(String)} so that
 * {@link #lookupLocal(String)} remains strictly local.</p>
 */
public final class Scope {
    private static final AtomicInteger NEXT_SCOPE_ID = new AtomicInteger();

    private final int scopeId;
    private final Scope parent;
    private final List<Scope> childScopes = new ArrayList<>();
    private final Map<String, Symbol> declarations = new LinkedHashMap<>();
    private final TreeNode ownerNode;

    /** Creates a scope and automatically attaches it to its parent. */
    public Scope(Scope parent, TreeNode ownerNode) {
        this.scopeId = NEXT_SCOPE_ID.getAndIncrement();
        this.parent = parent;
        this.ownerNode = ownerNode;
        if (parent != null) {
            parent.childScopes.add(this);
        }
    }

    public int getScopeId() {
        return scopeId;
    }

    public Scope getParent() {
        return parent;
    }

    public List<Scope> getChildScopes() {
        return Collections.unmodifiableList(childScopes);
    }

    public TreeNode getOwnerNode() {
        return ownerNode;
    }

    /** Returns declarations made directly in this scope, keyed by source name. */
    public Map<String, Symbol> getDeclarations() {
        return Collections.unmodifiableMap(declarations);
    }

    /** Looks up a declaration in this scope only. */
    public Optional<Symbol> lookupLocal(String sourceName) {
        Objects.requireNonNull(sourceName, "sourceName");
        return Optional.ofNullable(declarations.get(sourceName));
    }

    /** Adds a declaration, rejecting duplicate source names in this scope. */
    public void declare(Symbol symbol) {
        Objects.requireNonNull(symbol, "symbol");
        if (symbol.getDeclaringScope() != this) {
            throw new IllegalArgumentException("symbol belongs to a different declaring scope");
        }
        if (declarations.containsKey(symbol.getOriginalName())) {
            throw new DuplicateDeclarationException(symbol.getOriginalName(), this);
        }
        declarations.put(symbol.getOriginalName(), symbol);
    }
}
