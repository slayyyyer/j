package j.verbs;

import j.array.JArray;
import j.array.JType;

/** {@code <:}: monadic decrement, dyadic less-or-equal. */
public final class LessEqual extends AtomVerb {
    public LessEqual() {
        super("<:");
    }

    @Override
    public JArray monadCell(JArray y) {
        Numeric.requireNumeric(y);
        if (y.type() == JType.INT) {
            return JArray.scalar(Numeric.decrement(y.longAt(0)));
        }
        return JArray.scalar(y.doubleAt(0) - 1.0);
    }

    @Override
    public JArray dyadCell(JArray x, JArray y) {
        return Comparison.test(Comparison.Kind.LESS_EQUAL, x, y);
    }
}
