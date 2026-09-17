package spl.parser;

import java.lang.reflect.Array;
import java.util.Arrays;
import java.util.List;

public class Rule {
    // TODO: Represent grammar production rules
    private final int number;
    private final NonTerminal lhs;
    private final List<String> rhs;

    public Rule(int number, NonTerminal lhs, List<String> rhs) {
        this.number = number;
        this.lhs = lhs;
        this.rhs = rhs;
    }

    public int getNumber() {
        return number;
    }

    public NonTerminal getLHS() {
        return lhs;
    }

    public List<String> getRHS() {
        return rhs;
    }

    @Override
    public String toString() {
        return number + ": " + lhs.getName() + " -> " + rhs;
    }

    public static final Rule[] RULES = {
            new Rule(0, NonTerminal.S_PRIME, rhs("P")),
            new Rule(1, NonTerminal.P, rhs("V_DECL", ":", "F_DECL", ":", "ALGO")),
            new Rule(2, NonTerminal.V_DECL, rhs()),
            new Rule(3, NonTerminal.V_DECL, rhs("USER_DEFINED_NAME", "V_DECL")),
            new Rule(4, NonTerminal.F_DECL, rhs()),
            new Rule(5, NonTerminal.F_DECL, rhs("F_TYPE", "F_DECL")),
            new Rule(6, NonTerminal.F_TYPE,
                    rhs("void", "USER_DEFINED_NAME", "(", "V_DECL", ")", "{", "P", "return", "}")),
            new Rule(7, NonTerminal.F_TYPE,
                    rhs("num", "USER_DEFINED_NAME", "(", "V_DECL", ")", "{", "P", "return", "(", "TERM", ")", "}")),
            new Rule(8, NonTerminal.ALGO, rhs()),
            new Rule(9, NonTerminal.ALGO, rhs("INSTR", ";", "ALGO")),
            new Rule(10, NonTerminal.OUTP, rhs("(", "TERM", ")")),
            new Rule(11, NonTerminal.OUTP, rhs("STRING")),
            new Rule(12, NonTerminal.INSTR, rhs("print", "OUTP")),
            new Rule(13, NonTerminal.INSTR, rhs("nop")),
            new Rule(14, NonTerminal.INSTR, rhs("comment", "STRING")),
            new Rule(15, NonTerminal.INSTR, rhs("ASSIGN")),
            new Rule(16, NonTerminal.INSTR, rhs("BRANCH")),
            new Rule(17, NonTerminal.INSTR, rhs("LOOP")),
            new Rule(18, NonTerminal.INSTR, rhs("CALL")),
            new Rule(19, NonTerminal.CALL, rhs("USER_DEFINED_NAME", "(", "INPUT", ")")),
            new Rule(20, NonTerminal.INPUT, rhs()),
            new Rule(21, NonTerminal.INPUT, rhs("TERM", "INPUT")),
            new Rule(22, NonTerminal.ASSIGN, rhs("USER_DEFINED_NAME", "=", "TERM")),
            new Rule(23, NonTerminal.TERM, rhs("USER_DEFINED_NAME")),
            new Rule(24, NonTerminal.TERM, rhs("NUM")),
            new Rule(25, NonTerminal.TERM, rhs("CALL")),
            new Rule(26, NonTerminal.TERM, rhs("mod", "(", "TERM", "TERM", ")")),
            new Rule(27, NonTerminal.TERM, rhs("add", "(", "TERM", "TERM", ")")),
            new Rule(28, NonTerminal.TERM, rhs("sub", "(", "TERM", "TERM", ")")),
            new Rule(29, NonTerminal.TERM, rhs("mul", "(", "TERM", "TERM", ")")),
            new Rule(30, NonTerminal.TERM, rhs("div", "(", "TERM", "TERM", ")")),
            new Rule(31, NonTerminal.TERM, rhs("neg", "(", "TERM", ")")),
            new Rule(32, NonTerminal.BRANCH,
                    rhs("if", "BOOL", "then", "{", "ALGO", "}", "else", "{", "ALGO", "}")),
            new Rule(33, NonTerminal.BOOL, rhs("not", "(", "BOOL", ")")),
            new Rule(34, NonTerminal.BOOL, rhs("and", "(", "BOOL", "BOOL", ")")),
            new Rule(35, NonTerminal.BOOL, rhs("or", "(", "BOOL", "BOOL", ")")),
            new Rule(36, NonTerminal.BOOL, rhs("eq", "(", "TERM", "TERM", ")")),
            new Rule(37, NonTerminal.BOOL, rhs("larger", "(", "TERM", "TERM", ")")),
            new Rule(38, NonTerminal.BOOL, rhs("lesser", "(", "TERM", "TERM", ")")),
            new Rule(39, NonTerminal.LOOP, rhs("COND", "BOOL", "do", "{", "ALGO", "}")),
            new Rule(40, NonTerminal.LOOP, rhs("do", "{", "ALGO", "}", "COND", "BOOL")),
            new Rule(41, NonTerminal.COND, rhs("while")),
            new Rule(42, NonTerminal.COND, rhs("until")),
    };

    private static List<String> rhs(String... symbols) {
        return Arrays.asList(symbols);
    }
}
