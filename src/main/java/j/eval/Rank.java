package j.eval;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

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
        int size = Shapes.size(shape);
        return switch (type) {
            case INT -> JArray.ints(shape, new long[size]);
            case FLOAT -> JArray.floats(shape, new double[size]);
            case CHAR -> JArray.chars(shape, new char[size]);
        };
    }

    private static JArray emptyResult(JArray probe, int[] frameShape) {
        return JArray.empty(probe.type(), Shapes.concat(frameShape, probe.shape()));
    }
}
