package j.verbs;

import j.JError;
import j.array.JArray;

/** {@code |.}: monadic reverse, dyadic rotate. */
public final class Reverse extends Verb {
    public Reverse() {
        super("|.");
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
        return y.reverseItems();
    }

    @Override
    public JArray dyadCell(JArray x, JArray y) {
        if (!x.type().isExact()) {
            throw new JError("domain error");
        }
        if (x.rank() > 1) {
            throw new JError("rank error");
        }
        if (x.rank() == 0) {
            return y.rotateItems(Numeric.exactLong(x));
        }
        if (x.size() > y.rank()) {
            throw new JError("rank error");
        }

        long[] counts = new long[x.size()];
        for (int axis = 0; axis < counts.length; axis++) {
            counts[axis] = Numeric.exactLongAt(x, axis);
        }
        try {
            return y.rotateAxes(counts);
        } catch (IllegalArgumentException outOfRange) {
            throw new JError("rank error");
        }
    }
}
