package spl.tree;

import spl.contract.ITreeBuilder;

public class TreeBuilder implements ITreeBuilder {
    
    private TreeNode root;
    private final XMLWriter xmlWriter;

    public TreeBuilder() {
        this.xmlWriter = new XMLWriter();
    }

    public void setRoot(TreeNode root) {
        this.root = root;
    }

    @Override
    public void buildTree() {
        if (root == null) {
            throw new IllegalStateException("Root node not set. Call setRoot() before buildTree()");
        }
        xmlWriter.write(root, "output/tree.xml");
    }

    //overloaded function to write to specified output path
    public void buildTree(String outputPath) {
        if (root == null) {
            throw new IllegalStateException("Root node not set. Call setRoot() before buildTree()");
        }
        xmlWriter.write(root, outputPath);
    }

    public TreeNode getRoot() {
        return root;
    }
}
