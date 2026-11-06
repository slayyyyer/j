package j.array;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class JFormatTest {
    @Test
    void formatsIntegerScalar() {
        assertEquals("42", JFormat.format(JArray.scalar(42)));
        assertEquals("_7", JFormat.format(JArray.scalar(-7)));
    }

    @Test
    void formatsFloatScalar() {
        assertEquals("2.5", JFormat.format(JArray.scalar(2.5)));
        assertEquals("_0.25", JFormat.format(JArray.scalar(-0.25)));
    }

    @Test
    void integralFloatsPrintWithoutADecimalPoint() {
        assertEquals("2", JFormat.format(JArray.scalar(2.0)));
    }

    @Test
    void floatsUseFiveSignificantDigits() {
        assertEquals("0.33333", JFormat.format(JArray.scalar(1.0 / 3.0)));
        assertEquals("0.66667", JFormat.format(JArray.scalar(2.0 / 3.0)));
        assertEquals("1.2346e6", JFormat.format(JArray.scalar(1_234_567.0)));
    }

    @Test
    void usesJNotationForSpecialFloats() {
        assertEquals("_", JFormat.format(JArray.scalar(Double.POSITIVE_INFINITY)));
        assertEquals("__", JFormat.format(JArray.scalar(Double.NEGATIVE_INFINITY)));
        assertEquals("_.", JFormat.format(JArray.scalar(Double.NaN)));
    }

    @Test
    void choosesPlainAndPowerNotationAtJThresholds() {
        assertEquals("100000", JFormat.format(JArray.scalar(1e5)));
        assertEquals("1e6", JFormat.format(JArray.scalar(1e6)));
        assertEquals("0.0001", JFormat.format(JArray.scalar(1e-4)));
        assertEquals("1e_5", JFormat.format(JArray.scalar(1e-5)));
        assertEquals("_1.5e_7", JFormat.format(JArray.scalar(-1.5e-7)));
    }

    @Test
    void formatsIntegerVector() {
        assertEquals("0 1 2 3 4", JFormat.format(JArray.ints(0, 1, 2, 3, 4)));
        assertEquals("_1 0 1", JFormat.format(JArray.ints(-1, 0, 1)));
    }

    @Test
    void formatsMatrixWithAlignedColumns() {
        JArray matrix = JArray.ints(
                new int[]{3, 4},
                0, 1, 2, 3,
                4, 5, 6, 7,
                8, 9, 10, 11);

        assertEquals("0 1  2  3\n4 5  6  7\n8 9 10 11", JFormat.format(matrix));
    }

    @Test
    void formatsSimpleMatrix() {
        JArray matrix = JArray.ints(new int[]{2, 3}, 1, 2, 3, 4, 5, 6);

        assertEquals("1 2 3\n4 5 6", JFormat.format(matrix));
    }

    @Test
    void separatesPlanesOfRankThreeArrays() {
        JArray cube = JArray.ints(new int[]{2, 2, 2}, 0, 1, 2, 3, 4, 5, 6, 7);

        assertEquals("0 1\n2 3\n\n4 5\n6 7", JFormat.format(cube));
    }

    @Test
    void formatsCharacterVectorAndScalar() {
        assertEquals("abc", JFormat.format(JArray.chars("abc")));
        assertEquals("a", JFormat.format(JArray.scalar('a')));
    }

    @Test
    void formatsCharacterMatrixAsRows() {
        JArray matrix = JArray.chars(
                new int[]{2, 3},
                'a', 'b', 'c',
                'd', 'e', 'f');

        assertEquals("abc\ndef", JFormat.format(matrix));
    }

    @Test
    void emptyArraysFormatAsNothing() {
        assertEquals("", JFormat.format(JArray.ints(new int[]{0})));
        assertEquals("", JFormat.format(JArray.chars("")));
        assertEquals("", JFormat.format(JArray.ints(new int[]{0, 3})));
    }

    @Test
    void toStringUsesTheSameDisplayFormat() {
        assertEquals("1 2 3", JArray.ints(1, 2, 3).toString());
    }
}
