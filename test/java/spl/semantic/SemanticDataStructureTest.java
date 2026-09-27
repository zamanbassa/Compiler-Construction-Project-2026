package spl.semantic;

import spl.tree.TreeNode;

/** Dependency-free tests for the Phase 2a symbol-table API. */
public final class SemanticDataStructureTest {
    private SemanticDataStructureTest() {
    }

    public static void main(String[] args) {
        supportsSymbolsScopesAndGeneratedNames();
        resolvesLocallyThenThroughAncestors();
        rejectsDuplicateDeclarationsAndRootExit();
        System.out.println("Semantic data-structure tests passed.");
    }

    private static void supportsSymbolsScopesAndGeneratedNames() {
        SymbolTable table = new SymbolTable();
        TreeNode functionNode = new TreeNode("F_DECL");
        Scope functionScope = table.enterScope(functionNode);
        Symbol variable = table.declare("#x", SymbolKind.VARIABLE, new TreeNode("#x", true));
        Symbol function = table.declare("#f", SymbolKind.FUNCTION, "function_main", functionNode);

        assert functionScope.getParent() == table.getRootScope();
        assert table.getRootScope().getChildScopes().contains(functionScope);
        assert functionScope.getOwnerNode() == functionNode;
        assert variable.getKind() == SymbolKind.VARIABLE;
        assert function.getKind() == SymbolKind.FUNCTION;
        assert variable.getGeneratedName().startsWith("__variable");
        assert function.getGeneratedName().equals("function_main");
        assert variable.getDeclaringScope() == functionScope;
        assert variable.getDeclarationNode() != null;
        assert variable.getType() == SemanticType.UNKNOWN;

        variable.setType(SemanticType.NUMERIC);
        assert variable.getType() == SemanticType.NUMERIC;
    }

    private static void resolvesLocallyThenThroughAncestors() {
        SymbolTable table = new SymbolTable();
        Symbol ancestor = table.declare("#value", SymbolKind.VARIABLE, null);
        Scope firstChild = table.enterScope();

        assert table.lookupLocal("#value").isEmpty();
        assert table.resolve("#value").orElseThrow() == ancestor;

        Symbol shadow = table.declare("#value", SymbolKind.VARIABLE, null);
        assert table.lookupLocal("#value").orElseThrow() == shadow;
        assert table.resolve("#value").orElseThrow() == shadow;

        table.exitScope();
        table.enterScope();
        assert table.resolve("#value").orElseThrow() == ancestor;
        assert table.lookupLocal("#value").isEmpty();
        assert table.resolve("#missing").isEmpty();
        assert firstChild.getParent() == table.getRootScope();
    }

    private static void rejectsDuplicateDeclarationsAndRootExit() {
        SymbolTable table = new SymbolTable();
        table.declare("#duplicate", SymbolKind.VARIABLE, null);
        try {
            table.declare("#duplicate", SymbolKind.FUNCTION, null);
            throw new AssertionError("expected duplicate declaration error");
        } catch (DuplicateDeclarationException expected) {
            assert expected.getMessage().contains("#duplicate");
        }

        try {
            table.exitScope();
            throw new AssertionError("expected root-scope exit error");
        } catch (ScopeException expected) {
            assert expected.getMessage().contains("root scope");
        }
    }
}
