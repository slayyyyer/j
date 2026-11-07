package j.verbs;

import j.array.JArray;
import j.array.JType;

/** {@code *}: monadic signum, dyadic multiplication. */
public final class Times extends AtomVerb {
    public Times() {
        super("*");
    }

    @Override
    public JArray monadCell(JArray y) {
        return Numeric.signum(y);
    }

    @Override
    public JArray dyadCell(JArray x, JArray y) {
        return Numeric.multiply(x, y);
    }

    @Override
    public JArray identity(JType type) {
        return Numeric.one(type);
    }
}
