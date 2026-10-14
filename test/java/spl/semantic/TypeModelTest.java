package spl.semantic;

import spl.tree.TreeNode;

/** Dependency-free Phase 2b model tests; checks remain active without -ea. */
public final class TypeModelTest {
    public static void main(String[] args) {
        check(SemanticType.values().length == 5, "five specified types");
        for (boolean terminal : new boolean[] {false, true}) {
            TreeNode node = new TreeNode("TERM", terminal);
            check(node.getType() == SemanticType.UNKNOWN, "node initial type");
            for (SemanticType type : SemanticType.values()) {
                node.setType(type);
                check(node.getType() == type, "node stores " + type);
            }
            expectNull(() -> node.setType(null));
        }
        SymbolTable table = new SymbolTable();
        TreeNode declaration = new TreeNode("#x", true);
        Symbol symbol = table.declare("#x", SymbolKind.VARIABLE, declaration);
        check(symbol.getType() == SemanticType.UNKNOWN, "symbol initial type");
        TreeNode reference = new TreeNode("#x", true);
        declaration.setResolvedSymbol(symbol);
        reference.setResolvedSymbol(symbol);
        Phase2aResult result = new Phase2aResult(declaration, table);
        for (SemanticType type : SemanticType.values()) {
            symbol.setType(type);
            check(reference.getResolvedSymbol().getType() == type, "shared symbol type");
        }
        reference.setType(SemanticType.NUMERIC);
        check(reference.getResolvedSymbol() == symbol, "binding preserved");
        check(declaration.getType() == SemanticType.UNKNOWN, "node types independent");
        check(result.getSymbolTable() == table, "original table reused");
        check(result.getResolvedTree() == declaration, "original tree reused");
        expectNull(() -> symbol.setType(null));
        Symbol function = table.declare("#f", SymbolKind.FUNCTION, null);
        check(function.getType() == SemanticType.UNKNOWN, "function initial type");
        function.setType(SemanticType.PROCEDURE);
        check(function.getType() == SemanticType.PROCEDURE, "procedure type");
        TypeAnalysisException error = new TypeAnalysisException("expected numeric");
        check(error.getMessage().startsWith("Type analysis error:"), "phase diagnostic");
        TypeAnalysisException located = new TypeAnalysisException(reference, "expected numeric");
        check(located.getMessage().contains("node " + reference.getNodeId()), "node diagnostic");
        check(!NameResolutionException.class.isInstance(error), "distinct exception class");
        System.out.println("Type model tests passed.");
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }

    private static void expectNull(Runnable action) {
        try {
            action.run();
        } catch (NullPointerException expected) {
            return;
        }
        throw new AssertionError("expected null type rejection");
    }
}
