package j.verbs;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

import j.JError;
import j.array.JArray;
import j.array.JFormat;
import j.eval.Environment;
import j.eval.Evaluator;
import j.eval.Value;
import j.parse.Parser;
import j.parse.Phrase;

class DerivedTest {
    private static JArray eval(String source) {
        Environment environment = new Environment();
        Phrase phrase = Parser.parse(source, environment);
        Value value = new Evaluator(environment).evaluate(phrase);
        return assertInstanceOf(Value.Noun.class, value).array();
    }

    @Test
    void insertFoldsAlongTheLeadingAxis() {
        assertEquals("10", JFormat.format(eval("+/ i. 5")));
        assertEquals("12 15 18 21", JFormat.format(eval("+/ i. 3 4")));
        assertEquals("6", JFormat.format(eval("+/ 1 2 3")));
    }

    @Test
    void rankConjunctionAppliesToCells() {
        assertEquals("6 22 38", JFormat.format(eval("+/\"1 i. 3 4")));
        assertEquals("2.5", JFormat.format(eval("(+/ % #) 1 2 3 4")));
    }

    @Test
    void prefixProducesRunningResults() {
        assertEquals("1 3 6", JFormat.format(eval("+/\\ 1 2 3")));
    }

    @Test
    void reflexAndPassiveWork() {
        assertEquals("2 4 6", JFormat.format(eval("+~ 1 2 3")));
        assertEquals("5", JFormat.format(eval("2 +~ 3")));
    }

    @Test
    void trainsComputeHooksAndForks() {
        assertEquals("2.5", JFormat.format(eval("(+/ % #) 1 2 3 4")));
        assertEquals("4 5 6 7", JFormat.format(eval("(+/ % #) i. 3 4")));
    }

    @Test
    void derivedVerbsCanBeValues() {
        Environment environment = new Environment();
        Value value = new Evaluator(environment).evaluate(Parser.parse("+/", environment));

        assertEquals("+/", assertInstanceOf(Value.VerbValue.class, value).verb().repr());
    }

    @Test
    void insertNeedsANonEmptyArgument() {
        assertEquals("domain error",
                assertThrows(JError.class, () -> eval("+/ i. 0")).getMessage());
    }
}
