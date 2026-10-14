package spl.semantic;

import java.util.ArrayList;
import java.util.List;
import spl.tree.TreeNode;


public final class TypeAnalyzer
{

    private final SymbolTable symbolTable;// available to the rule lanes(2b.3 symbol typing)

    public TypeAnalyzer(SymbolTable symbolTable)
    {
        this.symbolTable = symbolTable;
    }


    public SemanticType analyze(TreeNode root)
    {
        if(root == null)
        {
            throw new TypeAnalysisException("cannot analyze a null syntax tree");
        }
        initializeFunctionTypes(root); //2b.3
        return analyzeNode(root);
    }

    private void initializeFunctionTypes(TreeNode node){

        if ("F_TYPE".equals(node.getValue()) && node.getChildren().size() >= 2){

            TreeNode returnKind = node.getChildren().get(0);
            TreeNode name = node.getChildren().get(1);
            Symbol symbol = requireResolvedSymbol(name);

            if ("void".equals(returnKind.getValue())){
                symbol.setType(SemanticType.PROCEDURE);
            }
            else if ("num".equals(returnKind.getValue())){
                symbol.setType(SemanticType.NUMERIC);
            }
            else{
                throw new TypeAnalysisException(node, "expected void or num function");
            }
            name.setType(symbol.getType());
        }

        for (TreeNode child : node.getChildren()){
            initializeFunctionTypes(child);
        }
    }

    //Helper
    private Symbol requireResolvedSymbol(TreeNode node){
        Symbol symbol = node.getResolvedSymbol();
        if (symbol == null){
            throw new TypeAnalysisException(node, "name has no resolved symbol");
        }
        return symbol;
    }

    //  engine

    private SemanticType analyzeNode(TreeNode node)
    {
        for(TreeNode child : node.getChildren())
        {
            // 1 type children first (post-order)
            analyzeNode(child);
        }
        SemanticType type = dispatch(node);// 2 apply this production's rule
        node.setType(type);// 3 record the result on the node
        return type;
    }

    private SemanticType dispatch(TreeNode node)
    {
        switch(node.getValue())
        {
            //  structural productions 
        case "SPL_PROG": return typeSplProg(node);
        case "P": return typeProgram(node);
        case "V_DECL": return typeVarDecls(node);
        case "F_DECL": return typeFunDecls(node);
        case "F_TYPE": return typeFunction(node);
        case "ALGO": return typeAlgorithm(node);

            //  2b.3 hooks 
        case "INSTR": return typeInstruction(node);
        case "OUTP": return typeOutput(node);
        case "ASSIGN": return typeAssign(node);
        case "CALL": return typeCall(node);
        case "INPUT": return typeInput(node);

            //  2b.4 hooks 
        case "TERM": return typeTerm(node);
        case "BOOL": return typeBool(node);
        case "BRANCH": return typeBranch(node);
        case "LOOP": return typeLoop(node);
        case "COND": return typeCond(node);

        default:
            if(node.isTerminal())
            {
                return SemanticType.OK;
            }
            throw new TypeAnalysisException(node,
            "unsupported or malformed production: " + node.getValue());
        }
    }


    //  STRUCTURAL RULES 



    private SemanticType typeSplProg(TreeNode node)
    {
        TreeNode program = firstChild(node, "P");
        if(program == null)
        {
            throw new TypeAnalysisException(node, "SPL_PROG must contain a P node");
        }
        requireType(program, SemanticType.OK);// becauuse program already typed by the walk
        return SemanticType.OK;
    }


    private SemanticType typeProgram(TreeNode node)
    {
        for(TreeNode child : node.getChildren())
        {
            switch(child.getValue())
            {
            case "V_DECL":
            case "F_DECL":
            case "ALGO":
                requireType(child, SemanticType.OK);
                break;
            default: /* ":" punctuation - ignore */ break;
            }
        }
        return SemanticType.OK;
    }

    /** V_DECL -> '' | USER_DEFINED_NAME V_DECL. Nullable -> OK. */
    private SemanticType typeVarDecls(TreeNode node)
    {
        // HOOK (2b.3): for each declared variable name, set its Symbol type to NUMERIC.
        for (TreeNode child : node.getChildren()){
            if (child.isTerminal() && child.getValue().startsWith("#")){
                Symbol symbol = requireResolvedSymbol(child);
                symbol.setType(SemanticType.NUMERIC);
                child.setType(SemanticType.NUMERIC);
            }
            else if ("V_DECL".equals(child.getValue())){
                requireType(child, SemanticType.OK);
            }
        }
        return SemanticType.OK;
    }

    /** F_DECL -> '' | F_TYPE F_DECL. Nullable -> OK; each F_TYPE child must be OK. */
    private SemanticType typeFunDecls(TreeNode node)
    {
        for(TreeNode child : node.getChildren())
        {
            if("F_TYPE".equals(child.getValue()))
            {
                requireType(child, SemanticType.OK);
            }
        }
        return SemanticType.OK;
    }


    private SemanticType typeFunction(TreeNode node)
    {
        // HOOK (2b.3): set this function's Symbol type (void -> PROCEDURE, num -> NUMERIC)
        //              and, for 'num', check the return TERM is NUMERIC.

        if (node.getChildren().size() < 2){
            throw new TypeAnalysisException(node, "malformed function declaration");
        }

        TreeNode kind = node.getChildren().get(0);
        TreeNode name = node.getChildren().get(1);
        Symbol symbol = requireResolvedSymbol(name);

        SemanticType functionType;
        if ("void".equals(kind.getValue())){
            functionType = SemanticType.PROCEDURE;
        }
        else if ("num".equals(kind.getValue())){
            functionType = SemanticType.NUMERIC;
        }
        else{
            throw new TypeAnalysisException(node, "expected void or num function");
        }

        symbol.setType(functionType);
        name.setType(functionType);

        TreeNode parameters = firstChild(node, "V_DECL");
        TreeNode body = firstChild(node, "P");// the nested sub-program
        if(parameters == null ||body == null)
        {
            throw new TypeAnalysisException(node, "malformed function declaration");
        }
        requireType(parameters, SemanticType.OK);
        requireType(body, SemanticType.OK);

        if (functionType == SemanticType.NUMERIC){
            TreeNode returnTerm = firstChild(node, "TERM");
            if (returnTerm == null){
                throw new TypeAnalysisException(node, "numeric function is missing its return term");
            }
            requireType(returnTerm, SemanticType.NUMERIC);
        }
        
        return SemanticType.OK;
    }


    private SemanticType typeAlgorithm(TreeNode node)
    {
        for(TreeNode child : node.getChildren())
        {
            if("INSTR".equals(child.getValue()))
            {
                requireType(child, SemanticType.OK);
            }
            // a nested ALGO child was already typed OK by the walk
        }
        return SemanticType.OK;
    }


    //  2b.3 HOOKS  declarations  calls  assignments  instructions

    private SemanticType typeInstruction(TreeNode node)
    {
        TreeNode child;

        if ((child = firstChild(node, "OUTP")) != null){
            requireType(child, SemanticType.OK);
        }
        else if ((child = firstChild(node, "ASSIGN")) != null || 
        (child = firstChild(node, "BRANCH")) != null || 
        (child = firstChild(node, "LOOP")) != null){
            requireType(child, SemanticType.OK);
        }
        else if ((child = firstChild(node, "CALL")) != null){
            requireType(child, SemanticType.PROCEDURE);
        }
        //otherwise this is nop or comment STRING which are correct
        return SemanticType.OK;
    }

    private SemanticType typeOutput(TreeNode node)
    {
        TreeNode term = firstChild(node, "TERM");

        if (term != null){
            requireType(term, SemanticType.NUMERIC);
        }
        //the other OUTP production contains STRING
        return SemanticType.OK;
    }
    private SemanticType typeAssign(TreeNode node)
    {
        if (node.getChildren().isEmpty()){
            throw new TypeAnalysisException(node, "malformed assignment");
        }

        TreeNode target = node.getChildren().get(0);
        Symbol symbol = requireResolvedSymbol(target);

        if (symbol.getKind() != SymbolKind.VARIABLE){
            throw new TypeAnalysisException(target, "assignment target must be a variable");
        }
        if (symbol.getType() != SemanticType.NUMERIC){
            throw new TypeAnalysisException(target, "assignment target must be a number");
        }

        TreeNode term = firstChild(node, "TERM");
        if (term == null){
            throw new TypeAnalysisException(node, "assignment is missing its value");
        }
        requireType(term, SemanticType.NUMERIC);

        return SemanticType.OK;
    }

    private SemanticType typeCall(TreeNode node)
    {
        if (node.getChildren().isEmpty()){
            throw new TypeAnalysisException(node, "malformed function call");
        }

        TreeNode name = node.getChildren().get(0);
        Symbol function = requireResolvedSymbol(name);

        if (function.getKind() != SymbolKind.FUNCTION){
            throw new TypeAnalysisException(name, "call target must be a function");
        }

        TreeNode input = firstChild(node, "INPUT");
        if (input == null){
            throw new TypeAnalysisException(node, "call is missing its input list");
        }
        requireType(input, SemanticType.OK);

        if (function.getType() != SemanticType.PROCEDURE && function.getType() != SemanticType.NUMERIC){
            throw new TypeAnalysisException(name, "function type is not known");
        }

        return function.getType();

    }
    private SemanticType typeInput(TreeNode node)
    {
        for (TreeNode child : node.getChildren()){
            if ("TERM".equals(child.getValue())){
                requireType(child, SemanticType.NUMERIC);
            }
            else if ("INPUT".equals(child.getValue())){
                requireType(child, SemanticType.OK);
            }
        }

        return SemanticType.OK;
    }


    //  2b.4 HOOKS  arithmetic  boolean  branch  loop

        private SemanticType typeTerm(TreeNode node)
        {
           if (node.getChildren().isEmpty()){
                throw new TypeAnalysisException(node, "malformed numeric term");
            }

            List<TreeNode> directTerms = new ArrayList<>();
            for (TreeNode child : node.getChildren()){
                if ("TERM".equals(child.getValue())){
                    directTerms.add(child);
                }
            }

            for (TreeNode child : node.getChildren()){
                if (!child.isTerminal()){
                    continue;
                }

                String value = child.getValue();
                switch (value) {
                    case "mod":
                    case "add":
                    case "sub":
                    case "mul":
                    case "div":
                        if (directTerms.size() != 2){
                            throw new TypeAnalysisException(node,
                                "arithmetic operator requires exactly two numeric operands");
                        }
                        for (TreeNode term : directTerms){
                            requireType(term, SemanticType.NUMERIC);
                        }
                        return SemanticType.NUMERIC;
                    case "neg":
                        if (directTerms.size() != 1){
                            throw new TypeAnalysisException(node,
                                "neg requires exactly one numeric operand");
                        }
                        requireType(directTerms.get(0), SemanticType.NUMERIC);
                        return SemanticType.NUMERIC;
                    default:
                        break;
                }
            }

            if (node.getChildren().size() == 1){
                TreeNode child = node.getChildren().get(0);
                if (child.isTerminal()){
                    if (isNumericLiteral(child.getValue())){
                        child.setType(SemanticType.NUMERIC);
                        return SemanticType.NUMERIC;
                    }
                    if (child.getValue().startsWith("#")){
                        Symbol symbol = requireResolvedSymbol(child);
                        if (symbol.getKind() != SymbolKind.VARIABLE){
                            throw new TypeAnalysisException(child,
                                "term reference must resolve to a variable");
                        }
                        if (symbol.getType() != SemanticType.NUMERIC){
                            throw new TypeAnalysisException(child,
                                "variable is not numeric in this term");
                        }
                        child.setType(SemanticType.NUMERIC);
                        return SemanticType.NUMERIC;
                    }
                }
                else if ("CALL".equals(child.getValue())){
                    requireType(child, SemanticType.NUMERIC);
                    return SemanticType.NUMERIC;
                }
            }

            throw new TypeAnalysisException(node, "malformed numeric term"); 
        }

        private SemanticType typeBool(TreeNode node)
        {
            if (node.getChildren().isEmpty()){
                throw new TypeAnalysisException(node, "malformed boolean expression");
            }

            for (TreeNode child : node.getChildren()){
                if (!child.isTerminal()){
                    continue;
                }

                String value = child.getValue();
                switch (value) {
                    case "not":
                        TreeNode innerBool = firstChild(node, "BOOL");
                        if (innerBool == null){
                            throw new TypeAnalysisException(node,
                                "not requires one boolean operand");
                        }
                        requireType(innerBool, SemanticType.BOOLEAN);
                        return SemanticType.BOOLEAN;
                    case "and":
                    case "or":
                        List<TreeNode> boolOperands = new ArrayList<>();
                        for (TreeNode operand : node.getChildren()){
                            if ("BOOL".equals(operand.getValue())){
                                boolOperands.add(operand);
                            }
                        }
                        if (boolOperands.size() != 2){
                            throw new TypeAnalysisException(node,
                                value + " requires exactly two boolean operands");
                        }
                        for (TreeNode operand : boolOperands){
                            requireType(operand, SemanticType.BOOLEAN);
                        }
                        return SemanticType.BOOLEAN;
                    case "eq":
                    case "larger":
                    case "lesser":
                        List<TreeNode> comparisonTerms = new ArrayList<>();
                        for (TreeNode operand : node.getChildren()){
                            if ("TERM".equals(operand.getValue())){
                                comparisonTerms.add(operand);
                            }
                        }
                        if (comparisonTerms.size() != 2){
                            throw new TypeAnalysisException(node,
                                value + " requires exactly two numeric operands");
                        }
                        for (TreeNode operand : comparisonTerms){
                            requireType(operand, SemanticType.NUMERIC);
                        }
                        return SemanticType.BOOLEAN;
                    default:
                        break;
                }
            }

            throw new TypeAnalysisException(node, "malformed boolean expression");     
        }

        private SemanticType typeBranch(TreeNode node)
        {
            TreeNode condition = firstChild(node, "BOOL");
            if (condition == null){
                throw new TypeAnalysisException(node, "branch is missing a boolean condition");
            }
            requireType(condition, SemanticType.BOOLEAN);

            List<TreeNode> algorithms = new ArrayList<>();
            for (TreeNode child : node.getChildren()){
                if ("ALGO".equals(child.getValue())){
                    algorithms.add(child);
                }
            }
            if (algorithms.size() != 2){
                throw new TypeAnalysisException(node, "branch must have exactly two algorithm bodies");
            }
            for (TreeNode algorithm : algorithms){
                requireType(algorithm, SemanticType.OK);
            }
            return SemanticType.OK;
        }

        private SemanticType typeLoop(TreeNode node)
        {
            TreeNode condition = firstChild(node, "COND");
            if (condition == null){
                throw new TypeAnalysisException(node, "loop is missing its condition");
            }
            requireType(condition, SemanticType.OK);

            TreeNode bool = firstChild(node, "BOOL");
            if (bool == null){
                throw new TypeAnalysisException(node, "loop is missing its boolean test");
            }
            requireType(bool, SemanticType.BOOLEAN);

            TreeNode algorithm = firstChild(node, "ALGO");
            if (algorithm == null){
                throw new TypeAnalysisException(node, "loop is missing its algorithm body");
            }
            requireType(algorithm, SemanticType.OK);

            return SemanticType.OK;
        }

        private SemanticType typeCond(TreeNode node)
        {
            if (node.getChildren().isEmpty()){
                throw new TypeAnalysisException(node, "malformed loop condition");
            }

            TreeNode child = node.getChildren().get(0);
            if (child.isTerminal() && ("while".equals(child.getValue()) || "until".equals(child.getValue()))){
                child.setType(SemanticType.OK);
                return SemanticType.OK;
            }

            throw new TypeAnalysisException(node, "condition must be while or until");
        }





    /** First direct child with the given value, or null. */
        public static TreeNode firstChild(TreeNode node, String value)
        {
            for(TreeNode child : node.getChildren())
            {
                if(value.equals(child.getValue()))return child;
            }
            return null;
        }


        public static boolean containsValue(TreeNode node, String value)
        {
            if(value.equals(node.getValue()))return true;
            for(TreeNode child : node.getChildren())
            {
                if(containsValue(child, value))return true;
            }
            return false;
        }


        public static List<TreeNode> findAll(TreeNode node, String value)
        {
            List<TreeNode> out = new ArrayList<>();
            collect(node, value, out);
            return out;
        }

        private static void collect(TreeNode node, String value, List<TreeNode> out)
        {
            if(value.equals(node.getValue()))out.add(node);
            for(TreeNode child : node.getChildren())collect(child, value, out);
        }


        public static void requireType(TreeNode node, SemanticType expected)
        {
            if(node.getType() != expected)
            {
                throw new TypeAnalysisException(node,
                "expected " + expected + " but found " + node.getType());
            }
        }

        private static TypeAnalysisException notImplemented(TreeNode node, String issue)
        {
            return new TypeAnalysisException(node, "type rule not yet implemented (Issue " + issue + ")");
        }

        private boolean isNumericLiteral(String value){
            return value.matches("-?(?:0|[1-9][0-9]*)(?:\\.[0-9]+)?");
        }
    }