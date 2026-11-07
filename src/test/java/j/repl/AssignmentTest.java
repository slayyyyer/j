package j.repl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import j.JError;

class AssignmentTest {
    private final Session session = new Session();

    private String evaluate(String line) {
        return session.evaluate(line).orElseThrow();
    }

    @Test
    void assignsNounsAndSuppressesOutput() {
        assertTrue(session.evaluate("a =: 2 + 3").isEmpty());
        assertEquals("5", evaluate("a"));
    }

    @Test
    void assignsVerbValues() {
        assertTrue(session.evaluate("sum =: +/").isEmpty());
        assertEquals("10", evaluate("sum i. 5"));

        assertTrue(session.evaluate("inc =: >:").isEmpty());
        assertEquals("2 3 4", evaluate("inc 1 2 3"));
    }

    @Test
    void assignsTrains() {
        assertTrue(session.evaluate("avg =: +/ % #").isEmpty());
        assertEquals("2.5", evaluate("avg 1 2 3 4"));
    }

    @Test
    void chainedAndLocalAssignmentsWork() {
        assertTrue(session.evaluate("b =: a =: 7").isEmpty());
        assertEquals("7", evaluate("a"));
        assertEquals("7", evaluate("b"));

        assertTrue(session.evaluate("c =. 4").isEmpty());
        assertEquals("4", evaluate("c"));
    }

    @Test
    void failedRightHandSideDoesNotBindTheName() {
        assertThrows(JError.class, () -> session.evaluate("bad =: missing"));
        assertEquals("value error",
                assertThrows(JError.class, () -> session.evaluate("bad")).getMessage());
    }

    @Test
    void inlineExplicitVerbsBindXAndY() {
        assertTrue(session.evaluate("inc =: {{ y + 1 }}").isEmpty());
        assertEquals("3", evaluate("inc 2"));

        assertTrue(session.evaluate("times =: {{ x * y }}").isEmpty());
        assertEquals("6", evaluate("2 times 3"));

        assertTrue(session.evaluate("total =: {{ +/ y }}").isEmpty());
        assertEquals("6", evaluate("total 1 2 3"));
    }

    @Test
    void multilineExplicitDefinitionsWork() {
        session.defineExplicit("f =: 3 : 0", java.util.List.of("y + 1"));
        assertEquals("3", evaluate("f 2"));
    }

    @Test
    void explicitBodiesCanBindLocalsAndGlobals() {
        session.defineExplicit(
                "f =: 3 : 0",
                java.util.List.of("a =. y + 1", "a * 2"));
        assertEquals("8", evaluate("f 3"));
        assertEquals("value error",
                assertThrows(JError.class, () -> session.evaluate("a")).getMessage());

        session.defineExplicit(
                "g =: 3 : 0",
                java.util.List.of("gv =: y", "y"));
        assertEquals("7", evaluate("g 7"));
        assertEquals("7", evaluate("gv"));
    }

    @Test
    void explicitBodiesSupportIfElseWhileForAndReturn() {
        session.defineExplicit(
                "sign =: 3 : 0",
                java.util.List.of(
                        "if. y > 0 do.",
                        "  y * 2",
                        "else.",
                        "  y - 1",
                        "end."));
        assertEquals("6", evaluate("sign 3"));
        assertEquals("_3", evaluate("sign _2"));

        session.defineExplicit(
                "loop =: 3 : 0",
                java.util.List.of(
                        "a =. 0",
                        "while. a < y do.",
                        "  a =. a + 1",
                        "end.",
                        "a"));
        assertEquals("4", evaluate("loop 4"));

        session.defineExplicit(
                "sumto =: 3 : 0",
                java.util.List.of(
                        "total =. 0",
                        "for_i. i. y do.",
                        "  total =. total + i",
                        "end.",
                        "total"));
        assertEquals("10", evaluate("sumto 5"));

        session.defineExplicit(
                "early =: 3 : 0",
                java.util.List.of(
                        "if. y > 0 do.",
                        "  return. y * 2",
                        "end.",
                        "0"));
        assertEquals("6", evaluate("early 3"));
        assertEquals("0", evaluate("early _1"));
    }

    @Test
    void multilineDirectDefinitionsWork() {
        session.defineDirect("f =: {{", java.util.List.of("a =. y + 1", "a * 2"));
        assertEquals("8", evaluate("f 3"));

        session.defineDirect(
                "g =: {{",
                java.util.List.of(
                        "if. y > 0 do.",
                        "  return. y * 2",
                        "end.",
                        "0"));
        assertEquals("6", evaluate("g 3"));
        assertEquals("0", evaluate("g _1"));
    }

    @Test
    void assignmentsInsideParenthesesProduceValuesAndBindNames() {
        assertEquals("5", evaluate("(a =. 3) + 2"));
        assertEquals("3", evaluate("a"));

        assertEquals("5", evaluate("(b =: 4) + 1"));
        assertEquals("4", evaluate("b"));
    }

    @Test
    void reassignmentChangesThePartOfSpeech() {
        session.evaluate("f =: +");
        assertEquals("5", evaluate("2 f 3"));

        session.evaluate("f =: 2");
        assertEquals("2", evaluate("f"));
    }
}
