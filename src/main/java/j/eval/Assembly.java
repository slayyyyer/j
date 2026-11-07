package j.eval;

import java.util.Arrays;
import java.util.List;

import j.JError;
import j.array.JArray;
import j.array.JType;
import j.array.Shapes;

/** Stacks equal-shaped cells into one array whose shape is frame + cell shape. */
public final class Assembly {
    private Assembly() {
    }

    public static JArray stack(int[] frameShape, List<JArray> cells) {
        if (cells.isEmpty()) {
            throw new IllegalArgumentException("cannot assemble zero cells");
        }

        JArray first = cells.get(0);
        int[] cellShape = first.shape();
        JType type = first.type();
        for (JArray cell : cells) {
            if (!Arrays.equals(cellShape, cell.shape())) {
                throw new JError("length error");
            }
            try {
                type = JArray.promote(type, cell.type());
            } catch (IllegalArgumentException domain) {
                throw new JError("domain error");
            }
        }

        int[] resultShape = Shapes.concat(frameShape, cellShape);
        int total = Shapes.size(resultShape);
        int cellSize = Shapes.size(cellShape);
        boolean allSparse = true;
        int entries = 0;
        for (JArray cell : cells) {
            if (!cell.isSparse() || cell.type() != type
                    || !JArray.atomEquals(cell.sparseFill(), first.sparseFill())) {
                allSparse = false;
                break;
            }
            entries += cell.sparseCount();
        }
        if (allSparse) {
            int[] indices = new int[entries];
            Object[] values = new Object[entries];
            int at = 0;
            int cellIndex = 0;
            for (JArray cell : cells) {
                int base = cellIndex * cellSize;
                int[] cellIndices = cell.sparseIndices();
                Object[] cellValues = cell.sparseValues();
                for (int entry = 0; entry < cellIndices.length; entry++) {
                    indices[at] = base + cellIndices[entry];
                    values[at] = cellValues[entry];
                    at++;
                }
                cellIndex++;
            }
            return JArray.sparse(type, resultShape, first.sparseFill(), indices, values);
        }
        Object[] data = new Object[total];
        int at = 0;
        for (JArray cell : cells) {
            for (int atom = 0; atom < cellSize; atom++) {
                data[at++] = JArray.convertAtom(cell.atomAt(atom), type);
            }
        }
        return JArray.of(type, resultShape, data);
    }
}
