package j.verbs;

import j.array.JArray;
import j.array.JType;

/** {@code ^}: monadic exponential, dyadic power. */
public final class Power extends AtomVerb {
    public Power() {
        super("^");
    }

    @Override
    public JArray monadCell(JArray y) {
        return Numeric.exp(y);
    }

    @Override
    public JArray dyadCell(JArray x, JArray y) {
        return Numeric.power(x, y);
    }

    @Override
    public JArray identity(JType type) {
        return Numeric.one(type);
    }
}
