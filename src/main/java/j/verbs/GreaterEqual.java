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
        return Numeric.increment(y);
    }

    @Override
    public JArray dyadCell(JArray x, JArray y) {
        return Comparison.test(Comparison.Kind.GREATER_EQUAL, x, y);
    }

    @Override
    public JArray identity(JType type) {
        return Numeric.one(type);
    }
}
