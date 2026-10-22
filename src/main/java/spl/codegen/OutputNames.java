package spl.codegen;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

public final class OutputNames implements InliningContract.TargetNames {

    private final Set<String> allocated;
    private int variableCounter = 0;
    private int labelCounter = 0;

    public OutputNames(Set<String> reservedNames) {
        this.allocated = new HashSet<>(reservedNames == null ? Collections.emptySet() : reservedNames);
    }

    public OutputNames(CodeGenerationInput input) {
        this(input == null ? Collections.emptySet() : input.getReservedNames());
    }

    @Override
    public String newVariable() {
        String name;
        do {
            name = "spl_in_v_" + variableCounter++;
        } while (!allocated.add(name));
        return name;
    }

    @Override
    public String newLabel() {
        String name;
        do {
            name = "spl_in_l_" + labelCounter++;
        } while (!allocated.add(name));
        return name;
    }
}
