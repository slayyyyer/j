package j.verbs;

import j.JError;
import j.array.JArray;
import j.array.JType;

/**
 * {@code ^}: monadic exponential, dyadic power. Integer base and non-negative
 * integer exponent keep an integer result (with overflow checking); every other
 * combination is floating point.
 */
public final class Power extends AtomVerb {
    public Power() {
        super("^");
    }

    @Override
    public JArray monadCell(JArray y) {
        Numeric.requireNumeric(y);
        return JArray.scalar(Math.exp(y.numericAt(0)));
    }

    @Override
    public JArray dyadCell(JArray x, JArray y) {
        Numeric.requireNumeric(x, y);
        if (x.type() == JType.INT && y.type() == JType.INT) {
            return integerPower(x.longAt(0), y.longAt(0));
        }
        return JArray.scalar(Math.pow(x.numericAt(0), y.numericAt(0)));
    }

    private static JArray integerPower(long base, long exponent) {
        if (exponent < 0) {
            return JArray.scalar(Math.pow((double) base, (double) exponent));
        }
        if (base == 0) {
            return JArray.scalar(exponent == 0 ? 1L : 0L);
        }
        if (base == 1) {
            return JArray.scalar(1L);
        }
        if (base == -1) {
            return JArray.scalar((exponent & 1L) == 0 ? 1L : -1L);
        }
        // |base| >= 2; 2^63 is already outside long, and larger exponents cannot
        // fit without extended integers.
        if (exponent > 63) {
            throw new JError("limit error");
        }

        long result = 1;
        for (long power = 0; power < exponent; power++) {
            result = Numeric.multiply(result, base);
        }
        return JArray.scalar(result);
    }
}
