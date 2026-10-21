package spl.codegen;

import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import spl.semantic.Symbol;
import spl.tree.TreeNode;

public final class InlineEnvironment implements InliningContract.LexicalEnvironment {

    private final InliningContract.LexicalEnvironment parent;
    private final Map<Symbol, String> slots = new IdentityHashMap<>();

    public InlineEnvironment(InliningContract.LexicalEnvironment parent,
                             List<Symbol> ownSymbols,
                             InliningContract.TargetNames names) {
        if (parent == null) {
            throw new CodeGenerationException("inline environment requires an enclosing environment");
        }
        if (names == null) {
            throw new CodeGenerationException("inline environment requires an allocator");
        }
        this.parent = parent;
        if (ownSymbols != null) {
            for (Symbol symbol : ownSymbols) {
                if (symbol == null) {
                    throw new CodeGenerationException("null symbol in inline frame");
                }
                if (slots.put(symbol, names.newVariable()) != null) {
                    throw new CodeGenerationException(symbol.getDeclarationNode(),
                            "duplicate symbol in inline frame: " + symbol.getGeneratedName());
                }
            }
        }
    }

    @Override
    public String storageFor(Symbol symbol, TreeNode useSite) {
        String slot = slots.get(symbol);
        if (slot != null) {
            return slot;
        }
        return parent.storageFor(symbol, useSite);
    }

    @Override
    public Optional<InliningContract.LexicalEnvironment> getParent() {
        return Optional.of(parent);
    }
}
