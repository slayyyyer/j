package j.verbs;

import java.util.ArrayList;
import java.util.List;

import j.JError;
import j.array.JArray;
import j.array.JType;
import j.array.Shapes;
import j.eval.Assembly;

/** {@code {}: monadic catalog is deferred; dyadic index selects items. */
public final class From extends Verb {
    public From() {
        super("{");
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
    public JArray dyadCell(JArray x, JArray y) {
        if (x.type() != JType.INT) {
            throw new JError("domain error");
        }

        int items = y.itemCount();
        List<JArray> selected = new ArrayList<>(x.size());
        for (int atom = 0; atom < x.size(); atom++) {
            long index = x.longAt(atom);
            if (index < -items || index >= items) {
                throw new JError("index error");
            }
            int resolved = (int) (index < 0 ? items + index : index);
            selected.add(y.item(resolved));
        }

        int[] resultShape = Shapes.concat(x.shape(), y.itemShape());
        if (selected.isEmpty()) {
            return JArray.empty(y.type(), resultShape);
        }
        return Assembly.stack(x.shape(), selected);
    }
}
