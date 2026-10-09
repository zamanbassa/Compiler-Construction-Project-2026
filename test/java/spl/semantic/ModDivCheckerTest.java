package spl.semantic;

import java.util.List;
import spl.lexer.Lexer;
import spl.lexer.Token;
import spl.parser.Parser;
import spl.tree.TreeNode;


public final class ModDivCheckerTest
{
    public static void main(String[] args)throws Exception
    {


        check(ModDivChecker.isDecimalNumber("3.14"), "3.14 is decimal");
        check(ModDivChecker.isDecimalNumber("-0.5"), "-0.5 is decimal");
        check(!ModDivChecker.isDecimalNumber("42"), "42 is not decimal");
        check(!ModDivChecker.isDecimalNumber("0"), "0 is not decimal");
        check(!ModDivChecker.isDecimalNumber("#x"), "#x is not decimal");
        check(!ModDivChecker.isDecimalNumber("\"a.b\""), "string is not decimal");


        accept("mod only, integer args", "#x : : #x = mod ( 7 2 ) ; ");
        accept("div only", "#x : : #x = div ( 7 2 ) ; ");
        accept("nested mod, integer only", "#x : : #x = add ( mod ( 7 2 ) 3 ) ; ");
        accept("decimal, no mod", "#x : : #x = 3.14 ; ");
        accept("div with a decimal (no mod)", "#x : : #x = div ( 3.5 2 ) ; ");


        TreeNode both = tree("#x : : #x = add ( mod ( 7 2 ) div ( 8 2 ) ) ; ");
        String msg = rejectAndGetMessage("mod and div together", both);
        check(msg.equals("A float-integer-conflict might perhaps be possible"),
        "exact conflict message");


        reject("decimal argument to mod", "#x : : #x = mod ( 3.5 2 ) ; ");
        reject("decimal elsewhere + mod",
        "#x : : #x = mod ( 7 2 ) ; #x = 1.5 ; ");


        ModDivChecker.check(null);

        System.out.println("Mod/div checker (2b.5) tests passed.");
    }



    private static TreeNode tree(String src)throws Exception
    {
        List<Token> tokens = new Lexer().tokenize(src);
        return new Parser().parse(tokens);
    }

    private static void accept(String name, String src)throws Exception
    {
        try
        {
            ModDivChecker.check(tree(src));
        }
        catch (FloatIntegerConflictException e)
        {
            throw new AssertionError("should have been accepted: " + name);
        }
    }

    private static void reject(String name, String src)throws Exception
    {
        rejectAndGetMessage(name, tree(src));
    }

    private static String rejectAndGetMessage(String name, TreeNode t)
    {
        try
        {
            ModDivChecker.check(t);
        }
        catch (FloatIntegerConflictException e)
        {
            return e.getMessage();
        }
        throw new AssertionError("should have been rejected: " + name);
    }

    private static void check(boolean condition, String message)
    {
        if(!condition)throw new AssertionError(message);
    }
}