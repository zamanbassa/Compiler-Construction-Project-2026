package spl.codegen;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import spl.lexer.Lexer;
import spl.parser.Parser;
import spl.semantic.DeclarationResolver;
import spl.semantic.Phase2aResult;
import spl.semantic.Phase2bResult;
import spl.semantic.Symbol;
import spl.semantic.TypeAnalyzer;

public final class NestedInliningTest {

    public static void main(String[] args) throws Exception {
        dependencyOrderIsBottomUp();
        expansionTreeShape();
        hygieneHoldsAtEveryLevel();
        lexicalChainResolvesFreeVariables();
        cycleIsRejected();
        acyclicGraphOrders();
        System.out.println("NestedInliningTest: all checks passed");
    }

    private static void dependencyOrderIsBottomUp() throws Exception {
        NestedInliner inliner = inlinerFor(NESTED);
        FunctionCatalogue catalogue = inliner.getCatalogue();
        FunctionCatalogue.Function f = catalogue.getFunctions().get(0);
        FunctionCatalogue.Function g = catalogue.getFunctions().get(1);
        FunctionCatalogue.Function h = catalogue.getFunctions().get(2);

        check(inliner.callees(f).equals(List.of(g)), "f depends on g");
        check(inliner.callees(g).equals(List.of(h)), "g depends on h (de-duplicated)");
        check(inliner.callees(h).isEmpty(), "h depends on nothing");

        List<FunctionCatalogue.Function> order = inliner.expansionOrder();
        check(order.indexOf(h) < order.indexOf(g), "h is expanded before g");
        check(order.indexOf(g) < order.indexOf(f), "g is expanded before f");
    }

    private static void expansionTreeShape() throws Exception {
        NestedInliner inliner = inlinerFor(NESTED);
        FunctionCatalogue catalogue = catalogueFor(inliner);
        FunctionCatalogue.Function f = catalogue.getFunctions().get(0);
        FunctionCatalogue.Function g = catalogue.getFunctions().get(1);
        FunctionCatalogue.Function h = catalogue.getFunctions().get(2);

        NestedInliner.Expansion fExp = inliner.instantiate(f, new RootEnvironment());
        check(fExp.getInstance().getTemplate() == f, "root of the expansion is f");
        check(fExp.getChildren().size() == 1, "f has one call site (g)");

        NestedInliner.Expansion gExp = fExp.getChildren().get(0);
        check(gExp.getInstance().getTemplate() == g, "f's child is g");
        check(gExp.getChildren().size() == 2, "g calls h at two call sites");

        NestedInliner.Expansion h1 = gExp.getChildren().get(0);
        NestedInliner.Expansion h2 = gExp.getChildren().get(1);
        check(h1.getInstance().getTemplate() == h && h2.getInstance().getTemplate() == h, "both of g's children are h");
        check(h1.getInstance() != h2.getInstance(), "each call site is a distinct h instance");
        check(h1.getChildren().isEmpty() && h2.getChildren().isEmpty(), "h is a leaf");
    }

    private static void hygieneHoldsAtEveryLevel() throws Exception {
        NestedInliner inliner = inlinerFor(NESTED);
        FunctionCatalogue catalogue = catalogueFor(inliner);
        FunctionCatalogue.Function f = catalogue.getFunctions().get(0);

        NestedInliner.Expansion fExp = inliner.instantiate(f, new RootEnvironment());
        List<FunctionInstance> instances = new ArrayList<>();
        flatten(fExp, instances);
        check(instances.size() == 4, "four instances: f, g, h, h");

        List<String> names = new ArrayList<>();
        for (FunctionInstance instance : instances) {
            FunctionCatalogue.Function template = (FunctionCatalogue.Function) instance.getTemplate();
            for (Symbol own : template.getParameters()) {
                names.add(instance.getEnvironment().storageFor(own, null));
            }
            for (Symbol own : template.getLocals()) {
                names.add(instance.getEnvironment().storageFor(own, null));
            }
            names.add(instance.getResult().getValueSlot().orElseThrow());
            names.add(instance.labelFor("A"));
            names.add(instance.labelFor("B"));
        }
        check(allDistinct(names), "every slot, return name and label across all copies is unique");
    }

    private static void lexicalChainResolvesFreeVariables() throws Exception {
        NestedInliner inliner = inlinerFor(NESTED);
        FunctionCatalogue catalogue = catalogueFor(inliner);
        FunctionCatalogue.Function f = catalogue.getFunctions().get(0);
        FunctionCatalogue.Function g = catalogue.getFunctions().get(1);
        Symbol x = f.getParameters().get(0);
        Symbol y = g.getParameters().get(0);

        NestedInliner.Expansion fExp = inliner.instantiate(f, new RootEnvironment());
        FunctionInstance fi = fExp.getInstance();
        FunctionInstance gi = fExp.getChildren().get(0).getInstance();
        FunctionInstance hi = fExp.getChildren().get(0).getChildren().get(0).getInstance();

        check(gi.getEnvironment().storageFor(x, null).equals(fi.getEnvironment().storageFor(x, null)),
                "g sees f's parameter through one enclosing level");
        check(hi.getEnvironment().storageFor(y, null).equals(gi.getEnvironment().storageFor(y, null)),
                "h sees g's parameter through one enclosing level");
        check(hi.getEnvironment().storageFor(x, null).equals(fi.getEnvironment().storageFor(x, null)),
                "h sees f's parameter through two enclosing levels");
    }

    private static void cycleIsRejected() {
        Map<String, Collection<String>> cyclic = new LinkedHashMap<>();
        cyclic.put("a", List.of("b"));
        cyclic.put("b", List.of("a"));
        boolean threw;
        try {
            NestedInliner.topologicalOrder(cyclic);
            threw = false;
        } catch (CodeGenerationException expected) {
            threw = true;
        }
        check(threw, "a cyclic call chain is rejected as non-inlinable");
    }

    private static void acyclicGraphOrders() {
        Map<String, Collection<String>> dag = new LinkedHashMap<>();
        dag.put("a", List.of("b", "c"));
        dag.put("b", List.of("c"));
        dag.put("c", List.of());
        List<String> order = NestedInliner.topologicalOrder(dag);
        check(order.indexOf("c") < order.indexOf("b"), "c precedes b");
        check(order.indexOf("b") < order.indexOf("a"), "b precedes a");
    }

    private static final String NESTED =
            "#r : num #f ( #x ) { : num #g ( #y ) { : num #h ( #z ) { : : return ( add ( #z 1 ) ) } "
            + ": return ( add ( #h ( #y ) #h ( #y ) ) ) } : return ( #g ( #x ) ) } : #r = #f ( 5 ) ; ";

    private static void flatten(NestedInliner.Expansion expansion, List<FunctionInstance> out) {
        out.add(expansion.getInstance());
        for (NestedInliner.Expansion child : expansion.getChildren()) {
            flatten(child, out);
        }
    }

    private static boolean allDistinct(List<String> values) {
        return new java.util.HashSet<>(values).size() == values.size();
    }

    private static NestedInliner inlinerFor(String source) throws Exception {
        FunctionCatalogue catalogue = catalogueFor(source);
        return new NestedInliner(catalogue, new OutputNames(catalogue.getInput()));
    }

    private static FunctionCatalogue catalogueFor(String source) throws Exception {
        Phase2aResult a = new DeclarationResolver().resolveWithResult(new Parser().parse(new Lexer().tokenize(source)));
        Phase2bResult b = new TypeAnalyzer(a.getSymbolTable()).analyzeWithResult(a);
        return new FunctionCatalogue(new CodeGenerationInput(b));
    }

    private static FunctionCatalogue catalogueFor(NestedInliner inliner) {
        return inliner.getCatalogue();
    }

    private static void check(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError("FAILED: " + message);
        }
        System.out.println("  ok: " + message);
    }
}
