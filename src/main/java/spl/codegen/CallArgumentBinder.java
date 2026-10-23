package spl.codegen;

import java.util.ArrayList;
import java.util.List;
import spl.semantic.Symbol;
import spl.tree.TreeNode;

/** Captures actual arguments before assigning a function instance's formal storage. */
public final class CallArgumentBinder {
    public List<String> bind(
            TreeNode call,
            FunctionCatalogue catalogue,
            FunctionInstance calleeInstance,
            AlgorithmCodeGenerator callerGenerator) {
        if (catalogue == null) {
            throw new CodeGenerationException("missing function catalogue");
        }
        if (calleeInstance == null) {
            throw new CodeGenerationException(call, "missing callee instance");
        }
        if (callerGenerator == null) {
            throw new CodeGenerationException(call, "missing caller term generator");
        }

        CodeGenerationInput input = catalogue.getInput();
        CodeEmitter emitter = callerGenerator.getEmitter();
        if (!emitter.usesInput(input)) {
            throw new CodeGenerationException(
                    call, "caller generator and catalogue must share the accepted input");
        }

        input.requireNode(call, "CALL");
        List<TreeNode> children = call.getChildren();
        if (children.size() != 4
                || !children.get(0).isTerminal()
                || !isToken(children.get(1), "(")
                || !"INPUT".equals(children.get(2).getValue())
                || !isToken(children.get(3), ")")) {
            throw new CodeGenerationException(call, "malformed CALL production");
        }

        TreeNode calleeName = children.get(0);
        Symbol calleeSymbol = calleeName.getResolvedSymbol();
        if (calleeSymbol == null) {
            throw new CodeGenerationException(calleeName, "CALL has no resolved function symbol");
        }
        FunctionCatalogue.Function function =
                catalogue.requireFunction(calleeSymbol, calleeName);
        if (calleeInstance.getTemplate() != function) {
            throw new CodeGenerationException(
                    call, "callee instance does not match the resolved function");
        }

        List<TreeNode> actuals = collectActualTerms(input, children.get(2));
        catalogue.requireArity(function, actuals.size(), call);

        List<String> formalStorage = new ArrayList<>(function.getParameters().size());
        for (Symbol formal : function.getParameters()) {
            formalStorage.add(calleeInstance.getEnvironment().storageFor(formal, call));
        }

        // Reuse the normal term translator's checks against scratch state before emission.
        for (TreeNode actual : actuals) {
            callerGenerator.validateTerm(actual);
        }

        emitter.declareVariables(formalStorage);

        List<String> captures = new ArrayList<>(actuals.size());
        for (TreeNode actual : actuals) {
            String capture = emitter.newTemporary();
            callerGenerator.emitTerm(actual, capture);
            captures.add(capture);
        }

        for (int i = 0; i < captures.size(); i++) {
            emitter.emitStatement(formalStorage.get(i) + " = " + captures.get(i));
        }

        return List.copyOf(captures);
    }

    private static List<TreeNode> collectActualTerms(
            CodeGenerationInput input, TreeNode inputList) {
        List<TreeNode> terms = new ArrayList<>();
        TreeNode current = inputList;
        while (true) {
            input.requireNode(current, "INPUT");
            List<TreeNode> children = current.getChildren();
            if (children.isEmpty()) {
                return List.copyOf(terms);
            }
            if (children.size() != 2
                    || !"TERM".equals(children.get(0).getValue())
                    || !"INPUT".equals(children.get(1).getValue())) {
                throw new CodeGenerationException(current, "malformed INPUT production");
            }
            terms.add(children.get(0));
            current = children.get(1);
        }
    }

    private static boolean isToken(TreeNode node, String value) {
        return node.isTerminal() && value.equals(node.getValue());
    }
}
