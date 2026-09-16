package spl.parser;

public enum Terminal {
    // categories
    USER_DEFINED_NAME, NUM, STRING,
    // marks
    LPAREN, RPAREN, LBRACE, RBRACE, SEMICOLON, ASSIGN_OP, COLON,

    // keywords
    VOID, NUM_TYPE, RETURN, PRINT, NOP, COMMENT,
    IF, THEN, ELSE,
    NOT, AND, OR, EQ, LARGER, LESSER,
    MOD, ADD, SUB, MUL,DIV, NEG, 
    DO, WHILE, UNTIL,

    // $
    EOF

}
