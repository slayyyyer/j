/**
 * Ported from src/test/java/j/array/JArrayTest.java.
 *
 * The TypeScript API differs where the Java version used varargs, `chars` for
 * a string, or a `toFloat` conversion. Promotion is tested through the
 * exported `promote`/`convertAtom` helpers, which are what the interpreter
 * actually uses.
 */
import { strict as assert } from 'node:assert';
import { describe, test } from 'node:test';
import { JError, JType } from '../src/j/atoms.ts';
import { JArray, convertAtom, promote } from '../src/j/array.ts';
import { format } from '../src/j/format.ts';

describe('JArray', () => {
  test('scalar has rank zero and one atom', () => {
    const scalar = JArray.scalar(JType.INT, 42);

    assert.equal(scalar.type, JType.INT);
    assert.equal(scalar.rank, 0);
    assert.equal(scalar.size, 1);
    assert.equal(scalar.isScalar, true);
    assert.deepEqual(scalar.shape, []);
    assert.equal(scalar.longAt(0), 42);
  });

  test('integer vector keeps shape and values', () => {
    const vector = JArray.intVector([10, 20, 30]);

    assert.equal(vector.rank, 1);
    assert.equal(vector.size, 3);
    assert.deepEqual(vector.shape, [3]);
    assert.equal(vector.longAt(0), 10);
    assert.equal(vector.longAt(2), 30);
    assert.equal(vector.isEmpty, false);
  });

  test('matrix is stored row major', () => {
    const matrix = JArray.ints([2, 3], [1, 2, 3, 4, 5, 6]);

    assert.equal(matrix.rank, 2);
    assert.equal(matrix.size, 6);
    assert.deepEqual(matrix.shape, [2, 3]);
    assert.equal(matrix.longAt(3), 4);
    assert.equal(matrix.longAt(5), 6);
    assert.deepEqual(matrix.strides(), [3, 1]);
  });

  test('character array from string', () => {
    const text = JArray.text('J!');

    assert.equal(text.type, JType.CHAR);
    assert.deepEqual(text.shape, [2]);
    assert.equal(text.charAt(0), 'J');
    assert.equal(text.charAt(1), '!');
  });

  test('empty vector has zero atoms', () => {
    const empty = JArray.ints([0], []);

    assert.equal(empty.size, 0);
    assert.equal(empty.isEmpty, true);
    assert.deepEqual(empty.shape, [0]);
  });

  test('empty factory requires zero dimension', () => {
    assert.throws(() => JArray.empty(JType.INT, [2, 3]), /empty array shape/);
    assert.throws(() => JArray.empty(JType.INT, []), /empty array shape/);
    assert.equal(JArray.empty(JType.CHAR, [2, 0]).size, 0);
  });

  test('shape and data must agree', () => {
    assert.throws(
      () => JArray.ints([2, 2], [1, 2, 3]),
      /needs 4 atoms but got 3/,
    );
  });

  test('negative dimensions are rejected', () => {
    assert.throws(
      () => JArray.ints([-1], []),
      (error: unknown) => error instanceof JError && error.message === 'domain error',
    );
  });

  // The Java array exposed toFloat(); the port promotes types at the atom
  // level instead, so these three checks target promote/convertAtom.
  test('promotes integers when combining with floats', () => {
    assert.equal(promote(JType.INT, JType.FLOAT), JType.FLOAT);
    assert.equal(convertAtom(1, JType.FLOAT), 1);
    assert.equal(convertAtom(0n, JType.FLOAT), 0);
  });

  test('float promotion is an identity', () => {
    assert.equal(promote(JType.FLOAT, JType.FLOAT), JType.FLOAT);
  });

  test('promoting characters is rejected', () => {
    assert.throws(
      () => promote(JType.CHAR, JType.FLOAT),
      (error: unknown) => error instanceof JError && error.message === 'domain error',
    );
  });

  test('inputs are copied so the array stays immutable', () => {
    const values = [1, 2, 3];
    const shape = [3];
    const array = JArray.ints(shape, values);

    values[0] = 99;
    shape[0] = 99;

    assert.equal(array.longAt(0), 1);
    assert.deepEqual(array.shape, [3]);
  });

  test('shape accessor is insulated from the constructor argument', () => {
    const shape = [2, 2];
    const array = JArray.ints(shape, [1, 2, 3, 4]);

    assert.notEqual(array.shape, shape);
    shape[0] = 99;
    assert.deepEqual(array.shape, [2, 2]);
  });

  test('numericAt widens integers', () => {
    assert.equal(JArray.scalar(JType.INT, 7).numericAt(0), 7);
    assert.equal(JArray.scalar(JType.FLOAT, 2.5).numericAt(0), 2.5);
    assert.throws(
      () => JArray.scalar(JType.CHAR, 'a').numericAt(0),
      /numericAt requires a real numeric array/,
    );
  });

  test('slices contiguous cells', () => {
    const matrix = JArray.ints([2, 3], [1, 2, 3, 4, 5, 6]);

    assert.equal(format(matrix.slice(3, [3])), '4 5 6');
    assert.equal(format(matrix.slice(0, [])), '1');
    assert.throws(() => matrix.slice(4, [3]), /slice out of bounds/);
  });

  test('typed accessors reject the wrong type', () => {
    const floats = JArray.scalar(JType.FLOAT, 1.5);

    assert.throws(() => floats.longAt(0), /longAt requires a .* array, got FLOAT/);
    assert.equal(floats.doubleAt(0), 1.5);
  });
});
