package spl.semantic;

/** Raised when a user-defined name cannot be resolved in lexical scope. */
public final class NameResolutionException extends RuntimeException {
    public NameResolutionException(String sourceName, SymbolKind expectedKind) {
        super("undefined " + expectedKind.name().toLowerCase()
                + " '" + sourceName + "' in the current lexical scope");
    }

    public NameResolutionException(
            String sourceName,
            SymbolKind expectedKind,
            SymbolKind actualKind) {
        super("name '" + sourceName + "' resolves to "
                + actualKind.name().toLowerCase() + ", expected "
                + expectedKind.name().toLowerCase());
    }
}