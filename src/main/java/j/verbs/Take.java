package j.verbs;

import j.JError;
import j.array.JArray;
import j.array.JType;

/** {@code {.}: monadic head, dyadic take. */
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
        return 0;
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
            throw new JError("length error");
        }
        return y.item(0);
    }

    @Override
    public JArray dyadCell(JArray x, JArray y) {
        if (x.type() != JType.INT) {
            throw new JError("domain error");
        }
        if (x.rank() != 0) {
            throw new JError("rank error");
        }
        long count = x.longAt(0);
        if (count < Integer.MIN_VALUE || count > Integer.MAX_VALUE) {
            throw new JError("limit error");
        }
        try {
            return y.takeItems((int) count);
        } catch (IllegalArgumentException outOfRange) {
            throw new JError("length error");
        }
    }
}
