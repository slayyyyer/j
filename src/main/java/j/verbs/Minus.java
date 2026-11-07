package j.verbs;

import j.array.JArray;
import j.array.JType;

/** {@code -}: monadic negation, dyadic subtraction. */
public final class Minus extends AtomVerb {
    public Minus() {
        super("-");
    }

    @Override
    public JArray monadCell(JArray y) {
        return Numeric.negate(y);
    }

    @Override
    public JArray dyadCell(JArray x, JArray y) {
        return Numeric.subtract(x, y);
    }

    @Override
    public JArray identity(JType type) {
        return Numeric.zero(type);
    }
}
