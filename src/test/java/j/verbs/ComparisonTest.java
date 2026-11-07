package j.verbs;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;

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

class ComparisonTest {
    private static JArray eval(String source) {
        Environment environment = new Environment();
        Phrase phrase = Parser.parse(source, environment);
        Value value = new Evaluator(environment).evaluate(phrase);
        return assertInstanceOf(Value.Noun.class, value).array();
    }

    @Test
    void equalityAndInequalityProduceBooleans() {
        JArray result = eval("1 2 3 = 1 2 4");
        assertEquals(JType.INT, result.type());
        assertEquals("1 1 0", JFormat.format(result));
        assertEquals("1 0 1", JFormat.format(eval("1 2 3 ~: 2")));
    }

    @Test
    void comparesCharacters() {
        assertEquals("1 1 0", JFormat.format(eval("'abc' = 'abd'")));
        assertEquals("1", JFormat.format(eval("'a' < 'b'")));
        assertEquals("0", JFormat.format(eval("'b' <: 'a'")));
    }

    @Test
    void comparesMixedNumericTypes() {
        assertEquals("1", JFormat.format(eval("1 = 1.0")));
        assertEquals("1", JFormat.format(eval("1 < 1.5")));
        assertEquals("1", JFormat.format(eval("2 > 1.5")));
    }

    @Test
    void orderingComparisonsWorkAtomWise() {
        assertEquals("1 0 0", JFormat.format(eval("1 2 3 < 2")));
        assertEquals("0 0 1", JFormat.format(eval("1 2 3 > 2")));
        assertEquals("1 1 0", JFormat.format(eval("1 2 3 <: 2")));
        assertEquals("0 1 1", JFormat.format(eval("1 2 3 >: 2")));
    }

    @Test
    void extendsScalarsAcrossFrames() {
        assertEquals("0 1\n0 0", JFormat.format(eval("(1 2) (3 4) = 2")));
        assertEquals("1 1\n1 1", JFormat.format(eval("(1 2) (3 4) < 10")));
    }

    @Test
    void monadicLessEqualAndGreaterEqualDecrementAndIncrement() {
        assertEquals("0 1 2", JFormat.format(eval("<: 1 2 3")));
        assertEquals("2 3 4", JFormat.format(eval(">: 1 2 3")));

        JArray decremented = eval("<: 2.5");
        assertEquals(JType.FLOAT, decremented.type());
        assertEquals(1.5, decremented.doubleAt(0));

        JArray lower = eval("<: _9223372036854775808");
        assertEquals(JType.BIGINT, lower.type());
        assertEquals("_9223372036854775809", JFormat.format(lower));

        JArray upper = eval(">: 9223372036854775807");
        assertEquals(JType.BIGINT, upper.type());
        assertEquals("9223372036854775808", JFormat.format(upper));
    }

    @Test
    void reportsMixedTypeAndLengthErrors() {
        assertEquals("domain error",
                assertThrows(JError.class, () -> eval("1 = 'a'")).getMessage());
        assertEquals("length error",
                assertThrows(JError.class, () -> eval("1 2 = 1 2 3")).getMessage());
    }

    @Test
    void monadicNubSieveAndSelfClassifyWorkOnItems() {
        assertEquals("1 1 0 1", JFormat.format(eval("~: 'hook'")));
        assertEquals("1 1 0 1 0", JFormat.format(eval("~: 1 2 1 3 2")));
        assertEquals("1 0 0 0\n0 1 1 0\n0 0 0 1",
                JFormat.format(eval("= 'hook'")));
    }

    @Test
    void boxAndOpenWork() {
        JArray boxed = eval("< 1 2");
        assertEquals(JType.BOX, boxed.type());
        assertEquals("+---+\n|1 2|\n+---+", JFormat.format(boxed));
        assertEquals("1 2", JFormat.format(eval("> < 1 2")));
    }
}
