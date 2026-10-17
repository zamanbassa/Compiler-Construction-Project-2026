
package spl.codegen;

import java.util.List;
import spl.semantic.SemanticType;
import spl.tree.TreeNode;

/**
 * Phase 3a.7 - loop translation. All four forms
 * Condition is emitted with emitBool (3a.5). Body is emitted with emitAlgo (3a.8) until 3a.8 lands, body via emitAlgo.
 */
public class LoopCodeGenerator extends AlgorithmCodeGenerator {

    public LoopCodeGenerator(CodeGenerationInput input) {
        super(input);
    }

    // Translate one LOOP node into labelled jumps
    public void emitLoop(TreeNode loop) {
        require(loop, "LOOP");

        List<TreeNode> children = loop.getChildren();
        boolean preTest = !children.isEmpty()
                && "COND".equals(children.get(0).getValue());

        TreeNode cond = child(loop, "COND");
        TreeNode bool = child(loop, "BOOL");
        TreeNode algo = child(loop, "ALGO");
        if (cond == null || bool == null || algo == null) {
            throw new CodeGenerationException(loop, "malformed LOOP production");
        }
        if (bool.getType() != SemanticType.BOOLEAN) {
            throw new CodeGenerationException(bool, "loop condition must be BOOLEAN");
        }

        String kind = cond.getChildren().isEmpty()
                ? "" : cond.getChildren().get(0).getValue();
        if (!"while".equals(kind) && !"until".equals(kind)) {
            throw new CodeGenerationException(cond, "COND must be while or until");
        }

        if (preTest) {
            emitPreTest(kind, bool, algo);
        } else {
            emitPostTest(kind, bool, algo);
        }
    }

    // Pre-test until exit when true while: same skeleton, labels swapped 
    private void emitPreTest(String kind, TreeNode bool, TreeNode algo) {
        String top  = emitter.newLabel();
        String body = emitter.newLabel();
        String exit = emitter.newLabel();

        emitter.emitLabel(top);
        if ("until".equals(kind)) {
            emitBool(bool, exit, body);
        } else { 
            emitBool(bool, body, exit);
        }
        emitter.emitLabel(body);
        emitAlgo(algo);
        emitter.emitGoto(top);
        emitter.emitLabel(exit);
    }

    // Post-test do-while: repeat when true do-until: same skeleton, labels swapped 
    private void emitPostTest(String kind, TreeNode bool, TreeNode algo) {
        String top  = emitter.newLabel();
        String exit = emitter.newLabel();

        emitter.emitLabel(top);
        emitAlgo(algo);
        if ("while".equals(kind)) {
            emitBool(bool, top, exit);
        } else {        
            emitBool(bool, exit, top);
        }
        emitter.emitLabel(exit);
    }

    private TreeNode child(TreeNode node, String value) {
        for (TreeNode c : node.getChildren()) {
            if (value.equals(c.getValue())) return c;
        }
        return null;
    }
}