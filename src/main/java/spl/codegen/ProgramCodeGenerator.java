package spl.codegen;

import java.util.List;
import spl.semantic.SemanticType;
import spl.semantic.Symbol;
import spl.semantic.SymbolKind;
import spl.tree.TreeNode;

public final class ProgramCodeGenerator {
    private static final String LINE_END = "\r\n";

    private static final String CODE_MINE_BEGIN =
            "/* begin of the non-executable code-mine for later inlining */";
    private static final String CODE_MINE_END =
            "/* end of the non-executable code-mine */";
    private static final String EXECUTABLE_BEGIN =
            "/* begin of executable C code */";
    private static final String EXECUTABLE_END =
            "/* end of executable C */";
    private static final String DEFERRED_FUNCTION_TEMPLATES =
            "/* function templates deferred to a later Phase 3b issue */";

    private final CodeGenerationInput input;

    public ProgramCodeGenerator(CodeGenerationInput input) {
        if (input == null) {
            throw new CodeGenerationException("missing input");
        }
        this.input = input;
    }

    public String generate() {
        TreeNode root = input.getTree();
        input.requireNode(root, "SPL_PROG");

        if (root.isTerminal() || root.getChildren().size() != 1) {
            throw new CodeGenerationException(
                    root, "SPL_PROG must contain exactly one P");
        }

        TreeNode program = root.getChildren().get(0);
        input.requireNode(program, "P");

        List<TreeNode> children = program.getChildren();
        if (program.isTerminal() || children.size() != 5
                || !"V_DECL".equals(children.get(0).getValue())
                || !isToken(children.get(1), ":")
                || !"F_DECL".equals(children.get(2).getValue())
                || !isToken(children.get(3), ":")
                || !"ALGO".equals(children.get(4).getValue())) {
            throw new CodeGenerationException(program, "malformed P production");
        }

        TreeNode variableDeclarations = children.get(0);
        TreeNode functionDeclarations = children.get(2);
        TreeNode algorithm = children.get(4);

        String functionDeclarationTranslation =
                translateFunctionDeclarations(functionDeclarations);
        String variableDeclarationTranslation =
                generateVariableDeclarations(variableDeclarations);
        String algorithmTranslation =
                new AlgorithmCodeGenerator(input).generate(algorithm);

        StringBuilder output = new StringBuilder();
        appendLine(output, CODE_MINE_BEGIN);
        output.append(functionDeclarationTranslation).append(LINE_END);
        appendLine(output, CODE_MINE_END);
        appendLine(output, EXECUTABLE_BEGIN);
        appendLine(output, "#include <iostream>");
        appendLine(output, "int main() {");
        output.append(variableDeclarationTranslation).append(LINE_END);

        appendAlgorithmAndReturn(output, algorithmTranslation);

        appendLine(output, "}");
        appendLine(output, EXECUTABLE_END);
        return output.toString();
    }


    public String generateVariableDeclarations(TreeNode declarations) {
        input.requireNode(declarations, "V_DECL");

        StringBuilder output = new StringBuilder();
        TreeNode current = declarations;

        while (true) {
            input.requireNode(current, "V_DECL");
            List<TreeNode> children = current.getChildren();

            if (children.isEmpty()) {
                output.append(' ');
                return output.toString();
            }
            if (children.size() != 2
                    || !children.get(0).isTerminal()
                    || !children.get(0).getValue().startsWith("#")
                    || !"V_DECL".equals(children.get(1).getValue())) {
                throw new CodeGenerationException(
                        current, "malformed V_DECL production");
            }

            TreeNode name = children.get(0);
            Symbol symbol = name.getResolvedSymbol();
            if (symbol == null) {
                throw new CodeGenerationException(
                        name, "variable declaration has no resolved symbol");
            }
            if (symbol.getKind() != SymbolKind.VARIABLE
                    || symbol.getDeclarationNode() != name
                    || symbol.getType() != SemanticType.NUMERIC
                    || name.getType() != SemanticType.NUMERIC) {
                throw new CodeGenerationException(
                        name, "invalid resolved variable declaration binding");
            }

            output.append(input.getNumericMode().getTargetType())
                    .append(' ')
                    .append(symbol.getGeneratedName())
                    .append(';')
                    .append(LINE_END);

            current = children.get(1);
        }
    }

    private String translateFunctionDeclarations(TreeNode declarations) {
        input.requireNode(declarations, "F_DECL");

        TreeNode current = declarations;
        boolean hasDeclarations = false;

        while (true) {
            input.requireNode(current, "F_DECL");
            List<TreeNode> children = current.getChildren();

            if (children.isEmpty()) {
                return hasDeclarations ? DEFERRED_FUNCTION_TEMPLATES : " ";
            }
            if (children.size() != 2
                    || !"F_TYPE".equals(children.get(0).getValue())
                    || !"F_DECL".equals(children.get(1).getValue())) {
                throw new CodeGenerationException(
                        current, "malformed F_DECL production");
            }

            input.requireNode(children.get(0), "F_TYPE");
            current = children.get(1);
            hasDeclarations = true;
        }
    }

    private static void appendAlgorithmAndReturn(
            StringBuilder output, String algorithmTranslation) {
        String normalized = normalizeLineEndings(algorithmTranslation);

        while (normalized.endsWith(LINE_END)) {
            normalized = normalized.substring(
                    0, normalized.length() - LINE_END.length());
        }

        output.append(normalized)
                .append(LINE_END)
                .append("return 0;")
                .append(LINE_END);
    }

    private static String normalizeLineEndings(String text) {
        return text.replace("\r\n", "\n")
                .replace('\r', '\n')
                .replace("\n", LINE_END);
    }

    private static void appendLine(StringBuilder output, String line) {
        output.append(line).append(LINE_END);
    }

    private static boolean isToken(TreeNode node, String value) {
        return node.isTerminal() && value.equals(node.getValue());
    }
}