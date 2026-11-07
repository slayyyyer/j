package j.verbs;

import java.util.ArrayList;
import java.util.List;

import j.array.JArray;
import j.array.Shapes;
import j.eval.Assembly;
import j.eval.Rank;

/** {@code u\\}: apply {@code u} to each prefix along the leading axis. */
public final class Prefix extends Verb {
    private final Verb operand;

    public Prefix(Verb operand) {
        super(operand.repr() + "\\");
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
            int[] resultShape = Shapes.concat(new int[]{0}, y.itemShape());
            return y.isSparse()
                    ? JArray.sparse(y.type(), resultShape, y.sparseFill(),
                            new int[0], new Object[0])
                    : JArray.empty(y.type(), resultShape);
        }

        List<JArray> results = new ArrayList<>(items);
        for (int length = 1; length <= items; length++) {
            results.add(Rank.applyMonad(operand, y.takeItems(length)));
        }
        return Assembly.stack(new int[]{items}, results);
    }
}
