package j.array;

import java.util.Arrays;

/** Shape arithmetic for row-major J arrays. */
public final class Shapes {
    private Shapes() {
    }

    /**
     * The number of atoms in an array of the given shape.
     *
     * <p>A scalar has the empty shape {@code []} and therefore one atom.
     */
    public static int size(int[] shape) {
        long size = 1;
        for (int dimension : shape) {
            if (dimension < 0) {
                throw new IllegalArgumentException("negative dimension in shape " + format(shape));
            }
            size *= dimension;
            if (size > Integer.MAX_VALUE) {
                throw new IllegalArgumentException("array is too large: shape " + format(shape));
            }
        }
        return (int) size;
    }

    /** Row-major strides for a shape; the last axis has stride 1. */
    public static int[] strides(int[] shape) {
        int[] strides = new int[shape.length];
        int stride = 1;
        for (int axis = shape.length - 1; axis >= 0; axis--) {
            strides[axis] = stride;
            stride *= shape[axis];
        }
        return strides;
    }

    /** The shape formed by appending one shape to another. */
    public static int[] concat(int[] first, int[] second) {
        int[] result = Arrays.copyOf(first, first.length + second.length);
        System.arraycopy(second, 0, result, first.length, second.length);
        return result;
    }

    /** J's spelling of a shape, e.g. {@code 3 4}; a scalar's shape formats as "". */
    public static String format(int[] shape) {
        StringBuilder text = new StringBuilder();
        for (int axis = 0; axis < shape.length; axis++) {
            if (axis > 0) {
                text.append(' ');
            }
            text.append(shape[axis]);
        }
        return text.toString();
    }
}
