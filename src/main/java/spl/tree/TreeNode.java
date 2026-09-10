package spl.tree;

import java.util.ArrayList;
import java.util.List;

public class TreeNode {
    private final String value;
    private final List<TreeNode> children = new ArrayList<>();

    public TreeNode(String value) {
        this.value = value;
    }

    public String getValue() {
        return value;
    }

    public List<TreeNode> getChildren() {
        return children;
    }

    public void addChild(TreeNode child) {
        children.add(child);
    }
}
