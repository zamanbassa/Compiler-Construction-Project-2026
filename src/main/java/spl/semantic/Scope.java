package spl.semantic;

import java.util.AbstractMap;
import java.util.AbstractSet;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import spl.tree.TreeNode;

public final class Scope {
    private static final AtomicInteger NEXT_SCOPE_ID = new AtomicInteger();

    private final int scopeId;
    private final Scope parent;
    private final List<Scope> childScopes = new ArrayList<>();
    private final Map<String, Symbol> variables = new LinkedHashMap<>();
    private final Map<String, Symbol> functions = new LinkedHashMap<>();
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

    /** Returns declarations made directly in this scope, containing all variable and function symbols. */
    public Map<String, Symbol> getDeclarations() {
        return new AbstractMap<>() {
            @Override
            public int size() {
                return variables.size() + functions.size();
            }

            @Override
            public boolean isEmpty() {
                return variables.isEmpty() && functions.isEmpty();
            }

            @Override
            public boolean containsKey(Object key) {
                return variables.containsKey(key) || functions.containsKey(key);
            }

            @Override
            public Symbol get(Object key) {
                Symbol s = variables.get(key);
                return s != null ? s : functions.get(key);
            }

            @Override
            public Collection<Symbol> values() {
                List<Symbol> all = new ArrayList<>(variables.size() + functions.size());
                all.addAll(variables.values());
                all.addAll(functions.values());
                return Collections.unmodifiableList(all);
            }

            @Override
            public Set<Entry<String, Symbol>> entrySet() {
                List<Entry<String, Symbol>> entries = new ArrayList<>(size());
                for (Symbol s : variables.values()) {
                    entries.add(new SimpleImmutableEntry<>(s.getOriginalName(), s));
                }
                for (Symbol s : functions.values()) {
                    entries.add(new SimpleImmutableEntry<>(s.getOriginalName(), s));
                }
                return new AbstractSet<>() {
                    @Override
                    public Iterator<Entry<String, Symbol>> iterator() {
                        return Collections.unmodifiableList(entries).iterator();
                    }

                    @Override
                    public int size() {
                        return entries.size();
                    }
                };
            }
        };
    }

    /** Looks up a declaration in this scope only (searching variables first, then functions). */
    public Optional<Symbol> lookupLocal(String sourceName) {
        Objects.requireNonNull(sourceName, "sourceName");
        Symbol symbol = variables.get(sourceName);
        if (symbol != null) {
            return Optional.of(symbol);
        }
        return Optional.ofNullable(functions.get(sourceName));
    }

    /** Looks up a declaration of the specified kind in this scope only. */
    public Optional<Symbol> lookupLocal(String sourceName, SymbolKind kind) {
        Objects.requireNonNull(sourceName, "sourceName");
        Objects.requireNonNull(kind, "kind");
        Map<String, Symbol> target = kind == SymbolKind.FUNCTION ? functions : variables;
        return Optional.ofNullable(target.get(sourceName));
    }

    /** Adds a declaration, rejecting duplicate source names of the same kind in this scope. */
    public void declare(Symbol symbol) {
        Objects.requireNonNull(symbol, "symbol");
        if (symbol.getDeclaringScope() != this) {
            throw new IllegalArgumentException("symbol belongs to a different declaring scope");
        }
        Map<String, Symbol> target = symbol.getKind() == SymbolKind.FUNCTION ? functions : variables;
        if (target.containsKey(symbol.getOriginalName())) {
            throw new DuplicateDeclarationException(symbol.getOriginalName(), this);
        }
        target.put(symbol.getOriginalName(), symbol);
    }
}