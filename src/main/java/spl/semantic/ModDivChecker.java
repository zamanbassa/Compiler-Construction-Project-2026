package spl.semantic;

import spl.tree.TreeNode;

public final class ModDivChecker
{

    private ModDivChecker()
    {
        }


        public static void check(TreeNode root)
        {
            if(root == null)
            {
                return;
            }

            boolean hasMod = TypeAnalyzer.containsValue(root, "mod");
            boolean hasDiv = TypeAnalyzer.containsValue(root, "div");

        // mod and div in the same program -> float-integer conflict
            if(hasMod && hasDiv)
            {
                throw new FloatIntegerConflictException();
            }

        // mod present -> decimals are not allowed anywhere in the program
            if(hasMod && containsDecimalNumber(root))
            {
                throw new FloatIntegerConflictException();
            }
        }


        private static boolean containsDecimalNumber(TreeNode node)
        {
            if(node.isTerminal() && isDecimalNumber(node.getValue()))
            {
                return true;
            }
            for(TreeNode child : node.getChildren())
            {
                if(containsDecimalNumber(child))
                {
                    return true;
                }
            }
            return false;
        }

        static boolean isDecimalNumber(String value)
        {
            return value.matches("-?[0-9]+\\.[0-9]+");
        }
    }