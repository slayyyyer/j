package j.verbs;

import java.util.Arrays;

import j.JError;
import j.array.JArray;
import j.array.JType;
import j.array.Shapes;

/** {@code i.}: monadic integers, dyadic index-of. */
public final class Iota extends Verb {
    public Iota() {
        super("i.");
    }

    @Override
    public int monadRank() {
        return 1;
    }

    @Override
    public int leftRank() {
        return WHOLE;
    }

    @Override
    public int rightRank() {
        return WHOLE;
    }

    @Override
    public JArray monadCell(JArray y) {
        if (!y.type().isExact()) {
            throw new JError("domain error");
        }
        if (y.rank() == 0) {
            return scalarIota(Numeric.exactLong(y));
        }
        if (y.rank() != 1) {
            throw new JError("rank error");
        }

        int[] shape = new int[y.size()];
        for (int axis = 0; axis < shape.length; axis++) {
            int dimension = Numeric.exactIntAt(y, axis);
            if (dimension < 0) {
                throw new JError("domain error");
            }
            shape[axis] = dimension;
        }

        int size = Shapes.size(shape);
        long[] data = new long[size];
        for (int atom = 0; atom < size; atom++) {
            data[atom] = atom;
        }
        return JArray.ints(shape, data);
    }

    /**
     * {@code x i. y}: the first index in {@code x} for each cell of {@code y}.
     * Cells of {@code y} have the rank of an item of {@code x}; a cell that does
     * not occur yields {@code #x}.
     */
    @Override
    public JArray dyadCell(JArray x, JArray y) {
        int itemRank = Math.max(0, x.rank() - 1);
        int cellRank = Math.min(itemRank, y.rank());
        int[] frameShape = Arrays.copyOf(y.shape(), y.rank() - cellRank);
        int[] cellShape = Arrays.copyOfRange(y.shape(), frameShape.length, y.rank());
        int frameSize = Shapes.size(frameShape);
        int cellSize = Shapes.size(cellShape);

        long[] indexes = new long[frameSize];
        for (int cell = 0; cell < frameSize; cell++) {
            indexes[cell] = Items.firstIndex(x, y.slice(cell * cellSize, cellShape));
        }
        return JArray.ints(frameShape, indexes);
    }

    private static JArray scalarIota(long limit) {
        if (limit == 0) {
            return JArray.ints(new long[0]);
        }
        if (limit > Integer.MAX_VALUE || limit < -Integer.MAX_VALUE) {
            throw new JError("limit error");
        }

        int size = (int) Math.abs(limit);
        long[] data = new long[size];
        if (limit > 0) {
            for (int atom = 0; atom < size; atom++) {
                data[atom] = atom;
            }
        } else {
            for (int atom = 0; atom < size; atom++) {
                data[atom] = size - 1L - atom;
            }
        }
        return JArray.ints(data);
    }
}
