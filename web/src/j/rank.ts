import { JError, JType, concatShapes, sizeOf } from './atoms.ts';
import { JArray, fillObject, type Atom } from './array.ts';
import { checkBudget } from './budget.ts';
import { stackAssembly } from './assembly.ts';
import type { Verb } from './verbs.ts';

/**
 * Applies a verb's cell function across argument frames.
 *
 * A verb with rank r sees cells of at most rank r; leading axes form the frame
 * and are iterated. A rank-0 verb such as `+` sees atoms, so scalar extension
 * falls out of frame agreement rather than being special-cased in the verb.
 */
export function applyMonad(verb: Verb, y: JArray): JArray {
  if (y.isSparse && verb.monadRank() === 0) {
    const sparseResult = applySparseMonad(verb, y);
    if (sparseResult) {
      return sparseResult;
    }
  }
  const cellRank = Math.min(resolveRank(verb.monadRank(), y.rank), y.rank);
  const frameShape = y.shape.slice(0, y.shape.length - cellRank);
  const cellShape = y.shape.slice(frameShape.length);
  const frameSize = sizeOf(frameShape);

  if (frameSize === 0) {
    const probe = verb.monadCell(prototype(y.type, cellShape));
    return emptyResult(probe, frameShape);
  }
  if (frameShape.length === 0) {
    return verb.monadCell(y);
  }

  const cellSize = sizeOf(cellShape);
  const results: JArray[] = [];
  for (let cell = 0; cell < frameSize; cell++) {
    checkBudget();
    results.push(verb.monadCell(y.slice(cell * cellSize, cellShape)));
  }
  return stackAssembly(frameShape, results);
}

export function applyDyad(verb: Verb, x: JArray, y: JArray): JArray {
  if ((x.isSparse || y.isSparse) && verb.leftRank() === 0 && verb.rightRank() === 0) {
    const sparseResult = applySparseDyad(verb, x, y);
    if (sparseResult) {
      return sparseResult;
    }
  }
  const xCellRank = Math.min(resolveRank(verb.leftRank(), x.rank), x.rank);
  const yCellRank = Math.min(resolveRank(verb.rightRank(), y.rank), y.rank);

  const xFrame = x.shape.slice(0, x.shape.length - xCellRank);
  const yFrame = y.shape.slice(0, y.shape.length - yCellRank);
  const xCellShape = x.shape.slice(xFrame.length);
  const yCellShape = y.shape.slice(yFrame.length);

  const frameShape = agree(xFrame, yFrame);
  const frameSize = sizeOf(frameShape);

  if (frameSize === 0) {
    const probe = verb.dyadCell(
      prototype(x.type, xCellShape),
      prototype(y.type, yCellShape),
    );
    return emptyResult(probe, frameShape);
  }
  if (frameShape.length === 0) {
    return verb.dyadCell(x, y);
  }

  const xCellSize = sizeOf(xCellShape);
  const yCellSize = sizeOf(yCellShape);
  const xBroadcast = xFrame.length === 0;
  const yBroadcast = yFrame.length === 0;

  const results: JArray[] = [];
  for (let cell = 0; cell < frameSize; cell++) {
    checkBudget();
    const xCell = xBroadcast ? x : x.slice(cell * xCellSize, xCellShape);
    const yCell = yBroadcast ? y : y.slice(cell * yCellSize, yCellShape);
    results.push(verb.dyadCell(xCell, yCell));
  }
  return stackAssembly(frameShape, results);
}

function applySparseMonad(verb: Verb, y: JArray): JArray | null {
  if (y.rank === 0) {
    return applySparseMonadScalar(verb, y);
  }
  try {
    const fillResult = verb.monadCell(scalarOf(y.type, y.sparseFill!));
    if (fillResult.rank !== 0) {
      return null;
    }
    const resultType = fillResult.type;
    const resultFill = fillResult.atomAt(0);
    const indices = y.sparseIndices;
    const values = y.sparseValues;
    const resultValues: Atom[] = [];
    for (const value of values) {
      checkBudget();
      const result = verb.monadCell(scalarOf(y.type, value));
      if (result.rank !== 0 || result.type !== resultType) {
        return null;
      }
      resultValues.push(result.atomAt(0));
    }
    return JArray.sparse(resultType, y.shape, resultFill, indices, resultValues);
  } catch (error) {
    if (error instanceof JError) {
      return null;
    }
    throw error;
  }
}

function applySparseDyad(verb: Verb, x: JArray, y: JArray): JArray | null {
  if (x.rank === 0 && y.rank === 0) {
    return applySparseDyadScalar(verb, x, y);
  }
  if (x.rank > 0 && y.rank > 0 && !x.sameShape(y)) {
    return null;
  }
  const xSparse = x.rank > 0 && !x.isSparse ? x.toSparse() : x;
  const ySparse = y.rank > 0 && !y.isSparse ? y.toSparse() : y;

  const resultShape = x.rank === 0 ? y.shape : x.shape;
  const xFill = x.rank === 0 ? x.atomAt(0) : xSparse.sparseFill!;
  const yFill = y.rank === 0 ? y.atomAt(0) : ySparse.sparseFill!;
  try {
    const fillResult = verb.dyadCell(scalarOf(x.type, xFill), scalarOf(y.type, yFill));
    if (fillResult.rank !== 0) {
      return null;
    }
    const resultType = fillResult.type;
    const resultFill = fillResult.atomAt(0);
    let indices: number[];
    if (x.rank === 0) {
      indices = ySparse.sparseIndices;
    } else if (y.rank === 0) {
      indices = xSparse.sparseIndices;
    } else {
      indices = union(xSparse.sparseIndices, ySparse.sparseIndices);
    }
    const resultValues: Atom[] = [];
    for (const index of indices) {
      checkBudget();
      const xAtom = x.rank === 0 ? xFill : xSparse.atomAt(index);
      const yAtom = y.rank === 0 ? yFill : ySparse.atomAt(index);
      const result = verb.dyadCell(scalarOf(x.type, xAtom), scalarOf(y.type, yAtom));
      if (result.rank !== 0 || result.type !== resultType) {
        return null;
      }
      resultValues.push(result.atomAt(0));
    }
    return JArray.sparse(resultType, resultShape, resultFill, indices, resultValues);
  } catch (error) {
    if (error instanceof JError) {
      return null;
    }
    throw error;
  }
}

function applySparseMonadScalar(verb: Verb, y: JArray): JArray | null {
  const sparseY = sparseScalarOf(y);
  if (!sparseY) {
    return null;
  }
  try {
    const fillResult = verb.monadCell(scalarOf(sparseY.type, sparseY.sparseFill!));
    const valueResult = verb.monadCell(scalarOf(sparseY.type, sparseY.atomAt(0)));
    if (
      fillResult.rank !== 0 ||
      valueResult.rank !== 0 ||
      fillResult.type !== valueResult.type
    ) {
      return null;
    }
    return JArray.sparse(
      valueResult.type,
      [],
      fillResult.atomAt(0),
      [0],
      [valueResult.atomAt(0)],
    );
  } catch (error) {
    if (error instanceof JError) {
      return null;
    }
    throw error;
  }
}

function applySparseDyadScalar(verb: Verb, x: JArray, y: JArray): JArray | null {
  const sparseX = sparseScalarOf(x);
  const sparseY = sparseScalarOf(y);
  if (!sparseX || !sparseY) {
    return null;
  }
  try {
    const fillResult = verb.dyadCell(
      scalarOf(sparseX.type, sparseX.sparseFill!),
      scalarOf(sparseY.type, sparseY.sparseFill!),
    );
    const valueResult = verb.dyadCell(
      scalarOf(sparseX.type, sparseX.atomAt(0)),
      scalarOf(sparseY.type, sparseY.atomAt(0)),
    );
    if (
      fillResult.rank !== 0 ||
      valueResult.rank !== 0 ||
      fillResult.type !== valueResult.type
    ) {
      return null;
    }
    return JArray.sparse(
      valueResult.type,
      [],
      fillResult.atomAt(0),
      [0],
      [valueResult.atomAt(0)],
    );
  } catch (error) {
    if (error instanceof JError) {
      return null;
    }
    throw error;
  }
}

function sparseScalarOf(array: JArray): JArray | null {
  if (array.rank !== 0) {
    return null;
  }
  if (array.isSparse) {
    return array;
  }
  const fill = fillObject(array.type);
  const value = array.atomAt(0);
  if (JArray.atomEquals(fill, value)) {
    return JArray.sparse(array.type, [], fill, [], []);
  }
  return JArray.sparse(array.type, [], fill, [0], [value]);
}

function union(left: number[], right: number[]): number[] {
  return [...new Set([...left, ...right])].sort((a, b) => a - b);
}

function scalarOf(type: JType, atom: Atom): JArray {
  return JArray.of(type, [], [atom]);
}

function agree(xFrame: number[], yFrame: number[]): number[] {
  if (xFrame.length === 0) {
    return yFrame;
  }
  if (yFrame.length === 0) {
    return xFrame;
  }
  if (xFrame.length !== yFrame.length || xFrame.some((value, axis) => value !== yFrame[axis])) {
    throw new JError('length error');
  }
  return xFrame;
}

/**
 * Resolves a verb rank against an argument. A negative rank asks for
 * `argumentRank + rank` cell axes, clamped at zero.
 */
function resolveRank(rank: number, argumentRank: number): number {
  if (rank >= 0) {
    return rank;
  }
  return Math.max(0, argumentRank + rank);
}

function prototype(type: JType, shape: number[]): JArray {
  return JArray.fillScalar(type).reshape(shape);
}

function emptyResult(probe: JArray, frameShape: number[]): JArray {
  return JArray.empty(probe.type, concatShapes(frameShape, probe.shape));
}

