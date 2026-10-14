package spl.semantic;
import java.util.List;
import spl.lexer.Lexer;
import spl.lexer.Token;
import spl.parser.Parser;
import spl.tree.TreeNode;

public final class TypeAnalyzerPhase23Test{
    public static void main(String[] args) throws Exception{
        accepts(": : ");
        accepts(": void #p ( ) { : : return } : #p ( ) ; ");
        accepts("#x : num #f ( ) { : : return ( 1 ) } : #x = #f ( ) ; ");

        rejects("#x : void #p ( ) { : : return } : #x = #p ( ) ; ");
        rejects(": void #p ( ) { : : return } " + "void #g ( #a ) { : : return } " + ": #g ( #p ( ) ) ; ");

        System.out.println("Phase 2b.3 tests passed.");
    }

    private static void accepts( String source) throws Exception{

        TreeNode tree = parse(source);
        Phase2aResult result = new DeclarationResolver().resolveWithResult(tree);
        new TypeAnalyzer(result.getSymbolTable()).analyze(tree);

    }

    private static void rejects( String source) throws Exception{
        TreeNode tree = parse(source);
        Phase2aResult result = new DeclarationResolver().resolveWithResult(tree);

        try{
            new TypeAnalyzer(result.getSymbolTable()).analyze(tree);
        }
        catch (TypeAnalysisException expected){
            return;
        }

        throw new AssertionError("expected a type error for: " + source);
    }

    private static TreeNode parse(String source) throws Exception{

        List<Token> tokens = new Lexer().tokenize(source);
        return new Parser().parse(tokens);

    }
}
