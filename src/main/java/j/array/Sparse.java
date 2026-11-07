package j.array;

import java.util.Arrays;

/**
 * Sparse storage: a fill atom plus sorted flat indices and their values.
 * Indices refer to the row-major positions of the enclosing {@link JArray}.
 */
public final class Sparse {
    private final Object fill;
    private final int[] indices;
    private final Object[] values;

    public Sparse(Object fill, int[] indices, Object[] values) {
        if (fill == null) {
            throw new IllegalArgumentException("a sparse fill must not be null");
        }
        if (indices.length != values.length) {
            throw new IllegalArgumentException("sparse indices and values must have equal length");
        }
        this.fill = fill;
        this.indices = indices.clone();
        this.values = values.clone();
        for (int entry = 0; entry < indices.length; entry++) {
            if (indices[entry] < 0) {
                throw new IllegalArgumentException("sparse index must be non-negative");
            }
            if (entry > 0 && indices[entry] <= indices[entry - 1]) {
                throw new IllegalArgumentException("sparse indices must be strictly increasing");
            }
            if (values[entry] == null) {
                throw new IllegalArgumentException("a sparse value must not be null");
            }
        }
    }

    public Object fill() {
        return fill;
    }

    public int[] indices() {
        return indices.clone();
    }

    public Object[] values() {
        return values.clone();
    }

    public int count() {
        return indices.length;
    }

    /** The stored atom at a flat index, or the fill atom when absent. */
    public Object atomAt(int index) {
        int found = Arrays.binarySearch(indices, index);
        return found >= 0 ? values[found] : fill;
    }
}
