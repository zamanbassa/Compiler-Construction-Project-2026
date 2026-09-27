package spl.lexer;

/** Generic lexical categories used by the SPL parser. */
public enum TokenType {
    IDENTIFIER, // User defined name
    KEYWORD,
    NUMBER,
    STRING,
    SYMBOL,
    EOF
}
