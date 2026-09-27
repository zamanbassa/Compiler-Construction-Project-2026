package spl.parser;

import java.util.Set;
import java.util.StringJoiner;

public class SyntaxError extends Exception {

    public SyntaxError(String message) {
        super(message);
    }

    public SyntaxError(String lexeme, int line, Set<Terminal> expected) {
        super(build(lexeme, line, expected));
    }

    private static String build(String lexeme, int line, Set<Terminal> expected) {
        StringBuilder sb = new StringBuilder();
        sb.append("Syntax error at line ").append(line).append(": unexpected '").append(lexeme).append("'");
        if (expected != null && !expected.isEmpty()) {
            StringJoiner sj = new StringJoiner(", ");
            for (Terminal t : expected)
                sj.add(display(t));
            sb.append("\n expected one of: ").append(sj);
        }
        return sb.toString();
    }

    private static String display(Terminal t) {
        switch (t) {
            case LPAREN:

                return "(";
            case RPAREN:

                return ")";
            case LBRACE:

                return "{";

            case RBRACE:

                return "}";
            case SEMICOLON:

                return ";";
            case COLON:

                return ":";
            case ASSIGN_OP:

                return "=";
            case EOF:
                return "end of input";
            default:
                return t.name().toLowerCase();

        }
    }
}