package j.verbs;

import java.util.ArrayList;
import java.util.List;

import j.JError;
import j.array.JArray;
import j.array.JType;
import j.array.Shapes;
import j.eval.Assembly;

/** {@code {}: monadic catalog, dyadic index. */
public final class From extends Verb {
    public From() {
        super("{");
    }

    @Override
    public int monadRank() {
        return 1;
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
        if (y.type() != JType.BOX) {
            throw new JError("domain error");
        }
        if (y.rank() == 0) {
            return y.boxAt(0);
        }
        if (y.rank() != 1) {
            throw new JError("rank error");
        }
        return catalog(y);
    }

    @Override
    public JArray dyadCell(JArray x, JArray y) {
        if (!x.type().isExact()) {
            throw new JError("domain error");
        }

        int items = y.itemCount();
        List<JArray> selected = new ArrayList<>(x.size());
        for (int atom = 0; atom < x.size(); atom++) {
            long index = Numeric.exactLongAt(x, atom);
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

    private static JArray catalog(JArray boxes) {
        int columns = boxes.size();
        if (columns == 0) {
            return JArray.empty(JType.INT, 1, 0);
        }

        List<JArray> contents = new ArrayList<>(columns);
        JType type = null;
        int combinations = 1;
        for (int box = 0; box < columns; box++) {
            JArray content = boxes.boxAt(box).ravel();
            contents.add(content);
            try {
                type = type == null ? content.type() : JArray.promote(type, content.type());
            } catch (IllegalArgumentException mixed) {
                throw new JError("domain error");
            }
            if (content.isEmpty()) {
                combinations = 0;
            } else if (combinations != 0) {
                long product = (long) combinations * content.size();
                if (product > Integer.MAX_VALUE) {
                    throw new JError("limit error");
                }
                combinations = (int) product;
            }
        }

        int[] shape = new int[]{combinations, columns};
        int atoms = Shapes.size(shape);
        Object[] result = new Object[atoms];
        for (int combination = 0; combination < combinations; combination++) {
            int remaining = combination;
            for (int column = columns - 1; column >= 0; column--) {
                JArray content = contents.get(column);
                int index = remaining % content.size();
                remaining /= content.size();
                Object atom = content.atomAt(index);
                try {
                    result[combination * columns + column] =
                            JArray.convertAtom(atom, type);
                } catch (IllegalArgumentException conversion) {
                    throw new JError("domain error");
                }
            }
        }
        return JArray.of(type, shape, result);
    }
}
