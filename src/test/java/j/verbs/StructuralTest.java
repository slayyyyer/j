package j.verbs;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
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

class StructuralTest {
    private static JArray eval(String source) {
        Environment environment = new Environment();
        Phrase phrase = Parser.parse(source, environment);
        Value value = new Evaluator(environment).evaluate(phrase);
        return assertInstanceOf(Value.Noun.class, value).array();
    }

    @Test
    void iotaBuildsIntegerArrays() {
        assertEquals("0 1 2 3 4", JFormat.format(eval("i. 5")));
        assertEquals("2 1 0", JFormat.format(eval("i. _3")));

        JArray matrix = eval("i. 3 4");
        assertEquals(2, matrix.rank());
        assertEquals(12, matrix.size());
        assertEquals(0L, matrix.longAt(0));
        assertEquals(11L, matrix.longAt(11));
    }

    @Test
    void shapeReportsAndReshapes() {
        JArray scalarShape = eval("$ 5");
        assertEquals(JType.INT, scalarShape.type());
        assertEquals(0, scalarShape.size());

        assertEquals("5", JFormat.format(eval("$ i. 5")));
        assertEquals("3 4", JFormat.format(eval("$ i. 3 4")));
        assertEquals("1 2 3\n4 5 6", JFormat.format(eval("2 3 $ 1 2 3 4 5 6")));
        assertEquals("1 2 1\n2 1 2", JFormat.format(eval("2 3 $ 1 2")));
    }

    @Test
    void tallyCountsItems() {
        assertEquals("3", JFormat.format(eval("# 1 2 3")));
        assertEquals("1", JFormat.format(eval("# 5")));
        assertEquals("3", JFormat.format(eval("# i. 3 4")));
    }

    @Test
    void ravelFlattensAndCatenates() {
        assertEquals("0 1 2 3 4 5", JFormat.format(eval(", i. 2 3")));
        assertEquals("1 2 3 4", JFormat.format(eval("1 2 , 3 4")));
        assertEquals("1 2 3", JFormat.format(eval("1 , 2 3")));
    }

    @Test
    void reverseAndRotateWorkOnTheLeadingAxis() {
        assertEquals("3 2 1", JFormat.format(eval("|. 1 2 3")));
        assertEquals("3 4 5\n0 1 2", JFormat.format(eval("|. i. 2 3")));
        assertEquals("2 3 1", JFormat.format(eval("1 |. 1 2 3")));
        assertEquals("3 1 2", JFormat.format(eval("_1 |. 1 2 3")));
    }

    @Test
    void headTakeAndDropWorkOnItems() {
        assertEquals("1", JFormat.format(eval("{. 1 2 3")));
        assertEquals("0 1 2", JFormat.format(eval("{. i. 2 3")));
        assertEquals("1 2", JFormat.format(eval("2 {. 1 2 3")));
        assertEquals("2 3", JFormat.format(eval("_2 {. 1 2 3")));
        assertEquals("2 3", JFormat.format(eval("}. 1 2 3")));
        assertEquals("2 3", JFormat.format(eval("1 }. 1 2 3")));
        assertEquals("1 2", JFormat.format(eval("_1 }. 1 2 3")));
    }

    @Test
    void overtakeFillsAndHeadOfEmptyUsesFill() {
        assertEquals("1 2 3 0 0", JFormat.format(eval("5 {. 1 2 3")));
        assertEquals("1 2 0", JFormat.format(eval("3 {. 1 2")));
        assertEquals("  abc", JFormat.format(eval("_5 {. 'abc'")));
        assertEquals("0", JFormat.format(eval("{. 0$0")));
        assertEquals(" ", JFormat.format(eval("{. ''")));
        assertEquals("0 0 0", JFormat.format(eval("{. i. 0 3")));
    }

    @Test
    void takeAndDropWorkAlongSuccessiveAxes() {
        assertEquals("0 1 2\n5 6 7",
                JFormat.format(eval("2 3 {. i. 4 5")));
        assertEquals("12 13 14\n17 18 19",
                JFormat.format(eval("_2 _3 {. i. 4 5")));
        assertEquals(" 7  8  9\n12 13 14\n17 18 19",
                JFormat.format(eval("1 2 }. i. 4 5")));
        assertEquals(" 0  1  2\n 5  6  7\n10 11 12",
                JFormat.format(eval("_1 _2 }. i. 4 5")));

        JArray over = eval("6 7 {. i. 2 3");
        assertArrayEquals(new int[]{6, 7}, over.shape());
        assertEquals(0L, over.longAt(0));
        assertEquals(2L, over.longAt(2));
        assertEquals(0L, over.longAt(3));
    }

    @Test
    void copyReplicatesItems() {
        assertEquals("1 1 1 2 2 2 3 3 3", JFormat.format(eval("3 # 1 2 3")));
        assertEquals("aac", JFormat.format(eval("2 0 1 # 'abc'")));
        assertEquals("0 1 2\n0 1 2\n3 4 5\n3 4 5",
                JFormat.format(eval("2 # i. 2 3")));
    }

    @Test
    void indexOfFindsItemsOrReturnsTheTally() {
        assertEquals("4", JFormat.format(eval("3 1 4 1 5 9 i. 5")));
        assertEquals("6", JFormat.format(eval("3 1 4 1 5 9 i. 7")));
        assertEquals("1 2 3", JFormat.format(eval("1 2 3 i. 2 3 4")));
        assertEquals("2", JFormat.format(eval("(i. 4 3) i. 6 7 8")));
        assertEquals("1", JFormat.format(eval("'abc' i. 'b'")));
        assertEquals("3", JFormat.format(eval("'abc' i. 'd'")));
    }

    @Test
    void indexingSelectsItemsAndSupportsNegativeIndices() {
        assertEquals("20", JFormat.format(eval("1 { 10 20 30")));
        assertEquals("30", JFormat.format(eval("_1 { 10 20 30")));
        assertEquals("10 30", JFormat.format(eval("0 2 { 10 20 30")));
        assertEquals("4 5 6 7", JFormat.format(eval("1 { i. 3 4")));
    }

    @Test
    void transposeReversesAxes() {
        assertEquals("0 3\n1 4\n2 5", JFormat.format(eval("|: i. 2 3")));
    }

    @Test
    void reportsStructuralErrors() {
        assertEquals("index error",
                assertThrows(JError.class, () -> eval("2 { 1 2")).getMessage());
        assertEquals("length error",
                assertThrows(JError.class, () -> eval("1 2 3 # 1 2")).getMessage());
        assertEquals("rank error",
                assertThrows(JError.class, () -> eval("1 2 , i. 2 3")).getMessage());
        assertEquals("domain error",
                assertThrows(JError.class, () -> eval("i. 'a'")).getMessage());
    }
}
