package j.array;

/**
 * The element type of a {@link JArray}.
 *
 * <p>J nouns are homogeneous. Numeric types form the promotion order
 * {@code INT < BIGINT < RATIONAL < FLOAT < COMPLEX}; characters and boxes are
 * separate and cannot be mixed into numeric arrays.
 */
public enum JType {
    INT,
    BIGINT,
    RATIONAL,
    FLOAT,
    COMPLEX,
    CHAR,
    BOX;

    /** Whether this type participates in arithmetic (characters and boxes do not). */
    public boolean isNumeric() {
        return this != CHAR && this != BOX;
    }

    /** Whether this type represents an exact rational value. */
    public boolean isExact() {
        return this == INT || this == BIGINT || this == RATIONAL;
    }

    /** Promotion rank for numeric types; non-numeric types return -1. */
    public int numericRank() {
        return switch (this) {
            case INT -> 0;
            case BIGINT -> 1;
            case RATIONAL -> 2;
            case FLOAT -> 3;
            case COMPLEX -> 4;
            case CHAR, BOX -> -1;
        };
    }
}
