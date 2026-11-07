package j.eval;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;

import j.JError;
import j.array.JArray;
import j.array.JType;
import j.array.Shapes;
import j.verbs.Verb;

/**
 * Applies a verb's cell function across argument frames.
 *
 * <p>A verb with rank {@code r} sees cells of at most rank {@code r}; leading axes
 * form the frame and are iterated. A rank-0 verb such as {@code +} therefore sees
 * atoms, so scalar extension falls out of frame agreement instead of being
 * special-cased in the verb.
 */
public final class Rank {
    private Rank() {
    }

    public static JArray applyMonad(Verb verb, JArray y) {
        if (y.isSparse() && verb.monadRank() == 0) {
            JArray sparseResult = applySparseMonad(verb, y);
            if (sparseResult != null) {
                return sparseResult;
            }
        }
        int cellRank = Math.min(resolveRank(verb.monadRank(), y.rank()), y.rank());
        int[] shape = y.shape();
        int[] frameShape = Arrays.copyOf(shape, shape.length - cellRank);
        int[] cellShape = Arrays.copyOfRange(shape, frameShape.length, shape.length);
        int frameSize = Shapes.size(frameShape);

        if (frameSize == 0) {
            JArray probe = verb.monadCell(prototype(y.type(), cellShape));
            return emptyResult(probe, frameShape);
        }
        if (frameShape.length == 0) {
            return verb.monadCell(y);
        }

        int cellSize = Shapes.size(cellShape);
        List<JArray> results = new ArrayList<>(frameSize);
        for (int cell = 0; cell < frameSize; cell++) {
            results.add(verb.monadCell(y.slice(cell * cellSize, cellShape)));
        }
        return Assembly.stack(frameShape, results);
    }

    public static JArray applyDyad(Verb verb, JArray x, JArray y) {
        if ((x.isSparse() || y.isSparse())
                && verb.leftRank() == 0 && verb.rightRank() == 0) {
            JArray sparseResult = applySparseDyad(verb, x, y);
            if (sparseResult != null) {
                return sparseResult;
            }
        }
        int xCellRank = Math.min(resolveRank(verb.leftRank(), x.rank()), x.rank());
        int yCellRank = Math.min(resolveRank(verb.rightRank(), y.rank()), y.rank());

        int[] xShape = x.shape();
        int[] yShape = y.shape();
        int[] xFrame = Arrays.copyOf(xShape, xShape.length - xCellRank);
        int[] yFrame = Arrays.copyOf(yShape, yShape.length - yCellRank);
        int[] xCellShape = Arrays.copyOfRange(xShape, xFrame.length, xShape.length);
        int[] yCellShape = Arrays.copyOfRange(yShape, yFrame.length, yShape.length);

        int[] frameShape = agree(xFrame, yFrame);
        int frameSize = Shapes.size(frameShape);

        if (frameSize == 0) {
            JArray probe = verb.dyadCell(
                    prototype(x.type(), xCellShape),
                    prototype(y.type(), yCellShape));
            return emptyResult(probe, frameShape);
        }
        if (frameShape.length == 0) {
            return verb.dyadCell(x, y);
        }

        int xCellSize = Shapes.size(xCellShape);
        int yCellSize = Shapes.size(yCellShape);
        boolean xBroadcast = xFrame.length == 0;
        boolean yBroadcast = yFrame.length == 0;

        List<JArray> results = new ArrayList<>(frameSize);
        for (int cell = 0; cell < frameSize; cell++) {
            JArray xCell = xBroadcast ? x : x.slice(cell * xCellSize, xCellShape);
            JArray yCell = yBroadcast ? y : y.slice(cell * yCellSize, yCellShape);
            results.add(verb.dyadCell(xCell, yCell));
        }
        return Assembly.stack(frameShape, results);
    }

    private static JArray applySparseMonad(Verb verb, JArray y) {
        if (y.rank() == 0) {
            return applySparseMonadScalar(verb, y);
        }
        try {
            JArray fillResult = verb.monadCell(scalarOf(y.type(), y.sparseFill()));
            if (fillResult.rank() != 0) {
                return null;
            }
            JType resultType = fillResult.type();
            Object resultFill = fillResult.atomAt(0);
            int[] indices = y.sparseIndices();
            Object[] values = y.sparseValues();
            Object[] resultValues = new Object[values.length];
            for (int entry = 0; entry < values.length; entry++) {
                JArray result = verb.monadCell(scalarOf(y.type(), values[entry]));
                if (result.rank() != 0 || result.type() != resultType) {
                    return null;
                }
                resultValues[entry] = result.atomAt(0);
            }
            return JArray.sparse(resultType, y.shape(), resultFill, indices, resultValues);
        } catch (JError domain) {
            return null;
        }
    }

    private static JArray applySparseDyad(Verb verb, JArray x, JArray y) {
        if (x.rank() == 0 && y.rank() == 0) {
            return applySparseDyadScalar(verb, x, y);
        }
        if (x.rank() > 0 && y.rank() > 0 && !Arrays.equals(x.shape(), y.shape())) {
            return null;
        }
        JArray xSparse = x;
        JArray ySparse = y;
        try {
            if (x.rank() > 0 && !x.isSparse()) {
                xSparse = x.toSparse();
            }
            if (y.rank() > 0 && !y.isSparse()) {
                ySparse = y.toSparse();
            }
        } catch (JError domain) {
            return null;
        }

        int[] resultShape = x.rank() == 0 ? y.shape() : x.shape();
        Object xFill = x.rank() == 0 ? x.atomAt(0) : xSparse.sparseFill();
        Object yFill = y.rank() == 0 ? y.atomAt(0) : ySparse.sparseFill();
        try {
            JArray fillResult = verb.dyadCell(
                    scalarOf(x.type(), xFill), scalarOf(y.type(), yFill));
            if (fillResult.rank() != 0) {
                return null;
            }
            JType resultType = fillResult.type();
            Object resultFill = fillResult.atomAt(0);
            int[] indices;
            if (x.rank() == 0) {
                indices = ySparse.sparseIndices();
            } else if (y.rank() == 0) {
                indices = xSparse.sparseIndices();
            } else {
                indices = union(xSparse.sparseIndices(), ySparse.sparseIndices());
            }
            Object[] resultValues = new Object[indices.length];
            for (int entry = 0; entry < indices.length; entry++) {
                Object xAtom = x.rank() == 0 ? xFill : xSparse.atomAt(indices[entry]);
                Object yAtom = y.rank() == 0 ? yFill : ySparse.atomAt(indices[entry]);
                JArray result = verb.dyadCell(scalarOf(x.type(), xAtom), scalarOf(y.type(), yAtom));
                if (result.rank() != 0 || result.type() != resultType) {
                    return null;
                }
                resultValues[entry] = result.atomAt(0);
            }
            return JArray.sparse(resultType, resultShape, resultFill, indices, resultValues);
        } catch (JError domain) {
            return null;
        }
    }

    private static JArray applySparseMonadScalar(Verb verb, JArray y) {
        JArray sparseY = sparseScalarOf(y);
        if (sparseY == null) {
            return null;
        }
        try {
            JArray fillResult = verb.monadCell(
                    scalarOf(sparseY.type(), sparseY.sparseFill()));
            JArray valueResult = verb.monadCell(
                    scalarOf(sparseY.type(), sparseY.atomAt(0)));
            if (fillResult.rank() != 0 || valueResult.rank() != 0
                    || fillResult.type() != valueResult.type()) {
                return null;
            }
            return JArray.sparse(valueResult.type(), new int[0], fillResult.atomAt(0),
                    new int[]{0}, new Object[]{valueResult.atomAt(0)});
        } catch (JError domain) {
            return null;
        }
    }

    private static JArray applySparseDyadScalar(Verb verb, JArray x, JArray y) {
        JArray sparseX = sparseScalarOf(x);
        JArray sparseY = sparseScalarOf(y);
        if (sparseX == null || sparseY == null) {
            return null;
        }
        try {
            JArray fillResult = verb.dyadCell(
                    scalarOf(sparseX.type(), sparseX.sparseFill()),
                    scalarOf(sparseY.type(), sparseY.sparseFill()));
            JArray valueResult = verb.dyadCell(
                    scalarOf(sparseX.type(), sparseX.atomAt(0)),
                    scalarOf(sparseY.type(), sparseY.atomAt(0)));
            if (fillResult.rank() != 0 || valueResult.rank() != 0
                    || fillResult.type() != valueResult.type()) {
                return null;
            }
            return JArray.sparse(valueResult.type(), new int[0], fillResult.atomAt(0),
                    new int[]{0}, new Object[]{valueResult.atomAt(0)});
        } catch (JError domain) {
            return null;
        }
    }

    private static JArray sparseScalarOf(JArray array) {
        if (array.rank() != 0) {
            return null;
        }
        if (array.isSparse()) {
            return array;
        }
        if (array.type() == JType.BOX) {
            return null;
        }
        try {
            Object fill = JArray.fillAtom(array.type()).atomAt(0);
            Object value = array.atomAt(0);
            if (Objects.equals(fill, value)) {
                return JArray.sparse(array.type(), new int[0], fill, new int[0], new Object[0]);
            }
            return JArray.sparse(array.type(), new int[0], fill,
                    new int[]{0}, new Object[]{value});
        } catch (JError domain) {
            return null;
        }
    }

    private static int[] union(int[] left, int[] right) {
        int[] joined = new int[left.length + right.length];
        int leftAt = 0;
        int rightAt = 0;
        int at = 0;
        while (leftAt < left.length && rightAt < right.length) {
            if (left[leftAt] < right[rightAt]) {
                joined[at++] = left[leftAt++];
            } else if (right[rightAt] < left[leftAt]) {
                joined[at++] = right[rightAt++];
            } else {
                joined[at++] = left[leftAt++];
                rightAt++;
            }
        }
        while (leftAt < left.length) {
            joined[at++] = left[leftAt++];
        }
        while (rightAt < right.length) {
            joined[at++] = right[rightAt++];
        }
        return Arrays.copyOf(joined, at);
    }

    private static JArray scalarOf(JType type, Object atom) {
        return JArray.of(type, new int[0], new Object[]{atom});
    }

    private static int[] agree(int[] xFrame, int[] yFrame) {
        if (xFrame.length == 0) {
            return yFrame;
        }
        if (yFrame.length == 0) {
            return xFrame;
        }
        if (!Arrays.equals(xFrame, yFrame)) {
            throw new JError("length error");
        }
        return xFrame;
    }

    /**
     * Resolves a verb rank against an argument. A negative rank asks for
     * {@code argumentRank + rank} cell axes, clamped at zero; this is J's
     * convention for ranks such as {@code _1}.
     */
    private static int resolveRank(int rank, int argumentRank) {
        if (rank >= 0) {
            return rank;
        }
        long resolved = (long) argumentRank + rank;
        return resolved < 0 ? 0 : (int) resolved;
    }

    private static JArray prototype(JType type, int[] shape) {
        return JArray.fillAtom(type).reshape(shape);
    }

    private static JArray emptyResult(JArray probe, int[] frameShape) {
        return JArray.empty(probe.type(), Shapes.concat(frameShape, probe.shape()));
    }
}
