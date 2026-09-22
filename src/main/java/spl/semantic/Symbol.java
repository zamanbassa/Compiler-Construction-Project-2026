package spl.semantic;

import java.util.Objects;
import spl.tree.TreeNode;

/**
 * A declaration recorded by Phase 2a.
 *
 * <p>The original name is the source-level lexeme used for lookup. The
 * generated name is stable compiler-internal storage for later phases. Type
 * information starts as {@link SemanticType#UNKNOWN} and can be refined by
 * Phase 2b.</p>
 */
public final class Symbol {
    private final String originalName;
    private final String generatedName;
    private final SymbolKind kind;
    private final Scope declaringScope;
    private final TreeNode declarationNode;
    private SemanticType type;

    public Symbol(
            String originalName,
            String generatedName,
            SymbolKind kind,
            Scope declaringScope,
            TreeNode declarationNode) {
        this.originalName = requireName(originalName, "originalName");
        this.generatedName = requireName(generatedName, "generatedName");
        this.kind = Objects.requireNonNull(kind, "kind");
        this.declaringScope = Objects.requireNonNull(declaringScope, "declaringScope");
        this.declarationNode = declarationNode;
        this.type = SemanticType.UNKNOWN;
    }

    public String getOriginalName() {
        return originalName;
    }

    public String getGeneratedName() {
        return generatedName;
    }

    public SymbolKind getKind() {
        return kind;
    }

    public Scope getDeclaringScope() {
        return declaringScope;
    }

    public TreeNode getDeclarationNode() {
        return declarationNode;
    }

    public SemanticType getType() {
        return type;
    }

    /** Updates the type inferred by a later semantic-analysis phase. */
    public void setType(SemanticType type) {
        this.type = Objects.requireNonNull(type, "type");
    }

    @Override
    public String toString() {
        return kind + " " + originalName + " -> " + generatedName + " (" + type + ")";
    }

    private static String requireName(String name, String field) {
        Objects.requireNonNull(name, field);
        if (name.isBlank()) {
            throw new IllegalArgumentException(field + " must not be blank");
        }
        return name;
    }
}
