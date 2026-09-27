package spl.semantic;

public enum SemanticType {
    UNKNOWN, // The declaration has not been type-checked yet.
    OK, // A statement or expression that is semantically valid but has no value. 
    NUMERIC,// A numeric value or numeric expression.
    PROCEDURE,// A procedure/function that does not return a value.
    BOOLEAN // A Boolean value or Boolean expression.
}
