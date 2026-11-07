package j.verbs;

import j.array.JArray;
import j.array.JType;

/** {@code +}: monadic conjugate (identity on reals), dyadic addition. */
public final class Plus extends Verb {
    public Plus() {
        super("+");
    }

    @Override
    public int monadRank() {
        return 0;
    }

    @Override
    public int leftRank() {
        return 0;
    }

    @Override
    public int rightRank() {
        return 0;
    }

    @Override
    public JArray monadCell(JArray y) {
        Numeric.requireNumeric(y);
        return y;
    }

    @Override
    public JArray dyadCell(JArray x, JArray y) {
        Numeric.requireNumeric(x, y);
        if (x.type() == JType.INT && y.type() == JType.INT) {
            return JArray.scalar(Numeric.add(x.longAt(0), y.longAt(0)));
        }
        return JArray.scalar(x.numericAt(0) + y.numericAt(0));
    }
}
