package j.verbs;

import j.array.JArray;

/** {@code #}: monadic tally; dyadic copy is not implemented yet. */
public final class Tally extends Verb {
    public Tally() {
        super("#");
    }

    @Override
    public int monadRank() {
        return WHOLE;
    }

    @Override
    public int leftRank() {
        return 1;
    }

    @Override
    public int rightRank() {
        return WHOLE;
    }

    @Override
    public JArray monadCell(JArray y) {
        return JArray.scalar(y.itemCount());
    }
}
