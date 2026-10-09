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
        return analyzeNode(root);
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
        TreeNode body = firstChild(node, "P");// the nested sub-program
        if(body != null)
        {
            requireType(body, SemanticType.OK);
        }
        // HOOK (2b.3): set this function's Symbol type (void -> PROCEDURE, num -> NUMERIC)
        //              and, for 'num', check the return TERM is NUMERIC.
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
        throw notImplemented(node, "2b.3");
    }

    private SemanticType typeOutput(TreeNode node)
    {
        throw notImplemented(node, "2b.3");
    }
    private SemanticType typeAssign(TreeNode node)
    {
        throw notImplemented(node, "2b.3");
    }
    private SemanticType typeCall(TreeNode node)
    {
        throw notImplemented(node, "2b.3");
    }
    private SemanticType typeInput(TreeNode node)
    {
        throw notImplemented(node, "2b.3");}


    //  2b.4 HOOKS  arithmetic  boolean  branch  loop

        private SemanticType typeTerm(TreeNode node)
        {
            throw notImplemented(node, "2b.4");
        }
        private SemanticType typeBool(TreeNode node)
        {
            throw notImplemented(node, "2b.4");
        }
        private SemanticType typeBranch(TreeNode node)
        {
            throw notImplemented(node, "2b.4");
        }
        private SemanticType typeLoop(TreeNode node)
        {
            throw notImplemented(node, "2b.4");
        }
        private SemanticType typeCond(TreeNode node)
        {
            throw notImplemented(node, "2b.4");
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
    }