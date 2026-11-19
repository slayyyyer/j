/**
 * Ported from src/test/java/j/verbs/SparseTest.java.
 *
 * The Java test built a boxed vector with JArray.boxes(); the port exposes
 * JArray.of(), which is used here with the same three box contents.
 */
import { strict as assert } from 'node:assert';
import { describe, test } from 'node:test';
import { JType } from '../src/j/atoms.ts';
import { JArray } from '../src/j/array.ts';
import { format } from '../src/j/format.ts';
import { display, evalSentence } from './support.ts';

const matrix = '$. 3 4 $ 0 75 0 53 0 0 67 67 93 0 51 83';

describe('sparse arrays', () => {
  test('converts dense arrays to sparse and displays index value rows', () => {
    const sparse = evalSentence(matrix);

    assert.equal(sparse.isSparse, true);
    assert.equal(sparse.sparseCount, 7);
    assert.equal(
      format(sparse),
      '0 1 | 75\n' +
        '0 3 | 53\n' +
        '1 2 | 67\n' +
        '1 3 | 67\n' +
        '2 0 | 93\n' +
        '2 2 | 51\n' +
        '2 3 | 83',
    );
  });

  test('sparse vectors display their flat indices', () => {
    const sparse = evalSentence('$. 0 5 0 2 0');

    assert.equal(sparse.isSparse, true);
    assert.equal(format(sparse), '1 | 5\n3 | 2');
  });

  test('boxed arrays convert to sparse and keep their contents', () => {
    const dense = JArray.of(JType.BOX, [3], [
      JArray.intVector([]),
      JArray.intVector([1, 2]),
      JArray.intVector([3, 4]),
    ]);
    const sparse = dense.toSparse();

    assert.equal(sparse.isSparse, true);
    assert.equal(sparse.type, JType.BOX);
    assert.equal(sparse.sparseCount, 2);
    assert.equal(format(sparse.boxAt(1)), '1 2');
    assert.equal(format(sparse), '1 | 1 2\n2 | 3 4');
    assert.equal(format(sparse.dense().boxAt(1)), '1 2');

    const reversed = sparse.reverseItems();
    assert.equal(reversed.isSparse, true);
    assert.equal(format(reversed), '0 | 3 4\n1 | 1 2');
  });

  test('sparse inverse spelling densifies', () => {
    const dense = evalSentence('$.^:_1 ' + matrix);

    assert.equal(dense.isSparse, false);
    assert.equal(format(dense), ' 0 75  0 53\n 0  0 67 67\n93  0 51 83');
    assert.equal(evalSentence('$.^:_1 1 2 3').isSparse, false);
  });

  test('zero sparse toggles back to dense', () => {
    const dense = evalSentence('0 $. ' + matrix);

    assert.equal(dense.isSparse, false);
    assert.equal(format(dense), ' 0 75  0 53\n 0  0 67 67\n93  0 51 83');
  });

  test('sparse shape tally and arithmetic use the fill value', () => {
    assert.equal(display('$ ' + matrix), '3 4');
    assert.equal(display('# ' + matrix), '3');

    const sum = evalSentence('1 + ' + matrix);
    assert.equal(sum.isSparse, true);
    assert.equal(
      format(sum),
      '0 1 | 76\n' +
        '0 3 | 54\n' +
        '1 2 | 68\n' +
        '1 3 | 68\n' +
        '2 0 | 94\n' +
        '2 2 | 52\n' +
        '2 3 | 84',
    );
  });

  test('insert and prefix preserve sparse storage', () => {
    const reduced = evalSentence('+/ $. 0 5 0 2 0');
    assert.equal(reduced.isSparse, true);
    assert.equal(format(reduced), '7');

    const prefixed = evalSentence('+/\\ $. 0 5 0 2 0');
    assert.equal(prefixed.isSparse, true);
    assert.equal(format(prefixed), '1 | 5\n2 | 5\n3 | 7\n4 | 7');

    const matrixReduced = evalSentence('+/ ' + matrix);
    assert.equal(matrixReduced.isSparse, true);
    assert.equal(format(matrixReduced), '0 |  93\n1 |  75\n2 | 118\n3 | 203');
  });

  test('arithmetic preserves sparse storage', () => {
    const negated = evalSentence('- ' + matrix);
    assert.equal(negated.isSparse, true);
    assert.equal(
      format(negated),
      '0 1 | _75\n' +
        '0 3 | _53\n' +
        '1 2 | _67\n' +
        '1 3 | _67\n' +
        '2 0 | _93\n' +
        '2 2 | _51\n' +
        '2 3 | _83',
    );

    const doubled = evalSentence(`(${matrix}) + (${matrix})`);
    assert.equal(doubled.isSparse, true);
    assert.equal(
      format(doubled),
      '0 1 | 150\n' +
        '0 3 | 106\n' +
        '1 2 | 134\n' +
        '1 3 | 134\n' +
        '2 0 | 186\n' +
        '2 2 | 102\n' +
        '2 3 | 166',
    );
  });

  test('structural operations preserve sparse storage', () => {
    const reversed = evalSentence('|. ' + matrix);
    assert.equal(reversed.isSparse, true);
    assert.equal(
      format(reversed),
      '0 0 | 93\n' +
        '0 2 | 51\n' +
        '0 3 | 83\n' +
        '1 2 | 67\n' +
        '1 3 | 67\n' +
        '2 1 | 75\n' +
        '2 3 | 53',
    );

    const transposed = evalSentence('|: ' + matrix);
    assert.equal(transposed.isSparse, true);
    assert.equal(
      format(transposed),
      '0 2 | 93\n' +
        '1 0 | 75\n' +
        '2 1 | 67\n' +
        '2 2 | 51\n' +
        '3 0 | 53\n' +
        '3 1 | 67\n' +
        '3 2 | 83',
    );

    const taken = evalSentence('2 {. ' + matrix);
    assert.equal(taken.isSparse, true);
    assert.equal(format(taken), '0 1 | 75\n0 3 | 53\n1 2 | 67\n1 3 | 67');

    const copied = evalSentence('2 # ' + matrix);
    assert.equal(copied.isSparse, true);
    assert.equal(
      format(copied),
      '0 1 | 75\n' +
        '0 3 | 53\n' +
        '1 1 | 75\n' +
        '1 3 | 53\n' +
        '2 2 | 67\n' +
        '2 3 | 67\n' +
        '3 2 | 67\n' +
        '3 3 | 67\n' +
        '4 0 | 93\n' +
        '4 2 | 51\n' +
        '4 3 | 83\n' +
        '5 0 | 93\n' +
        '5 2 | 51\n' +
        '5 3 | 83',
    );

    const dropped = evalSentence('1 }. ' + matrix);
    assert.equal(dropped.isSparse, true);
    assert.equal(
      format(dropped),
      '0 2 | 67\n' +
        '0 3 | 67\n' +
        '1 0 | 93\n' +
        '1 2 | 51\n' +
        '1 3 | 83',
    );

    const raveled = evalSentence(', ' + matrix);
    assert.equal(raveled.isSparse, true);
    assert.equal(
      format(raveled),
      ' 1 | 75\n' +
        ' 3 | 53\n' +
        ' 6 | 67\n' +
        ' 7 | 67\n' +
        ' 8 | 93\n' +
        '10 | 51\n' +
        '11 | 83',
    );

    const takeAxes = evalSentence('2 3 {. ' + matrix);
    assert.equal(takeAxes.isSparse, true);
    assert.equal(format(takeAxes), '0 1 | 75\n1 2 | 67');

    const dropAxes = evalSentence('1 1 }. ' + matrix);
    assert.equal(dropAxes.isSparse, true);
    assert.equal(format(dropAxes), '0 1 | 67\n0 2 | 67\n1 1 | 51\n1 2 | 83');

    const reshaped = evalSentence('2 4 $ , ' + matrix);
    assert.equal(reshaped.isSparse, true);
    assert.equal(format(reshaped), '0 1 | 75\n0 3 | 53\n1 2 | 67\n1 3 | 67');

    const appended = evalSentence(`(${matrix}) , (${matrix})`);
    assert.equal(appended.isSparse, true);
    assert.equal(
      format(appended),
      '0 1 | 75\n' +
        '0 3 | 53\n' +
        '1 2 | 67\n' +
        '1 3 | 67\n' +
        '2 0 | 93\n' +
        '2 2 | 51\n' +
        '2 3 | 83\n' +
        '3 1 | 75\n' +
        '3 3 | 53\n' +
        '4 2 | 67\n' +
        '4 3 | 67\n' +
        '5 0 | 93\n' +
        '5 2 | 51\n' +
        '5 3 | 83',
    );

    const appendedDense = evalSentence(`(${matrix}) , (0 $. ${matrix})`);
    assert.equal(appendedDense.isSparse, true);
    assert.equal(format(appendedDense), format(appended));
  });
});
