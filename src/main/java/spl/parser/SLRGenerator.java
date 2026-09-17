//generates a SLRTables object
package spl.parser;
import java.util.*;

public class SLRGenerator {

    private final List<Rule> rules = Arrays.asList(Rule.RULES);
    private final List<Set<Item>> states = new ArrayList<>();
    private final Map<Integer, Map<String, Integer>> transitions = new HashMap<>();
    private final Map<NonTerminal, Set<Terminal>> followSets = new EnumMap<>(NonTerminal.class);

    public SLRTables generateSLRTables(){

        calculateFollowSets();
        buildStates();
        SLRTables tables = new SLRTables();
        buildActionAndGotoTables(tables);
        return tables;
    }

    public void calculateFollowSets(){

        Set<NonTerminal> nullable = EnumSet.noneOf(NonTerminal.class);
        Map<NonTerminal, Set<Terminal>> first = new EnumMap<>(NonTerminal.class);

        for (NonTerminal nt : NonTerminal.values()){
            first.put(nt, EnumSet.noneOf(Terminal.class));
            followSets.put(nt, EnumSet.noneOf(Terminal.class));
        }

        //nullable
        boolean changed;

        do{
            changed = false;

            for (Rule r: rules){

                NonTerminal lhs = r.getLHS();

                if (r.getRHS().isEmpty()){
                    changed |= nullable.add(lhs);
                    continue;
                }

                boolean allNullable = true;

                for (String symbol : r.getRHS()){
                    if (!isNonTerminal(symbol) || !nullable.contains(nonTerminal(symbol))){
                        allNullable = false;
                        break;
                    }
                }
                
                if (allNullable){
                    changed |= nullable.add(lhs);
                }
            }
        }
        while (changed);

        //First
        do{
            changed = false;

            for (Rule r: rules){
                Set<Terminal> lhsFirst = first.get(r.getLHS());

                for (String symbol : r.getRHS()){

                    if (isNonTerminal(symbol)){
                        changed |= lhsFirst.addAll(first.get(nonTerminal(symbol)));

                        if (!nullable.contains(nonTerminal(symbol))){
                            break;
                        }

                    }

                    else{
                        changed |= lhsFirst.add(terminal(symbol));
                        break;
                    }
                }
            }
        }
        while (changed);

        followSets.get(NonTerminal.S_PRIME).add(Terminal.EOF); //EOF follows the start symbol
        
        //Follow
        do{

            changed = false;

            for (Rule r: rules){

                NonTerminal lhs = r.getLHS();

                Set<Terminal> trailer = EnumSet.copyOf(followSets.get(lhs));
                List<String> rhs = r.getRHS();

                for (int i = rhs.size() - 1; i >= 0; i--){

                    String symbol = rhs.get(i);

                    if (isNonTerminal(symbol)){

                        NonTerminal curr = nonTerminal(symbol);

                        changed |= followSets.get(curr).addAll(trailer);

                        if (nullable.contains(curr)){
                            trailer.addAll(first.get(curr));
                        }
                        else{
                            trailer = EnumSet.copyOf(first.get(curr));
                        }
                    }
                    else{
                        trailer = EnumSet.of(terminal(symbol));
                    }
                }
            }
        }
        while (changed);

    }

    public void buildStates(){
        //goto algorithm

        states.clear();
        transitions.clear();

        Set<Item> initialItems = new LinkedHashSet<>();
        initialItems.add(new Item(rules.get(0), 0));

        Set<Item> initialState = closure(initialItems);
        states.add(initialState);

        for (int stateNumber = 0; stateNumber < states.size(); stateNumber++){

            Set<Item> state = states.get(stateNumber);
            Set<String> symbols = new LinkedHashSet<>();

            for (Item i: state){

                if (i.nextSymbol() != null){
                    symbols.add(i.nextSymbol());
                }

            }

            for (String symbol: symbols){
                
                Set<Item> target = goTo(state,symbol);

                if (target.isEmpty()){
                    continue;
                }

                int targetNumber = findState(target);

                if (targetNumber == -1){ 
                    targetNumber = states.size();
                    states.add(target);
                }

                transitions.computeIfAbsent(stateNumber, ignored -> new HashMap<>()).put(symbol, targetNumber);
            }
        }
    }
    //helper
    private Set<Item> closure(Set<Item> items){
        Set<Item> result = new LinkedHashSet<>(items);

        boolean changed;

        do{

            changed = false;

            Set<Item> additions = new LinkedHashSet<>();

            for (Item i: result){

                String next = i.nextSymbol();

                if (next == null || !isNonTerminal(next)){
                    continue;
                }

                NonTerminal nt = nonTerminal(next);

                for (Rule r: rules){
                    if (r.getLHS() == nt){
                        additions.add(new Item(r, 0));
                    }
                }
            }
            changed = result.addAll(additions);
        }
        while (changed);

        return result;
    }


    //helper
    private Set<Item> goTo(Set<Item> state, String symbol){

        Set<Item> moved = new LinkedHashSet<>();

        for (Item i: state){
            if (symbol.equals(i.nextSymbol())){
                moved.add(i.advance());
            }
        }

        if (moved.isEmpty()){
            return Collections.emptySet();
        }

        return closure(moved);

    }

    //helper
    private int findState(Set<Item> i){
        for (int j = 0; j < states.size(); j++){
            if (states.get(j).equals(i)){
                return j;
            }
        }
        return -1;
    }

    public void buildActionAndGotoTables(SLRTables tables){
        //shift,reduce,accept and GOTO entries
        for (int stateNumber = 0; stateNumber < states.size(); stateNumber++){

            Set<Item> state = states.get(stateNumber);

            for (Item i: state){

                String next = i.nextSymbol();

                if (next != null){

                    Integer nextState = transitions.getOrDefault(
                        stateNumber, Collections.emptyMap()
                    ).get(next);

                    if (nextState == null){
                        continue;
                    }

                    if (isNonTerminal(next)){
                        tables.setGoto(
                            stateNumber, nonTerminal(next), nextState
                        );
                    }
                    else{
                        tables.setAction(stateNumber, terminal(next), Action.shift(nextState));
                    }

                    continue;
                }

                Rule r = i.rule;

                if (r.getLHS() == NonTerminal.S_PRIME){
                    tables.setAction(stateNumber, Terminal.EOF, Action.ACCEPT);
                    continue;
                }

                Set<Terminal> lookahead = followSets.get(r.getLHS());

                for (Terminal t: lookahead){
                    tables.setAction(stateNumber, t, Action.reduce(r));
                }
            }
        }
    }

    private static final class Item {
        private final Rule rule;
        private final int position; //position of the dot in the rule

        private Item(Rule rule, int position){
            this.rule = rule;
            this.position = position;
        }

        private boolean isComplete(){
            return position >= rule.getRHS().size();
        }

        private String nextSymbol(){
            if (isComplete()){
                return null;
            }
            else{
                return rule.getRHS().get(position);
            }
        }

        private Item advance(){
            return new Item(rule, position + 1);
        }

        @Override
        public boolean equals(Object other){
            if (!(other instanceof Item item)){
                return false;
            }
            return rule.getNumber() == item.rule.getNumber() && position == item.position;
        }

        @Override
        public int hashCode(){
            return Objects.hash(rule.getNumber(), position);
        }
    }

    //HELPER FUNCTIONS
    private boolean isNonTerminal( String symbol){
        for (NonTerminal nt: NonTerminal.values()){
            if (nt.getName().equals(symbol)){
                return true;
            }
        }
        return false;
    }

    private NonTerminal nonTerminal(String symbol){
        return NonTerminal.valueOf(symbol);
    }

    private Terminal terminal (String symbol){
        Terminal result = TERMINALS.get(symbol);
        if (result == null){
            throw new IllegalArgumentException(symbol + " is an unknown terminal");
        }
        return result;
    }

    private static final Map<String, Terminal> TERMINALS = Map.ofEntries(

        Map.entry("USER_DEFINED_NAME", Terminal.USER_DEFINED_NAME),
        Map.entry("NUM", Terminal.NUM),
        Map.entry("STRING", Terminal.STRING),

        Map.entry("}", Terminal.RBRACE),
        Map.entry("{", Terminal.LBRACE),
        Map.entry("(", Terminal.LPAREN),
        Map.entry(")", Terminal.RPAREN),
        Map.entry(";", Terminal.SEMICOLON),
        Map.entry(":", Terminal.COLON),
        Map.entry("=", Terminal.ASSIGN_OP),

        Map.entry("if", Terminal.IF),
        Map.entry("then", Terminal.THEN),
        Map.entry("else", Terminal.ELSE),

        Map.entry("void", Terminal.VOID),
        Map.entry("num", Terminal.NUM_TYPE),
        Map.entry("return", Terminal.RETURN),
        Map.entry("print", Terminal.PRINT),
        Map.entry("nop", Terminal.NOP),
        Map.entry("comment", Terminal.COMMENT),

        Map.entry("do", Terminal.DO),
        Map.entry("while", Terminal.WHILE),
        Map.entry("until", Terminal.UNTIL),

        Map.entry("mod", Terminal.MOD),
        Map.entry("add", Terminal.ADD),
        Map.entry("sub", Terminal.SUB),
        Map.entry("mul", Terminal.MUL),
        Map.entry("div", Terminal.DIV),
        Map.entry("neg", Terminal.NEG),

        Map.entry("not", Terminal.NOT),
        Map.entry("and", Terminal.AND),
        Map.entry("or", Terminal.OR),
        Map.entry("eq", Terminal.EQ),
        Map.entry("larger", Terminal.LARGER),
        Map.entry("lesser", Terminal.LESSER)


    );
}


