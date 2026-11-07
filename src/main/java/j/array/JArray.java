package j.array;

import java.math.BigInteger;
import java.util.Arrays;

/**
 * A J noun: flat, row-major storage plus a shape.
 *
 * <p>Atoms are immutable Java objects: {@link Long}, {@link BigInteger},
 * {@link Rational}, {@link Double}, {@link Complex}, {@link Character}, or a nested
 * {@link JArray} for a box. The shape and storage are immutable; input arrays and
 * exposed shapes are copied.
 */
public final class JArray {
    private final JType type;
    private final int[] shape;
    private final Object[] data;
    private final int size;

    private JArray(JType type, int[] shape, Object[] data) {
        int expectedSize = Shapes.size(shape);
        if (data.length != expectedSize) {
            throw new IllegalArgumentException(
                    "shape " + Shapes.format(shape) + " needs " + expectedSize
                            + " atoms but got " + data.length);
        }
        this.type = type;
        this.shape = shape;
        this.data = data;
        this.size = data.length;
    }

    // ---------------------------------------------------------------- factories

    /** An integer vector with one element per value. */
    public static JArray ints(long... values) {
        return ints(new int[]{values.length}, values);
    }

    /** An integer array of the given shape, filled row by row. */
    public static JArray ints(int[] shape, long... values) {
        Object[] boxed = new Object[values.length];
        for (int atom = 0; atom < values.length; atom++) {
            boxed[atom] = values[atom];
        }
        return new JArray(JType.INT, shape.clone(), boxed);
    }

    /** A float vector with one element per value. */
    public static JArray floats(double... values) {
        return floats(new int[]{values.length}, values);
    }

    /** A float array of the given shape, filled row by row. */
    public static JArray floats(int[] shape, double... values) {
        Object[] boxed = new Object[values.length];
        for (int atom = 0; atom < values.length; atom++) {
            boxed[atom] = values[atom];
        }
        return new JArray(JType.FLOAT, shape.clone(), boxed);
    }

    /** A character vector containing the characters of {@code text}. */
    public static JArray chars(String text) {
        return chars(new int[]{text.length()}, text.toCharArray());
    }

    /** A character array of the given shape, filled row by row. */
    public static JArray chars(int[] shape, char... values) {
        Object[] boxed = new Object[values.length];
        for (int atom = 0; atom < values.length; atom++) {
            boxed[atom] = values[atom];
        }
        return new JArray(JType.CHAR, shape.clone(), boxed);
    }

    /** An extended-integer vector. */
    public static JArray bigints(BigInteger... values) {
        return bigints(new int[]{values.length}, values);
    }

    /** An extended-integer array of the given shape. */
    public static JArray bigints(int[] shape, BigInteger... values) {
        return new JArray(JType.BIGINT, shape.clone(), values.clone());
    }

    /** A rational vector. */
    public static JArray rationals(Rational... values) {
        return rationals(new int[]{values.length}, values);
    }

    /** A rational array of the given shape. */
    public static JArray rationals(int[] shape, Rational... values) {
        return new JArray(JType.RATIONAL, shape.clone(), values.clone());
    }

    /** A complex vector. */
    public static JArray complexes(Complex... values) {
        return complexes(new int[]{values.length}, values);
    }

    /** A complex array of the given shape. */
    public static JArray complexes(int[] shape, Complex... values) {
        return new JArray(JType.COMPLEX, shape.clone(), values.clone());
    }

    /** A boxed vector. */
    public static JArray boxes(JArray... values) {
        return boxes(new int[]{values.length}, values);
    }

    /** A boxed array of the given shape. */
    public static JArray boxes(int[] shape, JArray... values) {
        return new JArray(JType.BOX, shape.clone(), values.clone());
    }

    /** A raw typed array, used by assembly after promotion. */
    public static JArray of(JType type, int[] shape, Object[] values) {
        return new JArray(type, shape.clone(), values.clone());
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

    /** A rank-0 extended-integer array. */
    public static JArray scalar(BigInteger value) {
        return bigints(new int[0], value);
    }

    /** A rank-0 rational array. */
    public static JArray scalar(Rational value) {
        return rationals(new int[0], value);
    }

    /** A rank-0 complex array. */
    public static JArray scalar(Complex value) {
        return complexes(new int[0], value);
    }

    /** A rank-0 box containing {@code value}. */
    public static JArray scalar(JArray value) {
        return boxes(new int[0], value);
    }

    /** An empty array of the given type. At least one dimension must be zero. */
    public static JArray empty(JType type, int... shape) {
        if (Shapes.size(shape) != 0) {
            throw new IllegalArgumentException("empty array shape must contain a zero: " + Shapes.format(shape));
        }
        return switch (type) {
            case INT -> ints(shape, new long[0]);
            case BIGINT -> bigints(shape, new BigInteger[0]);
            case RATIONAL -> rationals(shape, new Rational[0]);
            case FLOAT -> floats(shape, new double[0]);
            case COMPLEX -> complexes(shape, new Complex[0]);
            case CHAR -> chars(shape, new char[0]);
            case BOX -> boxes(shape, new JArray[0]);
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
        return (Long) data[index];
    }

    public double doubleAt(int index) {
        requireType(JType.FLOAT, "doubleAt");
        return (Double) data[index];
    }

    public char charAt(int index) {
        requireType(JType.CHAR, "charAt");
        return (Character) data[index];
    }

    /** An exact integer atom from an INT or BIGINT array. */
    public BigInteger bigAt(int index) {
        return switch (type) {
            case INT -> BigInteger.valueOf((Long) data[index]);
            case BIGINT -> (BigInteger) data[index];
            default -> throw new IllegalStateException("bigAt requires an integer array, got " + type);
        };
    }

    /** A rational atom from an INT, BIGINT or RATIONAL array. */
    public Rational rationalAt(int index) {
        return switch (type) {
            case INT -> new Rational(BigInteger.valueOf((Long) data[index]), BigInteger.ONE);
            case BIGINT -> new Rational((BigInteger) data[index], BigInteger.ONE);
            case RATIONAL -> (Rational) data[index];
            default -> throw new IllegalStateException("rationalAt requires an exact array, got " + type);
        };
    }

    /** A complex atom from any numeric array. */
    public Complex complexAt(int index) {
        return switch (type) {
            case INT -> new Complex(((Long) data[index]).doubleValue(), 0.0);
            case BIGINT -> new Complex(((BigInteger) data[index]).doubleValue(), 0.0);
            case RATIONAL -> new Complex(((Rational) data[index]).toDouble(), 0.0);
            case FLOAT -> new Complex((Double) data[index], 0.0);
            case COMPLEX -> (Complex) data[index];
            default -> throw new IllegalStateException("complexAt requires a numeric array, got " + type);
        };
    }

    /** A boxed atom. */
    public JArray boxAt(int index) {
        requireType(JType.BOX, "boxAt");
        return (JArray) data[index];
    }

    /** The raw immutable atom, used by generic promotion and equality. */
    public Object atomAt(int index) {
        return data[index];
    }

    /** Reads an atom as a double, widening exact numeric types. Complex is excluded. */
    public double numericAt(int index) {
        return switch (type) {
            case INT -> ((Long) data[index]).doubleValue();
            case BIGINT -> ((BigInteger) data[index]).doubleValue();
            case RATIONAL -> ((Rational) data[index]).toDouble();
            case FLOAT -> (Double) data[index];
            default -> throw new IllegalStateException("numericAt requires a real numeric array, got " + type);
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
        return new JArray(type, newShape.clone(), Arrays.copyOfRange(data, offset, offset + count));
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
     * larger. Empty sources are filled with the type's default fill atom.
     */
    public JArray reshape(int[] newShape) {
        int destinationSize = Shapes.size(newShape);
        if (destinationSize == 0) {
            return empty(type, newShape);
        }
        if (size == 0) {
            return filled(type, newShape);
        }
        Object[] result = new Object[destinationSize];
        for (int atom = 0; atom < destinationSize; atom++) {
            result[atom] = data[atom % size];
        }
        return new JArray(type, newShape.clone(), result);
    }

    /** Flattens the array into a vector. */
    public JArray ravel() {
        return new JArray(type, new int[]{size}, data.clone());
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
        return copyItems(counts, new int[counts.length]);
    }

    /**
     * Replicates each item and inserts fill items after it. A complex count
     * {@code a j. b} is represented by {@code copies[i] == a} and
     * {@code fills[i] == b}.
     */
    public JArray copyItems(int[] copies, int[] fills) {
        if (copies.length != itemCount() || fills.length != itemCount()) {
            throw new IllegalArgumentException("copy counts must match the item count");
        }
        long total = 0;
        for (int item = 0; item < copies.length; item++) {
            if (copies[item] < 0 || fills[item] < 0) {
                throw new IllegalArgumentException("negative copy count");
            }
            total += (long) copies[item] + fills[item];
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
        Object[] result = new Object[totalAtoms];
        Object fill = fillObject(type);
        int at = 0;
        for (int item = 0; item < itemCount(); item++) {
            for (int copy = 0; copy < copies[item]; copy++) {
                for (int atom = 0; atom < itemSize; atom++) {
                    result[at++] = data[item * itemSize + atom];
                }
            }
            for (int fillItem = 0; fillItem < fills[item]; fillItem++) {
                for (int atom = 0; atom < itemSize; atom++) {
                    result[at++] = fill;
                }
            }
        }
        return new JArray(type, newShape, result);
    }

    /**
     * Takes {@code count} leading items, or trailing items when negative. An
     * overtake fills the missing items with the type's default fill atom.
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
        Object[] result = new Object[total];
        Object fill = fillObject(type);
        for (int item = 0; item < take; item++) {
            int sourceItem;
            if (count >= 0) {
                sourceItem = item < items ? item : -1;
            } else {
                sourceItem = item < fillItems ? -1 : start + (item - fillItems);
            }
            for (int atom = 0; atom < itemSize; atom++) {
                result[item * itemSize + atom] =
                        sourceItem < 0 ? fill : data[sourceItem * itemSize + atom];
            }
        }
        return new JArray(type, newShape, result);
    }

    /** Takes along successive axes, filling overtaken coordinates. */
    public JArray takeAxes(int[] counts) {
        if (shape.length == 0) {
            throw new IllegalArgumentException("take needs a non-scalar argument");
        }
        if (counts.length > shape.length) {
            throw new IllegalArgumentException("take has too many axes");
        }

        int[] newShape = shape.clone();
        int[] fill = new int[counts.length];
        int[] shift = new int[counts.length];
        for (int axis = 0; axis < counts.length; axis++) {
            long magnitude = Math.abs((long) counts[axis]);
            if (magnitude > Integer.MAX_VALUE) {
                throw new IllegalArgumentException("take result is too large");
            }
            newShape[axis] = (int) magnitude;
            fill[axis] = Math.max(0, newShape[axis] - shape[axis]);
            shift[axis] = Math.max(0, shape[axis] - newShape[axis]);
        }

        int total = Shapes.size(newShape);
        int[] destinationStrides = Shapes.strides(newShape);
        int[] sourceStrides = Shapes.strides(shape);
        Object fillAtom = fillObject(type);
        Object[] result = new Object[total];
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
            result[atom] = filled ? fillAtom : data[source];
        }
        return new JArray(type, newShape, result);
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
        Object[] result = new Object[keep * itemSize];
        for (int atom = 0; atom < result.length; atom++) {
            int item = start + atom / itemSize;
            result[atom] = data[item * itemSize + atom % itemSize];
        }
        return new JArray(type, newShape, result);
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
        Object[] result = new Object[total];
        for (int atom = 0; atom < total; atom++) {
            int remainder = atom;
            int source = 0;
            for (int axis = 0; axis < shape.length; axis++) {
                int coordinate = remainder / destinationStrides[axis];
                remainder %= destinationStrides[axis];
                source += (coordinate + starts[axis]) * sourceStrides[axis];
            }
            result[atom] = data[source];
        }
        return new JArray(type, newShape, result);
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
        Object[] result = new Object[Shapes.size(newShape)];
        int at = 0;
        for (int atom = 0; atom < size; atom++) {
            result[at++] = convertAtom(data[atom], resultType);
        }
        for (int atom = 0; atom < other.size; atom++) {
            result[at++] = convertAtom(other.data[atom], resultType);
        }
        return new JArray(resultType, newShape, result);
    }

    /** Reverses the axes of an array of rank 2 or more. */
    public JArray transpose() {
        return transposeAxes(reversedAxes(shape.length));
    }

    /** Reorders axes using the generalized-transpose permutation. */
    public JArray transposeAxes(int[] axes) {
        if (axes.length != shape.length) {
            throw new IllegalArgumentException("axis permutation has the wrong length");
        }
        if (shape.length < 2) {
            return this;
        }
        int[] newShape = new int[shape.length];
        for (int axis = 0; axis < shape.length; axis++) {
            newShape[axis] = shape[axes[axis]];
        }
        int[] sourceStrides = Shapes.strides(shape);
        int[] destinationStrides = Shapes.strides(newShape);
        int[] sourceIndices = new int[size];
        for (int atom = 0; atom < size; atom++) {
            int remainder = atom;
            int source = 0;
            for (int axis = 0; axis < shape.length; axis++) {
                int coordinate = remainder / destinationStrides[axis];
                remainder %= destinationStrides[axis];
                source += coordinate * sourceStrides[axes[axis]];
            }
            sourceIndices[atom] = source;
        }
        return remap(newShape, sourceIndices);
    }

    private static int[] reversedAxes(int rank) {
        int[] axes = new int[rank];
        for (int axis = 0; axis < rank; axis++) {
            axes[axis] = rank - 1 - axis;
        }
        return axes;
    }

    /** Rotates successive axes by the given item counts. */
    public JArray rotateAxes(long[] counts) {
        if (shape.length == 0) {
            throw new IllegalArgumentException("rotate needs a non-scalar argument");
        }
        if (counts.length > shape.length) {
            throw new IllegalArgumentException("rotate has too many axes");
        }
        int[] sourceStrides = Shapes.strides(shape);
        int[] destinationStrides = Shapes.strides(shape);
        int[] starts = new int[shape.length];
        for (int axis = 0; axis < counts.length; axis++) {
            if (shape[axis] == 0) {
                continue;
            }
            long shift = Math.floorMod(counts[axis], (long) shape[axis]);
            starts[axis] = (int) shift;
        }

        Object[] result = new Object[size];
        for (int atom = 0; atom < size; atom++) {
            int remainder = atom;
            int source = 0;
            for (int axis = 0; axis < shape.length; axis++) {
                int coordinate = remainder / destinationStrides[axis];
                remainder %= destinationStrides[axis];
                int sourceCoordinate = shape[axis] == 0
                        ? 0
                        : Math.floorMod(coordinate + starts[axis], shape[axis]);
                source += sourceCoordinate * sourceStrides[axis];
            }
            result[atom] = data[source];
        }
        return new JArray(type, shape, result);
    }

    private JArray remap(int[] newShape, int[] sourceIndices) {
        int destinationSize = Shapes.size(newShape);
        if (sourceIndices.length != destinationSize) {
            throw new IllegalArgumentException("source map has the wrong length");
        }
        Object[] result = new Object[destinationSize];
        for (int atom = 0; atom < destinationSize; atom++) {
            result[atom] = data[sourceIndices[atom]];
        }
        return new JArray(type, newShape, result);
    }

    private static JArray filled(JType type, int[] shape) {
        int size = Shapes.size(shape);
        Object fill = fillObject(type);
        Object[] result = new Object[size];
        Arrays.fill(result, fill);
        return new JArray(type, shape, result);
    }

    /** The default fill atom: zero, space, or an empty box. */
    public static JArray fillAtom(JType type) {
        return switch (type) {
            case INT -> JArray.scalar(0L);
            case BIGINT -> JArray.scalar(BigInteger.ZERO);
            case RATIONAL -> JArray.scalar(Rational.ZERO);
            case FLOAT -> JArray.scalar(0.0);
            case COMPLEX -> JArray.scalar(Complex.ZERO);
            case CHAR -> JArray.scalar(' ');
            case BOX -> JArray.scalar(JArray.ints(new long[0]));
        };
    }

    private static Object fillObject(JType type) {
        return switch (type) {
            case INT -> 0L;
            case BIGINT -> BigInteger.ZERO;
            case RATIONAL -> Rational.ZERO;
            case FLOAT -> 0.0;
            case COMPLEX -> Complex.ZERO;
            case CHAR -> ' ';
            case BOX -> JArray.ints(new long[0]);
        };
    }

    /** Numeric promotion for arrays; characters and boxes only combine with themselves. */
    public static JType promote(JType left, JType right) {
        if (left == right) {
            return left;
        }
        if (!left.isNumeric() || !right.isNumeric()) {
            throw new IllegalArgumentException("domain");
        }
        return left.numericRank() >= right.numericRank() ? left : right;
    }

    /** Converts one atom to another JType, widening numeric values as needed. */
    public static Object convertAtom(Object atom, JType target) {
        return switch (target) {
            case INT -> {
                if (atom instanceof Long value) {
                    yield value;
                }
                if (atom instanceof BigInteger value) {
                    yield value.longValueExact();
                }
                if (atom instanceof Rational value && value.isInteger()) {
                    yield value.numerator().longValueExact();
                }
                throw new IllegalArgumentException("cannot convert to INT");
            }
            case BIGINT -> {
                if (atom instanceof Long value) {
                    yield BigInteger.valueOf(value);
                }
                if (atom instanceof BigInteger value) {
                    yield value;
                }
                if (atom instanceof Rational value && value.isInteger()) {
                    yield value.numerator();
                }
                throw new IllegalArgumentException("cannot convert to BIGINT");
            }
            case RATIONAL -> {
                if (atom instanceof Long value) {
                    yield new Rational(BigInteger.valueOf(value), BigInteger.ONE);
                }
                if (atom instanceof BigInteger value) {
                    yield new Rational(value, BigInteger.ONE);
                }
                if (atom instanceof Rational value) {
                    yield value;
                }
                throw new IllegalArgumentException("cannot convert to RATIONAL");
            }
            case FLOAT -> {
                if (atom instanceof Long value) {
                    yield value.doubleValue();
                }
                if (atom instanceof BigInteger value) {
                    yield value.doubleValue();
                }
                if (atom instanceof Rational value) {
                    yield value.toDouble();
                }
                if (atom instanceof Double value) {
                    yield value;
                }
                throw new IllegalArgumentException("cannot convert to FLOAT");
            }
            case COMPLEX -> {
                if (atom instanceof Complex value) {
                    yield value;
                }
                if (atom instanceof Long value) {
                    yield new Complex(value.doubleValue(), 0.0);
                }
                if (atom instanceof BigInteger value) {
                    yield new Complex(value.doubleValue(), 0.0);
                }
                if (atom instanceof Rational value) {
                    yield new Complex(value.toDouble(), 0.0);
                }
                if (atom instanceof Double value) {
                    yield new Complex(value, 0.0);
                }
                throw new IllegalArgumentException("cannot convert to COMPLEX");
            }
            case CHAR -> {
                if (atom instanceof Character value) {
                    yield value;
                }
                throw new IllegalArgumentException("cannot convert to CHAR");
            }
            case BOX -> {
                if (atom instanceof JArray value) {
                    yield value;
                }
                throw new IllegalArgumentException("cannot convert to BOX");
            }
        };
    }

    /**
     * Promotes an integer or exact array to floating point. Characters, boxes and
     * complex arrays cannot be promoted this way.
     */
    public JArray toFloat() {
        if (type == JType.FLOAT) {
            return this;
        }
        if (!type.isExact()) {
            throw new IllegalStateException("cannot promote " + type + " to float");
        }
        Object[] result = new Object[size];
        for (int atom = 0; atom < size; atom++) {
            result[atom] = numericAt(atom);
        }
        return new JArray(JType.FLOAT, shape, result);
    }

    @Override
    public String toString() {
        return JFormat.format(this);
    }
}
