package spl.parser;

public class Action {
    private enum ActionType {
        SHIFT, REDUCE, ACCEPT
    }

    private final ActionType action;
    private final int state;
    private final Rule rule;

    private Action(ActionType actionType, int state, Rule rule) {
        action = actionType;
        this.state = state;
        this.rule = rule;
    }

    public static Action shift(int state) {
        return new Action(ActionType.SHIFT, state, null);
    }

    public static Action reduce(Rule rule) {
        return new Action(ActionType.REDUCE, -1, rule);
    }

    public static final Action ACCEPT = new Action(ActionType.ACCEPT, -1, null);

    public boolean isShift() {
        return action == ActionType.SHIFT;
    }

    public boolean isReduce() {
        return action == ActionType.REDUCE;
    }

    public boolean isAccept() {
        return action == ActionType.ACCEPT;
    }

    public int getState() {
        return state;
    }

    public Rule getRule() {
        return rule;
    }

    @Override
    public String toString() {
        switch (action) {
            case SHIFT:

                return "s" + state;
            case REDUCE:
                return "r" + rule.getNumber();

            default:
                return "acc";
        }
    }
}
