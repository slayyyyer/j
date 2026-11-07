package j.verbs;

import j.JError;
import j.array.JArray;
import j.array.JType;

/** {@code {.}: monadic head, dyadic take with fill. */
public final class Take extends Verb {
    public Take() {
        super("{.");
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
            return y;
        }
        if (y.itemCount() == 0) {
            JArray fill = JArray.fillAtom(y.type());
            return y.rank() > 1 ? fill.reshape(y.itemShape()) : fill;
        }
        return y.item(0);
    }

    @Override
    public JArray dyadCell(JArray x, JArray y) {
        if (x.type() != JType.INT) {
            throw new JError("domain error");
        }
        if (x.rank() > 1) {
            throw new JError("rank error");
        }
        JArray source = y.rank() == 0 ? y.ravel() : y;
        try {
            if (x.rank() == 0) {
                long count = x.longAt(0);
                if (count < Integer.MIN_VALUE || count > Integer.MAX_VALUE) {
                    throw new JError("limit error");
                }
                return source.takeItems((int) count);
            }

            int[] counts = new int[x.size()];
            for (int axis = 0; axis < counts.length; axis++) {
                long count = x.longAt(axis);
                if (count < Integer.MIN_VALUE || count > Integer.MAX_VALUE) {
                    throw new JError("limit error");
                }
                counts[axis] = (int) count;
            }
            if (counts.length > source.rank()) {
                throw new JError("rank error");
            }
            return source.takeAxes(counts);
        } catch (IllegalArgumentException outOfRange) {
            throw new JError("limit error");
        }
    }
}
