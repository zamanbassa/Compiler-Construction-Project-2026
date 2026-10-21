package spl.codegen;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import spl.semantic.Symbol;

public final class FunctionInstance implements InliningContract.InlineInstance {

    private final InliningContract.FunctionTemplate template;
    private final InliningContract.LexicalEnvironment environment;
    private final InliningContract.TargetNames names;
    private final Map<String, String> labels = new LinkedHashMap<>();
    private final InliningContract.ReturnResult result;

    public FunctionInstance(InliningContract.FunctionTemplate template,
                            InliningContract.LexicalEnvironment enclosing,
                            InliningContract.TargetNames names) {
        if (template == null) {
            throw new CodeGenerationException("inline instance requires a template");
        }
        if (enclosing == null) {
            throw new CodeGenerationException("inline instance requires an enclosing environment");
        }
        if (names == null) {
            throw new CodeGenerationException("inline instance requires an allocator");
        }
        this.template = template;
        this.names = names;

        List<Symbol> own = new ArrayList<>(template.getParameters());
        own.addAll(template.getLocals());
        this.environment = new InlineEnvironment(enclosing, own, names);

        String slot = template.getReturnTerm().isPresent() ? names.newVariable() : null;
        Optional<String> valueSlot = Optional.ofNullable(slot);
        this.result = () -> valueSlot;
    }

    @Override
    public InliningContract.FunctionTemplate getTemplate() {
        return template;
    }

    @Override
    public InliningContract.LexicalEnvironment getEnvironment() {
        return environment;
    }

    @Override
    public String labelFor(String templateLabel) {
        if (templateLabel == null) {
            throw new CodeGenerationException("null template label");
        }
        return labels.computeIfAbsent(templateLabel, key -> names.newLabel());
    }

    @Override
    public InliningContract.ReturnResult getResult() {
        return result;
    }
}
