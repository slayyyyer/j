package j.verbs;

import j.JError;
import j.array.JArray;
import j.array.JType;

/** Shared atom comparison logic for {@code = ~: < > <: >:}. */
final class Comparison {
    enum Kind {
        EQUAL,
        NOT_EQUAL,
        LESS,
        GREATER,
        LESS_EQUAL,
        GREATER_EQUAL
    }

    private Comparison() {
    }

    static JArray test(Kind kind, JArray x, JArray y) {
        boolean result;
        if (x.type().isNumeric() != y.type().isNumeric()) {
            throw new JError("domain error");
        }
        if (x.type() == JType.CHAR) {
            result = from(Character.compare(x.charAt(0), y.charAt(0)), kind);
        } else if (x.type() == JType.INT && y.type() == JType.INT) {
            result = from(Long.compare(x.longAt(0), y.longAt(0)), kind);
        } else {
            double left = x.numericAt(0);
            double right = y.numericAt(0);
            if (Double.isNaN(left) || Double.isNaN(right)) {
                // IEEE comparisons: NaN is unequal to everything, including itself;
                // every ordering comparison is false.
                result = kind == Kind.NOT_EQUAL;
            } else if (left == right) {
                // Also covers -0.0 and 0.0, which J treats as equal.
                result = kind == Kind.EQUAL
                        || kind == Kind.LESS_EQUAL
                        || kind == Kind.GREATER_EQUAL;
            } else {
                result = from(Double.compare(left, right), kind);
            }
        }
        return JArray.scalar(result ? 1L : 0L);
    }

    private static boolean from(int comparison, Kind kind) {
        return switch (kind) {
            case EQUAL -> comparison == 0;
            case NOT_EQUAL -> comparison != 0;
            case LESS -> comparison < 0;
            case GREATER -> comparison > 0;
            case LESS_EQUAL -> comparison <= 0;
            case GREATER_EQUAL -> comparison >= 0;
        };
    }
}
