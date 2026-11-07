package j.verbs;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import j.array.JArray;
import j.array.JFormat;
import j.eval.Environment;
import j.eval.Evaluator;
import j.eval.Value;
import j.parse.Parser;
import j.parse.Phrase;

class SparseTest {
    private static JArray eval(String source) {
        Environment environment = new Environment();
        Phrase phrase = Parser.parse(source, environment);
        Value value = new Evaluator(environment).evaluate(phrase);
        return assertInstanceOf(Value.Noun.class, value).array();
    }

    @Test
    void convertsDenseArraysToSparseAndDisplaysIndexValueRows() {
        JArray sparse = eval("$. 3 4 $ 0 75 0 53 0 0 67 67 93 0 51 83");

        assertTrue(sparse.isSparse());
        assertEquals(7, sparse.sparseCount());
        assertEquals(
                "0 1 | 75\n"
                        + "0 3 | 53\n"
                        + "1 2 | 67\n"
                        + "1 3 | 67\n"
                        + "2 0 | 93\n"
                        + "2 2 | 51\n"
                        + "2 3 | 83",
                JFormat.format(sparse));
    }

    @Test
    void sparseVectorsDisplayTheirFlatIndices() {
        JArray sparse = eval("$. 0 5 0 2 0");

        assertTrue(sparse.isSparse());
        assertEquals("1 | 5\n3 | 2", JFormat.format(sparse));
    }

    @Test
    void zeroSparseTogglesBackToDense() {
        JArray dense = eval("0 $. $. 3 4 $ 0 75 0 53 0 0 67 67 93 0 51 83");

        assertFalse(dense.isSparse());
        assertEquals(" 0 75  0 53\n 0  0 67 67\n93  0 51 83", JFormat.format(dense));
    }

    @Test
    void sparseShapeTallyAndArithmeticUseTheFillValue() {
        assertEquals("3 4", JFormat.format(eval("$ $. 3 4 $ 0 75 0 53 0 0 67 67 93 0 51 83")));
        assertEquals("3", JFormat.format(eval("# $. 3 4 $ 0 75 0 53 0 0 67 67 93 0 51 83")));
        JArray sum = eval("1 + $. 3 4 $ 0 75 0 53 0 0 67 67 93 0 51 83");
        assertTrue(sum.isSparse());
        assertEquals(
                "0 1 | 76\n"
                        + "0 3 | 54\n"
                        + "1 2 | 68\n"
                        + "1 3 | 68\n"
                        + "2 0 | 94\n"
                        + "2 2 | 52\n"
                        + "2 3 | 84",
                JFormat.format(sum));
    }

    @Test
    void insertAndPrefixPreserveSparseStorage() {
        JArray reduced = eval("+/ $. 0 5 0 2 0");
        assertTrue(reduced.isSparse());
        assertEquals("7", JFormat.format(reduced));

        JArray prefixed = eval("+/\\ $. 0 5 0 2 0");
        assertTrue(prefixed.isSparse());
        assertEquals("1 | 5\n2 | 5\n3 | 7\n4 | 7", JFormat.format(prefixed));

        JArray matrixReduced = eval("+/ $. 3 4 $ 0 75 0 53 0 0 67 67 93 0 51 83");
        assertTrue(matrixReduced.isSparse());
        assertEquals(
                "0 |  93\n1 |  75\n2 | 118\n3 | 203",
                JFormat.format(matrixReduced));
    }

    @Test
    void arithmeticPreservesSparseStorage() {
        JArray negated = eval("- $. 3 4 $ 0 75 0 53 0 0 67 67 93 0 51 83");

        assertTrue(negated.isSparse());
        assertEquals(
                "0 1 | _75\n"
                        + "0 3 | _53\n"
                        + "1 2 | _67\n"
                        + "1 3 | _67\n"
                        + "2 0 | _93\n"
                        + "2 2 | _51\n"
                        + "2 3 | _83",
                JFormat.format(negated));

        JArray doubled = eval(
                "($. 3 4 $ 0 75 0 53 0 0 67 67 93 0 51 83) + "
                        + "($. 3 4 $ 0 75 0 53 0 0 67 67 93 0 51 83)");
        assertTrue(doubled.isSparse());
        assertEquals(
                "0 1 | 150\n"
                        + "0 3 | 106\n"
                        + "1 2 | 134\n"
                        + "1 3 | 134\n"
                        + "2 0 | 186\n"
                        + "2 2 | 102\n"
                        + "2 3 | 166",
                JFormat.format(doubled));
    }

    @Test
    void structuralOperationsPreserveSparseStorage() {
        JArray reversed = eval("|. $. 3 4 $ 0 75 0 53 0 0 67 67 93 0 51 83");
        assertTrue(reversed.isSparse());
        assertEquals(
                "0 0 | 93\n"
                        + "0 2 | 51\n"
                        + "0 3 | 83\n"
                        + "1 2 | 67\n"
                        + "1 3 | 67\n"
                        + "2 1 | 75\n"
                        + "2 3 | 53",
                JFormat.format(reversed));

        JArray transposed = eval("|: $. 3 4 $ 0 75 0 53 0 0 67 67 93 0 51 83");
        assertTrue(transposed.isSparse());
        assertEquals(
                "0 2 | 93\n"
                        + "1 0 | 75\n"
                        + "2 1 | 67\n"
                        + "2 2 | 51\n"
                        + "3 0 | 53\n"
                        + "3 1 | 67\n"
                        + "3 2 | 83",
                JFormat.format(transposed));

        JArray taken = eval("2 {. $. 3 4 $ 0 75 0 53 0 0 67 67 93 0 51 83");
        assertTrue(taken.isSparse());
        assertEquals(
                "0 1 | 75\n0 3 | 53\n1 2 | 67\n1 3 | 67",
                JFormat.format(taken));

        JArray copied = eval("2 # $. 3 4 $ 0 75 0 53 0 0 67 67 93 0 51 83");
        assertTrue(copied.isSparse());
        assertEquals(
                "0 1 | 75\n"
                        + "0 3 | 53\n"
                        + "1 1 | 75\n"
                        + "1 3 | 53\n"
                        + "2 2 | 67\n"
                        + "2 3 | 67\n"
                        + "3 2 | 67\n"
                        + "3 3 | 67\n"
                        + "4 0 | 93\n"
                        + "4 2 | 51\n"
                        + "4 3 | 83\n"
                        + "5 0 | 93\n"
                        + "5 2 | 51\n"
                        + "5 3 | 83",
                JFormat.format(copied));

        JArray dropped = eval("1 }. $. 3 4 $ 0 75 0 53 0 0 67 67 93 0 51 83");
        assertTrue(dropped.isSparse());
        assertEquals(
                "0 2 | 67\n"
                        + "0 3 | 67\n"
                        + "1 0 | 93\n"
                        + "1 2 | 51\n"
                        + "1 3 | 83",
                JFormat.format(dropped));

        JArray raveled = eval(", $. 3 4 $ 0 75 0 53 0 0 67 67 93 0 51 83");
        assertTrue(raveled.isSparse());
        assertEquals(
                " 1 | 75\n"
                        + " 3 | 53\n"
                        + " 6 | 67\n"
                        + " 7 | 67\n"
                        + " 8 | 93\n"
                        + "10 | 51\n"
                        + "11 | 83",
                JFormat.format(raveled));

        JArray takeAxes = eval("2 3 {. $. 3 4 $ 0 75 0 53 0 0 67 67 93 0 51 83");
        assertTrue(takeAxes.isSparse());
        assertEquals("0 1 | 75\n1 2 | 67", JFormat.format(takeAxes));

        JArray dropAxes = eval("1 1 }. $. 3 4 $ 0 75 0 53 0 0 67 67 93 0 51 83");
        assertTrue(dropAxes.isSparse());
        assertEquals(
                "0 1 | 67\n0 2 | 67\n1 1 | 51\n1 2 | 83",
                JFormat.format(dropAxes));

        JArray reshaped = eval("2 4 $ , $. 3 4 $ 0 75 0 53 0 0 67 67 93 0 51 83");
        assertTrue(reshaped.isSparse());
        assertEquals(
                "0 1 | 75\n0 3 | 53\n1 2 | 67\n1 3 | 67",
                JFormat.format(reshaped));

        JArray appended = eval(
                "($. 3 4 $ 0 75 0 53 0 0 67 67 93 0 51 83) , "
                        + "($. 3 4 $ 0 75 0 53 0 0 67 67 93 0 51 83)");
        assertTrue(appended.isSparse());
        assertEquals(
                "0 1 | 75\n"
                        + "0 3 | 53\n"
                        + "1 2 | 67\n"
                        + "1 3 | 67\n"
                        + "2 0 | 93\n"
                        + "2 2 | 51\n"
                        + "2 3 | 83\n"
                        + "3 1 | 75\n"
                        + "3 3 | 53\n"
                        + "4 2 | 67\n"
                        + "4 3 | 67\n"
                        + "5 0 | 93\n"
                        + "5 2 | 51\n"
                        + "5 3 | 83",
                JFormat.format(appended));

        JArray appendedDense = eval(
                "($. 3 4 $ 0 75 0 53 0 0 67 67 93 0 51 83) , "
                        + "(0 $. $. 3 4 $ 0 75 0 53 0 0 67 67 93 0 51 83)");
        assertTrue(appendedDense.isSparse());
        assertEquals(JFormat.format(appended), JFormat.format(appendedDense));
    }
}
