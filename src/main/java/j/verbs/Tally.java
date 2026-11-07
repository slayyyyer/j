package j.verbs;

import java.util.Arrays;

import j.JError;
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
        if (x.type() != JType.INT) {
            throw new JError("domain error");
        }
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

        int[] counts = new int[y.itemCount()];
        if (x.rank() == 0) {
            long count = x.longAt(0);
            checkCount(count);
            Arrays.fill(counts, (int) count);
        } else {
            if (x.size() != counts.length) {
                throw new JError("length error");
            }
            for (int item = 0; item < counts.length; item++) {
                long count = x.longAt(item);
                checkCount(count);
                counts[item] = (int) count;
            }
        }

        try {
            return y.copyItems(counts);
        } catch (IllegalArgumentException tooLarge) {
            throw new JError("limit error");
        }
    }

    private static void checkCount(long count) {
        if (count < 0) {
            throw new JError("domain error");
        }
        if (count > Integer.MAX_VALUE) {
            throw new JError("limit error");
        }
    }
}
