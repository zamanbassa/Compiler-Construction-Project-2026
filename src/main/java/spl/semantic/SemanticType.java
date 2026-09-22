package spl.semantic;

/** Semantic types used by name and type analysis. */
public enum SemanticType {
    /** The declaration has not been type-checked yet. */
    UNKNOWN,
    /** A statement or expression that is semantically valid but has no value. */
    OK,
    /** A numeric value or numeric expression. */
    NUMERIC,
    /** A procedure/function that does not return a value. */
    PROCEDURE,
    /** A Boolean value or Boolean expression. */
    BOOLEAN
}
