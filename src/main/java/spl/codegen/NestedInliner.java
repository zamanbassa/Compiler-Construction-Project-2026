package spl.codegen;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import spl.semantic.Symbol;
import spl.tree.TreeNode;

public final class NestedInliner {

    private final FunctionCatalogue catalogue;
    private final InliningContract.TargetNames names;

    public NestedInliner(FunctionCatalogue catalogue, InliningContract.TargetNames names) {
        if (catalogue == null) {
            throw new CodeGenerationException("nested inliner requires a catalogue");
        }
        if (names == null) {
            throw new CodeGenerationException("nested inliner requires an allocator");
        }
        this.catalogue = catalogue;
        this.names = names;
    }

    public FunctionCatalogue getCatalogue() {
        return catalogue;
    }

    public List<TreeNode> callSites(FunctionCatalogue.Function function) {
        List<TreeNode> calls = new ArrayList<>();
        collectCalls(function.getAlgorithm(), calls);
        function.getReturnTerm().ifPresent(term -> collectCalls(term, calls));
        return calls;
    }

    private void collectCalls(TreeNode node, List<TreeNode> out) {
        if (node == null) {
            return;
        }
        if ("CALL".equals(node.getValue())) {
            out.add(node);
        }
        for (TreeNode child : node.getChildren()) {
            collectCalls(child, out);
        }
    }

    public FunctionCatalogue.Function calleeOf(TreeNode call) {
        List<TreeNode> children = call.getChildren();
        if (children.isEmpty()) {
            throw new CodeGenerationException(call, "malformed CALL: missing callee name");
        }
        TreeNode name = children.get(0);
        Symbol symbol = name.getResolvedSymbol();
        if (symbol == null) {
            throw new CodeGenerationException(name, "CALL has no resolved function symbol");
        }
        return catalogue.requireFunction(symbol, name);
    }

    public List<FunctionCatalogue.Function> callees(FunctionCatalogue.Function function) {
        List<FunctionCatalogue.Function> out = new ArrayList<>();
        Set<FunctionCatalogue.Function> seen = Collections.newSetFromMap(new IdentityHashMap<>());
        for (TreeNode call : callSites(function)) {
            FunctionCatalogue.Function callee = calleeOf(call);
            if (seen.add(callee)) {
                out.add(callee);
            }
        }
        return out;
    }

    public List<FunctionCatalogue.Function> expansionOrder() {
        Map<FunctionCatalogue.Function, Collection<FunctionCatalogue.Function>> graph = new LinkedHashMap<>();
        for (FunctionCatalogue.Function function : catalogue.getFunctions()) {
            graph.put(function, callees(function));
        }
        return topologicalOrder(graph);
    }

    public static <T> List<T> topologicalOrder(Map<T, Collection<T>> dependencies) {
        List<T> order = new ArrayList<>();
        Set<T> done = new LinkedHashSet<>();
        Set<T> active = new HashSet<>();
        for (T node : dependencies.keySet()) {
            visit(node, dependencies, done, active, order);
        }
        return order;
    }

    private static <T> void visit(T node, Map<T, Collection<T>> dependencies,
                                  Set<T> done, Set<T> active, List<T> order) {
        if (done.contains(node)) {
            return;
        }
        if (!active.add(node)) {
            throw new CodeGenerationException("recursive or cyclic call chain is not inlinable");
        }
        for (T dependency : dependencies.getOrDefault(node, List.of())) {
            visit(dependency, dependencies, done, active, order);
        }
        active.remove(node);
        if (done.add(node)) {
            order.add(node);
        }
    }

    // Produces the hygienic instance tree only. Turning each instance into target text
    // (argument->parameter binding, body emission via the environment and labelFor, return
    // wiring) is Phase 3b.4; stitching the top-level Expansion into main is Phase 3b.6.
    public Expansion instantiate(FunctionCatalogue.Function function,
                                 InliningContract.LexicalEnvironment enclosing) {
        return instantiate(function, enclosing, Collections.newSetFromMap(new IdentityHashMap<>()));
    }

    private Expansion instantiate(FunctionCatalogue.Function function,
                                  InliningContract.LexicalEnvironment enclosing,
                                  Set<FunctionCatalogue.Function> active) {
        if (!active.add(function)) {
            throw new CodeGenerationException(function.getDeclaration(),
                    "recursive call chain is not inlinable");
        }
        FunctionInstance instance = new FunctionInstance(function, enclosing, names);
        List<Expansion> children = new ArrayList<>();
        for (TreeNode call : callSites(function)) {
            children.add(instantiate(calleeOf(call), instance.getEnvironment(), active));
        }
        active.remove(function);
        return new Expansion(instance, List.copyOf(children));
    }

    public static final class Expansion {
        private final FunctionInstance instance;
        private final List<Expansion> children;

        Expansion(FunctionInstance instance, List<Expansion> children) {
            this.instance = instance;
            this.children = children;
        }

        public FunctionInstance getInstance() {
            return instance;
        }

        public List<Expansion> getChildren() {
            return children;
        }
    }
}
