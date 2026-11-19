/**
 * Core value types and shape arithmetic for the TypeScript J interpreter.
 *
 * A J noun is an array of atoms. Numeric atoms form the promotion order
 * INT < BIGINT < RATIONAL < FLOAT < COMPLEX. Characters are one-character
 * strings and boxes hold nested arrays, so an atom is one of:
 *
 *   number  -> INT or FLOAT (the array's JType says which)
 *   bigint  -> BIGINT
 *   Rational, Complex
 *   string  -> CHAR
 *   JArray  -> BOX
 */

export const JType = {
  INT: 'INT',
  BIGINT: 'BIGINT',
  RATIONAL: 'RATIONAL',
  FLOAT: 'FLOAT',
  COMPLEX: 'COMPLEX',
  CHAR: 'CHAR',
  BOX: 'BOX',
} as const;

export type JType = (typeof JType)[keyof typeof JType];

/** Numeric atoms only: characters and boxes are separate domains. */
export type ScalarAtom = number | bigint | Rational | Complex | string;

/** An error reported to the J user, such as `domain error`. */
export class JError extends Error {
  /** 1-based column for lexical/syntax errors, otherwise 0. */
  readonly column: number;

  constructor(message: string, column = 0) {
    super(message);
    this.name = 'JError';
    this.column = column;
  }
}

export function isNumericType(type: JType): boolean {
  return type !== JType.CHAR && type !== JType.BOX;
}

export function isExactType(type: JType): boolean {
  return type === JType.INT || type === JType.BIGINT || type === JType.RATIONAL;
}

export function numericRank(type: JType): number {
  switch (type) {
    case JType.INT:
      return 0;
    case JType.BIGINT:
      return 1;
    case JType.RATIONAL:
      return 2;
    case JType.FLOAT:
      return 3;
    case JType.COMPLEX:
      return 4;
    default:
      return -1;
  }
}

function gcd(a: bigint, b: bigint): bigint {
  while (b !== 0n) {
    const next = a % b;
    a = b;
    b = next;
  }
  return a;
}

/** An exact J rational atom, normalized with a positive denominator. */
export class Rational {
  readonly numerator: bigint;
  readonly denominator: bigint;

  static readonly ZERO = new Rational(0n, 1n);
  static readonly ONE = new Rational(1n, 1n);

  constructor(numerator: bigint, denominator: bigint) {
    if (denominator === 0n) {
      throw new JError('domain error');
    }
    if (denominator < 0n) {
      numerator = -numerator;
      denominator = -denominator;
    }
    const factor = gcd(numerator < 0n ? -numerator : numerator, denominator);
    this.numerator = numerator / factor;
    this.denominator = denominator / factor;
  }

  add(other: Rational): Rational {
    return new Rational(
      this.numerator * other.denominator + other.numerator * this.denominator,
      this.denominator * other.denominator,
    );
  }

  subtract(other: Rational): Rational {
    return new Rational(
      this.numerator * other.denominator - other.numerator * this.denominator,
      this.denominator * other.denominator,
    );
  }

  multiply(other: Rational): Rational {
    return new Rational(this.numerator * other.numerator, this.denominator * other.denominator);
  }

  negate(): Rational {
    return new Rational(-this.numerator, this.denominator);
  }

  isInteger(): boolean {
    return this.denominator === 1n;
  }

  signum(): number {
    return this.numerator === 0n ? 0 : this.numerator < 0n ? -1 : 1;
  }

  toDouble(): number {
    const negative = this.numerator < 0n;
    const numerator = negative ? -this.numerator : this.numerator;
    const whole = numerator / this.denominator;
    const remainder = numerator % this.denominator;
    const scale = 10n ** 18n;
    const fraction = Number((remainder * scale) / this.denominator) / 1e18;
    const value = Number(whole) + fraction;
    return negative ? -value : value;
  }
}

/** A complex J atom, with double-precision parts. */
export class Complex {
  readonly real: number;
  readonly imaginary: number;

  static readonly ZERO = new Complex(0, 0);
  static readonly ONE = new Complex(1, 0);

  constructor(real: number, imaginary: number) {
    this.real = real;
    this.imaginary = imaginary;
  }

  add(other: Complex): Complex {
    return new Complex(this.real + other.real, this.imaginary + other.imaginary);
  }

  subtract(other: Complex): Complex {
    return new Complex(this.real - other.real, this.imaginary - other.imaginary);
  }

  multiply(other: Complex): Complex {
    return new Complex(
      this.real * other.real - this.imaginary * other.imaginary,
      this.real * other.imaginary + this.imaginary * other.real,
    );
  }

  divide(other: Complex): Complex {
    const denominator = other.real * other.real + other.imaginary * other.imaginary;
    return new Complex(
      (this.real * other.real + this.imaginary * other.imaginary) / denominator,
      (this.imaginary * other.real - this.real * other.imaginary) / denominator,
    );
  }

  negate(): Complex {
    return new Complex(-this.real, -this.imaginary);
  }

  conjugate(): Complex {
    return new Complex(this.real, -this.imaginary);
  }

  magnitude(): number {
    return Math.hypot(this.real, this.imaginary);
  }

}

/**
 * Browser-friendly ceiling for materialized arrays. The Java interpreter
 * limits itself to Integer.MAX_VALUE atoms; a demo does not need that much
 * memory, so oversized results report a J limit error instead.
 */
export const MAX_ARRAY_SIZE = 10_000_000;

/** Number of atoms in an array of the given shape (the product of its axes). */
export function sizeOf(shape: readonly number[]): number {
  let size = 1;
  for (const dimension of shape) {
    if (!Number.isInteger(dimension) || dimension < 0) {
      throw new JError('domain error');
    }
    size *= dimension;
    if (size > MAX_ARRAY_SIZE) {
      throw new JError('limit error');
    }
  }
  return size;
}

/** Row-major strides; the last axis has stride 1. */
export function stridesOf(shape: readonly number[]): number[] {
  const strides = new Array<number>(shape.length);
  let stride = 1;
  for (let axis = shape.length - 1; axis >= 0; axis--) {
    strides[axis] = stride;
    stride *= shape[axis];
  }
  return strides;
}

export function concatShapes(first: readonly number[], second: readonly number[]): number[] {
  return [...first, ...second];
}
