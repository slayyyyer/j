package j.verbs;

import java.util.ArrayList;
import java.util.List;

import j.JError;
import j.array.JArray;

/** {@code |:}: monadic transpose, dyadic axis permutation. */
public final class Transpose extends Verb {
    public Transpose() {
        super("|:");
    }

    @Override
    public int monadRank() {
        return WHOLE;
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
        return y.transpose();
    }

    @Override
    public JArray dyadCell(JArray x, JArray y) {
        if (!x.type().isExact()) {
            throw new JError("domain error");
        }
        if (x.rank() > 1) {
            throw new JError("rank error");
        }
        if (y.rank() < 2) {
            return y;
        }

        List<Integer> axes = new ArrayList<>(y.rank());
        for (int axis = 0; axis < y.rank(); axis++) {
            axes.add(axis);
        }
        for (int atom = 0; atom < x.size(); atom++) {
            int axis = Numeric.exactIntAt(x, atom);
            if (axis < 0 || axis >= y.rank()) {
                throw new JError("index error");
            }
            if (!axes.remove(Integer.valueOf(axis))) {
                throw new JError("domain error");
            }
            axes.add(axis);
        }

        int[] permutation = new int[axes.size()];
        for (int axis = 0; axis < permutation.length; axis++) {
            permutation[axis] = axes.get(axis);
        }
        return y.transposeAxes(permutation);
    }
}
