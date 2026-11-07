package j.repl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import j.JError;

class SessionTest {
    private final Session session = new Session();

    private String evaluate(String line) {
        return session.evaluate(line).orElseThrow();
    }

    @Test
    void addsScalars() {
        assertEquals("5", evaluate("2 + 3"));
    }

    @Test
    void subtractsRightToLeft() {
        assertEquals("9", evaluate("10 - 3 - 2"));
    }

    @Test
    void monadAppliesToTheWholeRightExpression() {
        assertEquals("_5", evaluate("- 2 + 3"));
    }

    @Test
    void negatesAndConjugatesVectors() {
        assertEquals("_1 _2 _3", evaluate("- 1 2 3"));
        assertEquals("1 2 3", evaluate("+ 1 2 3"));
    }

    @Test
    void extendsScalarsAcrossVectors() {
        assertEquals("11 12 13", evaluate("1 2 3 + 10"));
        assertEquals("11 12 13", evaluate("10 + 1 2 3"));
    }

    @Test
    void addsEqualShapedVectors() {
        assertEquals("2 4 6", evaluate("1 2 3 + 1 2 3"));
    }

    @Test
    void reportsLengthErrorForMismatchedVectors() {
        JError error = assertThrows(JError.class, () -> session.evaluate("1 2 3 + 1 2"));

        assertEquals("length error", error.getMessage());
    }

    @Test
    void assemblesNumericStrandsAndPromotesToFloat() {
        assertEquals("1 2 3.5", evaluate("1 2 3.5"));
    }

    @Test
    void assemblesEqualShapedNounsAsRows() {
        assertEquals("1 2\n3 4", evaluate("(1 2) (3 4)"));
        assertEquals("11 12\n13 14", evaluate("(1 2) (3 4) + 10"));
    }

    @Test
    void displaysCharactersAndStrings() {
        assertEquals("abc", evaluate("'abc'"));
        assertEquals("ab\ncd", evaluate("('ab') ('cd')"));
        assertEquals("", evaluate("''"));
    }

    @Test
    void displaysAVerbValue() {
        assertEquals("+", evaluate("+"));
    }

    @Test
    void blankAndCommentLinesProduceNoOutput() {
        assertTrue(session.evaluate("").isEmpty());
        assertTrue(session.evaluate("   NB. a comment").isEmpty());
    }

    @Test
    void reportsValueAndSyntaxErrors() {
        assertEquals("value error", assertThrows(JError.class, () -> session.evaluate("foo")).getMessage());
        assertEquals("syntax error", assertThrows(JError.class, () -> session.evaluate("1 +")).getMessage());
    }

    @Test
    void characterArithmeticIsADomainError() {
        JError error = assertThrows(JError.class, () -> session.evaluate("2 + 'a'"));

        assertEquals("domain error", error.getMessage());
    }
}
