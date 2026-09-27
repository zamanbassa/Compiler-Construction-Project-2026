package spl.lexer;

import java.util.Objects;

/** A single lexical token together with its source location. */
public final class Token {
    private final TokenType type;
    private final String lexeme;
    private final int line;
    private final int column;

    /** Creates a token without a known source location. */
    public Token(TokenType type, String lexeme) {
        this(type, lexeme, -1, -1);
    }

    public Token(TokenType type, String lexeme, int line, int column) {
        this.type = Objects.requireNonNull(type, "type");
        this.lexeme = Objects.requireNonNull(lexeme, "lexeme");
        this.line = line;
        this.column = column;
    }

    public TokenType getType() {
        return type;
    }

    public String getLexeme() {
        return lexeme;
    }

    public int getLine() {
        return line;
    }

    public int getColumn() {
        return column;
    }

    @Override
    public String toString() {
        if (line < 0) {
            return type + "(\"" + lexeme + "\")";
        }
        return type + "(\"" + lexeme + "\") at " + line + ":" + column;
    }

    @Override
    public boolean equals(Object other) {
        if (!(other instanceof Token token)) {
            return false;
        }
        return type == token.type
                && lexeme.equals(token.lexeme)
                && line == token.line
                && column == token.column;
    }

    @Override
    public int hashCode() {
        return Objects.hash(type, lexeme, line, column);
    }
}
