package j.verbs;

import j.JError;
import j.array.JArray;
import j.array.JType;

/** {@code |.}: monadic reverse, dyadic rotate. */
public final class Reverse extends Verb {
    public Reverse() {
        super("|.");
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
        return WHOLE;
    }

    @Override
    public JArray monadCell(JArray y) {
        return y.reverseItems();
    }

    @Override
    public JArray dyadCell(JArray x, JArray y) {
        if (x.type() != JType.INT) {
            throw new JError("domain error");
        }
        if (x.rank() != 0) {
            throw new JError("rank error");
        }
        return y.rotateItems(x.longAt(0));
    }
}
