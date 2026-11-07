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
        assertEquals(
                " 1 76  1 54\n 1  1 68 68\n94  1 52 84",
                JFormat.format(eval("1 + $. 3 4 $ 0 75 0 53 0 0 67 67 93 0 51 83")));
    }
}
