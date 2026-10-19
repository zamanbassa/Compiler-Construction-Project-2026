package spl.codegen;

import spl.lexer.Lexer;
import spl.parser.Parser;
import spl.semantic.*;
import spl.tree.TreeNode;

public final class FunctionCatalogueTest {
    public static void main(String[] args) throws Exception {
        CodeGenerationInput input = accepted("#root : void #f ( #a #b ) { #local : num #g ( #a ) { : : return ( add ( #a 1 ) ) } : return } void #h ( ) { : void #g ( ) { : : return } : return } : ");
        FunctionCatalogue catalogue = new FunctionCatalogue(input);
        check(catalogue.getInput() == input, "input identity");
        check(catalogue.getInput().getTree() == input.getTree(), "tree identity");
        check(catalogue.getInput().getSymbolTable() == input.getSymbolTable(), "table identity");
        check(catalogue.getFunctions().size() == 4, "all nested functions indexed");
        FunctionCatalogue.Function f=catalogue.getFunctions().get(0), g=catalogue.getFunctions().get(1),
                h=catalogue.getFunctions().get(2), otherG=catalogue.getFunctions().get(3);
        check(f.getSymbol().getOriginalName().equals("#f") && h.getSymbol().getOriginalName().equals("#h"), "source ordering");
        check(f.getParameters().size()==2 && f.getParameters().get(0).getOriginalName().equals("#a")
                && f.getParameters().get(1).getOriginalName().equals("#b"), "parameter order");
        check(f.getLocals().size()==1 && f.getLocals().get(0).getOriginalName().equals("#local"), "local metadata");
        check(f.getReturnTerm().isEmpty() && g.getReturnTerm().isPresent(), "void vs numeric returns");
        check(h.getParameters().isEmpty() && h.getLocals().isEmpty(), "empty declarations");
        check(g.getSymbol().getOriginalName().equals(otherG.getSymbol().getOriginalName())
                && g.getSymbol()!=otherG.getSymbol(), "same-spelled nested names distinct");
        check(f.getParameters().get(0)!=g.getParameters().get(0), "shadowed parameter identity");
        check(f.getNestedDeclarations().get(0)==g.getDeclaration(), "nested declaration identity");
        check(f.getAlgorithm().getParent()==f.getBodyProgram(), "ALGO retained, not copied");
        for(FunctionCatalogue.Function fn:catalogue.getFunctions()) {
            Symbol symbol=fn.getSymbol();String original=symbol.getGeneratedName();
            check(catalogue.requireFunction(symbol,fn.getDeclaration())==fn,"symbol index");
            check(catalogue.requireGeneratedName(original,fn.getDeclaration())==fn,"generated-name index");
            TreeNode name=fn.getDeclaration().getChildren().get(1);
            check(name==symbol.getDeclarationNode() && name.getResolvedSymbol()==symbol,"declaration binding identity");
            check(new FunctionCatalogue(input).requireFunction(symbol,name).getSymbol()==symbol
                    && symbol.getGeneratedName().equals(original),"no rename/mutation");
        }
        TreeNode context=f.getDeclaration();
        catalogue.requireArity(f,2,context);
        fails(()->catalogue.requireArity(f,1,context),"expected 2, got 1");
        fails(()->catalogue.requireFunction(null,context),"missing catalogue entry");
        fails(()->catalogue.requireFunction(f.getParameters().get(0),context),"missing catalogue entry");
        FunctionCatalogue foreign=new FunctionCatalogue(accepted(": void #f ( ) { : : return } : "));
        Symbol foreignSymbol=foreign.getFunctions().get(0).getSymbol();
        fails(()->catalogue.requireFunction(foreignSymbol,context),"missing catalogue entry");
        fails(()->catalogue.requireArity(foreign.getFunctions().get(0),0,context),"foreign function template");
        fails(()->catalogue.requireGeneratedName("#f",context),"missing catalogue entry");
        fails(()->new FunctionCatalogue((CodeGenerationInput)null),"missing accepted input");
        try { f.getParameters().clear(); throw new AssertionError("mutable parameters"); } catch(UnsupportedOperationException expected) { }
        try { catalogue.getFunctions().clear(); throw new AssertionError("mutable catalogue"); } catch(UnsupportedOperationException expected) { }
        TreeNode name=g.getDeclaration().getChildren().get(1);Symbol saved=name.getResolvedSymbol();
        name.setResolvedSymbol(null);
        fails(()->new FunctionCatalogue(new CodeGenerationInput(input.getTree(),input.getSymbolTable(),input.getNumericMode())),"missing resolved symbol");
        name.setResolvedSymbol(foreignSymbol);
        fails(()->new FunctionCatalogue(new CodeGenerationInput(input.getTree(),input.getSymbolTable(),input.getNumericMode())),"missing resolved symbol");
        name.setResolvedSymbol(saved);
        CodeGenerationInput real=accepted(": num #f ( ) { : : return ( 0.5 ) } : ");
        check(new FunctionCatalogue(real).getInput().getNumericMode()==NumericMode.REAL,"global real mode");
        check(input.getNumericMode()==NumericMode.WHOLE,"global whole mode");
        check(new FunctionCatalogue(accepted(": : ")).getFunctions().isEmpty(),"no functions");
        System.out.println("Function catalogue tests passed.");
    }
    static CodeGenerationInput accepted(String source)throws Exception {
        Phase2aResult names=new DeclarationResolver().resolveWithResult(new Parser().parse(new Lexer().tokenize(source)));
        return new CodeGenerationInput(new TypeAnalyzer(names.getSymbolTable()).analyzeWithResult(names));
    }
    static void fails(Runnable operation,String diagnostic) {
        try {operation.run();throw new AssertionError("expected "+diagnostic);}
        catch(CodeGenerationException error){check(error.getMessage().contains(diagnostic),error.getMessage());}
    }
    static void check(boolean value,String message){if(!value)throw new AssertionError(message);}
}
