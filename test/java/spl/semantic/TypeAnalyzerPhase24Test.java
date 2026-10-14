package spl.semantic;

import java.util.List;
import spl.lexer.Lexer;
import spl.lexer.Token;
import spl.parser.Parser;
import spl.tree.TreeNode;

public final class TypeAnalyzerPhase24Test {
    private TypeAnalyzerPhase24Test() {
    }

    public static void main(String[] args) throws Exception {
        accepts("#x : : #x = 1 ; ");
        accepts("#x : : #x = add ( 1 2 ) ; ");
        accepts("#x : : #x = sub ( 9 4 ) ; ");
        accepts("#x : : #x = mul ( 3 7 ) ; ");
        accepts("#x : : #x = div ( 8 2 ) ; ");
        accepts("#x : : #x = mod ( 7 3 ) ; ");
        accepts("#x : : #x = neg ( 5 ) ; ");
        accepts("#x : : #x = add ( mul ( 2 3 ) sub ( 9 4 ) ) ; ");
        accepts("#x : : if not ( eq ( 1 2 ) ) then { #x = 1 ; } else { #x = 2 ; } ; ");
        accepts("#x : : if and ( eq ( 1 1 ) eq ( 2 2 ) ) then { #x = 1 ; } else { #x = 2 ; } ; ");
        accepts("#x : : if or ( eq ( 1 2 ) eq ( 3 3 ) ) then { #x = 1 ; } else { #x = 2 ; } ; ");
        accepts("#x : : if eq ( 1 1 ) then { #x = 1 ; } else { #x = 2 ; } ; ");
        accepts("#x : : if larger ( #x 0 ) then { #x = add ( #x 1 ) ; } else { #x = sub ( #x 1 ) ; } ; ");
        accepts("#x : : if lesser ( #x 0 ) then { #x = add ( #x 1 ) ; } else { #x = sub ( #x 1 ) ; } ; ");
        accepts("#x : : while larger ( #x 0 ) do { #x = sub ( #x 1 ) ; } ; ");
        accepts("#x : : until lesser ( #x 0 ) do { #x = add ( #x 1 ) ; } ; ");
        accepts("#x : : do { #x = add ( #x 1 ) ; } while larger ( #x 0 ) ; ");
        accepts("#x : : do { #x = sub ( #x 1 ) ; } until lesser ( #x 0 ) ; ");

        accepts("#x : num #f ( ) { : : return ( add ( 1 2 ) ) } : #x = #f ( ) ; ");
        accepts("#x : num #f ( ) { : : return ( #x ) } : #x = #f ( ) ; ");

        rejects("#x : void #f ( ) { : : return } : #x = #f ( ) ; ");
        rejects("#x : void #f ( ) { : : return } : #x = add ( 1 #f ( ) ) ; ");

        System.out.println("Phase 2b.4 tests passed.");
    }

    private static void accepts(String source) throws Exception {
        TreeNode tree = parse(source);
        Phase2aResult result = new DeclarationResolver().resolveWithResult(tree);
        new TypeAnalyzer(result.getSymbolTable()).analyze(tree);
    }

    private static void rejects(String source) throws Exception {
        TreeNode tree = parse(source);
        Phase2aResult result = new DeclarationResolver().resolveWithResult(tree);

        try {
            new TypeAnalyzer(result.getSymbolTable()).analyze(tree);
        } catch (TypeAnalysisException expected) {
            return;
        }

        throw new AssertionError("expected a type error for: " + source);
    }

    private static TreeNode parse(String source) throws Exception {
        List<Token> tokens = new Lexer().tokenize(source);
        return new Parser().parse(tokens);
    }
}
