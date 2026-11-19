/**
 * Ported from src/test/java/j/array/JFormatTest.java.
 *
 * The TypeScript display entry point is `format()`; JArray does not override
 * Object.toString(), so the Java toString check has no direct counterpart.
 */
import { strict as assert } from 'node:assert';
import { describe, test } from 'node:test';
import { JType } from '../src/j/atoms.ts';
import { JArray } from '../src/j/array.ts';
import { format } from '../src/j/format.ts';

describe('format', () => {
  test('formats integer scalar', () => {
    assert.equal(format(JArray.scalar(JType.INT, 42)), '42');
    assert.equal(format(JArray.scalar(JType.INT, -7)), '_7');
  });

  test('formats float scalar', () => {
    assert.equal(format(JArray.scalar(JType.FLOAT, 2.5)), '2.5');
    assert.equal(format(JArray.scalar(JType.FLOAT, -0.25)), '_0.25');
  });

  test('integral floats print without a decimal point', () => {
    assert.equal(format(JArray.scalar(JType.FLOAT, 2)), '2');
  });

  test('floats use five significant digits', () => {
    assert.equal(format(JArray.scalar(JType.FLOAT, 1 / 3)), '0.33333');
    assert.equal(format(JArray.scalar(JType.FLOAT, 2 / 3)), '0.66667');
    assert.equal(format(JArray.scalar(JType.FLOAT, 1_234_567)), '1.2346e6');
  });

  test('uses J notation for special floats', () => {
    assert.equal(format(JArray.scalar(JType.FLOAT, Number.POSITIVE_INFINITY)), '_');
    assert.equal(format(JArray.scalar(JType.FLOAT, Number.NEGATIVE_INFINITY)), '__');
    assert.equal(format(JArray.scalar(JType.FLOAT, Number.NaN)), '_.');
  });

  test('chooses plain and power notation at J thresholds', () => {
    assert.equal(format(JArray.scalar(JType.FLOAT, 1e5)), '100000');
    assert.equal(format(JArray.scalar(JType.FLOAT, 1e6)), '1e6');
    assert.equal(format(JArray.scalar(JType.FLOAT, 1e-4)), '0.0001');
    assert.equal(format(JArray.scalar(JType.FLOAT, 1e-5)), '1e_5');
    assert.equal(format(JArray.scalar(JType.FLOAT, -1.5e-7)), '_1.5e_7');
  });

  test('formats integer vector', () => {
    assert.equal(format(JArray.intVector([0, 1, 2, 3, 4])), '0 1 2 3 4');
    assert.equal(format(JArray.intVector([-1, 0, 1])), '_1 0 1');
  });

  test('formats matrix with aligned columns', () => {
    const matrix = JArray.ints(
      [3, 4],
      [0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11],
    );

    assert.equal(format(matrix), '0 1  2  3\n4 5  6  7\n8 9 10 11');
  });

  test('formats simple matrix', () => {
    const matrix = JArray.ints([2, 3], [1, 2, 3, 4, 5, 6]);

    assert.equal(format(matrix), '1 2 3\n4 5 6');
  });

  test('separates planes of rank three arrays', () => {
    const cube = JArray.ints([2, 2, 2], [0, 1, 2, 3, 4, 5, 6, 7]);

    assert.equal(format(cube), '0 1\n2 3\n\n4 5\n6 7');
  });

  test('formats character vector and scalar', () => {
    assert.equal(format(JArray.text('abc')), 'abc');
    assert.equal(format(JArray.scalar(JType.CHAR, 'a')), 'a');
  });

  test('formats character matrix as rows', () => {
    const matrix = JArray.chars([2, 3], ['a', 'b', 'c', 'd', 'e', 'f']);

    assert.equal(format(matrix), 'abc\ndef');
  });

  test('empty arrays format as nothing', () => {
    assert.equal(format(JArray.ints([0], [])), '');
    assert.equal(format(JArray.text('')), '');
    assert.equal(format(JArray.ints([0, 3], [])), '');
  });
});
