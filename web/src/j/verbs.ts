import { JError, JType, MAX_ARRAY_SIZE, isExactType, sizeOf } from './atoms.ts';
import { checkBudget } from './budget.ts';
import { JArray, convertAtom, promote } from './array.ts';
import { stackAssembly } from './assembly.ts';
import {
  add,
  compare,
  conjugate,
  decrement,
  divide,
  equal,
  exp,
  exactLong,
  exactLongAt,
  firstIndex,
  increment,
  isNaNValue,
  itemsEqual,
  multiply,
  negate,
  nubSieve,
  one,
  power,
  reciprocal,
  selfClassify,
  signum,
  subtract,
  zero,
} from './numeric.ts';

/** A rank meaning "the whole argument". */
export const WHOLE = Number.MAX_SAFE_INTEGER;

/** Requires a scalar or vector of exact integers (an index or shape spec). */
function requireExactVector(x: JArray): void {
  if (!isExactType(x.type)) {
    throw new JError('domain error');
  }
  if (x.rank > 1) {
    throw new JError('rank error');
  }
}

/** Reads exact item counts and checks that there is one per leading axis. */
function axisCounts(x: JArray, rank: number): number[] {
  const counts = new Array<number>(x.size);
  for (let axis = 0; axis < counts.length; axis++) {
    counts[axis] = exactLongAt(x, axis);
  }
  if (counts.length > rank) {
    throw new JError('rank error');
  }
  return counts;
}

/**
 * A J verb.
 *
 * Every verb is ambivalent: it has monadic and dyadic definitions, selected by
 * the evaluator from the sentence structure. A verb declares its ranks; the
 * rank machinery applies its cell function across argument frames. A valence
 * the verb does not support throws `valence error`.
 */
export abstract class Verb {
  private readonly reprText: string;

  constructor(repr: string) {
    this.reprText = repr;
  }

  /** The verb's J spelling, used when a verb value is displayed. */
  repr(): string {
    return this.reprText;
  }

  abstract monadRank(): number;

  abstract leftRank(): number;

  abstract rightRank(): number;

  monadCell(_y: JArray): JArray {
    throw new JError('valence error');
  }

  dyadCell(_x: JArray, _y: JArray): JArray {
    throw new JError('valence error');
  }

  /** Identity used by insert over an empty argument. */
  identity(_type: JType): JArray {
    throw new JError('domain error');
  }
}

/** Base class for verbs whose cell function works on atoms (rank 0). */
export abstract class AtomVerb extends Verb {
  monadRank(): number {
    return 0;
  }

  leftRank(): number {
    return 0;
  }

  rightRank(): number {
    return 0;
  }
}

// --------------------------------------------------------------- arithmetic

export class Plus extends AtomVerb {
  constructor() {
    super('+');
  }

  monadCell(y: JArray): JArray {
    return conjugate(y);
  }

  dyadCell(x: JArray, y: JArray): JArray {
    return add(x, y);
  }

  identity(type: JType): JArray {
    return zero(type);
  }
}

export class Minus extends AtomVerb {
  constructor() {
    super('-');
  }

  monadCell(y: JArray): JArray {
    return negate(y);
  }

  dyadCell(x: JArray, y: JArray): JArray {
    return subtract(x, y);
  }

  identity(type: JType): JArray {
    return zero(type);
  }
}

export class Times extends AtomVerb {
  constructor() {
    super('*');
  }

  monadCell(y: JArray): JArray {
    return signum(y);
  }

  dyadCell(x: JArray, y: JArray): JArray {
    return multiply(x, y);
  }

  identity(type: JType): JArray {
    return one(type);
  }
}

export class Divide extends AtomVerb {
  constructor() {
    super('%');
  }

  monadCell(y: JArray): JArray {
    return reciprocal(y);
  }

  dyadCell(x: JArray, y: JArray): JArray {
    return divide(x, y);
  }

  identity(type: JType): JArray {
    return one(type);
  }
}

export class Power extends AtomVerb {
  constructor() {
    super('^');
  }

  monadCell(y: JArray): JArray {
    return exp(y);
  }

  dyadCell(x: JArray, y: JArray): JArray {
    return power(x, y);
  }

  identity(type: JType): JArray {
    return one(type);
  }
}

// -------------------------------------------------------------- comparisons

type ComparisonKind = 'equal' | 'notEqual' | 'less' | 'greater' | 'lessEqual' | 'greaterEqual';

function comparisonTest(kind: ComparisonKind, x: JArray, y: JArray): JArray {
  let result: boolean;
  if (x.type === JType.CHAR || y.type === JType.CHAR) {
    if (x.type !== JType.CHAR || y.type !== JType.CHAR) {
      throw new JError('domain error');
    }
    result = fromComparison(x.charAt(0) < y.charAt(0) ? -1 : x.charAt(0) > y.charAt(0) ? 1 : 0, kind);
  } else if (x.type === JType.BOX || y.type === JType.BOX) {
    if (x.type !== JType.BOX || y.type !== JType.BOX) {
      throw new JError('domain error');
    }
    if (kind !== 'equal' && kind !== 'notEqual') {
      throw new JError('domain error');
    }
    result = itemsEqual(x.boxAt(0), y.boxAt(0));
    if (kind === 'notEqual') {
      result = !result;
    }
  } else {
    if (kind === 'equal' || kind === 'notEqual') {
      result = equal(x, y);
      if (kind === 'notEqual') {
        result = !result;
      }
    } else if (isNaNValue(x) || isNaNValue(y)) {
      result = false;
    } else {
      result = fromComparison(compare(x, y), kind);
    }
  }
  return JArray.scalar(JType.INT, result ? 1 : 0);
}

function fromComparison(comparison: number, kind: ComparisonKind): boolean {
  switch (kind) {
    case 'equal':
      return comparison === 0;
    case 'notEqual':
      return comparison !== 0;
    case 'less':
      return comparison < 0;
    case 'greater':
      return comparison > 0;
    case 'lessEqual':
      return comparison <= 0;
    case 'greaterEqual':
      return comparison >= 0;
  }
}

export class Equal extends Verb {
  constructor() {
    super('=');
  }

  monadRank(): number {
    return WHOLE;
  }

  leftRank(): number {
    return 0;
  }

  rightRank(): number {
    return 0;
  }

  monadCell(y: JArray): JArray {
    return selfClassify(y);
  }

  dyadCell(x: JArray, y: JArray): JArray {
    return comparisonTest('equal', x, y);
  }

  identity(type: JType): JArray {
    return one(type);
  }
}

export class NotEqual extends Verb {
  constructor() {
    super('~:');
  }

  monadRank(): number {
    return WHOLE;
  }

  leftRank(): number {
    return 0;
  }

  rightRank(): number {
    return 0;
  }

  monadCell(y: JArray): JArray {
    return nubSieve(y);
  }

  dyadCell(x: JArray, y: JArray): JArray {
    return comparisonTest('notEqual', x, y);
  }

  identity(type: JType): JArray {
    return zero(type);
  }
}

export class Less extends Verb {
  constructor() {
    super('<');
  }

  monadRank(): number {
    return WHOLE;
  }

  leftRank(): number {
    return 0;
  }

  rightRank(): number {
    return 0;
  }

  monadCell(y: JArray): JArray {
    return JArray.scalar(JType.BOX, y);
  }

  dyadCell(x: JArray, y: JArray): JArray {
    return comparisonTest('less', x, y);
  }

  identity(type: JType): JArray {
    return zero(type);
  }
}

export class Greater extends Verb {
  constructor() {
    super('>');
  }

  monadRank(): number {
    return WHOLE;
  }

  leftRank(): number {
    return 0;
  }

  rightRank(): number {
    return 0;
  }

  monadCell(y: JArray): JArray {
    if (y.type !== JType.BOX) {
      return y;
    }
    if (y.rank === 0) {
      return y.boxAt(0);
    }
    if (y.isEmpty) {
      return JArray.empty(JType.INT, y.shape);
    }
    const contents: JArray[] = [];
    for (let atom = 0; atom < y.size; atom++) {
      contents.push(y.boxAt(atom));
    }
    return stackAssembly(y.shape, contents);
  }

  dyadCell(x: JArray, y: JArray): JArray {
    return comparisonTest('greater', x, y);
  }

  identity(type: JType): JArray {
    return zero(type);
  }
}

export class LessEqual extends AtomVerb {
  constructor() {
    super('<:');
  }

  monadCell(y: JArray): JArray {
    return decrement(y);
  }

  dyadCell(x: JArray, y: JArray): JArray {
    return comparisonTest('lessEqual', x, y);
  }

  identity(type: JType): JArray {
    return one(type);
  }
}

export class GreaterEqual extends AtomVerb {
  constructor() {
    super('>:');
  }

  monadCell(y: JArray): JArray {
    return increment(y);
  }

  dyadCell(x: JArray, y: JArray): JArray {
    return comparisonTest('greaterEqual', x, y);
  }

  identity(type: JType): JArray {
    return one(type);
  }
}

// ---------------------------------------------------------------- structure

export class Iota extends Verb {
  constructor() {
    super('i.');
  }

  monadRank(): number {
    return 1;
  }

  leftRank(): number {
    return WHOLE;
  }

  rightRank(): number {
    return WHOLE;
  }

  monadCell(y: JArray): JArray {
    if (!isExactType(y.type)) {
      throw new JError('domain error');
    }
    if (y.rank === 0) {
      return scalarIota(exactLong(y));
    }
    if (y.rank !== 1) {
      throw new JError('rank error');
    }

    const shape = new Array<number>(y.size);
    for (let axis = 0; axis < shape.length; axis++) {
      const dimension = exactLongAt(y, axis);
      if (dimension < 0) {
        throw new JError('domain error');
      }
      shape[axis] = dimension;
    }

    const size = sizeOf(shape);
    checkBudget();
    const data = Array.from({ length: size }, (_, atom) => atom);
    return JArray.ints(shape, data);
  }

  dyadCell(x: JArray, y: JArray): JArray {
    const itemRank = Math.max(0, x.rank - 1);
    const cellRank = Math.min(itemRank, y.rank);
    const frameShape = y.shape.slice(0, y.rank - cellRank);
    const cellShape = y.shape.slice(frameShape.length);
    const frameSize = sizeOf(frameShape);
    const cellSize = sizeOf(cellShape);

    const indexes = new Array<number>(frameSize);
    for (let cell = 0; cell < frameSize; cell++) {
      indexes[cell] = firstIndex(x, y.slice(cell * cellSize, cellShape));
    }
    return JArray.ints(frameShape, indexes);
  }
}

function scalarIota(limit: number): JArray {
  if (limit === 0) {
    return JArray.intVector([]);
  }
  if (Math.abs(limit) > MAX_ARRAY_SIZE) {
    throw new JError('limit error');
  }
  const size = Math.abs(limit);
  checkBudget();
  const data = new Array<number>(size);
  if (limit > 0) {
    for (let atom = 0; atom < size; atom++) {
      checkBudget();
      data[atom] = atom;
    }
  } else {
    for (let atom = 0; atom < size; atom++) {
      checkBudget();
      data[atom] = size - 1 - atom;
    }
  }
  return JArray.intVector(data);
}

export class Shape extends Verb {
  constructor() {
    super('$');
  }

  monadRank(): number {
    return WHOLE;
  }

  leftRank(): number {
    return 1;
  }

  rightRank(): number {
    return WHOLE;
  }

  monadCell(y: JArray): JArray {
    return JArray.intVector(y.shape);
  }

  dyadCell(x: JArray, y: JArray): JArray {
    requireExactVector(x);
    const shape = new Array<number>(x.size);
    for (let axis = 0; axis < shape.length; axis++) {
      const dimension = exactLongAt(x, axis);
      if (dimension < 0) {
        throw new JError('domain error');
      }
      shape[axis] = dimension;
    }
    return y.reshape(shape);
  }
}

export class Tally extends Verb {
  constructor() {
    super('#');
  }

  monadRank(): number {
    return WHOLE;
  }

  leftRank(): number {
    return 1;
  }

  rightRank(): number {
    return WHOLE;
  }

  monadCell(y: JArray): JArray {
    return JArray.scalar(JType.INT, y.itemCount);
  }

  dyadCell(x: JArray, y: JArray): JArray {
    if (x.rank > 1) {
      throw new JError('rank error');
    }

    if (x.size === 0) {
      if (y.rank === 0) {
        return JArray.empty(y.type, [0]);
      }
      if (y.itemCount === 0) {
        return y.copyItems([]);
      }
      throw new JError('length error');
    }

    const copies = new Array<number>(y.itemCount);
    const fills = new Array<number>(y.itemCount);
    if (x.rank === 0) {
      readCount(x, 0, copies, fills);
      copies.fill(copies[0]);
      fills.fill(fills[0]);
    } else {
      if (x.size !== copies.length) {
        throw new JError('length error');
      }
      for (let item = 0; item < copies.length; item++) {
        readCount(x, item, copies, fills);
      }
    }

    return y.copyItems(copies, fills);
  }
}

function readCount(x: JArray, index: number, copies: number[], fills: number[]): void {
  if (x.type === JType.COMPLEX) {
    const count = x.complexAt(index);
    copies[index] = integerCountFromDouble(count.real);
    fills[index] = integerCountFromDouble(count.imaginary);
    return;
  }
  if (!isExactType(x.type)) {
    throw new JError('domain error');
  }
  copies[index] = integerCount(exactLongAt(x, index));
  fills[index] = 0;
}

function integerCountFromDouble(value: number): number {
  if (!Number.isFinite(value) || value !== Math.round(value)) {
    throw new JError('domain error');
  }
  return integerCount(value);
}

function integerCount(value: number): number {
  if (value < 0) {
    throw new JError('domain error');
  }
  if (value > MAX_ARRAY_SIZE) {
    throw new JError('limit error');
  }
  return value;
}

export class Ravel extends Verb {
  constructor() {
    super(',');
  }

  monadRank(): number {
    return WHOLE;
  }

  leftRank(): number {
    return WHOLE;
  }

  rightRank(): number {
    return WHOLE;
  }

  monadCell(y: JArray): JArray {
    return y.ravel();
  }

  dyadCell(x: JArray, y: JArray): JArray {
    return x.append(y);
  }
}

export class Reverse extends Verb {
  constructor() {
    super('|.');
  }

  monadRank(): number {
    return WHOLE;
  }

  leftRank(): number {
    return 1;
  }

  rightRank(): number {
    return WHOLE;
  }

  monadCell(y: JArray): JArray {
    return y.reverseItems();
  }

  dyadCell(x: JArray, y: JArray): JArray {
    requireExactVector(x);
    if (x.rank === 0) {
      return y.rotateItems(exactLong(x));
    }
    return y.rotateAxes(axisCounts(x, y.rank));
  }
}

export class From extends Verb {
  constructor() {
    super('{');
  }

  monadRank(): number {
    return 1;
  }

  leftRank(): number {
    return WHOLE;
  }

  rightRank(): number {
    return WHOLE;
  }

  monadCell(y: JArray): JArray {
    if (y.type !== JType.BOX) {
      throw new JError('domain error');
    }
    if (y.rank === 0) {
      return y.boxAt(0);
    }
    if (y.rank !== 1) {
      throw new JError('rank error');
    }
    return catalog(y);
  }

  dyadCell(x: JArray, y: JArray): JArray {
    requireExactVector(x);
    const items = y.itemCount;
    const selected: JArray[] = [];
    for (let atom = 0; atom < x.size; atom++) {
      checkBudget();
      const index = exactLongAt(x, atom);
      if (index < -items || index >= items) {
        throw new JError('index error');
      }
      selected.push(y.item(index < 0 ? items + index : index));
    }
    const resultShape = [...x.shape, ...y.itemShape()];
    if (selected.length === 0) {
      return JArray.empty(y.type, resultShape);
    }
    return stackAssembly(x.shape, selected);
  }
}

function catalog(boxes: JArray): JArray {
  const columns = boxes.size;
  if (columns === 0) {
    return JArray.empty(JType.INT, [1, 0]);
  }

  const contents: JArray[] = [];
  let type: JType | null = null;
  let combinations = 1;
  for (let box = 0; box < columns; box++) {
    const content = boxes.boxAt(box).ravel();
    contents.push(content);
    type = type === null ? content.type : promote(type, content.type);
    if (content.isEmpty) {
      combinations = 0;
    } else if (combinations !== 0) {
      const product = combinations * content.size;
      if (product > MAX_ARRAY_SIZE) {
        throw new JError('limit error');
      }
      combinations = product;
    }
  }

  const shape = [combinations, columns];
  const result = new Array(combinations * columns);
  for (let combination = 0; combination < combinations; combination++) {
    checkBudget();
    let remaining = combination;
    for (let column = columns - 1; column >= 0; column--) {
      const content = contents[column];
      const index = remaining % content.size;
      remaining = Math.floor(remaining / content.size);
      result[combination * columns + column] = convertAtom(content.atomAt(index), type!);
    }
  }
  return JArray.of(type!, shape, result);
}

export class Take extends Verb {
  constructor() {
    super('{.');
  }

  monadRank(): number {
    return WHOLE;
  }

  leftRank(): number {
    return 1;
  }

  rightRank(): number {
    return WHOLE;
  }

  monadCell(y: JArray): JArray {
    if (y.rank === 0) {
      return y;
    }
    if (y.itemCount === 0) {
      const fill = JArray.fillScalar(y.type);
      return y.rank > 1 ? fill.reshape(y.itemShape()) : fill;
    }
    return y.item(0);
  }

  dyadCell(x: JArray, y: JArray): JArray {
    requireExactVector(x);
    const source = y.rank === 0 ? y.ravel() : y;
    if (x.rank === 0) {
      return source.takeItems(exactLong(x));
    }
    return source.takeAxes(axisCounts(x, source.rank));
  }
}

export class Drop extends Verb {
  constructor() {
    super('}.');
  }

  monadRank(): number {
    return WHOLE;
  }

  leftRank(): number {
    return 1;
  }

  rightRank(): number {
    return WHOLE;
  }

  monadCell(y: JArray): JArray {
    if (y.rank === 0) {
      return JArray.empty(y.type, [0]);
    }
    return y.dropItems(1);
  }

  dyadCell(x: JArray, y: JArray): JArray {
    requireExactVector(x);
    const source = y.rank === 0 ? y.ravel() : y;
    if (x.rank === 0) {
      return source.dropItems(exactLong(x));
    }
    return source.dropAxes(axisCounts(x, source.rank));
  }
}

export class Transpose extends Verb {
  constructor() {
    super('|:');
  }

  monadRank(): number {
    return WHOLE;
  }

  leftRank(): number {
    return WHOLE;
  }

  rightRank(): number {
    return WHOLE;
  }

  monadCell(y: JArray): JArray {
    return y.transpose();
  }

  dyadCell(x: JArray, y: JArray): JArray {
    requireExactVector(x);
    if (y.rank < 2) {
      return y;
    }
    const axes = Array.from({ length: y.rank }, (_, axis) => axis);
    for (let atom = 0; atom < x.size; atom++) {
      const axis = exactLongAt(x, atom);
      if (axis < 0 || axis >= y.rank) {
        throw new JError('index error');
      }
      const position = axes.indexOf(axis);
      if (position < 0) {
        throw new JError('domain error');
      }
      axes.splice(position, 1);
      axes.push(axis);
    }
    return y.transposeAxes(axes);
  }
}

export class SparseVerb extends Verb {
  constructor() {
    super('$.');
  }

  monadRank(): number {
    return WHOLE;
  }

  leftRank(): number {
    return WHOLE;
  }

  rightRank(): number {
    return WHOLE;
  }

  monadCell(y: JArray): JArray {
    return y.isSparse ? y : y.toSparse();
  }

  dyadCell(x: JArray, y: JArray): JArray {
    if (!isExactType(x.type) || !x.isScalar || exactLong(x) !== 0) {
      throw new JError('domain error');
    }
    return y.toggleSparse();
  }
}

export class SparseInverseVerb extends Verb {
  constructor() {
    super('$.^:_1');
  }

  monadRank(): number {
    return WHOLE;
  }

  leftRank(): number {
    return WHOLE;
  }

  rightRank(): number {
    return WHOLE;
  }

  monadCell(y: JArray): JArray {
    return y.dense();
  }
}

// --------------------------------------------------------------- primitives

const VERBS = new Map<string, Verb>([
  ['+', new Plus()],
  ['-', new Minus()],
  ['*', new Times()],
  ['%', new Divide()],
  ['^', new Power()],
  ['=', new Equal()],
  ['<', new Less()],
  ['>', new Greater()],
  ['<:', new LessEqual()],
  ['>:', new GreaterEqual()],
  ['~:', new NotEqual()],
  ['i.', new Iota()],
  ['$', new Shape()],
  ['$.', new SparseVerb()],
  ['$.^:_1', new SparseInverseVerb()],
  ['#', new Tally()],
  ['|.', new Reverse()],
  [',', new Ravel()],
  ['{', new From()],
  ['{.', new Take()],
  ['}.', new Drop()],
  ['|:', new Transpose()],
]);

export function lookupPrimitive(spelling: string): Verb | null {
  return VERBS.get(spelling) ?? null;
}

