package j.verbs;

import java.util.ArrayList;
import java.util.List;

import j.array.JArray;
import j.array.JType;
import j.eval.Assembly;

/** {@code >}: monadic open, dyadic greater-than. */
public final class Greater extends Verb {
    public Greater() {
        super(">");
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
        if (y.type() != JType.BOX) {
            return y;
        }
        if (y.rank() == 0) {
            return y.boxAt(0);
        }
        if (y.isEmpty()) {
            return JArray.empty(JType.INT, y.shape());
        }

        List<JArray> contents = new ArrayList<>(y.size());
        for (int atom = 0; atom < y.size(); atom++) {
            contents.add(y.boxAt(atom));
        }
        return Assembly.stack(y.shape(), contents);
    }

    @Override
    public JArray dyadCell(JArray x, JArray y) {
        return Comparison.test(Comparison.Kind.GREATER, x, y);
    }

    @Override
    public JArray identity(JType type) {
        return Numeric.zero(type);
    }
}
