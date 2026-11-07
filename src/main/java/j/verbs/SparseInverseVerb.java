package j.verbs;

import j.array.JArray;

/** {@code $.^:_1}: the inverse sparse spelling, densifying a sparse array. */
public final class SparseInverseVerb extends Verb {
    public SparseInverseVerb() {
        super("$.^:_1");
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
        return y.dense();
    }
}
