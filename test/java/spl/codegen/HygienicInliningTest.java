package spl.codegen;

import java.util.Optional;
import spl.lexer.Lexer;
import spl.parser.Parser;
import spl.semantic.DeclarationResolver;
import spl.semantic.Phase2aResult;
import spl.semantic.Phase2bResult;
import spl.semantic.Symbol;
import spl.semantic.TypeAnalyzer;
import spl.tree.TreeNode;

public final class HygienicInliningTest {

    public static void main(String[] args) throws Exception {
        freshStoragePerCopy();
        freeVariableDelegation();
        freshStableLabels();
        distinctReturnSlots();
        collisionFree();
        missingStorageErrors();
        voidHasNoReturnSlot();
        deterministicAllocation();
        System.out.println("HygienicInliningTest: all checks passed");
    }

    private static void freshStoragePerCopy() throws Exception {
        CodeGenerationInput input = accepted(NUMERIC_FREE_VAR);
        FunctionCatalogue catalogue = new FunctionCatalogue(input);
        FunctionCatalogue.Function f = catalogue.getFunctions().get(0);
        Symbol x = f.getParameters().get(0);

        OutputNames names = new OutputNames(input);
        RootEnvironment root = new RootEnvironment();
        FunctionInstance a = new FunctionInstance(f, root, names);
        FunctionInstance b = new FunctionInstance(f, root, names);

        String ax = a.getEnvironment().storageFor(x, null);
        String bx = b.getEnvironment().storageFor(x, null);
        check(ax.startsWith("spl_in_v_"), "own parameter gets a fresh inline slot");
        check(!ax.equals(bx), "two copies of a function get disjoint storage");
        check(ax.equals(a.getEnvironment().storageFor(x, null)), "storage is stable within an instance");
    }

    private static void freeVariableDelegation() throws Exception {
        CodeGenerationInput input = accepted(NUMERIC_FREE_VAR);
        FunctionCatalogue catalogue = new FunctionCatalogue(input);
        FunctionCatalogue.Function f = catalogue.getFunctions().get(0);
        Symbol r = findSymbol(f.getReturnTerm().orElseThrow(), "#r");
        check(r != null, "free variable symbol located");

        OutputNames names = new OutputNames(input);
        RootEnvironment root = new RootEnvironment();
        FunctionInstance a = new FunctionInstance(f, root, names);
        FunctionInstance b = new FunctionInstance(f, root, names);

        check(a.getEnvironment().storageFor(r, null).equals(r.getGeneratedName()),
                "free variable resolves to the enclosing (original) name");
        check(a.getEnvironment().storageFor(r, null).equals(b.getEnvironment().storageFor(r, null)),
                "both copies share the same enclosing storage for a free variable");
    }

    private static void freshStableLabels() throws Exception {
        CodeGenerationInput input = accepted(NUMERIC_FREE_VAR);
        FunctionCatalogue catalogue = new FunctionCatalogue(input);
        FunctionCatalogue.Function f = catalogue.getFunctions().get(0);

        OutputNames names = new OutputNames(input);
        RootEnvironment root = new RootEnvironment();
        FunctionInstance a = new FunctionInstance(f, root, names);
        FunctionInstance b = new FunctionInstance(f, root, names);

        String a0 = a.labelFor("L0");
        check(a0.startsWith("spl_in_l_"), "template label maps to a fresh target label");
        check(a0.equals(a.labelFor("L0")), "same template label is stable within an instance");
        check(!a0.equals(a.labelFor("L1")), "different template labels get different targets");
        check(!a0.equals(b.labelFor("L0")), "same template label is fresh across instances");
    }

    private static void distinctReturnSlots() throws Exception {
        CodeGenerationInput input = accepted(NUMERIC_FREE_VAR);
        FunctionCatalogue catalogue = new FunctionCatalogue(input);
        FunctionCatalogue.Function f = catalogue.getFunctions().get(0);

        OutputNames names = new OutputNames(input);
        RootEnvironment root = new RootEnvironment();
        FunctionInstance a = new FunctionInstance(f, root, names);
        FunctionInstance b = new FunctionInstance(f, root, names);

        Optional<String> sa = a.getResult().getValueSlot();
        Optional<String> sb = b.getResult().getValueSlot();
        check(sa.isPresent() && sb.isPresent(), "numeric function has a return slot");
        check(!sa.get().equals(sb.get()), "each copy has its own return slot");
    }

    private static void collisionFree() throws Exception {
        CodeGenerationInput input = accepted(NUMERIC_FREE_VAR);
        FunctionCatalogue catalogue = new FunctionCatalogue(input);
        FunctionCatalogue.Function f = catalogue.getFunctions().get(0);
        Symbol x = f.getParameters().get(0);

        OutputNames names = new OutputNames(input);
        RootEnvironment root = new RootEnvironment();
        FunctionInstance a = new FunctionInstance(f, root, names);

        String var = a.getEnvironment().storageFor(x, null);
        String label = a.labelFor("L0");
        String ret = a.getResult().getValueSlot().orElseThrow();
        check(!var.equals(label) && !var.equals(ret) && !label.equals(ret), "variable, label, return names are mutually distinct");
        check(!input.getReservedNames().contains(var), "fresh variable does not collide with a reserved source name");
        check(!input.getReservedNames().contains(label), "fresh label does not collide with a reserved source name");
    }

    private static void missingStorageErrors() throws Exception {
        CodeGenerationInput input = accepted(NUMERIC_FREE_VAR);
        FunctionCatalogue catalogue = new FunctionCatalogue(input);
        FunctionCatalogue.Function f = catalogue.getFunctions().get(0);

        OutputNames names = new OutputNames(input);
        RootEnvironment root = new RootEnvironment();
        FunctionInstance a = new FunctionInstance(f, root, names);

        check(throwsCodeGen(() -> root.storageFor(f.getSymbol(), null)),
                "root rejects a non-variable symbol instead of inventing a binding");
        check(throwsCodeGen(() -> a.getEnvironment().storageFor(f.getSymbol(), null)),
                "inline frame delegates an unknown symbol up to the error, not a new slot");
    }

    private static void voidHasNoReturnSlot() throws Exception {
        CodeGenerationInput input = accepted(VOID_PROC);
        FunctionCatalogue catalogue = new FunctionCatalogue(input);
        FunctionCatalogue.Function p = catalogue.getFunctions().get(0);

        OutputNames names = new OutputNames(input);
        FunctionInstance a = new FunctionInstance(p, new RootEnvironment(), names);
        check(a.getResult().getValueSlot().isEmpty(), "void function has no return slot");
    }

    private static void deterministicAllocation() {
        java.util.Set<String> reserved = java.util.Set.of("__variable0", "__function1");
        OutputNames one = new OutputNames(reserved);
        OutputNames two = new OutputNames(reserved);
        for (int i = 0; i < 5; i++) {
            check(one.newVariable().equals(two.newVariable()), "variable sequence is deterministic");
            check(one.newLabel().equals(two.newLabel()), "label sequence is deterministic");
        }
    }

    private static final String NUMERIC_FREE_VAR =
            "#r : num #f ( #x ) { : : return ( add ( #x #r ) ) } : #r = #f ( 5 ) ; ";
    private static final String VOID_PROC =
            " : void #p ( ) { : : return } : ";

    private static Symbol findSymbol(TreeNode node, String value) {
        if (node.isTerminal() && value.equals(node.getValue())) {
            return node.getResolvedSymbol();
        }
        for (TreeNode child : node.getChildren()) {
            Symbol found = findSymbol(child, value);
            if (found != null) {
                return found;
            }
        }
        return null;
    }

    private static boolean throwsCodeGen(Runnable action) {
        try {
            action.run();
            return false;
        } catch (CodeGenerationException expected) {
            return true;
        }
    }

    private static CodeGenerationInput accepted(String source) throws Exception {
        Phase2aResult a = new DeclarationResolver().resolveWithResult(new Parser().parse(new Lexer().tokenize(source)));
        Phase2bResult b = new TypeAnalyzer(a.getSymbolTable()).analyzeWithResult(a);
        return new CodeGenerationInput(b);
    }

    private static void check(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError("FAILED: " + message);
        }
        System.out.println("  ok: " + message);
    }
}
