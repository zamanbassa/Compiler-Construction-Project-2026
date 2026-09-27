package spl.contract;

import java.util.List;
import spl.lexer.Token;

/** Converts an SPL source program into a stream of tokens. */
public interface ILexer {
    List<Token> tokenize(String input);
}
