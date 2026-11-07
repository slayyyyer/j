package j.verbs;

import j.JError;
import j.array.JArray;

/** {@code ,}: monadic ravel, dyadic catenate. */
public final class Ravel extends Verb {
    public Ravel() {
        super(",");
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
        return y.ravel();
    }

    @Override
    public JArray dyadCell(JArray x, JArray y) {
        try {
            return x.append(y);
        } catch (IllegalArgumentException mismatch) {
            throw new JError("rank error");
        }
    }
}
