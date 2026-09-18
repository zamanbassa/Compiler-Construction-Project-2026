package spl.contract;

import java.util.List;
import spl.lexer.Token;
import spl.parser.SyntaxError;
import spl.tree.TreeNode;

/** Parses lexer tokens into an SPL syntax tree. */
public interface IParser {
    TreeNode parse(List<Token> tokens) throws SyntaxError;
}
