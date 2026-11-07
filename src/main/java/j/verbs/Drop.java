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
        return 0;
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
        return y.dropItems((int) count);
    }
}
