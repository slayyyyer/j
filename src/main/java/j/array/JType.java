package j.array;

/**
 * The element type of a {@link JArray}.
 *
 * <p>J nouns are homogeneous, so one type describes every atom. Boxing will add a
 * {@code BOX} constant whose storage is an array of {@link JArray} instances.
 */
public enum JType {
    INT,
    FLOAT,
    CHAR;

    /** Whether this type participates in arithmetic (characters do not). */
    public boolean isNumeric() {
        return this != CHAR;
    }
}
