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
        return Numeric.decrement(y);
    }

    @Override
    public JArray dyadCell(JArray x, JArray y) {
        return Comparison.test(Comparison.Kind.LESS_EQUAL, x, y);
    }

    @Override
    public JArray identity(JType type) {
        return Numeric.one(type);
    }
}
