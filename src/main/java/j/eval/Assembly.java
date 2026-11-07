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
            type = promote(type, cell.type());
        }

        int[] resultShape = Shapes.concat(frameShape, cellShape);
        int total = Shapes.size(resultShape);
        int cellSize = Shapes.size(cellShape);

        return switch (type) {
            case INT -> {
                long[] data = new long[total];
                int at = 0;
                for (JArray cell : cells) {
                    for (int atom = 0; atom < cellSize; atom++) {
                        data[at++] = cell.longAt(atom);
                    }
                }
                yield JArray.ints(resultShape, data);
            }
            case FLOAT -> {
                double[] data = new double[total];
                int at = 0;
                for (JArray cell : cells) {
                    for (int atom = 0; atom < cellSize; atom++) {
                        data[at++] = cell.numericAt(atom);
                    }
                }
                yield JArray.floats(resultShape, data);
            }
            case CHAR -> {
                char[] data = new char[total];
                int at = 0;
                for (JArray cell : cells) {
                    for (int atom = 0; atom < cellSize; atom++) {
                        data[at++] = cell.charAt(atom);
                    }
                }
                yield JArray.chars(resultShape, data);
            }
        };
    }

    private static JType promote(JType left, JType right) {
        if (left == JType.CHAR || right == JType.CHAR) {
            if (left == right) {
                return JType.CHAR;
            }
            throw new JError("domain error");
        }
        if (left == JType.FLOAT || right == JType.FLOAT) {
            return JType.FLOAT;
        }
        return JType.INT;
    }
}
