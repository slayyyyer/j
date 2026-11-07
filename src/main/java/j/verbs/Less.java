package j.verbs;

import j.array.JArray;
import j.array.JType;

/** {@code <}: monadic box, dyadic less-than. */
public final class Less extends Verb {
    public Less() {
        super("<");
    }

    @Override
    public int monadRank() {
        return WHOLE;
    }

    @Override
    public int leftRank() {
        return 0;
    }

    @Override
    public int rightRank() {
        return 0;
    }

    @Override
    public JArray monadCell(JArray y) {
        return JArray.scalar(y);
    }

    @Override
    public JArray dyadCell(JArray x, JArray y) {
        return Comparison.test(Comparison.Kind.LESS, x, y);
    }

    @Override
    public JArray identity(JType type) {
        return Numeric.zero(type);
    }
}
