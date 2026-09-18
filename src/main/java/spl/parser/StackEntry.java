package spl.parser;

import spl.tree.TreeNode;

public final class StackEntry {

    public final int state;
    public final Object symbol;
    public final TreeNode node;

    public StackEntry(int state, Object symbol, TreeNode node) {
        this.state = state;
        this.symbol = symbol;
        this.node = node;
    }

}
