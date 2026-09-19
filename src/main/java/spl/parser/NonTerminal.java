package spl.parser;

public enum NonTerminal {
    // TODO: Add grammar non-terminals
    S_PRIME,
    P,
    V_DECL,
    F_DECL,
    F_TYPE,
    ALGO,
    OUTP,
    INSTR,
    CALL,
    INPUT,
    ASSIGN,
    TERM,
    BRANCH,
    BOOL,
    LOOP,
    COND;

    public String getName() {
        return name();
    }
}
