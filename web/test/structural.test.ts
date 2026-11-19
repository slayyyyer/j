/**
 * Ported from src/test/java/j/verbs/StructuralTest.java.
 *
 * Java evaluated through Environment + Evaluator directly; the shared
 * `evalSentence` helper does the same with the TypeScript parser's name
 * classifier.
 */
import { strict as assert } from 'node:assert';
import { describe, test } from 'node:test';
import { JType } from '../src/j/atoms.ts';
import { display, evalSentence, expectJError } from './support.ts';

describe('structural verbs', () => {
  test('iota builds integer arrays', () => {
    assert.equal(display('i. 5'), '0 1 2 3 4');
    assert.equal(display('i. _3'), '2 1 0');

    const matrix = evalSentence('i. 3 4');
    assert.equal(matrix.rank, 2);
    assert.equal(matrix.size, 12);
    assert.equal(matrix.longAt(0), 0);
    assert.equal(matrix.longAt(11), 11);
  });

  test('shape reports and reshapes', () => {
    const scalarShape = evalSentence('$ 5');
    assert.equal(scalarShape.type, JType.INT);
    assert.equal(scalarShape.size, 0);

    assert.equal(display('$ i. 5'), '5');
    assert.equal(display('$ i. 3 4'), '3 4');
    assert.equal(display('2 3 $ 1 2 3 4 5 6'), '1 2 3\n4 5 6');
    assert.equal(display('2 3 $ 1 2'), '1 2 1\n2 1 2');
  });

  test('tally counts items', () => {
    assert.equal(display('# 1 2 3'), '3');
    assert.equal(display('# 5'), '1');
    assert.equal(display('# i. 3 4'), '3');
  });

  test('ravel flattens and catenates', () => {
    assert.equal(display(', i. 2 3'), '0 1 2 3 4 5');
    assert.equal(display('1 2 , 3 4'), '1 2 3 4');
    assert.equal(display('1 , 2 3'), '1 2 3');
  });

  test('reverse and rotate work on the leading axis', () => {
    assert.equal(display('|. 1 2 3'), '3 2 1');
    assert.equal(display('|. i. 2 3'), '3 4 5\n0 1 2');
    assert.equal(display('1 |. 1 2 3'), '2 3 1');
    assert.equal(display('_1 |. 1 2 3'), '3 1 2');
  });

  test('head take and drop work on items', () => {
    assert.equal(display('{. 1 2 3'), '1');
    assert.equal(display('{. i. 2 3'), '0 1 2');
    assert.equal(display('2 {. 1 2 3'), '1 2');
    assert.equal(display('_2 {. 1 2 3'), '2 3');
    assert.equal(display('}. 1 2 3'), '2 3');
    assert.equal(display('1 }. 1 2 3'), '2 3');
    assert.equal(display('_1 }. 1 2 3'), '1 2');
  });

  test('overtake fills and head of empty uses fill', () => {
    assert.equal(display('5 {. 1 2 3'), '1 2 3 0 0');
    assert.equal(display('3 {. 1 2'), '1 2 0');
    assert.equal(display("_5 {. 'abc'"), '  abc');
    assert.equal(display('{. 0 $ 0'), '0');
    assert.equal(display("{. ''"), ' ');
    assert.equal(display('{. i. 0 3'), '0 0 0');
  });

  test('take and drop work along successive axes', () => {
    assert.equal(display('2 3 {. i. 4 5'), '0 1 2\n5 6 7');
    assert.equal(display('_2 _3 {. i. 4 5'), '12 13 14\n17 18 19');
    assert.equal(display('1 2 }. i. 4 5'), ' 7  8  9\n12 13 14\n17 18 19');
    assert.equal(display('_1 _2 }. i. 4 5'), ' 0  1  2\n 5  6  7\n10 11 12');

    const over = evalSentence('6 7 {. i. 2 3');
    assert.deepEqual(over.shape, [6, 7]);
    assert.equal(over.longAt(0), 0);
    assert.equal(over.longAt(2), 2);
    assert.equal(over.longAt(3), 0);
  });

  test('insert uses primitive identities on empty arrays', () => {
    assert.equal(display('+/ i. 0'), '0');
    assert.equal(display('*/ i. 0'), '1');
    assert.equal(display('-/ i. 0'), '0');
    assert.equal(display('%/ i. 0'), '1');
    assert.equal(display('^/ i. 0'), '1');
    assert.equal(display('=/ i. 0'), '1');
    assert.equal(display('~:/ i. 0'), '0');
    assert.equal(display('</ i. 0'), '0');
    assert.equal(display('<:/ i. 0'), '1');
    assert.equal(display('>/ i. 0'), '0');
    assert.equal(display('>:/ i. 0'), '1');
  });

  test('copy replicates items', () => {
    assert.equal(display('3 # 1 2 3'), '1 1 1 2 2 2 3 3 3');
    assert.equal(display("2 0 1 # 'abc'"), 'aac');
    assert.equal(display('2 # i. 2 3'), '0 1 2\n0 1 2\n3 4 5\n3 4 5');
  });

  test('complex copy counts insert fills', () => {
    assert.equal(display('2j1 0 1j2 # i. 3 2'), '0 1\n0 1\n0 0\n4 5\n0 0\n0 0');
  });

  test('catalog combines boxed items', () => {
    assert.equal(display('{ (<1 2),(<3 4)'), '1 3\n1 4\n2 3\n2 4');
  });

  test('index of finds items or returns the tally', () => {
    assert.equal(display('3 1 4 1 5 9 i. 5'), '4');
    assert.equal(display('3 1 4 1 5 9 i. 7'), '6');
    assert.equal(display('1 2 3 i. 2 3 4'), '1 2 3');
    assert.equal(display('(i. 4 3) i. 6 7 8'), '2');
    assert.equal(display("'abc' i. 'b'"), '1');
    assert.equal(display("'abc' i. 'd'"), '3');
  });

  test('indexing selects items and supports negative indices', () => {
    assert.equal(display('1 { 10 20 30'), '20');
    assert.equal(display('_1 { 10 20 30'), '30');
    assert.equal(display('0 2 { 10 20 30'), '10 30');
    assert.equal(display('1 { i. 3 4'), '4 5 6 7');
  });

  test('transpose reverses axes and supports a dyadic axis list', () => {
    assert.equal(display('|: i. 2 3'), '0 3\n1 4\n2 5');
    assert.equal(display('1 0 |: i. 2 3'), '0 3\n1 4\n2 5');
  });

  test('rotate supports multiple axes', () => {
    assert.equal(display('1 1 |. i. 3 4'), '5  6  7 4\n9 10 11 8\n1  2  3 0');
  });

  test('reports structural errors', () => {
    assert.equal(expectJError(() => evalSentence('2 { 1 2')).message, 'index error');
    assert.equal(expectJError(() => evalSentence('1 2 3 # 1 2')).message, 'length error');
    assert.equal(expectJError(() => evalSentence('1 2 , i. 2 3')).message, 'rank error');
    assert.equal(expectJError(() => evalSentence("i. 'a'")).message, 'domain error');
    assert.equal(expectJError(() => evalSentence('_1 # 1 2 3')).message, 'domain error');
    assert.equal(expectJError(() => evalSentence('1r2 { 10 20')).message, 'domain error');
  });
});
