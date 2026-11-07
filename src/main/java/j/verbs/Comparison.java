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
        if (x.type() == JType.CHAR || y.type() == JType.CHAR) {
            if (x.type() != JType.CHAR || y.type() != JType.CHAR) {
                throw new JError("domain error");
            }
            result = from(Character.compare(x.charAt(0), y.charAt(0)), kind);
        } else if (x.type() == JType.BOX || y.type() == JType.BOX) {
            if (x.type() != JType.BOX || y.type() != JType.BOX) {
                throw new JError("domain error");
            }
            if (kind != Kind.EQUAL && kind != Kind.NOT_EQUAL) {
                throw new JError("domain error");
            }
            result = Items.equal(x.boxAt(0), y.boxAt(0));
            if (kind == Kind.NOT_EQUAL) {
                result = !result;
            }
        } else if (x.type().isNumeric() && y.type().isNumeric()) {
            if (kind == Kind.EQUAL || kind == Kind.NOT_EQUAL) {
                result = Numeric.equal(x, y);
                if (kind == Kind.NOT_EQUAL) {
                    result = !result;
                }
            } else if (Numeric.isNaN(x) || Numeric.isNaN(y)) {
                result = false;
            } else {
                result = from(Numeric.compare(x, y), kind);
            }
        } else {
            throw new JError("domain error");
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
