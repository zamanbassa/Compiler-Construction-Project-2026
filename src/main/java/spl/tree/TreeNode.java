package spl.tree;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import spl.semantic.SemanticType;
import spl.semantic.Symbol;

public class TreeNode {
    private static int idCounter = 0;

    private final int nodeId;
    private final String value;
    private final boolean isTerminal;
    private final List<TreeNode> children = new ArrayList<>();
    private TreeNode parent;
    private Symbol resolvedSymbol;
    private SemanticType type = SemanticType.UNKNOWN;

    public TreeNode(String value) {
        this(value,false);
    }

     public TreeNode(String value, boolean isTerminal) {
        this.nodeId = ++idCounter;
        this.value = value;
        this.isTerminal = isTerminal;
        this.parent = null;
    }

    public int getNodeId(){
        return nodeId;
    }

    public String getValue() {
        return value;
    }

    public boolean isTerminal(){
        return isTerminal;
    }

    public List<TreeNode> getChildren() {
        return children;
    }

    public TreeNode getParent(){
        return parent;
    }

    /** Type of this syntax node, independent of the declaration symbol type. */
    public SemanticType getType() {
        return type;
    }

    /** Records Phase 2b analysis without changing the Phase 2a symbol binding. */
    public void setType(SemanticType type) {
        this.type = Objects.requireNonNull(type, "type");
    }

    public Symbol getResolvedSymbol() {
        return resolvedSymbol;
    }

    public void setResolvedSymbol(Symbol resolvedSymbol) {
        this.resolvedSymbol = resolvedSymbol;
    }

    public void addChild(TreeNode child) {
        if(child != null){
            children.add(child);
            child.parent = this;
        }
    }

     public List<Integer> getChildrenIds() {
        List<Integer> ids = new ArrayList<>();
        for (TreeNode child : children) {
            ids.add(child.getNodeId());
        }
        return ids;
    }

    public static void resetIdCounter() {
        idCounter = 0;
    }
}
