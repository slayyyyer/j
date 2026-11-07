package j.array;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class JArrayTest {
    @Test
    void scalarHasRankZeroAndOneAtom() {
        JArray scalar = JArray.scalar(42);

        assertEquals(JType.INT, scalar.type());
        assertEquals(0, scalar.rank());
        assertEquals(1, scalar.size());
        assertTrue(scalar.isScalar());
        assertArrayEquals(new int[0], scalar.shape());
        assertEquals(42, scalar.longAt(0));
    }

    @Test
    void intVectorKeepsShapeAndValues() {
        JArray vector = JArray.ints(10, 20, 30);

        assertEquals(1, vector.rank());
        assertEquals(3, vector.size());
        assertArrayEquals(new int[]{3}, vector.shape());
        assertEquals(10, vector.longAt(0));
        assertEquals(30, vector.longAt(2));
        assertFalse(vector.isEmpty());
    }

    @Test
    void matrixIsStoredRowMajor() {
        JArray matrix = JArray.ints(new int[]{2, 3}, 1, 2, 3, 4, 5, 6);

        assertEquals(2, matrix.rank());
        assertEquals(6, matrix.size());
        assertArrayEquals(new int[]{2, 3}, matrix.shape());
        assertEquals(4, matrix.longAt(3));
        assertEquals(6, matrix.longAt(5));
        assertArrayEquals(new int[]{3, 1}, matrix.strides());
    }

    @Test
    void characterArrayFromString() {
        JArray text = JArray.chars("J!");

        assertEquals(JType.CHAR, text.type());
        assertArrayEquals(new int[]{2}, text.shape());
        assertEquals('J', text.charAt(0));
        assertEquals('!', text.charAt(1));
    }

    @Test
    void emptyVectorHasZeroAtoms() {
        JArray empty = JArray.ints(new int[]{0});

        assertEquals(0, empty.size());
        assertTrue(empty.isEmpty());
        assertArrayEquals(new int[]{0}, empty.shape());
    }

    @Test
    void emptyFactoryRequiresZeroDimension() {
        assertThrows(IllegalArgumentException.class, () -> JArray.empty(JType.INT, 2, 3));
        assertThrows(IllegalArgumentException.class, () -> JArray.empty(JType.INT));
        assertEquals(0, JArray.empty(JType.CHAR, 2, 0).size());
    }

    @Test
    void shapeAndDataMustAgree() {
        IllegalArgumentException error = assertThrows(
                IllegalArgumentException.class,
                () -> JArray.ints(new int[]{2, 2}, 1, 2, 3));

        assertTrue(error.getMessage().contains("needs 4 atoms but got 3"));
    }

    @Test
    void negativeDimensionsAreRejected() {
        assertThrows(IllegalArgumentException.class, () -> JArray.ints(new int[]{-1}, new long[0]));
    }

    @Test
    void promotingIntegersProducesFloats() {
        JArray promoted = JArray.ints(new int[]{2, 2}, 1, 2, 3, 4).toFloat();

        assertEquals(JType.FLOAT, promoted.type());
        assertArrayEquals(new int[]{2, 2}, promoted.shape());
        assertEquals(1.0, promoted.doubleAt(0));
        assertEquals(4.0, promoted.doubleAt(3));
    }

    @Test
    void promotingFloatsIsAnIdentity() {
        JArray floats = JArray.floats(0.5, 1.5);

        assertSame(floats, floats.toFloat());
    }

    @Test
    void promotingCharactersIsRejected() {
        assertThrows(IllegalStateException.class, () -> JArray.chars("ab").toFloat());
    }

    @Test
    void inputsAreCopiedSoTheArrayStaysImmutable() {
        long[] values = {1, 2, 3};
        int[] shape = {3};
        JArray array = JArray.ints(shape, values);

        values[0] = 99;
        shape[0] = 99;

        assertEquals(1, array.longAt(0));
        assertArrayEquals(new int[]{3}, array.shape());
    }

    @Test
    void shapeAccessorReturnsACopy() {
        JArray array = JArray.ints(new int[]{2, 2}, 1, 2, 3, 4);

        array.shape()[0] = 99;

        assertArrayEquals(new int[]{2, 2}, array.shape());
    }

    @Test
    void numericAtWidensIntegers() {
        assertEquals(7.0, JArray.scalar(7).numericAt(0));
        assertEquals(2.5, JArray.scalar(2.5).numericAt(0));
        assertThrows(IllegalStateException.class, () -> JArray.scalar('a').numericAt(0));
    }

    @Test
    void slicesContiguousCells() {
        JArray matrix = JArray.ints(new int[]{2, 3}, 1, 2, 3, 4, 5, 6);

        assertEquals("4 5 6", JFormat.format(matrix.slice(3, new int[]{3})));
        assertEquals("1", JFormat.format(matrix.slice(0, new int[0])));
        assertThrows(IllegalArgumentException.class, () -> matrix.slice(4, new int[]{3}));
    }

    @Test
    void typedAccessorsRejectTheWrongType() {
        JArray floats = JArray.floats(1.5);

        assertThrows(IllegalStateException.class, () -> floats.longAt(0));
        assertEquals(1.5, floats.doubleAt(0));
    }
}
