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

    public static  final Rule[] RULES = {
            new Rule(0, NonTerminal.S_PRIME, rhs("P"))
            //TODO: rest of the rules
    };

    private static List<String> rhs(String... symbols) {
        return Arrays.asList(symbols);
    }
}
