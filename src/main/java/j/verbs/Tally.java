package j.verbs;

import java.util.Arrays;

import j.JError;
import j.array.Complex;
import j.array.JArray;
import j.array.JType;

/** {@code #}: monadic tally, dyadic copy. */
public final class Tally extends Verb {
    public Tally() {
        super("#");
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
        return JArray.scalar(y.itemCount());
    }

    @Override
    public JArray dyadCell(JArray x, JArray y) {
        if (x.rank() > 1) {
            throw new JError("rank error");
        }

        if (x.size() == 0) {
            if (y.rank() == 0) {
                return JArray.empty(y.type(), new int[]{0});
            }
            if (y.itemCount() == 0) {
                return y.copyItems(new int[0]);
            }
            throw new JError("length error");
        }

        int[] copies = new int[y.itemCount()];
        int[] fills = new int[y.itemCount()];
        if (x.rank() == 0) {
            readCount(x, 0, copies, fills);
            Arrays.fill(copies, copies[0]);
            Arrays.fill(fills, fills[0]);
        } else {
            if (x.size() != copies.length) {
                throw new JError("length error");
            }
            for (int item = 0; item < copies.length; item++) {
                readCount(x, item, copies, fills);
            }
        }

        try {
            return y.copyItems(copies, fills);
        } catch (IllegalArgumentException tooLarge) {
            throw new JError("limit error");
        }
    }

    private static void readCount(JArray x, int index, int[] copies, int[] fills) {
        if (x.type() == JType.COMPLEX) {
            Complex count = x.complexAt(index);
            copies[index] = integerCount(count.real());
            fills[index] = integerCount(count.imaginary());
            return;
        }
        if (!x.type().isExact()) {
            throw new JError("domain error");
        }
        copies[index] = integerCount(Numeric.exactLongAt(x, index));
        fills[index] = 0;
    }

    private static int integerCount(double value) {
        if (!Double.isFinite(value) || value != Math.rint(value)) {
            throw new JError("domain error");
        }
        return integerCount((long) value);
    }

    private static int integerCount(long value) {
        if (value < 0) {
            throw new JError("domain error");
        }
        if (value > Integer.MAX_VALUE) {
            throw new JError("limit error");
        }
        return (int) value;
    }
}
