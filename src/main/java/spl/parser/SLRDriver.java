package spl.parser;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;

import spl.lexer.Token;
import spl.lexer.TokenType;
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
            // if index>=actual length , error occurred
            if (inputPosition >= tokens.size()) {
                int state = stack.peek().state;
                throw new SyntaxError("end of input", -1, tables.expectedTerminals(state));
            }

            // retrieve curr single token from input
            Token currToken = tokens.get(inputPosition);

            // stack top-state
            int state = stack.peek().state;

            Terminal terminal = getTerminal(currToken);

            // ACTION[state, terminal]
            Action action = tables.getAction(state, terminal);

            // if we couldn't find an entry for such in the table..
            if (action == null) {
                throw new SyntaxError(
                        currToken.getLexeme(), currToken.getLine(), tables.expectedTerminals(state));
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

    // mappings

    private Terminal getTerminal(Token token) {
        switch (token.getType()) {
            case IDENTIFIER:
                return Terminal.USER_DEFINED_NAME;
            case NUMBER:
                return Terminal.NUM;
            case STRING:
                return Terminal.STRING;
            case EOF:
                return Terminal.EOF;
            case KEYWORD:
                return keyword(token.getLexeme());
            case SYMBOL:
                return symbol(token.getLexeme());

            default:
                throw new IllegalStateException("Unmapped token type: " + token.getType());
        }
    }

    private Terminal keyword(String word) {
        switch (word) {
            case "void":

                return Terminal.VOID;
            case "num":

                return Terminal.NUM_TYPE;
            case "return":

                return Terminal.RETURN;
            case "print":

                return Terminal.PRINT;
            case "nop":

                return Terminal.NOP;
            case "comment":

                return Terminal.COMMENT;
            case "if":

                return Terminal.IF;
            case "then":

                return Terminal.THEN;
            case "else":

                return Terminal.ELSE;
            case "not":

                return Terminal.NOT;
            case "and":

                return Terminal.AND;
            case "or":

                return Terminal.OR;
            case "eq":

                return Terminal.EQ;
            case "larger":

                return Terminal.LARGER;
            case "lesser":

                return Terminal.LESSER;
            case "mod":

                return Terminal.MOD;
            case "add":

                return Terminal.ADD;
            case "sub":

                return Terminal.SUB;
            case "mul":

                return Terminal.MUL;

            case "div":

                return Terminal.DIV;

            case "neg":

                return Terminal.NEG;

            case "do":

                return Terminal.DO;

            case "while":

                return Terminal.WHILE;

            case "until":

                return Terminal.UNTIL;

            default:
                throw new IllegalStateException("Unknown keyword: " + word);
        }
    }

    private Terminal symbol(String sy) {
        switch (sy) {
            case "(":

                return Terminal.LPAREN;

            case ")":

                return Terminal.RPAREN;
            case "{":

                return Terminal.LBRACE;
            case "}":

                return Terminal.RBRACE;
            case ";":

                return Terminal.SEMICOLON;
            case "=":

                return Terminal.ASSIGN_OP;
            case ":":

                return Terminal.COLON;

            default:
                throw new IllegalStateException("Unknown symbol: " + sy);
        }
    }
}
