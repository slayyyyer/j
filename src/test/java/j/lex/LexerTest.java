package j.lex;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;

import j.JError;
import j.array.JArray;
import j.array.JType;
import org.junit.jupiter.api.Test;

class LexerTest {
    @Test
    void emptyInputProducesOnlyEof() {
        List<Token> tokens = Lexer.lex("");

        assertEquals(1, tokens.size());
        assertEquals(TokenType.EOF, tokens.get(0).type());
        assertEquals(1, tokens.get(0).column());
        assertEquals("", tokens.get(0).text());
    }

    @Test
    void lexesIntegerLiterals() {
        List<Token> tokens = Lexer.lex("42 _7 0");

        assertInt(tokens.get(0), 42);
        assertInt(tokens.get(1), -7);
        assertInt(tokens.get(2), 0);
        assertEquals("42", tokens.get(0).text());
        assertEquals("_7", tokens.get(1).text());
        assertEquals(TokenType.EOF, tokens.get(3).type());
    }

    @Test
    void lexesFloatLiterals() {
        List<Token> tokens = Lexer.lex("3.5 _0.25 1e6 2.5e_3 3.");

        assertFloat(tokens.get(0), 3.5);
        assertFloat(tokens.get(1), -0.25);
        assertFloat(tokens.get(2), 1e6);
        assertFloat(tokens.get(3), 2.5e-3);
        assertFloat(tokens.get(4), 3.0);
        assertEquals(JType.FLOAT, tokens.get(4).literal().type());
    }

    @Test
    void lexesInfinityAndNaN() {
        List<Token> tokens = Lexer.lex("_ __ _.");

        assertFloat(tokens.get(0), Double.POSITIVE_INFINITY);
        assertFloat(tokens.get(1), Double.NEGATIVE_INFINITY);
        assertFloat(tokens.get(2), Double.NaN);
        assertEquals("_", tokens.get(0).text());
        assertEquals("__", tokens.get(1).text());
        assertEquals("_.", tokens.get(2).text());
    }

    @Test
    void underscoreBeforeNumberIsNegativeButAloneIsInfinity() {
        List<Token> tokens = Lexer.lex("_3 _ 3");

        assertInt(tokens.get(0), -3);
        assertFloat(tokens.get(1), Double.POSITIVE_INFINITY);
        assertInt(tokens.get(2), 3);
    }

    @Test
    void parsesLongMinimum() {
        List<Token> tokens = Lexer.lex("_9223372036854775808");

        assertInt(tokens.get(0), Long.MIN_VALUE);
    }

    @Test
    void promotesIntegersOutsideLongRangeToExtendedIntegers() {
        List<Token> tokens = Lexer.lex("9223372036854775808");

        assertEquals(JType.BIGINT, tokens.get(0).literal().type());
        assertEquals("9223372036854775808", tokens.get(0).literal().bigAt(0).toString());
    }

    @Test
    void lexesExtendedRationalAndComplexLiterals() {
        List<Token> tokens = Lexer.lex("123x 1r2 3j4");

        assertEquals(JType.BIGINT, tokens.get(0).literal().type());
        assertEquals(JType.RATIONAL, tokens.get(1).literal().type());
        assertEquals(JType.COMPLEX, tokens.get(2).literal().type());
    }

    @Test
    void lexesNames() {
        List<Token> tokens = Lexer.lex("alpha beta_2 x");

        assertEquals(new Token(TokenType.NAME, "alpha", null, 1), tokens.get(0));
        assertEquals("beta_2", tokens.get(1).text());
        assertEquals(7, tokens.get(1).column());
        assertEquals("x", tokens.get(2).text());
        assertEquals(14, tokens.get(2).column());
    }

    @Test
    void tokenizesMultiCharacterPrimitivesAsSingleTokens() {
        List<Token> tokens = Lexer.lex("{. }. |. |: i. ~: <: >: @:");

        assertEquals(List.of("{.", "}.", "|.", "|:", "i.", "~:", "<:", ">:", "@:"), texts(tokens));
        for (int index = 0; index < 9; index++) {
            assertEquals(TokenType.PRIMITIVE, tokens.get(index).type());
        }
    }

    @Test
    void iotaIsPrimitiveButIBareIsAName() {
        assertEquals(TokenType.PRIMITIVE, Lexer.lex("i.").get(0).type());
        assertEquals(TokenType.NAME, Lexer.lex("i").get(0).type());
    }

    @Test
    void tokenizesSingleCharacterPrimitives() {
        List<Token> tokens = Lexer.lex("+ - * % ^ = < > ~ $ , # { ; & . : \" / \\");

        assertEquals(
                List.of("+", "-", "*", "%", "^", "=", "<", ">", "~", "$", ",", "#",
                        "{", ";", "&", ".", ":", "\"", "/", "\\"),
                texts(tokens));
    }

    @Test
    void keepsAdverbsAndConjunctionsAsSeparateTokens() {
        assertEquals(List.of("+", "/"), texts(Lexer.lex("+/")));
        assertEquals(List.of("+", "/", ".", "*"), texts(Lexer.lex("+/ .*")));
        assertEquals(List.of("+", "/", "\"", "1"), texts(Lexer.lex("+/" + '"' + "1")));
        assertEquals(List.of("+", "\\", "i.", "5"), texts(Lexer.lex("+\\i.5")));
    }

    @Test
    void separatesAssignmentsFromComparison() {
        List<Token> tokens = Lexer.lex("a =: b =. c = d");

        assertEquals(TokenType.ASSIGN_GLOBAL, tokens.get(1).type());
        assertEquals("=:", tokens.get(1).text());
        assertEquals(TokenType.ASSIGN_LOCAL, tokens.get(3).type());
        assertEquals("=.", tokens.get(3).text());
        assertEquals(TokenType.PRIMITIVE, tokens.get(5).type());
        assertEquals("=", tokens.get(5).text());
    }

    @Test
    void tokenizesParenthesesAndDirectDefinitionDelimiters() {
        List<Token> tokens = Lexer.lex("({{ y }})");

        assertEquals(TokenType.LPAREN, tokens.get(0).type());
        assertEquals(TokenType.LBRACE2, tokens.get(1).type());
        assertEquals("{{", tokens.get(1).text());
        assertEquals(TokenType.NAME, tokens.get(2).type());
        assertEquals(TokenType.RBRACE2, tokens.get(3).type());
        assertEquals("}}", tokens.get(3).text());
        assertEquals(TokenType.RPAREN, tokens.get(4).type());
    }

    @Test
    void lexesQuotedStringsWithDoubledQuoteEscapes() {
        List<Token> tokens = Lexer.lex("'abc' 'don''t' '' ''''");

        assertEquals(TokenType.STRING, tokens.get(0).type());
        assertEquals("'abc'", tokens.get(0).text());
        assertEquals("abc", stringValue(tokens.get(0)));
        assertEquals(JType.CHAR, tokens.get(0).literal().type());
        assertEquals(1, tokens.get(0).literal().rank());
        assertEquals(3, tokens.get(0).literal().size());

        assertEquals("don't", stringValue(tokens.get(1)));
        assertEquals("", stringValue(tokens.get(2)));
        assertEquals("'", stringValue(tokens.get(3)));
    }

    @Test
    void skipsCommentsToEndOfLine() {
        List<Token> tokens = Lexer.lex("1 + 2 NB. hi\n3");

        assertEquals(List.of("1", "+", "2", "3"), texts(tokens));
    }

    @Test
    void commentOnlyLineProducesOnlyEof() {
        List<Token> tokens = Lexer.lex("NB. just a comment");

        assertEquals(1, tokens.size());
        assertEquals(TokenType.EOF, tokens.get(0).type());
    }

    @Test
    void commentMarkerInsideStringIsText() {
        Token token = Lexer.lex("'NB. hi'").get(0);

        assertEquals("NB. hi", stringValue(token));
    }

    @Test
    void tracksOneBasedColumns() {
        List<Token> tokens = Lexer.lex("  12 + 3");

        assertEquals(3, tokens.get(0).column());
        assertEquals(6, tokens.get(1).column());
        assertEquals(8, tokens.get(2).column());
        assertEquals(9, tokens.get(3).column());
    }

    @Test
    void lexesACompleteTrainSentence() {
        List<Token> tokens = Lexer.lex("mean =: +/ % # NB. train\nmean 1 2 3 4");

        assertEquals(
                List.of("mean", "=:", "+", "/", "%", "#", "mean", "1", "2", "3", "4"),
                texts(tokens));
    }

    @Test
    void unterminatedStringReportsSpellingErrorAtOpeningQuote() {
        JError error = assertThrows(JError.class, () -> Lexer.lex("  'abc"));

        assertEquals("spelling error", error.getMessage());
        assertEquals(3, error.column());
    }

    @Test
    void malformedExponentReportsSpellingError() {
        JError missingDigits = assertThrows(JError.class, () -> Lexer.lex("1e"));
        assertEquals("spelling error", missingDigits.getMessage());
        assertEquals(2, missingDigits.column());

        JError missingAfterUnderscore = assertThrows(JError.class, () -> Lexer.lex("1e_"));
        assertEquals("spelling error", missingAfterUnderscore.getMessage());
    }

    @Test
    void unknownCharacterReportsSpellingError() {
        JError error = assertThrows(JError.class, () -> Lexer.lex("1 ` 2"));

        assertEquals("spelling error", error.getMessage());
        assertEquals(3, error.column());
    }

    @Test
    void malformedNumberSuffixesReportSpellingError() {
        assertThrows(JError.class, () -> Lexer.lex("1_2"));
        assertThrows(JError.class, () -> Lexer.lex("1.5x"));
        assertThrows(JError.class, () -> Lexer.lex("1r"));
    }

    // --------------------------------------------------------------- helpers

    private static void assertInt(Token token, long expected) {
        assertEquals(TokenType.NUMBER, token.type());
        assertEquals(JType.INT, token.literal().type());
        assertTrue(token.literal().isScalar());
        assertEquals(expected, token.literal().longAt(0));
    }

    private static void assertFloat(Token token, double expected) {
        assertEquals(TokenType.NUMBER, token.type());
        assertEquals(JType.FLOAT, token.literal().type());
        assertTrue(token.literal().isScalar());
        assertEquals(expected, token.literal().doubleAt(0));
    }

    private static String stringValue(Token token) {
        JArray characters = token.literal();
        StringBuilder value = new StringBuilder(characters.size());
        for (int index = 0; index < characters.size(); index++) {
            value.append(characters.charAt(index));
        }
        return value.toString();
    }

    private static List<String> texts(List<Token> tokens) {
        List<String> texts = new ArrayList<>();
        for (Token token : tokens) {
            if (token.type() == TokenType.EOF) {
                break;
            }
            texts.add(token.text());
        }
        return texts;
    }
}
