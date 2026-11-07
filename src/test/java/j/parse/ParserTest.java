package j.parse;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import j.JError;

class ParserTest {
    private static Phrase parse(String source) {
        return Parser.parse(source, name -> false);
    }

    private static Expr expr(String source) {
        return assertInstanceOf(Phrase.Noun.class, parse(source)).expr();
    }

    private static void assertNumber(Expr expr, long value) {
        Expr.Lit lit = assertInstanceOf(Expr.Lit.class, expr);
        assertEquals(value, lit.value().longAt(0));
    }

    @Test
    void parsesALiteralNoun() {
        assertNumber(expr("42"), 42);
    }

    @Test
    void parsesAdjacentNounsAsAStrand() {
        Expr.Strand strand = assertInstanceOf(Expr.Strand.class, expr("1 2 3"));

        assertEquals(3, strand.parts().size());
        assertNumber(strand.parts().get(0), 1);
        assertNumber(strand.parts().get(2), 3);
    }

    @Test
    void parsesDyadsRightToLeft() {
        Expr.Dyad outer = assertInstanceOf(Expr.Dyad.class, expr("2 + 3 + 4"));

        assertNumber(outer.x(), 2);
        assertEquals("+", ((VerbExpr.Lit) outer.verb()).verb().repr());
        Expr.Dyad inner = assertInstanceOf(Expr.Dyad.class, outer.y());
        assertNumber(inner.x(), 3);
        assertNumber(inner.y(), 4);
    }

    @Test
    void monadWrapsTheWholeExpressionToItsRight() {
        Expr.Monad monad = assertInstanceOf(Expr.Monad.class, expr("- 2 + 3"));

        assertEquals("-", ((VerbExpr.Lit) monad.verb()).verb().repr());
        assertInstanceOf(Expr.Dyad.class, monad.y());
    }

    @Test
    void parsesSequentialMonads() {
        Expr.Monad outer = assertInstanceOf(Expr.Monad.class, expr("+ - 5"));

        assertEquals("+", ((VerbExpr.Lit) outer.verb()).verb().repr());
        assertInstanceOf(Expr.Monad.class, outer.y());
    }

    @Test
    void parsesAParenthesizedNounOrVerb() {
        assertInstanceOf(Expr.Dyad.class, expr("(1 + 2)"));

        Phrase.Verb verb = assertInstanceOf(Phrase.Verb.class, parse("(+)"));
        assertEquals("+", ((VerbExpr.Lit) verb.verb()).verb().repr());
    }

    @Test
    void parsesAVerbValue() {
        Phrase.Verb phrase = assertInstanceOf(Phrase.Verb.class, parse("+"));

        assertEquals("+", ((VerbExpr.Lit) phrase.verb()).verb().repr());
    }

    @Test
    void groupsNounsOnBothSidesOfADyad() {
        Expr.Dyad dyad = assertInstanceOf(Expr.Dyad.class, expr("1 2 3 + 10"));

        assertInstanceOf(Expr.Strand.class, dyad.x());
        assertNumber(dyad.y(), 10);
    }

    @Test
    void reportsTrailingVerbAsSyntaxError() {
        JError error = assertThrows(JError.class, () -> parse("1 +"));

        assertEquals("syntax error", error.getMessage());
        assertEquals(3, error.column());
    }

    @Test
    void reportsEmptyParenthesesAsSyntaxError() {
        JError error = assertThrows(JError.class, () -> parse("()"));

        assertEquals("syntax error", error.getMessage());
        assertEquals(2, error.column());
    }

    @Test
    void reportsUnclosedParenthesesAsSyntaxError() {
        assertThrows(JError.class, () -> parse("(1 + 2"));
    }

    @Test
    void rejectsUnimplementedPrimitives() {
        assertThrows(JError.class, () -> parse("2 & 3"));
    }

    @Test
    void parsesAssignmentsAndChains() {
        Phrase.Assign assignment = assertInstanceOf(Phrase.Assign.class, parse("a =: 2 + 3"));
        assertEquals("a", assignment.name());
        assertTrue(assignment.global());
        assertInstanceOf(Phrase.Noun.class, assignment.value());

        Phrase.Assign chained = assertInstanceOf(Phrase.Assign.class, parse("a =: b =: 1"));
        assertInstanceOf(Phrase.Assign.class, chained.value());

        Phrase.Assign local = assertInstanceOf(Phrase.Assign.class, parse("a =. 1"));
        assertFalse(local.global());
    }
}
