package spl.lexer;

import java.util.List;

/**
 * Small dependency-free regression test suite.
 * Run with assertions enabled: java -ea spl.lexer.LexerTest
 */
public final class LexerTest {
    private LexerTest() {
    }

    public static void main(String[] args) {
        tokenizesKeywordsNamesNumbersStringsAndSymbols();
        acceptsSpaceAndNewlineSeparators();
        rejectsMissingSeparators();
        rejectsInvalidNamesNumbersAndStrings();
        System.out.println("Lexer tests passed.");
    }

    private static void tokenizesKeywordsNamesNumbersStringsAndSymbols() {
        String input = "num #main ( #x ) { print \"hello,world\" ; } 0 -0.5 42.11 ";
        List<Token> tokens = new Lexer().tokenize(input);

        assertType(tokens, 0, TokenType.KEYWORD, "num");
        assertType(tokens, 1, TokenType.IDENTIFIER, "#main");
        assertType(tokens, 2, TokenType.SYMBOL, "(");
        assertType(tokens, 3, TokenType.IDENTIFIER, "#x");
        assertType(tokens, 4, TokenType.SYMBOL, ")");
        assertType(tokens, 5, TokenType.SYMBOL, "{");
        assertType(tokens, 6, TokenType.KEYWORD, "print");
        assertType(tokens, 7, TokenType.STRING, "\"hello,world\"");
        assertType(tokens, 8, TokenType.SYMBOL, ";");
        assertType(tokens, 9, TokenType.SYMBOL, "}");
        assertType(tokens, 10, TokenType.NUMBER, "0");
        assertType(tokens, 11, TokenType.NUMBER, "-0.5");
        assertType(tokens, 12, TokenType.NUMBER, "42.11");
        assertType(tokens, 13, TokenType.EOF, "");
    }

    private static void acceptsSpaceAndNewlineSeparators() {
        List<Token> tokens = new Lexer().tokenize("print\r\n\"ok\" \n nop ");
        assertType(tokens, 0, TokenType.KEYWORD, "print");
        assertType(tokens, 1, TokenType.STRING, "\"ok\"");
        assertType(tokens, 2, TokenType.KEYWORD, "nop");
        assert tokens.get(1).getLine() == 2 : "string should be on line 2";
        assert tokens.get(2).getLine() == 3 : "nop should be on line 3";
    }

    private static void rejectsMissingSeparators() {
        assertThrows("print", "must be followed by a blank space");
        assertThrows("0", "must be followed by a blank space");
        assertThrows("#x", "must be followed by a blank space");
        assertThrows(";", "must be followed by a blank space");
    }

    private static void rejectsInvalidNamesNumbersAndStrings() {
        assertThrows("#x_ ", "must be followed by a blank space");
        assertThrows("007 ", "invalid number");
        assertThrows("0.10 ", "invalid number");
        assertThrows("\"Hello\" ", "invalid character inside a string");
        assertThrows("\"unterminated", "unterminated string");
        assertThrows("wat ", "unknown word");
    }

    private static void assertType(List<Token> tokens, int index, TokenType type, String lexeme) {
        Token token = tokens.get(index);
        assert token.getType() == type : "expected " + type + ", got " + token;
        assert token.getLexeme().equals(lexeme)
                : "expected lexeme '" + lexeme + "', got '" + token.getLexeme() + "'";
    }

    private static void assertThrows(String input, String expectedMessagePart) {
        try {
            new Lexer().tokenize(input);
            throw new AssertionError("expected a lexical error for: " + input);
        } catch (LexicalException exception) {
            assert exception.getMessage().contains(expectedMessagePart)
                    : "unexpected message: " + exception.getMessage();
        }
    }
}
