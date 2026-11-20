package spl.codegen;
import java.util.*;
import spl.semantic.Symbol;
import spl.tree.TreeNode;

//builds structured func templates and renders them in the code mine comment
public final class FunctionTemplateRenderer{

    private static final String LINE_END = "\r\n";
    private final CodeGenerationInput input;
    private final FunctionCatalogue catalogue;
    private final Set<String> usedReturnSlots = new HashSet<>();
    private int returnSlotCounter;

    public FunctionTemplateRenderer(CodeGenerationInput input){

        if (input == null){
            throw new CodeGenerationException("missing input");
        }

        this.input = input;
        this.catalogue = new FunctionCatalogue(input);
        this.usedReturnSlots.addAll(input.getReservedNames());
    }

    //complete display only payload for outer code-mine comment
    public String renderCodeMine(){

        TreeNode program = input.getTree();

        if ("SPL_PROG".equals(program.getValue())){
            if (program.getChildren().size() != 1){
                throw new CodeGenerationException(program, "SPL_PROGRAM must contain exactly one P");
            }
            program = program.getChildren().get(0);
        }

        input.requireNode(program, "P");
        TreeNode declarations = child(program, "F_DECL");

        StringBuilder output = new StringBuilder();
        TreeNode current = declarations;
        boolean hasFunctions = false;

        while (true){
            input.requireNode(current, "F_DECL");
            List<TreeNode> children = current.getChildren();

            if (children.isEmpty()){
                break;
            }

            if (children.size() != 2 || !"F_TYPE".equals(children.get(0).getValue()) || 
            !"F_DECL".equals(children.get(1).getValue())){
                throw new CodeGenerationException(current, "malformed F_DECL production");
            }

            TreeNode declaration = children.get(0);
            input.requireNode(declaration, "F_TYPE");
            FunctionCatalogue.Function function = functionForDeclaration(declaration);

            output.append(renderTemplate(buildTemplate(function)));
            current = children.get(1);
            hasFunctions = true;
        }
        return hasFunctions ? output.toString() : " ";
    }

    private FunctionTemplate buildTemplate(FunctionCatalogue.Function function){

        String returnSlot = function.getReturnTerm().isPresent() ? allocateReturnSlot(function) : null;

        List<FunctionTemplate> nestedTemplates = new ArrayList<>();

        for (TreeNode nestedDeclaration : function.getNestedDeclarations()){
            nestedTemplates.add(
                buildTemplate(functionForDeclaration(nestedDeclaration))
            );
        }

        return new FunctionTemplate(function, returnSlot, nestedTemplates);
    }

    private String allocateReturnSlot(FunctionCatalogue.Function function){

        String prefix = function.getSymbol().getGeneratedName() + "_return_";
        String slot;
        
        do{
            slot = prefix + returnSlotCounter++;
        }
        while (!usedReturnSlots.add(slot));
        
        return slot;
    }

    private String declarations(List<Symbol> symbols){

        StringBuilder output = new StringBuilder();

        if (symbols.isEmpty()){
            return " ";
        }

        String type = input.getNumericMode().getTargetType();

        for (Symbol symbol : symbols){
            output.append(type).append(' ').append(symbol.getGeneratedName()).append(';').append(LINE_END);
        }
        return output.toString();
    }

    private FunctionCatalogue.Function functionForDeclaration(TreeNode declaration){

        List<TreeNode> children = declaration.getChildren();
        if (children.size() < 2){
            throw new CodeGenerationException(declaration, "malformed F_TYPE declaration" );
        }

        TreeNode name = children.get(1);
        Symbol symbol = name.getResolvedSymbol();
        return catalogue.requireFunction(symbol, name);
    }

    private static TreeNode child(TreeNode node, String production){
        
        TreeNode found = null;

        for (TreeNode n : node.getChildren()){

            if (!n.isTerminal() && production.equals(n.getValue())){

                if (found != null){
                    throw new CodeGenerationException(node, "duplicate " + production);
                }
                found = n;
            }
        }

        if (found == null){
            throw new CodeGenerationException(node, "missing " + production);
        }

        return found;
    }

    //escapes the display payload before it is placed inside the outer comment
    public static String serializeOuterComment(String payload){

        if (payload == null){
            throw new CodeGenerationException("missing code-mine payload");
        }

        String safePayload = payload.replace("*/", "* /");

        return "/* begin of the non-executable code-mine for later inlining" + LINE_END + safePayload
        + LINE_END + "end of the non-executable code-mine */";
    }

    private String renderTemplate(FunctionTemplate template){

        FunctionCatalogue.Function function = template.function;
        boolean numeric = function.getReturnTerm().isPresent();
        String functionName = function.getSymbol().getGeneratedName();

        StringBuilder output = new StringBuilder();
        if (numeric){
            output.append("begin of code-mine for Function ").append(functionName).append(LINE_END);
        }
        else{
            output.append("begin of code-mine for void-procedure ").append(functionName).append(LINE_END);
        }

        output.append("Parameters for inlining:").append(LINE_END)
        .append(declarations(function.getParameters()))
        .append(LINE_END)
        .append("Body of the procedure:").append(LINE_END);

        if (template.returnSlot != null){
            output.append("Return slot: ")
            .append(input.getNumericMode().getTargetType())
            .append(' ').append(template.returnSlot)
            .append(';').append(LINE_END);
        }

        output.append(declarations(function.getLocals())).append(LINE_END);

        for (FunctionTemplate nested : template.nestedTemplates){
            output.append(renderTemplate(nested));
        }

        String bodyCode = AlgorithmCodeGenerator.generateFunctionTemplateBody(input, function, template.returnSlot);
        String normalizedBodyCode = bodyCode.replace("\r\n", "\n").replace('\r','\n')
        .replace("\n", LINE_END);
        output.append(normalizedBodyCode);

        if (!normalizedBodyCode.endsWith(LINE_END)){
            output.append(LINE_END);
        }

        if (numeric){
            output.append("end of code-mine for Function ").append(functionName).append(LINE_END)
            .append("The result is found in the return value ").append(template.returnSlot).append(LINE_END);
        }
        else{
            output.append("end of code-mine for void-procedure ").append(functionName).append(LINE_END);
        }

        return output.toString();
    }

    private static final class FunctionTemplate{

        private final FunctionCatalogue.Function function;
        private final String returnSlot;
        private final List<FunctionTemplate> nestedTemplates;

        private FunctionTemplate(
            FunctionCatalogue.Function function, String returnSlot, List<FunctionTemplate> nestedTemplates
        ){
            this.function = function;
            this.returnSlot = returnSlot;
            this.nestedTemplates = List.copyOf(nestedTemplates);
        }
    }
}