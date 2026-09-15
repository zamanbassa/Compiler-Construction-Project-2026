package spl.parser;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;

import javax.swing.Action;

import spl.lexer.Token;
import spl.tree.TreeNode;

public class SLRDriver {
    private final SLRTables tables;

    public SLRDriver(SLRTables tables) {
        this.tables = tables;
    }

    public TreeNode parse(List<Token> tokens) throws SyntaxError {
        // state, symbol and treenode
        Deque<StackEntry> stack = new ArrayDeque<>();

        // Initial parser state 0$
        stack.push(new StackEntry(0, null, null));

        int inputPosition = 0;

        while (true) {
            // if index>actual length , error occurred
            if (inputPosition >= tokens.size()) {
                throw new SyntaxError("Unexpected end of input");
            }

            // retrieve curr single token from input
            Token currToken = tokens.get(inputPosition);

            // stack top-state
            int state = stack.peek().state;

            Terminal terminal = tables.getTerminal(currToken);


            // ACTION[state, terminal]
            Action action = tables.getAction(state, terminal);

            // if we couldn't find an entry for such in the table..
            if (action == null) {
                throw new SyntaxError("Unexpected token '" + currToken.getLexeme() + "' at line " + currToken.getLine());
            }

            if (action.isShift()) {

                int nextState = action.getState();

                // make a tree node
                TreeNode tNode = new TreeNode(currToken.getLexeme());

                // shift the the current input a well as the state to teh stack
                stack.push(new StackEntry(nextState, terminal, tNode));

                // move 'pointer'to next input token/character
                inputPosition++;
            } else if (action.isReduce()) {
                Rule rule = action.getRule();

                // count the rhs length
                int rhsSize = rule.getRHS().size();

                TreeNode parent = new TreeNode(rule.getLHS().getName());
                // children in OG l-to-r order
                TreeNode[] children = new TreeNode[rhsSize];
                // pop that many from the stack
                for (int i = rhsSize - 1; i >= 0; i--) {
                    StackEntry entry = stack.pop();

                    children[i] = entry.node;
                }
                // add kids to new non-terminal
                for (TreeNode child : children) {
                    parent.addChild(child);
                }

                // referring to teh GOTO table
                int prevState = stack.peek().state;

                NonTerminal lhs = rule.getLHS();

                int gotoState = tables.getGoto(prevState, lhs);

                // the stack top should at all times contain a state
                stack.push(new StackEntry(gotoState, lhs, parent));

            }

            else if (action.isAccept()) {
                StackEntry root = stack.peek();

                return root.node;
            } else {
                throw new SyntaxError("Invalid parser action for token '" + currToken.getLexeme() + "' at line "
                        + currToken.getLine());
            }
        }
    }
}
