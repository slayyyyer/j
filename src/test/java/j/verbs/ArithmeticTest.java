package j.verbs;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import j.JError;
import j.array.JArray;
import j.array.JFormat;
import j.array.JType;
import j.eval.Environment;
import j.eval.Evaluator;
import j.eval.Value;
import j.parse.Parser;
import j.parse.Phrase;

class ArithmeticTest {
    private static JArray eval(String source) {
        Environment environment = new Environment();
        Phrase phrase = Parser.parse(source, environment);
        Value value = new Evaluator(environment).evaluate(phrase);
        return assertInstanceOf(Value.Noun.class, value).array();
    }

    @Test
    void multipliesWithPromotionAndScalarExtension() {
        JArray integers = eval("2 * 3");
        assertEquals(JType.INT, integers.type());
        assertEquals(6L, integers.longAt(0));

        JArray floats = eval("2 * 3.5");
        assertEquals(JType.FLOAT, floats.type());
        assertEquals(7.0, floats.doubleAt(0));

        assertEquals("10 20 30", JFormat.format(eval("1 2 3 * 10")));
        assertEquals("10 20\n30 40", JFormat.format(eval("(1 2) (3 4) * 10")));
    }

    @Test
    void signumReturnsTheSignOfEachAtom() {
        assertEquals("_1 0 1", JFormat.format(eval("* _3 0 4")));
        assertEquals(JType.FLOAT, eval("* _.").type());
        assertTrue(Double.isNaN(eval("* _.").doubleAt(0)));
    }

    @Test
    void divisionIsAlwaysFloatingPoint() {
        JArray result = eval("4 % 2");
        assertEquals(JType.FLOAT, result.type());
        assertEquals(2.0, result.doubleAt(0));

        assertEquals("0.5 1 1.5", JFormat.format(eval("1 2 3 % 2")));
        assertEquals("2 1 0.5", JFormat.format(eval("2 % 1 2 4")));
        assertEquals("0.25", JFormat.format(eval("% 4")));
    }

    @Test
    void divisionByZeroProducesInfinitiesAndNan() {
        assertEquals("_", JFormat.format(eval("1 % 0")));
        assertEquals("__", JFormat.format(eval("_1 % 0")));
        assertEquals("_.", JFormat.format(eval("0 % 0")));
        assertEquals("_", JFormat.format(eval("% 0")));

        assertEquals(Double.POSITIVE_INFINITY, eval("1 % 0").doubleAt(0));
        assertTrue(Double.isNaN(eval("0 % 0").doubleAt(0)));
    }

    @Test
    void powerKeepsIntegerResultsWhenPossible() {
        JArray result = eval("2 ^ 10");
        assertEquals(JType.INT, result.type());
        assertEquals(1024L, result.longAt(0));

        assertEquals(-8L, eval("_2 ^ 3").longAt(0));
        assertEquals(1L, eval("0 ^ 0").longAt(0));
        assertEquals("0.5", JFormat.format(eval("2 ^ _1")));
        assertEquals("0.25", JFormat.format(eval("2 ^ _2")));
        assertEquals(JType.FLOAT, eval("2 ^ 3.0").type());
    }

    @Test
    void exponentialIsFloatingPoint() {
        assertEquals("1 2.7183", JFormat.format(eval("^ 0 1")));
        assertEquals("1.4142", JFormat.format(eval("2 ^ 0.5")));
    }

    @Test
    void reportsIntegerOverflow() {
        assertEquals("limit error",
                assertThrows(JError.class, () -> eval("2 * 4611686018427387904")).getMessage());
        assertEquals("limit error",
                assertThrows(JError.class, () -> eval("2 ^ 63")).getMessage());
        assertEquals(4611686018427387904L, eval("2 ^ 62").longAt(0));
    }

    @Test
    void characterArithmeticIsADomainError() {
        assertEquals("domain error",
                assertThrows(JError.class, () -> eval("* 'a'")).getMessage());
        assertEquals("domain error",
                assertThrows(JError.class, () -> eval("2 % 'a'")).getMessage());
        assertEquals("domain error",
                assertThrows(JError.class, () -> eval("^ 'a'")).getMessage());
    }
}
