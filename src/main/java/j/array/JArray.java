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

    /**
     * A contiguous copy of {@code count} atoms at {@code offset}, with the given
     * shape. Rank application uses this to extract a cell: trailing axes are
     * contiguous in row-major order.
     */
    public JArray slice(int offset, int[] newShape) {
        int count = Shapes.size(newShape);
        if (offset < 0 || offset > size - count) {
            throw new IllegalArgumentException("slice out of bounds");
        }
        Object copy = switch (type) {
            case INT -> Arrays.copyOfRange((long[]) data, offset, offset + count);
            case FLOAT -> Arrays.copyOfRange((double[]) data, offset, offset + count);
            case CHAR -> Arrays.copyOfRange((char[]) data, offset, offset + count);
        };
        return new JArray(type, newShape.clone(), copy);
    }

    // -------------------------------------------------------------- structure

    /** The number of items: 1 for a scalar, otherwise the leading axis length. */
    public int itemCount() {
        return shape.length == 0 ? 1 : shape[0];
    }

    /** The shape of one item: empty for a scalar, otherwise the trailing axes. */
    public int[] itemShape() {
        return shape.length == 0
                ? new int[0]
                : Arrays.copyOfRange(shape, 1, shape.length);
    }

    /** The number of atoms in one item. */
    public int itemSize() {
        return Shapes.size(itemShape());
    }

    /** The {@code index}-th item. A scalar has exactly one item, itself. */
    public JArray item(int index) {
        if (shape.length == 0) {
            if (index != 0) {
                throw new IndexOutOfBoundsException("scalar has no item " + index);
            }
            return this;
        }
        int count = shape[0];
        if (index < 0 || index >= count) {
            throw new IndexOutOfBoundsException("item " + index + " out of range 0.." + (count - 1));
        }
        return slice(index * itemSize(), itemShape());
    }

    /**
     * Rebuilds the array with a new shape, cycling the atoms when the new shape is
     * larger. Empty sources are filled with zeros or NUL characters.
     */
    public JArray reshape(int[] newShape) {
        int destinationSize = Shapes.size(newShape);
        if (destinationSize == 0) {
            return remap(newShape, new int[0]);
        }
        if (size == 0) {
            return filled(type, newShape);
        }
        int[] sourceIndices = new int[destinationSize];
        for (int atom = 0; atom < destinationSize; atom++) {
            sourceIndices[atom] = atom % size;
        }
        return remap(newShape, sourceIndices);
    }

    /** Flattens the array into a vector. */
    public JArray ravel() {
        int[] sourceIndices = new int[size];
        for (int atom = 0; atom < size; atom++) {
            sourceIndices[atom] = atom;
        }
        return remap(new int[]{size}, sourceIndices);
    }

    /** Reverses the items along the leading axis. */
    public JArray reverseItems() {
        if (shape.length == 0) {
            return this;
        }
        int itemSize = itemSize();
        int[] sourceIndices = new int[size];
        for (int atom = 0; atom < size; atom++) {
            int item = atom / itemSize;
            int offset = atom % itemSize;
            sourceIndices[atom] = (shape[0] - 1 - item) * itemSize + offset;
        }
        return remap(shape, sourceIndices);
    }

    /** Rotates the leading axis left by {@code count} items. */
    public JArray rotateItems(long count) {
        if (shape.length == 0 || shape[0] == 0) {
            return this;
        }
        int items = shape[0];
        int shift = (int) Math.floorMod(count, (long) items);
        if (shift == 0) {
            return this;
        }
        int itemSize = itemSize();
        int[] sourceIndices = new int[size];
        for (int atom = 0; atom < size; atom++) {
            int item = atom / itemSize;
            int offset = atom % itemSize;
            sourceIndices[atom] = ((item + shift) % items) * itemSize + offset;
        }
        return remap(shape, sourceIndices);
    }

    /** Takes {@code count} leading items, or trailing items when negative. */
    public JArray takeItems(int count) {
        if (shape.length == 0) {
            throw new IllegalArgumentException("take needs a non-scalar argument");
        }
        int items = shape[0];
        long magnitude = Math.abs((long) count);
        if (magnitude > items) {
            throw new IllegalArgumentException("take exceeds the leading axis");
        }
        int take = (int) magnitude;
        int start = count >= 0 ? 0 : items - take;
        int itemSize = itemSize();
        int[] newShape = shape.clone();
        newShape[0] = take;
        int[] sourceIndices = new int[take * itemSize];
        for (int atom = 0; atom < sourceIndices.length; atom++) {
            int item = start + atom / itemSize;
            sourceIndices[atom] = item * itemSize + atom % itemSize;
        }
        return remap(newShape, sourceIndices);
    }

    /** Drops {@code count} leading items, or trailing items when negative. */
    public JArray dropItems(int count) {
        if (shape.length == 0) {
            throw new IllegalArgumentException("drop needs a non-scalar argument");
        }
        int items = shape[0];
        int magnitude = (int) Math.min(Math.abs((long) count), (long) items);
        int start = count >= 0 ? magnitude : 0;
        int end = count >= 0 ? items : items - magnitude;
        int keep = end - start;
        int itemSize = itemSize();
        int[] newShape = shape.clone();
        newShape[0] = keep;
        int[] sourceIndices = new int[keep * itemSize];
        for (int atom = 0; atom < sourceIndices.length; atom++) {
            int item = start + atom / itemSize;
            sourceIndices[atom] = item * itemSize + atom % itemSize;
        }
        return remap(newShape, sourceIndices);
    }

    /** Catenates {@code other} after this array along the leading axis. */
    public JArray append(JArray other) {
        int[] otherShape = other.shape();
        int[] newShape;

        if (shape.length == 0 && otherShape.length == 0) {
            newShape = new int[]{2};
        } else if (shape.length <= 1 && otherShape.length <= 1) {
            newShape = new int[]{size + other.size};
        } else {
            if (shape.length != otherShape.length) {
                throw new IllegalArgumentException("rank");
            }
            for (int axis = 1; axis < shape.length; axis++) {
                if (shape[axis] != otherShape[axis]) {
                    throw new IllegalArgumentException("shape");
                }
            }
            newShape = shape.clone();
            newShape[0] += otherShape[0];
        }

        JType resultType = promote(type, other.type);
        int total = Shapes.size(newShape);
        return switch (resultType) {
            case INT -> {
                long[] data = new long[total];
                for (int atom = 0; atom < size; atom++) {
                    data[atom] = longAt(atom);
                }
                for (int atom = 0; atom < other.size; atom++) {
                    data[size + atom] = other.longAt(atom);
                }
                yield JArray.ints(newShape, data);
            }
            case FLOAT -> {
                double[] data = new double[total];
                for (int atom = 0; atom < size; atom++) {
                    data[atom] = numericAt(atom);
                }
                for (int atom = 0; atom < other.size; atom++) {
                    data[size + atom] = other.numericAt(atom);
                }
                yield JArray.floats(newShape, data);
            }
            case CHAR -> {
                char[] data = new char[total];
                for (int atom = 0; atom < size; atom++) {
                    data[atom] = charAt(atom);
                }
                for (int atom = 0; atom < other.size; atom++) {
                    data[size + atom] = other.charAt(atom);
                }
                yield JArray.chars(newShape, data);
            }
        };
    }

    /** Reverses the axes of an array of rank 2 or more. */
    public JArray transpose() {
        if (shape.length < 2) {
            return this;
        }
        int[] newShape = new int[shape.length];
        for (int axis = 0; axis < shape.length; axis++) {
            newShape[axis] = shape[shape.length - 1 - axis];
        }
        int[] sourceStrides = Shapes.strides(shape);
        int[] destinationStrides = Shapes.strides(newShape);
        int[] coordinates = new int[shape.length];
        int[] sourceIndices = new int[size];
        for (int atom = 0; atom < size; atom++) {
            int remainder = atom;
            for (int axis = 0; axis < shape.length; axis++) {
                coordinates[axis] = remainder / destinationStrides[axis];
                remainder %= destinationStrides[axis];
            }
            int source = 0;
            for (int axis = 0; axis < shape.length; axis++) {
                source += coordinates[axis] * sourceStrides[shape.length - 1 - axis];
            }
            sourceIndices[atom] = source;
        }
        return remap(newShape, sourceIndices);
    }

    private JArray remap(int[] newShape, int[] sourceIndices) {
        int destinationSize = Shapes.size(newShape);
        if (sourceIndices.length != destinationSize) {
            throw new IllegalArgumentException("source map has the wrong length");
        }
        return switch (type) {
            case INT -> {
                long[] data = new long[destinationSize];
                for (int atom = 0; atom < destinationSize; atom++) {
                    data[atom] = longAt(sourceIndices[atom]);
                }
                yield JArray.ints(newShape, data);
            }
            case FLOAT -> {
                double[] data = new double[destinationSize];
                for (int atom = 0; atom < destinationSize; atom++) {
                    data[atom] = doubleAt(sourceIndices[atom]);
                }
                yield JArray.floats(newShape, data);
            }
            case CHAR -> {
                char[] data = new char[destinationSize];
                for (int atom = 0; atom < destinationSize; atom++) {
                    data[atom] = charAt(sourceIndices[atom]);
                }
                yield JArray.chars(newShape, data);
            }
        };
    }

    private static JArray filled(JType type, int[] shape) {
        int size = Shapes.size(shape);
        return switch (type) {
            case INT -> JArray.ints(shape, new long[size]);
            case FLOAT -> JArray.floats(shape, new double[size]);
            case CHAR -> JArray.chars(shape, new char[size]);
        };
    }

    private static JType promote(JType left, JType right) {
        if (left == JType.CHAR || right == JType.CHAR) {
            if (left == JType.CHAR && right == JType.CHAR) {
                return JType.CHAR;
            }
            throw new IllegalArgumentException("domain");
        }
        if (left == JType.FLOAT || right == JType.FLOAT) {
            return JType.FLOAT;
        }
        return JType.INT;
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
