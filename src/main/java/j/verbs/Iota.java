package j.verbs;

import j.JError;
import j.array.JArray;
import j.array.JType;
import j.array.Shapes;

/** {@code i.}: monadic integers; dyadic index-of is not implemented yet. */
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
        return 1;
    }

    @Override
    public int rightRank() {
        return WHOLE;
    }

    @Override
    public JArray monadCell(JArray y) {
        if (y.type() != JType.INT) {
            throw new JError("domain error");
        }
        if (y.rank() == 0) {
            return scalarIota(y.longAt(0));
        }
        if (y.rank() != 1) {
            throw new JError("rank error");
        }

        int[] shape = new int[y.size()];
        for (int axis = 0; axis < shape.length; axis++) {
            long dimension = y.longAt(axis);
            if (dimension < 0) {
                throw new JError("domain error");
            }
            if (dimension > Integer.MAX_VALUE) {
                throw new JError("limit error");
            }
            shape[axis] = (int) dimension;
        }

        int size = Shapes.size(shape);
        long[] data = new long[size];
        for (int atom = 0; atom < size; atom++) {
            data[atom] = atom;
        }
        return JArray.ints(shape, data);
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
