package j.verbs;

import j.JError;
import j.array.JArray;
import j.array.JType;

/** {@code +}: monadic conjugate (identity on reals), dyadic addition. */
public final class Plus extends AtomVerb {
    public Plus() {
        super("+");
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

    @Override
    public JArray identity(JType type) {
        return switch (type) {
            case INT -> JArray.scalar(0L);
            case FLOAT -> JArray.scalar(0.0);
            case CHAR -> throw new JError("domain error");
        };
    }
}
