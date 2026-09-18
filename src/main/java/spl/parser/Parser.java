package spl.parser;

import spl.contract.IParser;
import java.util.List;
import spl.lexer.Token;
import spl.tree.TreeNode;

public class Parser implements IParser {
    private final SLRDriver driver;

    public Parser(){
        SLRTables tables = new SLRGenerator().generateSLRTables();
        this.driver = new SLRDriver(tables);
    }

    public Parser(SLRTables tables){
        this.driver = new SLRDriver(tables);
    }

    public TreeNode parse(List<Token> tokens) throws SyntaxError{

        if (tokens == null || tokens.isEmpty()){
            throw new SyntaxError("The Parser recieved no tokens");
        }
        return driver.parse(tokens);
    }

}
