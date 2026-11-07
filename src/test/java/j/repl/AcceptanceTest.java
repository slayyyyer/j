package j.repl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import j.JError;

class AcceptanceTest {
    private final Session session = new Session();

    private String evaluate(String line) {
        return session.evaluate(line).orElseThrow();
    }

    @Test
    void allProjectBriefExamplesWork() {
        assertEquals("14", evaluate("2 * 3 + 4"));
        assertEquals("0 1 2 3 4", evaluate("i. 5"));
        assertEquals("10", evaluate("+/ i. 5"));
        assertEquals("0 1  2  3\n4 5  6  7\n8 9 10 11", evaluate("i. 3 4"));
        assertEquals("6 22 38", evaluate("+/\"1 i. 3 4"));
        assertEquals("12 15 18 21", evaluate("+/ i. 3 4"));
        assertEquals("2.5", evaluate("(+/ % #) 1 2 3 4"));
        assertEquals("3 2 1", evaluate("|. 1 2 3"));
        assertEquals("1 2 3\n4 5 6", evaluate("2 3 $ 1 2 3 4 5 6"));
        assertEquals("11 12 13", evaluate("1 2 3 + 10"));

        JError mismatch = assertThrows(JError.class, () -> session.evaluate("1 2 3 + 1 2"));
        assertEquals("length error", mismatch.getMessage());

        assertTrue(session.evaluate("avg =: +/ % #").isEmpty());
        assertEquals("2.5", evaluate("avg 1 2 3 4"));
    }
}
