package j.verbs;

import j.array.JArray;

/** {@code |:}: monadic transpose; dyadic transpose is not implemented yet. */
public final class Transpose extends Verb {
    public Transpose() {
        super("|:");
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
        return y.transpose();
    }
}
