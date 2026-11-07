package j.verbs;

import j.array.JArray;
import j.array.JType;

/** {@code =}: monadic self-classify, dyadic equality. */
public final class Equal extends Verb {
    public Equal() {
        super("=");
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
        return Items.selfClassify(y);
    }

    @Override
    public JArray dyadCell(JArray x, JArray y) {
        return Comparison.test(Comparison.Kind.EQUAL, x, y);
    }

    @Override
    public JArray identity(JType type) {
        return Numeric.one(type);
    }
}
