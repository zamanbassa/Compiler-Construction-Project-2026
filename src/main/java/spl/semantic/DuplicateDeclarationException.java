package spl.semantic;

/** Thrown when a scope receives two declarations with the same source name. */
public final class DuplicateDeclarationException extends RuntimeException {
    public DuplicateDeclarationException(String sourceName, Scope scope) {
        super("duplicate declaration '" + sourceName + "' in scope " + scope.getScopeId());
    }
}
