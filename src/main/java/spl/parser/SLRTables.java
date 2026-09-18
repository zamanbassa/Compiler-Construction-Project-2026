package spl.parser;

import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

public class SLRTables {

    private final Map<Integer, Map<Terminal, Action>> actionTable = new HashMap<>();
    private final Map<Integer, Map<NonTerminal, Integer>> gotoTable = new HashMap<>();

    public void setAction(int state, Terminal terminal, Action action) {

        Map<Terminal, Action> row = actionTable.computeIfAbsent(state, ignored -> new HashMap<>());

        Action existing = row.get(terminal);

        if (existing != null && !existing.toString().equals(action.toString())) {
            throw new IllegalStateException(
                    "SLR conflict at state " + state + " for terminal " + terminal + ": existing action " +
                            existing + " conflicts with new action " + action);
        }

        row.put(terminal, action);
    }

    public void setGoto(int state, NonTerminal nonTerminal, int nextState) {

        Map<NonTerminal, Integer> row = gotoTable.computeIfAbsent(state, ignored -> new HashMap<>());
        Integer existing = row.get(nonTerminal);

        if (existing != null && existing != nextState) {

            throw new IllegalStateException("GOTO conflict at state " + state + ", non terminal " + nonTerminal);

        }

        row.put(nonTerminal, nextState);
    }

    public Action getAction(int state, Terminal terminal) {

        Map<Terminal, Action> row = actionTable.get(state);

        if (row == null) {
            return null;
        }

        return row.get(terminal);
    }

    public int getGoto(int state, NonTerminal nonTerminal) {

        Map<NonTerminal, Integer> row = gotoTable.get(state);

        if (row == null || !row.containsKey(nonTerminal)) {
            return -1;
        }

        return row.get(nonTerminal);

    }

    public Set<Terminal> expectedTerminals(int state) {
        Map<Terminal, Action> row = actionTable.get(state);
        if (row == null)
            return Collections.emptySet();
        return row.keySet();
    }

}
