package spl.codegen;

import java.util.*;
import spl.semantic.*;
import spl.tree.TreeNode;

/** Immutable function metadata over the ORIGINAL accepted Phase 2b objects.
 * Do not mutate the underlying tree/table after construction. No semantic passes are rerun.
 */
public final class FunctionCatalogue {
    private final CodeGenerationInput input;
    private final Map<Symbol, Function> bySymbol = new IdentityHashMap<>();
    private final Map<String, Function> byGeneratedName = new LinkedHashMap<>();
    private final Map<TreeNode, Scope> scopes = new IdentityHashMap<>();
    public FunctionCatalogue(Phase2bResult result) { this(new CodeGenerationInput(result)); }
    public FunctionCatalogue(CodeGenerationInput input) {
        if (input == null) throw new CodeGenerationException("missing accepted input");
        this.input = input;
        indexScopes(input.getSymbolTable().getRootScope());
        TreeNode program = input.getTree();
        if ("SPL_PROG".equals(program.getValue())) program = required(program, "P");
        input.requireNode(program, "P");
        scanProgram(program);
    }
    public CodeGenerationInput getInput() { return input; }
    public List<Function> getFunctions() { return List.copyOf(byGeneratedName.values()); }
    public Function requireFunction(Symbol symbol, TreeNode useSite) {
        Function function = bySymbol.get(symbol);
        if (function == null) throw new CodeGenerationException(useSite,
                "missing catalogue entry for resolved function (unresolved, foreign or non-function binding)");
        return function;
    }
    /** Backend generated-name index only. Never pass a source name to this API. */
    public Function requireGeneratedName(String name, TreeNode useSite) {
        Function function = byGeneratedName.get(name);
        if (function == null) throw new CodeGenerationException(useSite, "missing catalogue entry for generated function " + name);
        return function;
    }
    /** Contextual safety check for later CALL expansion; does not modify Phase 2b rules. */
    public void requireArity(Function function, int actualCount, TreeNode call) {
        if (function == null || bySymbol.get(function.getSymbol()) != function)
            throw new CodeGenerationException(call, "foreign function template");
        int expected = function.getParameters().size();
        if (actualCount != expected) throw new CodeGenerationException(call,
                "argument count mismatch for " + function.getSymbol().getGeneratedName()
                + ": expected " + expected + ", got " + actualCount);
    }
    private void indexScopes(Scope scope) {
        if (scope.getOwnerNode() != null && scopes.put(scope.getOwnerNode(), scope) != null)
            throw new CodeGenerationException(scope.getOwnerNode(), "duplicate scope owner");
        for (Scope child : scope.getChildScopes()) indexScopes(child);
    }
    private void scanProgram(TreeNode program) {
        TreeNode list = required(program, "F_DECL");
        while (!list.getChildren().isEmpty()) {
            TreeNode declaration = required(list, "F_TYPE");
            List<TreeNode> children = declaration.getChildren();
            if (children.size() < 2) throw new CodeGenerationException(declaration, "missing function name");
            TreeNode name = children.get(1);
            Symbol symbol = name.getResolvedSymbol();
            Scope scope = scopes.get(declaration);
            if (symbol == null || symbol.getKind() != SymbolKind.FUNCTION
                    || symbol.getDeclarationNode() != name || scope == null
                    || scope.getParent() != symbol.getDeclaringScope()
                    || symbol.getDeclaringScope().getDeclarations().values().stream().noneMatch(s -> s == symbol))
                throw new CodeGenerationException(name, "invalid resolved function declaration/scope");
            TreeNode body = required(declaration, "P");
            String kind = children.get(0).getValue();
            TreeNode result = optional(declaration, "TERM");
            if (!("void".equals(kind) && result == null && symbol.getType() == SemanticType.PROCEDURE)
                    && !("num".equals(kind) && result != null && symbol.getType() == SemanticType.NUMERIC))
                throw new CodeGenerationException(declaration, "inconsistent function kind/return metadata");
            List<TreeNode> nested = new ArrayList<>();
            TreeNode nestedList = required(body, "F_DECL");
            while (!nestedList.getChildren().isEmpty()) {
                nested.add(required(nestedList, "F_TYPE"));
                nestedList = required(nestedList, "F_DECL");
            }
            Function function = new Function(symbol, declaration, variables(required(declaration, "V_DECL"), scope),
                    variables(required(body, "V_DECL"), scope), nested, body, required(body, "ALGO"), result);
            if (bySymbol.put(symbol, function) != null || byGeneratedName.put(symbol.getGeneratedName(), function) != null)
                throw new CodeGenerationException(name, "duplicate function catalogue entry");
            scanProgram(body);
            list = required(list, "F_DECL");
        }
    }
    private List<Symbol> variables(TreeNode list, Scope scope) {
        List<Symbol> symbols = new ArrayList<>();
        while (!list.getChildren().isEmpty()) {
            TreeNode name = list.getChildren().get(0);
            Symbol symbol = name.getResolvedSymbol();
            if (symbol == null || symbol.getKind() != SymbolKind.VARIABLE || symbol.getDeclarationNode() != name
                    || symbol.getDeclaringScope() != scope)
                throw new CodeGenerationException(name, "invalid resolved parameter/local binding");
            symbols.add(symbol);
            list = required(list, "V_DECL");
        }
        return symbols;
    }
    private static TreeNode optional(TreeNode node, String production) {
        TreeNode found = null;
        for (TreeNode child : node.getChildren()) if (!child.isTerminal() && production.equals(child.getValue())) {
            if (found != null) throw new CodeGenerationException(node, "duplicate " + production);
            found = child;
        }
        return found;
    }
    private static TreeNode required(TreeNode node, String production) {
        TreeNode child = optional(node, production);
        if (child == null) throw new CodeGenerationException(node, "missing " + production);
        return child;
    }
    public static final class Function implements InliningContract.FunctionTemplate {
        private final Symbol symbol;
        private final TreeNode declaration, body, algo, result;
        private final List<Symbol> parameters, locals;
        private final List<TreeNode> nested;
        private Function(Symbol symbol, TreeNode declaration, List<Symbol> parameters, List<Symbol> locals,
                List<TreeNode> nested, TreeNode body, TreeNode algo, TreeNode result) {
            this.symbol=symbol; this.declaration=declaration; this.parameters=List.copyOf(parameters);
            this.locals=List.copyOf(locals); this.nested=List.copyOf(nested); this.body=body; this.algo=algo; this.result=result;
        }
        public Symbol getSymbol() { return symbol; }
        public TreeNode getDeclaration() { return declaration; }
        public List<Symbol> getParameters() { return parameters; }
        public List<Symbol> getLocals() { return locals; }
        public List<TreeNode> getNestedDeclarations() { return nested; }
        public TreeNode getBodyProgram() { return body; }
        public TreeNode getAlgorithm() { return algo; }
        public Optional<TreeNode> getReturnTerm() { return Optional.ofNullable(result); }
    }
}
