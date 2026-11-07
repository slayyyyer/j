package j.verbs;

import j.array.JArray;
import j.array.JType;

/** {@code >:}: monadic increment, dyadic greater-or-equal. */
public final class GreaterEqual extends AtomVerb {
    public GreaterEqual() {
        super(">:");
    }

    @Override
    public JArray monadCell(JArray y) {
        Numeric.requireNumeric(y);
        if (y.type() == JType.INT) {
            return JArray.scalar(Numeric.increment(y.longAt(0)));
        }
        return JArray.scalar(y.doubleAt(0) + 1.0);
    }

    @Override
    public JArray dyadCell(JArray x, JArray y) {
        return Comparison.test(Comparison.Kind.GREATER_EQUAL, x, y);
    }
}
