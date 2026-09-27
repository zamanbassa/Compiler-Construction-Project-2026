package spl.semantic;

import java.util.Objects;

public final class GeneratedNameGenerator {
    private int nextId;

    /** Creates a generator whose first generated name ends in {@code 0}. */
    public GeneratedNameGenerator() {
        nextId = 0;
    }

    /** Returns the next generated name for the supplied symbol kind. */
    public String generate(SymbolKind kind) {
        Objects.requireNonNull(kind, "kind");
        String prefix = kind == SymbolKind.FUNCTION ? "__function" : "__variable";
        return prefix + nextId++;
    }
}
