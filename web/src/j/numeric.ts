import { Complex, JError, JType, Rational, isExactType, isNumericType } from './atoms.ts';
import { JArray } from './array.ts';
import type { Atom } from './array.ts';

/** Shared numeric promotion, arithmetic and comparison for atom verbs. */
const TOLERANCE = Math.pow(2, -44);

export function requireNumeric(...arrays: JArray[]): void {
  for (const array of arrays) {
    if (!isNumericType(array.type)) {
      throw new JError('domain error');
    }
  }
}

type ArithmeticOp = 'add' | 'subtract' | 'multiply';

function arithmetic(op: ArithmeticOp, x: JArray, y: JArray): JArray {
  requireNumeric(x, y);

  if (x.type === JType.COMPLEX || y.type === JType.COMPLEX) {
    const left = x.complexAt(0);
    const right = y.complexAt(0);
    return JArray.scalar(
      JType.COMPLEX,
      op === 'add' ? left.add(right) : op === 'subtract' ? left.subtract(right) : left.multiply(right),
    );
  }
  if (x.type === JType.FLOAT || y.type === JType.FLOAT) {
    const left = x.numericAt(0);
    const right = y.numericAt(0);
    return JArray.scalar(
      JType.FLOAT,
      op === 'add' ? left + right : op === 'subtract' ? left - right : left * right,
    );
  }
  if (x.type === JType.RATIONAL || y.type === JType.RATIONAL) {
    const left = x.rationalAt(0);
    const right = y.rationalAt(0);
    const result =
      op === 'add' ? left.add(right) : op === 'subtract' ? left.subtract(right) : left.multiply(right);
    return rationalResult(result);
  }

  if (x.type === JType.INT && y.type === JType.INT) {
    const left = x.longAt(0);
    const right = y.longAt(0);
    const result = op === 'add' ? left + right : op === 'subtract' ? left - right : left * right;
    if (Number.isSafeInteger(result)) {
      return JArray.scalar(JType.INT, result);
    }
  }

  const left = x.bigAt(0);
  const right = y.bigAt(0);
  return exactResult(
    op === 'add' ? left + right : op === 'subtract' ? left - right : left * right,
  );
}

export function add(x: JArray, y: JArray): JArray {
  return arithmetic('add', x, y);
}

export function subtract(x: JArray, y: JArray): JArray {
  return arithmetic('subtract', x, y);
}

export function multiply(x: JArray, y: JArray): JArray {
  return arithmetic('multiply', x, y);
}

export function negate(y: JArray): JArray {
  requireNumeric(y);
  switch (y.type) {
    case JType.COMPLEX:
      return JArray.scalar(JType.COMPLEX, y.complexAt(0).negate());
    case JType.FLOAT:
      return JArray.scalar(JType.FLOAT, -y.doubleAt(0));
    case JType.RATIONAL:
      return JArray.scalar(JType.RATIONAL, y.rationalAt(0).negate());
    case JType.BIGINT:
      return exactResult(-y.bigAt(0));
    case JType.INT: {
      const value = y.longAt(0);
      return Number.isSafeInteger(-value)
        ? JArray.scalar(JType.INT, -value)
        : exactResult(-BigInt(value));
    }
    default:
      throw new JError('domain error');
  }
}

export function conjugate(y: JArray): JArray {
  requireNumeric(y);
  return y.type === JType.COMPLEX ? JArray.scalar(JType.COMPLEX, y.complexAt(0).conjugate()) : y;
}

export function signum(y: JArray): JArray {
  requireNumeric(y);
  switch (y.type) {
    case JType.COMPLEX: {
      const value = y.complexAt(0);
      const magnitude = value.magnitude();
      if (magnitude === 0) {
        return JArray.scalar(JType.INT, 0);
      }
      return JArray.scalar(JType.COMPLEX, new Complex(value.real / magnitude, value.imaginary / magnitude));
    }
    case JType.FLOAT: {
      const value = y.doubleAt(0);
      return Number.isNaN(value)
        ? JArray.scalar(JType.FLOAT, Number.NaN)
        : JArray.scalar(JType.INT, Math.sign(value));
    }
    case JType.RATIONAL:
      return JArray.scalar(JType.INT, y.rationalAt(0).signum());
    case JType.BIGINT:
      return JArray.scalar(JType.INT, Number(y.bigAt(0) > 0n ? 1 : y.bigAt(0) < 0n ? -1 : 0));
    case JType.INT:
      return JArray.scalar(JType.INT, Math.sign(y.longAt(0)));
    default:
      throw new JError('domain error');
  }
}

export function reciprocal(y: JArray): JArray {
  requireNumeric(y);
  if (y.type === JType.COMPLEX) {
    return JArray.scalar(JType.COMPLEX, Complex.ONE.divide(y.complexAt(0)));
  }
  return JArray.scalar(JType.FLOAT, 1 / y.numericAt(0));
}

export function divide(x: JArray, y: JArray): JArray {
  requireNumeric(x, y);
  if (x.type === JType.COMPLEX || y.type === JType.COMPLEX) {
    return JArray.scalar(JType.COMPLEX, x.complexAt(0).divide(y.complexAt(0)));
  }
  return JArray.scalar(JType.FLOAT, x.numericAt(0) / y.numericAt(0));
}

export function exp(y: JArray): JArray {
  requireNumeric(y);
  if (y.type === JType.COMPLEX) {
    const value = y.complexAt(0);
    const magnitude = Math.exp(value.real);
    return JArray.scalar(
      JType.COMPLEX,
      new Complex(magnitude * Math.cos(value.imaginary), magnitude * Math.sin(value.imaginary)),
    );
  }
  return JArray.scalar(JType.FLOAT, Math.exp(y.numericAt(0)));
}

export function power(x: JArray, y: JArray): JArray {
  requireNumeric(x, y);
  if (x.type === JType.COMPLEX || y.type === JType.COMPLEX) {
    return JArray.scalar(JType.COMPLEX, complexPower(x.complexAt(0), y.complexAt(0)));
  }
  if (isExactType(y.type) && isInteger(y)) {
    const exponent = exactLong(y);
    if (exponent < 0) {
      return JArray.scalar(JType.FLOAT, Math.pow(x.numericAt(0), exponent));
    }
    if (isExactType(x.type)) {
      return exactPower(x, exponent);
    }
    return JArray.scalar(JType.FLOAT, Math.pow(x.numericAt(0), exponent));
  }
  return JArray.scalar(JType.FLOAT, Math.pow(x.numericAt(0), y.numericAt(0)));
}

function exactPower(base: JArray, exponent: number): JArray {
  let result = Rational.ONE;
  let factor = base.rationalAt(0);
  let power = exponent;
  while (power > 0) {
    if (power % 2 === 1) {
      result = result.multiply(factor);
    }
    power = Math.floor(power / 2);
    if (power > 0) {
      factor = factor.multiply(factor);
    }
  }
  return rationalResult(result);
}

function complexPower(base: Complex, exponent: Complex): Complex {
  const magnitude = base.magnitude();
  if (magnitude === 0) {
    return Complex.ZERO;
  }
  const angle = Math.atan2(base.imaginary, base.real);
  const logMagnitude = Math.log(magnitude);
  const resultMagnitude = Math.exp(
    exponent.real * logMagnitude - exponent.imaginary * angle,
  );
  const resultAngle = exponent.real * angle + exponent.imaginary * logMagnitude;
  return new Complex(resultMagnitude * Math.cos(resultAngle), resultMagnitude * Math.sin(resultAngle));
}

export function increment(y: JArray): JArray {
  requireNumeric(y);
  return add(y, one(y.type));
}

export function decrement(y: JArray): JArray {
  requireNumeric(y);
  return subtract(y, one(y.type));
}

export function zero(type: JType): JArray {
  switch (type) {
    case JType.INT:
      return JArray.scalar(JType.INT, 0);
    case JType.BIGINT:
      return JArray.scalar(JType.BIGINT, 0n);
    case JType.RATIONAL:
      return JArray.scalar(JType.RATIONAL, Rational.ZERO);
    case JType.FLOAT:
      return JArray.scalar(JType.FLOAT, 0);
    case JType.COMPLEX:
      return JArray.scalar(JType.COMPLEX, Complex.ZERO);
    default:
      throw new JError('domain error');
  }
}

export function one(type: JType): JArray {
  switch (type) {
    case JType.INT:
      return JArray.scalar(JType.INT, 1);
    case JType.BIGINT:
      return JArray.scalar(JType.BIGINT, 1n);
    case JType.RATIONAL:
      return JArray.scalar(JType.RATIONAL, Rational.ONE);
    case JType.FLOAT:
      return JArray.scalar(JType.FLOAT, 1);
    case JType.COMPLEX:
      return JArray.scalar(JType.COMPLEX, Complex.ONE);
    default:
      throw new JError('domain error');
  }
}

export function isNaNValue(y: JArray): boolean {
  if (y.type === JType.FLOAT) {
    return Number.isNaN(y.doubleAt(0));
  }
  if (y.type === JType.COMPLEX) {
    const value = y.complexAt(0);
    return Number.isNaN(value.real) || Number.isNaN(value.imaginary);
  }
  return false;
}

/** Compares two numeric atoms, returning a negative, zero or positive number. */
export function compare(x: JArray, y: JArray): number {
  requireNumeric(x, y);
  if (x.type === JType.COMPLEX || y.type === JType.COMPLEX) {
    throw new JError('domain error');
  }
  if (isExactType(x.type) && isExactType(y.type)) {
    const left = x.rationalAt(0);
    const right = y.rationalAt(0);
    const difference = left.numerator * right.denominator - right.numerator * left.denominator;
    return difference === 0n ? 0 : difference < 0n ? -1 : 1;
  }
  const left = x.numericAt(0);
  const right = y.numericAt(0);
  if (left === right) {
    return 0;
  }
  return left < right ? -1 : 1;
}

export function equal(x: JArray, y: JArray): boolean {
  requireNumeric(x, y);
  if (x.type === JType.COMPLEX || y.type === JType.COMPLEX) {
    const left = x.complexAt(0);
    const right = y.complexAt(0);
    return tolerant(left.real, right.real) && tolerant(left.imaginary, right.imaginary);
  }
  if (isExactType(x.type) && isExactType(y.type)) {
    return compare(x, y) === 0;
  }
  const left = x.numericAt(0);
  const right = y.numericAt(0);
  if (Number.isNaN(left) || Number.isNaN(right)) {
    return false;
  }
  return tolerant(left, right);
}

function tolerant(left: number, right: number): boolean {
  if (Number.isNaN(left) || Number.isNaN(right)) {
    return false;
  }
  if (left === right) {
    return true;
  }
  if (!Number.isFinite(left) || !Number.isFinite(right)) {
    return false;
  }
  return Math.abs(left - right) <= TOLERANCE * Math.max(Math.abs(left), Math.abs(right));
}

/** Atom-level equality used when comparing items of any type. */
export function equalAtoms(left: Atom, right: Atom): boolean {
  if (typeof left === 'string' || typeof right === 'string') {
    return left === right;
  }
  if (left instanceof JArray || right instanceof JArray) {
    if (!(left instanceof JArray) || !(right instanceof JArray)) {
      return false;
    }
    return itemsEqual(left, right);
  }
  if (left instanceof Complex || right instanceof Complex) {
    const a = complexAtom(left);
    const b = complexAtom(right);
    return tolerant(a.real, b.real) && tolerant(a.imaginary, b.imaginary);
  }
  if (typeof left === 'number' || typeof right === 'number') {
    return tolerant(doubleAtom(left), doubleAtom(right));
  }
  const a = rationalAtom(left);
  const b = rationalAtom(right);
  return a.numerator * b.denominator === b.numerator * a.denominator;
}

function complexAtom(atom: Atom): Complex {
  if (atom instanceof Complex) {
    return atom;
  }
  return new Complex(doubleAtom(atom), 0);
}

function doubleAtom(atom: Atom): number {
  if (typeof atom === 'number') {
    return atom;
  }
  if (typeof atom === 'bigint') {
    return Number(atom);
  }
  if (atom instanceof Rational) {
    return atom.toDouble();
  }
  throw new Error('not a real numeric atom');
}

function rationalAtom(atom: Atom): Rational {
  if (typeof atom === 'number') {
    return new Rational(BigInt(atom), 1n);
  }
  if (typeof atom === 'bigint') {
    return new Rational(atom, 1n);
  }
  if (atom instanceof Rational) {
    return atom;
  }
  throw new Error('not an exact numeric atom');
}

export function isInteger(y: JArray): boolean {
  switch (y.type) {
    case JType.INT:
    case JType.BIGINT:
      return true;
    case JType.RATIONAL:
      return y.rationalAt(0).isInteger();
    default:
      return false;
  }
}

export function exactLong(y: JArray): number {
  return exactLongAt(y, 0);
}

export function exactLongAt(array: JArray, index: number): number {
  switch (array.type) {
    case JType.INT:
      return array.longAt(index);
    case JType.BIGINT:
      return safeBigToNumber(array.bigAt(index));
    case JType.RATIONAL: {
      const value = array.rationalAt(index);
      if (!value.isInteger()) {
        throw new JError('domain error');
      }
      return safeBigToNumber(value.numerator);
    }
    default:
      throw new JError('domain error');
  }
}

function safeBigToNumber(value: bigint): number {
  if (
    value < BigInt(Number.MIN_SAFE_INTEGER) ||
    value > BigInt(Number.MAX_SAFE_INTEGER)
  ) {
    throw new JError('limit error');
  }
  return Number(value);
}

function exactResult(value: bigint): JArray {
  if (value >= BigInt(Number.MIN_SAFE_INTEGER) && value <= BigInt(Number.MAX_SAFE_INTEGER)) {
    return JArray.scalar(JType.INT, Number(value));
  }
  return JArray.scalar(JType.BIGINT, value);
}

function rationalResult(value: Rational): JArray {
  return value.isInteger() ? exactResult(value.numerator) : JArray.scalar(JType.RATIONAL, value);
}

// ------------------------------------------------------------------- items

/** Item-level equality and classification. */
export function itemsEqual(a: JArray, b: JArray): boolean {
  if (!a.sameShape(b)) {
    return false;
  }
  if (a.type === JType.BOX || b.type === JType.BOX) {
    if (a.type !== JType.BOX || b.type !== JType.BOX) {
      return false;
    }
    for (let atom = 0; atom < a.size; atom++) {
      if (!itemsEqual(a.boxAt(atom), b.boxAt(atom))) {
        return false;
      }
    }
    return true;
  }
  if (a.type === JType.CHAR || b.type === JType.CHAR) {
    if (a.type !== JType.CHAR || b.type !== JType.CHAR) {
      return false;
    }
    for (let atom = 0; atom < a.size; atom++) {
      if (a.charAt(atom) !== b.charAt(atom)) {
        return false;
      }
    }
    return true;
  }
  if (!isNumericType(a.type) || !isNumericType(b.type)) {
    return false;
  }
  for (let atom = 0; atom < a.size; atom++) {
    if (!equalAtoms(a.atomAt(atom), b.atomAt(atom))) {
      return false;
    }
  }
  return true;
}

export function uniqueItems(y: JArray): JArray[] {
  const unique: JArray[] = [];
  for (let item = 0; item < y.itemCount; item++) {
    const candidate = y.item(item);
    if (!unique.some((known) => itemsEqual(known, candidate))) {
      unique.push(candidate);
    }
  }
  return unique;
}

/** `~: y`: true at the first occurrence of each distinct item. */
export function nubSieve(y: JArray): JArray {
  const unique: JArray[] = [];
  const flags: number[] = new Array(y.itemCount).fill(0);
  for (let item = 0; item < flags.length; item++) {
    const candidate = y.item(item);
    const seen = unique.some((known) => itemsEqual(known, candidate));
    flags[item] = seen ? 0 : 1;
    if (!seen) {
      unique.push(candidate);
    }
  }
  return JArray.intVector(flags);
}

/** `= y`: rows are nub items, columns are items of y. */
export function selfClassify(y: JArray): JArray {
  const unique = uniqueItems(y);
  const items = y.itemCount;
  const table = new Array<number>(unique.length * items);
  let at = 0;
  for (const row of unique) {
    for (let item = 0; item < items; item++) {
      table[at++] = itemsEqual(row, y.item(item)) ? 1 : 0;
    }
  }
  return JArray.ints([unique.length, items], table);
}

export function firstIndex(x: JArray, cell: JArray): number {
  for (let item = 0; item < x.itemCount; item++) {
    if (itemsEqual(x.item(item), cell)) {
      return item;
    }
  }
  return x.itemCount;
}
