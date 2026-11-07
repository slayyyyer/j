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

    /**
     * Replicates each item according to {@code counts}. The result has a leading
     * axis equal to the sum of the counts and the same item shape as this array.
     */
    public JArray copyItems(int[] counts) {
        if (counts.length != itemCount()) {
            throw new IllegalArgumentException("copy counts must match the item count");
        }
        long total = 0;
        for (int count : counts) {
            if (count < 0) {
                throw new IllegalArgumentException("negative copy count");
            }
            total += count;
            if (total > Integer.MAX_VALUE) {
                throw new IllegalArgumentException("copy result is too large");
            }
        }

        int[] newShape;
        if (shape.length == 0) {
            newShape = new int[]{(int) total};
        } else {
            newShape = shape.clone();
            newShape[0] = (int) total;
        }

        int itemSize = itemSize();
        int totalAtoms = Shapes.size(newShape);
        int[] sourceIndices = new int[totalAtoms];
        int at = 0;
        for (int item = 0; item < itemCount(); item++) {
            for (int copy = 0; copy < counts[item]; copy++) {
                for (int atom = 0; atom < itemSize; atom++) {
                    sourceIndices[at++] = item * itemSize + atom;
                }
            }
        }
        return remap(newShape, sourceIndices);
    }

    /**
     * Takes {@code count} leading items, or trailing items when negative. An
     * overtake fills the missing items with zeros or spaces, matching J's default
     * numeric and literal fill.
     */
    public JArray takeItems(int count) {
        if (shape.length == 0) {
            throw new IllegalArgumentException("take needs a non-scalar argument");
        }
        int items = shape[0];
        long magnitude = Math.abs((long) count);
        if (magnitude > Integer.MAX_VALUE) {
            throw new IllegalArgumentException("take result is too large");
        }
        int take = (int) magnitude;
        int itemSize = itemSize();
        int[] newShape = shape.clone();
        newShape[0] = take;
        int total = Shapes.size(newShape);
        int fillItems = Math.max(0, take - items);
        int start = count >= 0 ? 0 : items - Math.min(take, items);
        int[] sourceIndices = new int[total];
        for (int item = 0; item < take; item++) {
            int sourceItem;
            if (count >= 0) {
                sourceItem = item < items ? item : -1;
            } else {
                sourceItem = item < fillItems ? -1 : start + (item - fillItems);
            }
            for (int atom = 0; atom < itemSize; atom++) {
                sourceIndices[item * itemSize + atom] =
                        sourceItem < 0 ? -1 : sourceItem * itemSize + atom;
            }
        }

        return switch (type) {
            case INT -> {
                long[] data = new long[total];
                for (int atom = 0; atom < total; atom++) {
                    data[atom] = sourceIndices[atom] < 0 ? 0 : longAt(sourceIndices[atom]);
                }
                yield JArray.ints(newShape, data);
            }
            case FLOAT -> {
                double[] data = new double[total];
                for (int atom = 0; atom < total; atom++) {
                    data[atom] = sourceIndices[atom] < 0 ? 0.0 : doubleAt(sourceIndices[atom]);
                }
                yield JArray.floats(newShape, data);
            }
            case CHAR -> {
                char[] data = new char[total];
                for (int atom = 0; atom < total; atom++) {
                    data[atom] = sourceIndices[atom] < 0 ? ' ' : charAt(sourceIndices[atom]);
                }
                yield JArray.chars(newShape, data);
            }
        };
    }

    /**
     * Takes along successive axes. Counts beyond the trailing axes are omitted;
     * overtaking an axis fills with the default numeric/literal fill atom.
     */
    public JArray takeAxes(int[] counts) {
        if (shape.length == 0) {
            throw new IllegalArgumentException("take needs a non-scalar argument");
        }
        if (counts.length > shape.length) {
            throw new IllegalArgumentException("take has too many axes");
        }

        int[] newShape = shape.clone();
        int[] take = new int[counts.length];
        int[] fill = new int[counts.length];
        int[] shift = new int[counts.length];
        for (int axis = 0; axis < counts.length; axis++) {
            long magnitude = Math.abs((long) counts[axis]);
            if (magnitude > Integer.MAX_VALUE) {
                throw new IllegalArgumentException("take result is too large");
            }
            take[axis] = (int) magnitude;
            newShape[axis] = take[axis];
            fill[axis] = Math.max(0, take[axis] - shape[axis]);
            shift[axis] = Math.max(0, shape[axis] - take[axis]);
        }

        int total = Shapes.size(newShape);
        int[] destinationStrides = Shapes.strides(newShape);
        int[] sourceStrides = Shapes.strides(shape);
        int[] sourceIndices = new int[total];
        for (int atom = 0; atom < total; atom++) {
            int remainder = atom;
            boolean filled = false;
            int source = 0;
            for (int axis = 0; axis < shape.length; axis++) {
                int coordinate = remainder / destinationStrides[axis];
                remainder %= destinationStrides[axis];
                if (axis >= counts.length) {
                    source += coordinate * sourceStrides[axis];
                    continue;
                }

                int sourceCoordinate;
                if (counts[axis] >= 0) {
                    if (coordinate >= shape[axis]) {
                        filled = true;
                        break;
                    }
                    sourceCoordinate = coordinate;
                } else {
                    if (coordinate < fill[axis]) {
                        filled = true;
                        break;
                    }
                    sourceCoordinate = coordinate - fill[axis] + shift[axis];
                }
                source += sourceCoordinate * sourceStrides[axis];
            }
            sourceIndices[atom] = filled ? -1 : source;
        }
        return remapWithFill(newShape, sourceIndices);
    }

    /** Drops along successive axes; negative counts drop from the end. */
    public JArray dropAxes(int[] counts) {
        if (shape.length == 0) {
            throw new IllegalArgumentException("drop needs a non-scalar argument");
        }
        if (counts.length > shape.length) {
            throw new IllegalArgumentException("drop has too many axes");
        }

        int[] newShape = shape.clone();
        int[] starts = new int[shape.length];
        for (int axis = 0; axis < counts.length; axis++) {
            long magnitude = Math.abs((long) counts[axis]);
            if (magnitude > Integer.MAX_VALUE) {
                throw new IllegalArgumentException("drop result is too large");
            }
            int dropped = (int) Math.min(magnitude, shape[axis]);
            newShape[axis] = shape[axis] - dropped;
            starts[axis] = counts[axis] >= 0 ? dropped : 0;
        }

        int total = Shapes.size(newShape);
        int[] destinationStrides = Shapes.strides(newShape);
        int[] sourceStrides = Shapes.strides(shape);
        int[] sourceIndices = new int[total];
        for (int atom = 0; atom < total; atom++) {
            int remainder = atom;
            int source = 0;
            for (int axis = 0; axis < shape.length; axis++) {
                int coordinate = remainder / destinationStrides[axis];
                remainder %= destinationStrides[axis];
                source += (coordinate + starts[axis]) * sourceStrides[axis];
            }
            sourceIndices[atom] = source;
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

    private JArray remapWithFill(int[] newShape, int[] sourceIndices) {
        int destinationSize = Shapes.size(newShape);
        if (sourceIndices.length != destinationSize) {
            throw new IllegalArgumentException("source map has the wrong length");
        }
        return switch (type) {
            case INT -> {
                long[] data = new long[destinationSize];
                for (int atom = 0; atom < destinationSize; atom++) {
                    data[atom] = sourceIndices[atom] < 0 ? 0 : longAt(sourceIndices[atom]);
                }
                yield JArray.ints(newShape, data);
            }
            case FLOAT -> {
                double[] data = new double[destinationSize];
                for (int atom = 0; atom < destinationSize; atom++) {
                    data[atom] = sourceIndices[atom] < 0 ? 0.0 : doubleAt(sourceIndices[atom]);
                }
                yield JArray.floats(newShape, data);
            }
            case CHAR -> {
                char[] data = new char[destinationSize];
                for (int atom = 0; atom < destinationSize; atom++) {
                    data[atom] = sourceIndices[atom] < 0 ? ' ' : charAt(sourceIndices[atom]);
                }
                yield JArray.chars(newShape, data);
            }
        };
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

    /** The default fill atom: zero for numeric arrays, a space for characters. */
    public static JArray fillAtom(JType type) {
        return switch (type) {
            case INT -> JArray.scalar(0L);
            case FLOAT -> JArray.scalar(0.0);
            case CHAR -> JArray.scalar(' ');
        };
    }

    private static JArray filled(JType type, int[] shape) {
        int size = Shapes.size(shape);
        return switch (type) {
            case INT -> JArray.ints(shape, new long[size]);
            case FLOAT -> JArray.floats(shape, new double[size]);
            case CHAR -> {
                char[] data = new char[size];
                Arrays.fill(data, ' ');
                yield JArray.chars(shape, data);
            }
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
