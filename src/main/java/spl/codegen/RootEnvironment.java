package spl.codegen;

import java.util.Optional;
import spl.semantic.Symbol;
import spl.semantic.SymbolKind;
import spl.tree.TreeNode;

public final class RootEnvironment implements InliningContract.LexicalEnvironment {

    @Override
    public String storageFor(Symbol symbol, TreeNode useSite) {
        if (symbol == null) {
            throw new CodeGenerationException(useSite, "no inline storage for a null symbol");
        }
        if (symbol.getKind() != SymbolKind.VARIABLE) {
            throw new CodeGenerationException(useSite,
                    "no inline storage for non-variable symbol " + symbol.getGeneratedName());
        }
        return symbol.getGeneratedName();
    }

    @Override
    public Optional<InliningContract.LexicalEnvironment> getParent() {
        return Optional.empty();
    }
}
