package spl.semantic;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import spl.tree.TreeNode;

public class DeclarationResolver {
    private SymbolTable symbolTable;

    public SymbolTable resolve(TreeNode root) {
        Objects.requireNonNull(root, "root");

        symbolTable = new SymbolTable();

        TreeNode program = unwrapProgram(root);
        processProgram(program);

        return symbolTable;
    }

     private TreeNode unwrapProgram(TreeNode root) {
        if ("SPL_PROG".equals(root.getValue())) {
            if (root.getChildren().size() != 1) {
                throw new IllegalArgumentException(
                        "SPL_PROG must contain exactly one program node");
            }

            return root.getChildren().get(0);
        }

        return root;
    }

    private void processProgram(TreeNode programNode) {
        requireValue(programNode, "P");

        TreeNode variableDeclarations = childWithValue(programNode, "V_DECL");
        TreeNode functionDeclarations = childWithValue(programNode, "F_DECL");

        declareVariables(variableDeclarations);

        List<TreeNode> functions = collectFunctions(functionDeclarations);

        for (TreeNode function : functions) {
            declareFunction(function);
        }

        for (TreeNode function : functions) {
            processFunction(function);
        }
    }

     private void declareVariables(TreeNode variableDeclarations) {
        if (variableDeclarations == null) {
            return;
        }

        TreeNode current = variableDeclarations;

        while (current != null
                && "V_DECL".equals(current.getValue())
                && !current.getChildren().isEmpty()) {

            TreeNode nameNode = current.getChildren().get(0);

            symbolTable.declare(
                    nameNode.getValue(),
                    SymbolKind.VARIABLE,
                    nameNode);

            current = findDirectChild(current, "V_DECL");
        }
    }

    private List<TreeNode> collectFunctions(TreeNode functionDeclarations) {
        List<TreeNode> functions = new ArrayList<>();

        TreeNode current = functionDeclarations;

        while (current != null
                && "F_DECL".equals(current.getValue())
                && !current.getChildren().isEmpty()) {

            TreeNode function = findDirectChild(current, "F_TYPE");

            if (function != null) {
                functions.add(function);
            }

            current = findDirectChild(current, "F_DECL");
        }

        return functions;
    }

    private void declareFunction(TreeNode functionNode) {
        TreeNode nameNode = functionNameNode(functionNode);

        symbolTable.declare(
                nameNode.getValue(),
                SymbolKind.FUNCTION,
                nameNode);
    }

    private void processFunction(TreeNode functionNode) {
        symbolTable.enterScope(functionNode);

        try {
            TreeNode parameters = functionParameterNode(functionNode);
            declareVariables(parameters);

            TreeNode bodyProgram = functionBodyProgram(functionNode);

            if (bodyProgram != null) {
                processProgram(bodyProgram);
            }

        } finally {
            symbolTable.exitScope();
        }
    }

    private TreeNode functionNameNode(TreeNode functionNode) {
        requireValue(functionNode, "F_TYPE");

        List<TreeNode> children = functionNode.getChildren();

        if (children.size() < 2) {
            throw new IllegalArgumentException(
                    "Malformed F_TYPE node: missing function name");
        }

        return children.get(1);
    }

    private TreeNode functionParameterNode(TreeNode functionNode) {
        requireValue(functionNode, "F_TYPE");

        List<TreeNode> children = functionNode.getChildren();

        for (int i = 0; i < children.size(); i++) {
            if ("(".equals(children.get(i).getValue())) {
                if (i + 1 < children.size()
                        && "V_DECL".equals(children.get(i + 1).getValue())) {
                    return children.get(i + 1);
                }
            }
        }

        throw new IllegalArgumentException(
                "Malformed F_TYPE node: missing parameter V_DECL");
    }

    private TreeNode functionBodyProgram(TreeNode functionNode) {
        requireValue(functionNode, "F_TYPE");

        for (TreeNode child : functionNode.getChildren()) {
            if ("P".equals(child.getValue())) {
                return child;
            }
        }

        return null;
    }

    private TreeNode childWithValue(TreeNode node, String value) {
        TreeNode child = findDirectChild(node, value);

        if (child == null) {
            throw new IllegalArgumentException(
                    node.getValue() + " is missing child " + value);
        }

        return child;
    }

    private TreeNode findDirectChild(TreeNode node, String value) {
        for (TreeNode child : node.getChildren()) {
            if (value.equals(child.getValue())) {
                return child;
            }
        }

        return null;
    }

    private void requireValue(TreeNode node, String expected) {
        if (!expected.equals(node.getValue())) {
            throw new IllegalArgumentException(
                    "Expected " + expected + " node but found "
                            + node.getValue());
        }
    }

}
