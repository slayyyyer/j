package j.verbs;

import j.JError;
import j.array.JArray;
import j.array.JType;

/** {@code }.}: monadic behead, dyadic drop. */
public final class Drop extends Verb {
    public Drop() {
        super("}.");
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
        if (y.rank() == 0) {
            return JArray.empty(y.type(), new int[]{0});
        }
        return y.dropItems(1);
    }

    @Override
    public JArray dyadCell(JArray x, JArray y) {
        if (!x.type().isExact()) {
            throw new JError("domain error");
        }
        if (x.rank() > 1) {
            throw new JError("rank error");
        }
        JArray source = y.rank() == 0 ? y.ravel() : y;
        if (x.rank() == 0) {
            long count = Numeric.exactLong(x);
            if (count < Integer.MIN_VALUE || count > Integer.MAX_VALUE) {
                throw new JError("limit error");
            }
            return source.dropItems((int) count);
        }

        int[] counts = new int[x.size()];
        for (int axis = 0; axis < counts.length; axis++) {
            long count = Numeric.exactLongAt(x, axis);
            if (count < Integer.MIN_VALUE || count > Integer.MAX_VALUE) {
                throw new JError("limit error");
            }
            counts[axis] = (int) count;
        }
        if (counts.length > source.rank()) {
            throw new JError("rank error");
        }
        try {
            return source.dropAxes(counts);
        } catch (IllegalArgumentException outOfRange) {
            throw new JError("limit error");
        }
    }
}
