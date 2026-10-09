package spl.semantic;

import spl.tree.TreeNode;

public final class TypeAnalyzerTest
{
    public static void main(String[] args)
    {
        TypeAnalyzer analyzer = new TypeAnalyzer(null);


        TreeNode emptyVarDecls = new TreeNode("V_DECL");
        check(analyzer.analyze(emptyVarDecls) == SemanticType.OK, "empty V_DECL is OK");
        check(emptyVarDecls.getType() == SemanticType.OK, "empty V_DECL type recorded");

        TreeNode emptyFunDecls = new TreeNode("F_DECL");
        check(analyzer.analyze(emptyFunDecls) == SemanticType.OK, "empty F_DECL is OK");


        TreeNode emptyAlgo = new TreeNode("ALGO");
        check(analyzer.analyze(emptyAlgo) == SemanticType.OK, "empty ALGO is OK");
        check(emptyAlgo.getType() == SemanticType.OK, "empty ALGO type recorded");


        TreeNode program = emptyProgramTree();
        check(analyzer.analyze(program) == SemanticType.OK, "empty program is OK");
        check(program.getType() == SemanticType.OK, "SPL_PROG recorded OK");

        check(TypeAnalyzer.firstChild(program, "P").getType() == SemanticType.OK,
        "nested P typed OK before root");

        TreeNode p = TypeAnalyzer.firstChild(program, "P");
        check(TypeAnalyzer.firstChild(p, "V_DECL").getType() == SemanticType.OK, "V_DECL typed");
        check(TypeAnalyzer.firstChild(p, "F_DECL").getType() == SemanticType.OK, "F_DECL typed");
        check(TypeAnalyzer.firstChild(p, "ALGO").getType() == SemanticType.OK, "ALGO typed");


        check(analyzer.analyze(new TreeNode(":", true)) == SemanticType.OK, "terminal is OK");


        expectTypeError(() -> analyzer.analyze(new TreeNode("MYSTERY_NODE")),
        "unknown non-terminal errors");
        expectTypeError(() -> analyzer.analyze(new TreeNode("SPL_PROG")),
        "SPL_PROG without P errors");
        expectTypeError(() -> analyzer.analyze(null),
        "null tree errors");

        System.out.println("Type analyzer (2b.2) tests passed.");
    }


    private static TreeNode emptyProgramTree()
    {
        TreeNode p = new TreeNode("P");
        p.addChild(new TreeNode("V_DECL"));
        p.addChild(new TreeNode(":", true));
        p.addChild(new TreeNode("F_DECL"));
        p.addChild(new TreeNode(":", true));
        p.addChild(new TreeNode("ALGO"));
        TreeNode root = new TreeNode("SPL_PROG");
        root.addChild(p);
        return root;
    }

    private static void check(boolean condition, String message)
    {
        if(!condition)throw new AssertionError(message);
    }

    private static void expectTypeError(Runnable action, String message)
    {
        try
        {
            action.run();
        }
        catch (TypeAnalysisException expected)
        {
            return;
        }
        throw new AssertionError("expected a TypeAnalysisException: " + message);
    }
}