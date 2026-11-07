package j.verbs;

import j.JError;
import j.array.JArray;
import j.array.JType;

/** {@code $}: monadic shape-of, dyadic reshape. */
public final class Shape extends Verb {
    public Shape() {
        super("$");
    }

    @Override
    public int monadRank() {
        return WHOLE;
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
        int[] shape = y.shape();
        long[] values = new long[shape.length];
        for (int axis = 0; axis < shape.length; axis++) {
            values[axis] = shape[axis];
        }
        return JArray.ints(values);
    }

    @Override
    public JArray dyadCell(JArray x, JArray y) {
        if (x.type() != JType.INT) {
            throw new JError("domain error");
        }
        if (x.rank() > 1) {
            throw new JError("rank error");
        }

        int[] shape = new int[x.size()];
        for (int axis = 0; axis < shape.length; axis++) {
            long dimension = x.longAt(axis);
            if (dimension < 0) {
                throw new JError("domain error");
            }
            if (dimension > Integer.MAX_VALUE) {
                throw new JError("limit error");
            }
            shape[axis] = (int) dimension;
        }
        return y.reshape(shape);
    }
}
