package spl.semantic;

import java.util.List;

import spl.lexer.Lexer;
import spl.lexer.Token;
import spl.parser.Parser;
import spl.tree.TreeNode;

public class DeclarationResolverTest {
    private DeclarationResolverTest() {
    }

    public static void main(String[] args) throws Exception {
        createsRootScope();
        registersProgramVariables();
        createsFunctionScope();
        registersParameters();
        supportsNestedShadowing();
        rejectsDuplicateVariables();
        registersSiblingFunctionsBeforeBodies();

        System.out.println("Declaration resolver tests passed.");
    }

    private static void createsRootScope() throws Exception {
        SymbolTable table = resolve(": : ");

        assert table.getRootScope() != null;
        assert table.getRootScope().getParent() == null;
    }

    private static void registersProgramVariables() throws Exception {
        SymbolTable table = resolve("#x #y : : ");

        Scope root = table.getRootScope();

        assert root.lookupLocal("#x").isPresent();
        assert root.lookupLocal("#y").isPresent();

        assert root.lookupLocal("#x").orElseThrow().getKind()
                == SymbolKind.VARIABLE;
    }

    private static void createsFunctionScope() throws Exception {
        SymbolTable table =
                resolve(": void #f ( ) { : : return } : ");

        Scope root = table.getRootScope();

        assert root.lookupLocal("#f").isPresent();
        assert root.lookupLocal("#f").orElseThrow().getKind()
                == SymbolKind.FUNCTION;

        assert root.getChildScopes().size() == 1;

        Scope functionScope = root.getChildScopes().get(0);

        assert functionScope.getParent() == root;
        assert "F_TYPE".equals(
                functionScope.getOwnerNode().getValue());
    }

    private static void registersParameters() throws Exception {
        SymbolTable table =
                resolve(": void #f ( #x #y ) { : : return } : ");

        Scope functionScope =
                table.getRootScope().getChildScopes().get(0);

        assert functionScope.lookupLocal("#x").isPresent();
        assert functionScope.lookupLocal("#y").isPresent();

        assert functionScope.lookupLocal("#x")
                .orElseThrow()
                .getKind() == SymbolKind.VARIABLE;
    }

    private static void supportsNestedShadowing() throws Exception {
        String source =
                ": void #f ( #x ) { "
              + "#y : "
              + "void #g ( #x ) { "
              + "#y : : return "
              + "} "
              + ": return "
              + "} : ";

        SymbolTable table = resolve(source);

        Scope fScope =
                table.getRootScope().getChildScopes().get(0);

        Scope gScope =
                fScope.getChildScopes().get(0);

        Symbol outerX =
                fScope.lookupLocal("#x").orElseThrow();

        Symbol innerX =
                gScope.lookupLocal("#x").orElseThrow();

        Symbol outerY =
                fScope.lookupLocal("#y").orElseThrow();

        Symbol innerY =
                gScope.lookupLocal("#y").orElseThrow();

        assert outerX != innerX;
        assert outerY != innerY;

        assert !outerX.getGeneratedName()
                .equals(innerX.getGeneratedName());

        assert !outerY.getGeneratedName()
                .equals(innerY.getGeneratedName());
    }

    private static void rejectsDuplicateVariables() throws Exception {
        try {
            resolve("#x #x : : ");

            throw new AssertionError(
                    "expected duplicate declaration");

        } catch (DuplicateDeclarationException expected) {
            assert expected.getMessage().contains("#x");
        }
    }

    private static void registersSiblingFunctionsBeforeBodies()
            throws Exception {

        String source =
                ": "
              + "void #f ( ) { : : return } "
              + "void #g ( ) { : : return } "
              + ": ";

        SymbolTable table = resolve(source);

        Scope root = table.getRootScope();

        assert root.lookupLocal("#f").isPresent();
        assert root.lookupLocal("#g").isPresent();

        assert root.getChildScopes().size() == 2;
    }

    private static SymbolTable resolve(String source)
            throws Exception {

        List<Token> tokens =
                new Lexer().tokenize(source);

        TreeNode tree =
                new Parser().parse(tokens);

        return new DeclarationResolver().resolve(tree);
    }
}
