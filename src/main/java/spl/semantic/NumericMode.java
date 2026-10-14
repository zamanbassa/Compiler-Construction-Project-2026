package spl.semantic;

/** Program-wide numeric representation, separate from the NUMERIC semantic type. */
public enum NumericMode {
    WHOLE("int"), REAL("float");
    private final String targetType;
    NumericMode(String targetType) { this.targetType = targetType; }
    public String getTargetType() { return targetType; }
}
