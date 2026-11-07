package j.verbs;

import j.array.JArray;
import j.array.JType;

/** {@code +}: monadic conjugate, dyadic addition. */
public final class Plus extends AtomVerb {
    public Plus() {
        super("+");
    }

    @Override
    public JArray monadCell(JArray y) {
        return Numeric.conjugate(y);
    }

    @Override
    public JArray dyadCell(JArray x, JArray y) {
        return Numeric.add(x, y);
    }

    @Override
    public JArray identity(JType type) {
        return Numeric.zero(type);
    }
}
