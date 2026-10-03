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
        rejectsDuplicateParameters();
        rejectsDuplicateFunctions();
        preservesLexemesAndGeneratesDeterministicNames();
        allowsRecursionAndCallsToLaterSiblings();
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

    private static void rejectsDuplicateParameters() throws Exception {
        try {
            resolve(": void #f ( #x #x ) { : : return } : ");

            throw new AssertionError("expected duplicate parameter");

        } catch (DuplicateDeclarationException expected) {
            assert expected.getMessage().contains("#x");
        }
    }

    private static void rejectsDuplicateFunctions() throws Exception {
        try {
            resolve(": void #f ( ) { : : return } "
                    + "void #f ( ) { : : return } : ");

            throw new AssertionError("expected duplicate function");

        } catch (DuplicateDeclarationException expected) {
            assert expected.getMessage().contains("#f");
        }
    }

    private static void preservesLexemesAndGeneratesDeterministicNames()
            throws Exception {
        String source = "#x : void #f ( #x ) { : : return } : ";

        SymbolTable first = resolve(source);
        SymbolTable second = resolve(source);

        Symbol firstRootVariable = first.getRootScope()
                .lookupLocal("#x").orElseThrow();
        Symbol firstFunction = first.getRootScope()
                .lookupLocal("#f").orElseThrow();
        Symbol firstParameter = first.getRootScope().getChildScopes().get(0)
                .lookupLocal("#x").orElseThrow();

        Symbol secondRootVariable = second.getRootScope()
                .lookupLocal("#x").orElseThrow();
        Symbol secondFunction = second.getRootScope()
                .lookupLocal("#f").orElseThrow();
        Symbol secondParameter = second.getRootScope().getChildScopes().get(0)
                .lookupLocal("#x").orElseThrow();

        assert firstRootVariable.getOriginalName().equals("#x");
        assert firstFunction.getOriginalName().equals("#f");
        assert firstParameter.getOriginalName().equals("#x");
        assert !firstRootVariable.getGeneratedName()
                .equals(firstParameter.getGeneratedName());
        assert firstRootVariable.getGeneratedName()
                .equals(secondRootVariable.getGeneratedName());
        assert firstFunction.getGeneratedName()
                .equals(secondFunction.getGeneratedName());
        assert firstParameter.getGeneratedName()
                .equals(secondParameter.getGeneratedName());
    }

    private static void allowsRecursionAndCallsToLaterSiblings()
            throws Exception {
        String source = ": "
                + "void #f ( ) { : : #f ( ) ; #g ( ) ; return } "
                + "void #g ( ) { : : return } "
                + ": ";

        SymbolTable table = resolve(source);

        assert table.getRootScope().lookupLocal("#f").isPresent();
        assert table.getRootScope().lookupLocal("#g").isPresent();
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
