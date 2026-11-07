package j.verbs;

import j.array.JArray;
import j.eval.Rank;

/** {@code u/}: insert the dyad between items, folding from the right. */
public final class Insert extends Verb {
    private final Verb operand;

    public Insert(Verb operand) {
        super(operand.repr() + "/");
        this.operand = operand;
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
        if (y.rank() == 0) {
            return y;
        }
        int items = y.itemCount();
        if (items == 0) {
            JArray identity = operand.identity(y.type());
            return y.rank() > 1 ? identity.reshape(y.itemShape()) : identity;
        }

        JArray accumulator = y.item(items - 1);
        for (int item = items - 2; item >= 0; item--) {
            accumulator = Rank.applyDyad(operand, y.item(item), accumulator);
        }
        return accumulator;
    }
}
