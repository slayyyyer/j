package j.verbs;

import j.JError;
import j.array.JArray;

/** Shared numeric checks and overflow-safe integer operations for atom verbs. */
final class Numeric {
    private Numeric() {
    }

    static void requireNumeric(JArray... arrays) {
        for (JArray array : arrays) {
            if (!array.type().isNumeric()) {
                throw new JError("domain error");
            }
        }
    }

    static long add(long x, long y) {
        try {
            return Math.addExact(x, y);
        } catch (ArithmeticException overflow) {
            throw new JError("limit error");
        }
    }

    static long subtract(long x, long y) {
        try {
            return Math.subtractExact(x, y);
        } catch (ArithmeticException overflow) {
            throw new JError("limit error");
        }
    }

    static long negate(long value) {
        try {
            return Math.negateExact(value);
        } catch (ArithmeticException overflow) {
            throw new JError("limit error");
        }
    }
}
