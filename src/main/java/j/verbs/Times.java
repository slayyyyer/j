package j.verbs;

import j.JError;
import j.array.JArray;
import j.array.JType;

/** {@code *}: monadic signum, dyadic multiplication. */
public final class Times extends AtomVerb {
    public Times() {
        super("*");
    }

    @Override
    public JArray monadCell(JArray y) {
        Numeric.requireNumeric(y);
        if (y.type() == JType.INT) {
            return JArray.scalar(Long.signum(y.longAt(0)));
        }
        double value = y.doubleAt(0);
        if (Double.isNaN(value)) {
            return JArray.scalar(Double.NaN);
        }
        return JArray.scalar((long) Math.signum(value));
    }

    @Override
    public JArray dyadCell(JArray x, JArray y) {
        Numeric.requireNumeric(x, y);
        if (x.type() == JType.INT && y.type() == JType.INT) {
            return JArray.scalar(Numeric.multiply(x.longAt(0), y.longAt(0)));
        }
        return JArray.scalar(x.numericAt(0) * y.numericAt(0));
    }

    @Override
    public JArray identity(JType type) {
        return switch (type) {
            case INT -> JArray.scalar(1L);
            case FLOAT -> JArray.scalar(1.0);
            case CHAR -> throw new JError("domain error");
        };
    }
}
