package spl.semantic;

/** Thrown when a scope operation would violate the scope-stack structure. */
public final class ScopeException extends IllegalStateException {
    public ScopeException(String message) {
        super(message);
    }
}
