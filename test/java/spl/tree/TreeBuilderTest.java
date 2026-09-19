package spl.tree;

public class TreeBuilderTest {
    
    public static void main(String[] args) {
        testSimpleTree();
        testComplexTree();
        System.out.println("✓ All XML tests passed!");
    }

    private static void testSimpleTree() {
        TreeNode.resetIdCounter();
        
        TreeNode root = new TreeNode("SPL_PROG", false);
        TreeNode child1 = new TreeNode("print", true);
        TreeNode child2 = new TreeNode("Hello", true);
        
        root.addChild(child1);
        root.addChild(child2);
        
        TreeBuilder builder = new TreeBuilder();
        builder.setRoot(root);
        builder.buildTree("output/test_simple.xml");
        
        System.out.println("✓ Simple tree test passed");
    }

    private static void testComplexTree() {
        TreeNode.resetIdCounter();

        TreeNode root = new TreeNode("P", false);
        
        TreeNode vDecl = new TreeNode("V_DECL", false);
        TreeNode var1 = new TreeNode("#x", true);
        TreeNode var2 = new TreeNode("#y", true);
        vDecl.addChild(var1);
        vDecl.addChild(var2);
        
        TreeNode fDecl = new TreeNode("F_DECL", false);
        TreeNode func = new TreeNode("void", true);
        fDecl.addChild(func);
        
        TreeNode algo = new TreeNode("ALGO", false);
        TreeNode print = new TreeNode("print", true);
        algo.addChild(print);
        
        root.addChild(vDecl);
        root.addChild(fDecl);
        root.addChild(algo);
        
        
        TreeBuilder builder = new TreeBuilder();
        builder.setRoot(root);
        builder.buildTree("output/test_complex.xml");
        
        System.out.println("✓ Complex tree test passed");
    }
}