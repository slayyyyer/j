package j.array;

import java.util.Arrays;

/**
 * A J noun: flat, row-major storage plus a shape.
 *
 * <p>There is no separate scalar class. A rank-0 array has the empty shape and one
 * atom; a vector has shape {@code [n]}, a matrix {@code [rows, cols]}, and so on.
 * Instances are immutable: input arrays and exposed shapes are copied.
 *
 * <p>Storage matches {@link JType}: {@code long[]}, {@code double[]} or
 * {@code char[]}. J requires homogeneous arrays; arithmetic promotes an integer
 * array to floating point when needed via {@link #toFloat()}.
 */
public final class JArray {
    private final JType type;
    private final int[] shape;
    private final Object data;
    private final int size;

    private JArray(JType type, int[] shape, Object data) {
        int expectedSize = Shapes.size(shape);
        int actualSize = storageLength(type, data);
        if (actualSize != expectedSize) {
            throw new IllegalArgumentException(
                    "shape " + Shapes.format(shape) + " needs " + expectedSize
                            + " atoms but got " + actualSize);
        }
        this.type = type;
        this.shape = shape;
        this.data = data;
        this.size = actualSize;
    }

    private static int storageLength(JType type, Object data) {
        return switch (type) {
            case INT -> ((long[]) data).length;
            case FLOAT -> ((double[]) data).length;
            case CHAR -> ((char[]) data).length;
        };
    }

    // ---------------------------------------------------------------- factories

    /** An integer vector with one element per value. */
    public static JArray ints(long... values) {
        return ints(new int[]{values.length}, values);
    }

    /** An integer array of the given shape, filled row by row. */
    public static JArray ints(int[] shape, long... values) {
        return new JArray(JType.INT, shape.clone(), values.clone());
    }

    /** A float vector with one element per value. */
    public static JArray floats(double... values) {
        return floats(new int[]{values.length}, values);
    }

    /** A float array of the given shape, filled row by row. */
    public static JArray floats(int[] shape, double... values) {
        return new JArray(JType.FLOAT, shape.clone(), values.clone());
    }

    /** A character vector containing the characters of {@code text}. */
    public static JArray chars(String text) {
        return chars(new int[]{text.length()}, text.toCharArray());
    }

    /** A character array of the given shape, filled row by row. */
    public static JArray chars(int[] shape, char... values) {
        return new JArray(JType.CHAR, shape.clone(), values.clone());
    }

    /** A rank-0 integer array. */
    public static JArray scalar(long value) {
        return ints(new int[0], value);
    }

    /** A rank-0 float array. */
    public static JArray scalar(double value) {
        return floats(new int[0], value);
    }

    /** A rank-0 character array. */
    public static JArray scalar(char value) {
        return chars(new int[0], value);
    }

    /**
     * An empty array of the given type. At least one dimension must be zero.
     */
    public static JArray empty(JType type, int... shape) {
        if (Shapes.size(shape) != 0) {
            throw new IllegalArgumentException("empty array shape must contain a zero: " + Shapes.format(shape));
        }
        return switch (type) {
            case INT -> ints(shape, new long[0]);
            case FLOAT -> floats(shape, new double[0]);
            case CHAR -> chars(shape, new char[0]);
        };
    }

    // ---------------------------------------------------------------- accessors

    public JType type() {
        return type;
    }

    public int rank() {
        return shape.length;
    }

    /** A copy of the shape, so callers cannot alter the array. */
    public int[] shape() {
        return shape.clone();
    }

    /** The number of atoms, which is the product of the shape. */
    public int size() {
        return size;
    }

    public boolean isEmpty() {
        return size == 0;
    }

    public boolean isScalar() {
        return shape.length == 0;
    }

    public boolean sameShape(JArray other) {
        return Arrays.equals(shape, other.shape);
    }

    /** Row-major strides into the flat storage. */
    public int[] strides() {
        return Shapes.strides(shape);
    }

    public long longAt(int index) {
        requireType(JType.INT, "longAt");
        return ((long[]) data)[index];
    }

    public double doubleAt(int index) {
        requireType(JType.FLOAT, "doubleAt");
        return ((double[]) data)[index];
    }

    public char charAt(int index) {
        requireType(JType.CHAR, "charAt");
        return ((char[]) data)[index];
    }

    /** Reads an atom as a double, widening integers. Characters are not numeric. */
    public double numericAt(int index) {
        return switch (type) {
            case INT -> (double) ((long[]) data)[index];
            case FLOAT -> ((double[]) data)[index];
            case CHAR -> throw new IllegalStateException("character arrays are not numeric");
        };
    }

    private void requireType(JType expected, String operation) {
        if (type != expected) {
            throw new IllegalStateException(operation + " requires a " + expected + " array, got " + type);
        }
    }

    // ------------------------------------------------------------- conversions

    /**
     * Promotes an integer array to floating point. Float arrays are returned as-is;
     * character arrays cannot be promoted.
     */
    public JArray toFloat() {
        if (type == JType.FLOAT) {
            return this;
        }
        if (type == JType.CHAR) {
            throw new IllegalStateException("cannot promote a character array to float");
        }
        long[] integers = (long[]) data;
        double[] floats = new double[integers.length];
        for (int atom = 0; atom < integers.length; atom++) {
            floats[atom] = integers[atom];
        }
        return new JArray(JType.FLOAT, shape, floats);
    }

    @Override
    public String toString() {
        return JFormat.format(this);
    }
}
