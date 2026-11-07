package j.verbs;

import j.JError;
import j.array.JArray;

/** {@code $.'}: sparse conversion. */
public final class SparseVerb extends Verb {
    public SparseVerb() {
        super("$.");
    }

    @Override
    public int monadRank() {
        return WHOLE;
    }

    @Override
    public int leftRank() {
        return WHOLE;
    }

    @Override
    public int rightRank() {
        return WHOLE;
    }

    @Override
    public JArray monadCell(JArray y) {
        return y.isSparse() ? y : y.toSparse();
    }

    @Override
    public JArray dyadCell(JArray x, JArray y) {
        if (!x.type().isExact() || !x.isScalar() || Numeric.exactLong(x) != 0) {
            throw new JError("domain error");
        }
        return y.toggleSparse();
    }
}
