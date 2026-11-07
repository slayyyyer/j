package j.verbs;

import j.array.JArray;
import j.array.JType;

/** {@code %}: monadic reciprocal, dyadic division. */
public final class Divide extends AtomVerb {
    public Divide() {
        super("%");
    }

    @Override
    public JArray monadCell(JArray y) {
        return Numeric.reciprocal(y);
    }

    @Override
    public JArray dyadCell(JArray x, JArray y) {
        return Numeric.divide(x, y);
    }

    @Override
    public JArray identity(JType type) {
        return Numeric.one(type);
    }
}
