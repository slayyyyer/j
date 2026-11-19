import {
  Complex,
  JError,
  JType,
  MAX_ARRAY_SIZE,
  Rational,
  isNumericType,
  numericRank,
  sizeOf,
  stridesOf,
} from './atoms.ts';
import type { ScalarAtom } from './atoms.ts';
import { Sparse } from './sparse.ts';

/** Any value stored in one cell of an array. */
export type Atom = ScalarAtom | JArray;

/**
 * A J noun: flat, row-major storage plus a shape.
 *
 * Dense arrays keep an Atom[] of row-major atoms; sparse arrays keep a fill
 * plus sorted index/value entries. Arrays are immutable by convention:
 * shapes are copied on construction, and every structural operation builds a
 * new array.
 */
export class JArray {
  readonly type: JType;
  readonly shape: number[];
  readonly size: number;
  private readonly data: Atom[] | null;
  private readonly sparseStorage: Sparse | null;

  constructor(type: JType, shape: number[], data: Atom[] | Sparse) {
    const expected = sizeOf(shape);
    this.type = type;
    this.shape = [...shape];
    this.size = expected;
    if (data instanceof Sparse) {
      this.sparseStorage = data;
      this.data = null;
      for (const index of data.indices) {
        if (index < 0 || index >= expected) {
          throw new Error('sparse index out of bounds');
        }
      }
    } else {
      if (data.length !== expected) {
        throw new Error(
          `shape ${shape.join(' ')} needs ${expected} atoms but got ${data.length}`,
        );
      }
      this.data = [...data];
      this.sparseStorage = null;
    }
  }

  // ------------------------------------------------------------- factories

  /** An integer array of the given shape, filled row by row. */
  static ints(shape: number[], values: number[]): JArray {
    return new JArray(JType.INT, shape, values);
  }

  /** An integer vector. */
  static intVector(values: number[]): JArray {
    return JArray.ints([values.length], values);
  }

  static chars(shape: number[], values: string[]): JArray {
    return new JArray(JType.CHAR, shape, values);
  }

  static text(text: string): JArray {
    return JArray.chars([text.length], [...text]);
  }

  /** A raw array, used by assembly after numeric promotion. */
  static of(type: JType, shape: number[], values: Atom[]): JArray {
    return new JArray(type, shape, values);
  }

  /** A rank-0 array of the given type holding one atom. */
  static scalar(type: JType, value: Atom): JArray {
    return new JArray(type, [], [value]);
  }

  /** An empty array of the given type; at least one axis must be zero. */
  static empty(type: JType, shape: number[]): JArray {
    if (sizeOf(shape) !== 0) {
      throw new Error('empty array shape must contain a zero');
    }
    return new JArray(type, shape, []);
  }

  /**
   * Builds a sparse array from unordered entries. Entries equal to the fill
   * are discarded and duplicate indices keep the last value.
   */
  static sparse(
    type: JType,
    shape: number[],
    fill: Atom,
    indices: number[],
    values: Atom[],
  ): JArray {
    if (indices.length !== values.length) {
      throw new Error('sparse indices and values must have equal length');
    }
    const entries = new Map<number, Atom>();
    for (let entry = 0; entry < indices.length; entry++) {
      if (!JArray.atomEquals(values[entry], fill)) {
        entries.set(indices[entry], values[entry]);
      }
    }
    const sorted = [...entries.keys()].sort((a, b) => a - b);
    return new JArray(
      type,
      shape,
      new Sparse(fill, sorted, sorted.map((index) => entries.get(index)!)),
    );
  }

  // ------------------------------------------------------------- accessors

  get rank(): number {
    return this.shape.length;
  }

  get isEmpty(): boolean {
    return this.size === 0;
  }

  get isScalar(): boolean {
    return this.shape.length === 0;
  }

  get isSparse(): boolean {
    return this.sparseStorage !== null;
  }

  get sparseCount(): number {
    return this.sparseStorage ? this.sparseStorage.count : 0;
  }

  get sparseIndices(): number[] {
    return this.sparseStorage ? [...this.sparseStorage.indices] : [];
  }

  get sparseValues(): Atom[] {
    return this.sparseStorage ? [...this.sparseStorage.values] : [];
  }

  get sparseFill(): Atom | null {
    return this.sparseStorage ? this.sparseStorage.fill : null;
  }

  sameShape(other: JArray): boolean {
    if (this.shape.length !== other.shape.length) {
      return false;
    }
    for (let axis = 0; axis < this.shape.length; axis++) {
      if (this.shape[axis] !== other.shape[axis]) {
        return false;
      }
    }
    return true;
  }

  strides(): number[] {
    return stridesOf(this.shape);
  }

  longAt(index: number): number {
    this.requireType(JType.INT, 'longAt');
    const value = this.atomAt(index);
    if (typeof value !== 'number') {
      throw new Error('longAt: corrupt integer array');
    }
    return value;
  }

  doubleAt(index: number): number {
    this.requireType(JType.FLOAT, 'doubleAt');
    const value = this.atomAt(index);
    if (typeof value !== 'number') {
      throw new Error('doubleAt: corrupt float array');
    }
    return value;
  }

  charAt(index: number): string {
    this.requireType(JType.CHAR, 'charAt');
    const value = this.atomAt(index);
    if (typeof value !== 'string') {
      throw new Error('charAt: corrupt character array');
    }
    return value;
  }

  /** An exact integer atom from an INT or BIGINT array. */
  bigAt(index: number): bigint {
    if (this.type === JType.INT) {
      return BigInt(this.longAt(index));
    }
    if (this.type === JType.BIGINT) {
      const value = this.atomAt(index);
      if (typeof value !== 'bigint') {
        throw new Error('bigAt: corrupt big integer array');
      }
      return value;
    }
    throw new Error(`bigAt requires an integer array, got ${this.type}`);
  }

  /** A rational atom from an INT, BIGINT or RATIONAL array. */
  rationalAt(index: number): Rational {
    switch (this.type) {
      case JType.INT:
        return new Rational(BigInt(this.longAt(index)), 1n);
      case JType.BIGINT:
        return new Rational(this.bigAt(index), 1n);
      case JType.RATIONAL:
        return this.atomAt(index) as Rational;
      default:
        throw new Error(`rationalAt requires an exact array, got ${this.type}`);
    }
  }

  /** A complex atom from any numeric array. */
  complexAt(index: number): Complex {
    switch (this.type) {
      case JType.INT:
        return new Complex(this.longAt(index), 0);
      case JType.BIGINT:
        return new Complex(Number(this.bigAt(index)), 0);
      case JType.RATIONAL:
        return new Complex(this.rationalAt(index).toDouble(), 0);
      case JType.FLOAT:
        return new Complex(this.doubleAt(index), 0);
      case JType.COMPLEX:
        return this.atomAt(index) as Complex;
      default:
        throw new Error(`complexAt requires a numeric array, got ${this.type}`);
    }
  }

  boxAt(index: number): JArray {
    this.requireType(JType.BOX, 'boxAt');
    const value = this.atomAt(index);
    if (!(value instanceof JArray)) {
      throw new Error('boxAt: corrupt boxed array');
    }
    return value;
  }

  /** The raw atom at a flat index, including the sparse fill when absent. */
  atomAt(index: number): Atom {
    return this.sparseStorage ? this.sparseStorage.atomAt(index) : this.data![index];
  }

  /** Reads an atom as a double, widening exact numeric types. Complex excluded. */
  numericAt(index: number): number {
    switch (this.type) {
      case JType.INT:
        return this.longAt(index);
      case JType.BIGINT:
        return Number(this.bigAt(index));
      case JType.RATIONAL:
        return this.rationalAt(index).toDouble();
      case JType.FLOAT:
        return this.doubleAt(index);
      default:
        throw new Error(`numericAt requires a real numeric array, got ${this.type}`);
    }
  }

  private requireType(expected: JType, operation: string): void {
    if (this.type !== expected) {
      throw new Error(`${operation} requires a ${expected} array, got ${this.type}`);
    }
  }

  private get denseData(): Atom[] {
    return this.data!;
  }

  /** A dense copy of this array; returns this array when already dense. */
  dense(): JArray {
    if (!this.sparseStorage) {
      return this;
    }
    const result: Atom[] = new Array(this.size).fill(this.sparseStorage.fill);
    const { indices, values } = this.sparseStorage;
    for (let entry = 0; entry < indices.length; entry++) {
      result[indices[entry]] = values[entry];
    }
    return new JArray(this.type, this.shape, result);
  }

  /** Converts a dense array to sparse storage; scalars stay dense. */
  toSparse(): JArray {
    if (this.sparseStorage || this.shape.length === 0) {
      return this;
    }
    const fill = fillObject(this.type);
    const indices: number[] = [];
    const values: Atom[] = [];
    for (let atom = 0; atom < this.size; atom++) {
      const value = this.atomAt(atom);
      if (!JArray.atomEquals(value, fill)) {
        indices.push(atom);
        values.push(value);
      }
    }
    return new JArray(this.type, this.shape, new Sparse(fill, indices, values));
  }

  /** Converts between sparse and dense representations. */
  toggleSparse(): JArray {
    return this.sparseStorage ? this.dense() : this.toSparse();
  }

  /**
   * A contiguous copy of `count` atoms at `offset` with the given shape.
   * Rank application uses this to extract a cell: trailing axes are
   * contiguous in row-major order.
   */
  slice(offset: number, newShape: number[]): JArray {
    const count = sizeOf(newShape);
    if (offset < 0 || offset > this.size - count) {
      throw new Error('slice out of bounds');
    }
    if (this.sparseStorage) {
      const indices: number[] = [];
      const values: Atom[] = [];
      const { indices: sourceIndices, values: sourceValues } = this.sparseStorage;
      for (let entry = 0; entry < sourceIndices.length; entry++) {
        if (sourceIndices[entry] >= offset && sourceIndices[entry] < offset + count) {
          indices.push(sourceIndices[entry] - offset);
          values.push(sourceValues[entry]);
        }
      }
      return JArray.sparse(this.type, newShape, this.sparseStorage.fill, indices, values);
    }
    return new JArray(this.type, newShape, this.denseData.slice(offset, offset + count));
  }

  // ------------------------------------------------------------- structure

  /** Number of items: 1 for a scalar, otherwise the leading axis. */
  get itemCount(): number {
    return this.shape.length === 0 ? 1 : this.shape[0];
  }

  /** Shape of one item: empty for a scalar, otherwise the trailing axes. */
  itemShape(): number[] {
    return this.shape.length === 0 ? [] : this.shape.slice(1);
  }

  /** Number of atoms in one item. */
  itemSize(): number {
    return sizeOf(this.itemShape());
  }

  /** The index-th item. A scalar has exactly one item, itself. */
  item(index: number): JArray {
    if (this.shape.length === 0) {
      if (index !== 0) {
        throw new JError('index error');
      }
      return this;
    }
    if (index < 0 || index >= this.shape[0]) {
      throw new JError('index error');
    }
    return this.slice(index * this.itemSize(), this.itemShape());
  }

  /**
   * Rebuilds the array with a new shape, cycling atoms when the new shape is
   * larger. Empty sources are filled with the type's default fill atom.
   */
  reshape(newShape: number[]): JArray {
    if (this.sparseStorage) {
      return this.reshapeSparse(newShape);
    }
    const destinationSize = sizeOf(newShape);
    if (destinationSize === 0) {
      return JArray.empty(this.type, newShape);
    }
    if (this.size === 0) {
      return filled(this.type, newShape);
    }
    const result: Atom[] = new Array(destinationSize);
    for (let atom = 0; atom < destinationSize; atom++) {
      result[atom] = this.denseData[atom % this.size];
    }
    return new JArray(this.type, newShape, result);
  }

  /** Flattens the array into a vector. */
  ravel(): JArray {
    if (this.sparseStorage) {
      return JArray.sparse(
        this.type,
        [this.size],
        this.sparseStorage.fill,
        this.sparseStorage.indices,
        this.sparseStorage.values,
      );
    }
    return new JArray(this.type, [this.size], this.denseData);
  }

  /** Reverses the items along the leading axis. */
  reverseItems(): JArray {
    if (this.shape.length === 0) {
      return this;
    }
    const itemSize = this.itemSize();
    const sourceIndices = new Array<number>(this.size);
    for (let atom = 0; atom < this.size; atom++) {
      const item = Math.floor(atom / itemSize);
      const offset = atom % itemSize;
      sourceIndices[atom] = (this.shape[0] - 1 - item) * itemSize + offset;
    }
    return this.sparseStorage
      ? this.remapSparse(this.shape, sourceIndices)
      : this.remap(this.shape, sourceIndices);
  }

  /** Rotates the leading axis left by `count` items. */
  rotateItems(count: number): JArray {
    if (this.shape.length === 0 || this.shape[0] === 0) {
      return this;
    }
    const items = this.shape[0];
    const shift = ((count % items) + items) % items;
    if (shift === 0) {
      return this;
    }
    const itemSize = this.itemSize();
    const sourceIndices = new Array<number>(this.size);
    for (let atom = 0; atom < this.size; atom++) {
      const item = Math.floor(atom / itemSize);
      const offset = atom % itemSize;
      sourceIndices[atom] = ((item + shift) % items) * itemSize + offset;
    }
    return this.sparseStorage
      ? this.remapSparse(this.shape, sourceIndices)
      : this.remap(this.shape, sourceIndices);
  }

  /** Replicates each item according to `counts`. */
  copyItems(copies: number[], fills: number[] = new Array(copies.length).fill(0)): JArray {
    if (this.sparseStorage) {
      return this.copySparseItems(copies, fills);
    }
    if (copies.length !== this.itemCount || fills.length !== this.itemCount) {
      throw new Error('copy counts must match the item count');
    }
    let total = 0;
    for (let item = 0; item < copies.length; item++) {
      if (copies[item] < 0 || fills[item] < 0) {
        throw new Error('negative copy count');
      }
      total += copies[item] + fills[item];
      if (total > MAX_ARRAY_SIZE) {
        throw new JError('limit error');
      }
    }

    const newShape = this.shape.length === 0 ? [total] : [total, ...this.shape.slice(1)];

    const itemSize = this.itemSize();
    const result: Atom[] = new Array(sizeOf(newShape));
    const fill = fillObject(this.type);
    let at = 0;
    for (let item = 0; item < this.itemCount; item++) {
      for (let copy = 0; copy < copies[item]; copy++) {
        for (let atom = 0; atom < itemSize; atom++) {
          result[at++] = this.denseData[item * itemSize + atom];
        }
      }
      for (let fillItem = 0; fillItem < fills[item]; fillItem++) {
        for (let atom = 0; atom < itemSize; atom++) {
          result[at++] = fill;
        }
      }
    }
    return new JArray(this.type, newShape, result);
  }

  /** Takes `count` leading items, or trailing items when negative. */
  takeItems(count: number): JArray {
    if (this.shape.length === 0) {
      throw new Error('take needs a non-scalar argument');
    }
    const take = Math.abs(count);
    if (take > MAX_ARRAY_SIZE) {
      throw new JError('limit error');
    }
    const itemSize = this.itemSize();
    const newShape = [...this.shape];
    newShape[0] = take;

    if (this.sparseStorage) {
      const fillItems = Math.max(0, take - this.shape[0]);
      const start = count >= 0 ? 0 : this.shape[0] - Math.min(take, this.shape[0]);
      const indices: number[] = [];
      const values: Atom[] = [];
      const { indices: sourceIndices, values: sourceValues } = this.sparseStorage;
      for (let entry = 0; entry < sourceIndices.length; entry++) {
        const sourceItem = Math.floor(sourceIndices[entry] / itemSize);
        const offset = sourceIndices[entry] % itemSize;
        let destinationItem: number;
        if (count >= 0) {
          if (sourceItem >= take) {
            continue;
          }
          destinationItem = sourceItem;
        } else {
          if (sourceItem < start) {
            continue;
          }
          destinationItem = sourceItem - start + fillItems;
        }
        indices.push(destinationItem * itemSize + offset);
        values.push(sourceValues[entry]);
      }
      return JArray.sparse(this.type, newShape, this.sparseStorage.fill, indices, values);
    }

    const items = this.shape[0];
    const total = sizeOf(newShape);
    const fillItems = Math.max(0, take - items);
    const start = count >= 0 ? 0 : items - Math.min(take, items);
    const result: Atom[] = new Array(total);
    const fill = fillObject(this.type);
    for (let item = 0; item < take; item++) {
      const sourceItem =
        count >= 0 ? (item < items ? item : -1) : item < fillItems ? -1 : start + (item - fillItems);
      for (let atom = 0; atom < itemSize; atom++) {
        result[item * itemSize + atom] =
          sourceItem < 0 ? fill : this.denseData[sourceItem * itemSize + atom];
      }
    }
    return new JArray(this.type, newShape, result);
  }

  /** Takes along successive axes, filling overtaken coordinates. */
  takeAxes(counts: number[]): JArray {
    if (this.shape.length === 0) {
      throw new Error('take needs a non-scalar argument');
    }
    if (counts.length > this.shape.length) {
      throw new Error('take has too many axes');
    }

    const newShape = [...this.shape];
    const fill = new Array<number>(counts.length);
    const shift = new Array<number>(counts.length);
    for (let axis = 0; axis < counts.length; axis++) {
      const magnitude = Math.abs(counts[axis]);
      newShape[axis] = magnitude;
      fill[axis] = Math.max(0, newShape[axis] - this.shape[axis]);
      shift[axis] = Math.max(0, this.shape[axis] - newShape[axis]);
    }

    const total = sizeOf(newShape);
    const destinationStrides = stridesOf(newShape);
    const sourceStrides = stridesOf(this.shape);

    const sourceIndexAt = (atom: number): number => {
      let remainder = atom;
      let source = 0;
      for (let axis = 0; axis < this.shape.length; axis++) {
        const coordinate = Math.floor(remainder / destinationStrides[axis]);
        remainder %= destinationStrides[axis];
        if (axis >= counts.length) {
          source += coordinate * sourceStrides[axis];
          continue;
        }
        let sourceCoordinate: number;
        if (counts[axis] >= 0) {
          if (coordinate >= this.shape[axis]) {
            return -1;
          }
          sourceCoordinate = coordinate;
        } else {
          if (coordinate < fill[axis]) {
            return -1;
          }
          sourceCoordinate = coordinate - fill[axis] + shift[axis];
        }
        source += sourceCoordinate * sourceStrides[axis];
      }
      return source;
    };

    if (this.sparseStorage) {
      const sourceIndices = new Array<number>(total);
      for (let atom = 0; atom < total; atom++) {
        sourceIndices[atom] = sourceIndexAt(atom);
      }
      return this.remapSparse(newShape, sourceIndices);
    }

    const fillAtom = fillObject(this.type);
    const result: Atom[] = new Array(total);
    for (let atom = 0; atom < total; atom++) {
      const source = sourceIndexAt(atom);
      result[atom] = source < 0 ? fillAtom : this.denseData[source];
    }
    return new JArray(this.type, newShape, result);
  }

  /** Drops `count` leading items, or trailing items when negative. */
  dropItems(count: number): JArray {
    if (this.shape.length === 0) {
      throw new Error('drop needs a non-scalar argument');
    }
    const items = this.shape[0];
    const magnitude = Math.min(Math.abs(count), items);
    const start = count >= 0 ? magnitude : 0;
    const end = count >= 0 ? items : items - magnitude;
    const keep = end - start;
    const itemSize = this.itemSize();
    const newShape = [...this.shape];
    newShape[0] = keep;

    if (this.sparseStorage) {
      const indices: number[] = [];
      const values: Atom[] = [];
      const { indices: sourceIndices, values: sourceValues } = this.sparseStorage;
      for (let entry = 0; entry < sourceIndices.length; entry++) {
        const sourceItem = Math.floor(sourceIndices[entry] / itemSize);
        if (sourceItem < start || sourceItem >= end) {
          continue;
        }
        indices.push((sourceItem - start) * itemSize + (sourceIndices[entry] % itemSize));
        values.push(sourceValues[entry]);
      }
      return JArray.sparse(this.type, newShape, this.sparseStorage.fill, indices, values);
    }

    const result: Atom[] = new Array(keep * itemSize);
    for (let atom = 0; atom < result.length; atom++) {
      const item = start + Math.floor(atom / itemSize);
      result[atom] = this.denseData[item * itemSize + (atom % itemSize)];
    }
    return new JArray(this.type, newShape, result);
  }

  /** Drops along successive axes; negative counts drop from the end. */
  dropAxes(counts: number[]): JArray {
    if (this.shape.length === 0) {
      throw new Error('drop needs a non-scalar argument');
    }
    if (counts.length > this.shape.length) {
      throw new Error('drop has too many axes');
    }

    const newShape = [...this.shape];
    const starts = new Array<number>(this.shape.length).fill(0);
    for (let axis = 0; axis < counts.length; axis++) {
      const dropped = Math.min(Math.abs(counts[axis]), this.shape[axis]);
      newShape[axis] = this.shape[axis] - dropped;
      starts[axis] = counts[axis] >= 0 ? dropped : 0;
    }

    const total = sizeOf(newShape);
    const destinationStrides = stridesOf(newShape);
    const sourceStrides = stridesOf(this.shape);
    const sourceIndexAt = (atom: number): number => {
      let remainder = atom;
      let source = 0;
      for (let axis = 0; axis < this.shape.length; axis++) {
        const coordinate = Math.floor(remainder / destinationStrides[axis]);
        remainder %= destinationStrides[axis];
        source += (coordinate + starts[axis]) * sourceStrides[axis];
      }
      return source;
    };

    if (this.sparseStorage) {
      const sourceIndices = new Array<number>(total);
      for (let atom = 0; atom < total; atom++) {
        sourceIndices[atom] = sourceIndexAt(atom);
      }
      return this.remapSparse(newShape, sourceIndices);
    }

    const result: Atom[] = new Array(total);
    for (let atom = 0; atom < total; atom++) {
      result[atom] = this.denseData[sourceIndexAt(atom)];
    }
    return new JArray(this.type, newShape, result);
  }

  /** Catenates `other` after this array along the leading axis. */
  append(other: JArray): JArray {
    if (
      (this.sparseStorage || other.sparseStorage) &&
      this.shape.length >= 1 &&
      this.shape.length === other.shape.length
    ) {
      let sameItemShape = true;
      for (let axis = 1; axis < this.shape.length; axis++) {
        if (this.shape[axis] !== other.shape[axis]) {
          sameItemShape = false;
          break;
        }
      }
      if (sameItemShape) {
        const sparseResult = this.appendSparse(other);
        if (sparseResult) {
          return sparseResult;
        }
      }
    }
    if (this.sparseStorage || other.sparseStorage) {
      return this.dense().append(other.dense());
    }

    let newShape: number[];
    if (this.shape.length === 0 && other.shape.length === 0) {
      newShape = [2];
    } else if (this.shape.length <= 1 && other.shape.length <= 1) {
      newShape = [this.size + other.size];
    } else {
      if (this.shape.length !== other.shape.length) {
        throw new JError('rank error');
      }
      for (let axis = 1; axis < this.shape.length; axis++) {
        if (this.shape[axis] !== other.shape[axis]) {
          throw new JError('rank error');
        }
      }
      newShape = [...this.shape];
      newShape[0] += other.shape[0];
    }

    const resultType = promote(this.type, other.type);
    const result: Atom[] = new Array(sizeOf(newShape));
    let at = 0;
    for (let atom = 0; atom < this.size; atom++) {
      result[at++] = convertAtom(this.atomAt(atom), resultType);
    }
    for (let atom = 0; atom < other.size; atom++) {
      result[at++] = convertAtom(other.atomAt(atom), resultType);
    }
    return new JArray(resultType, newShape, result);
  }

  private appendSparse(other: JArray): JArray | null {
    let resultType: JType;
    try {
      resultType = promote(this.type, other.type);
    } catch (error) {
      if (error instanceof JError) {
        return null;
      }
      throw error;
    }
    const left = this.sparseStorage ? this : this.toSparse();
    const right = other.sparseStorage ? other : other.toSparse();
    if (!left.sparseStorage || !right.sparseStorage) {
      return null;
    }

    const leftFill = convertAtom(left.sparseFill!, resultType);
    const rightFill = convertAtom(right.sparseFill!, resultType);
    if (!JArray.atomEquals(leftFill, rightFill)) {
      return null;
    }

    const leftIndices = left.sparseIndices;
    const leftValues = left.sparseValues;
    const rightIndices = right.sparseIndices;
    const rightValues = right.sparseValues;
    const indices: number[] = [];
    const values: Atom[] = [];
    for (let entry = 0; entry < leftIndices.length; entry++) {
      indices.push(leftIndices[entry]);
      values.push(convertAtom(leftValues[entry], resultType));
    }
    for (let entry = 0; entry < rightIndices.length; entry++) {
      indices.push(left.size + rightIndices[entry]);
      values.push(convertAtom(rightValues[entry], resultType));
    }
    const newShape = [...this.shape];
    newShape[0] += other.shape[0];
    return JArray.sparse(resultType, newShape, leftFill, indices, values);
  }

  /** Reverses the axes of an array of rank 2 or more. */
  transpose(): JArray {
    return this.transposeAxes(reversedAxes(this.shape.length));
  }

  /** Reorders axes using the generalized-transpose permutation. */
  transposeAxes(axes: number[]): JArray {
    if (axes.length !== this.shape.length) {
      throw new Error('axis permutation has the wrong length');
    }
    if (this.shape.length < 2) {
      return this;
    }
    const newShape = axes.map((axis) => this.shape[axis]);
    const sourceStrides = stridesOf(this.shape);
    const destinationStrides = stridesOf(newShape);
    const sourceIndices = new Array<number>(this.size);
    for (let atom = 0; atom < this.size; atom++) {
      let remainder = atom;
      let source = 0;
      for (let axis = 0; axis < this.shape.length; axis++) {
        const coordinate = Math.floor(remainder / destinationStrides[axis]);
        remainder %= destinationStrides[axis];
        source += coordinate * sourceStrides[axes[axis]];
      }
      sourceIndices[atom] = source;
    }
    return this.sparseStorage
      ? this.remapSparse(newShape, sourceIndices)
      : this.remap(newShape, sourceIndices);
  }

  /** Rotates successive axes by the given item counts. */
  rotateAxes(counts: number[]): JArray {
    if (this.shape.length === 0) {
      throw new Error('rotate needs a non-scalar argument');
    }
    if (counts.length > this.shape.length) {
      throw new Error('rotate has too many axes');
    }
    const strides = stridesOf(this.shape);
    const starts = new Array<number>(this.shape.length).fill(0);
    for (let axis = 0; axis < counts.length; axis++) {
      if (this.shape[axis] === 0) {
        continue;
      }
      starts[axis] = ((counts[axis] % this.shape[axis]) + this.shape[axis]) % this.shape[axis];
    }

    const sourceIndices = new Array<number>(this.size);
    for (let atom = 0; atom < this.size; atom++) {
      let remainder = atom;
      let source = 0;
      for (let axis = 0; axis < this.shape.length; axis++) {
        const coordinate = Math.floor(remainder / strides[axis]);
        remainder %= strides[axis];
        const sourceCoordinate =
          this.shape[axis] === 0
            ? 0
            : (((coordinate + starts[axis]) % this.shape[axis]) + this.shape[axis]) %
              this.shape[axis];
        source += sourceCoordinate * strides[axis];
      }
      sourceIndices[atom] = source;
    }
    if (this.sparseStorage) {
      return this.remapSparse(this.shape, sourceIndices);
    }
    const result: Atom[] = new Array(this.size);
    for (let atom = 0; atom < this.size; atom++) {
      result[atom] = this.denseData[sourceIndices[atom]];
    }
    return new JArray(this.type, this.shape, result);
  }

  private remap(newShape: number[], sourceIndices: number[]): JArray {
    const destinationSize = sizeOf(newShape);
    if (sourceIndices.length !== destinationSize) {
      throw new Error('source map has the wrong length');
    }
    const result: Atom[] = new Array(destinationSize);
    for (let atom = 0; atom < destinationSize; atom++) {
      result[atom] = this.denseData[sourceIndices[atom]];
    }
    return new JArray(this.type, newShape, result);
  }

  private remapSparse(newShape: number[], destinationToSource: number[]): JArray {
    const destinationSize = sizeOf(newShape);
    if (destinationToSource.length !== destinationSize) {
      throw new Error('source map has the wrong length');
    }
    const inverse = new Array<number>(this.size).fill(-1);
    for (let destination = 0; destination < destinationToSource.length; destination++) {
      const source = destinationToSource[destination];
      if (source >= 0) {
        inverse[source] = destination;
      }
    }
    const indices: number[] = [];
    const values: Atom[] = [];
    const { fill, indices: sourceIndices, values: sourceValues } = this.sparseStorage!;
    for (let entry = 0; entry < sourceIndices.length; entry++) {
      const destination = inverse[sourceIndices[entry]];
      if (destination >= 0) {
        indices.push(destination);
        values.push(sourceValues[entry]);
      }
    }
    return JArray.sparse(this.type, newShape, fill, indices, values);
  }

  private reshapeSparse(newShape: number[]): JArray {
    const destinationSize = sizeOf(newShape);
    const { fill, indices: sourceIndices, values: sourceValues } = this.sparseStorage!;
    if (destinationSize === 0 || this.size === 0) {
      return JArray.sparse(this.type, newShape, fill, [], []);
    }
    const indices: number[] = [];
    const values: Atom[] = [];
    for (let entry = 0; entry < sourceIndices.length; entry++) {
      for (
        let destination = sourceIndices[entry];
        destination < destinationSize;
        destination += this.size
      ) {
        indices.push(destination);
        values.push(sourceValues[entry]);
      }
    }
    return JArray.sparse(this.type, newShape, fill, indices, values);
  }

  private copySparseItems(copies: number[], fills: number[]): JArray {
    if (copies.length !== this.itemCount || fills.length !== this.itemCount) {
      throw new Error('copy counts must match the item count');
    }
    let total = 0;
    for (let item = 0; item < copies.length; item++) {
      if (copies[item] < 0 || fills[item] < 0) {
        throw new Error('negative copy count');
      }
      total += copies[item] + fills[item];
      if (total > MAX_ARRAY_SIZE) {
        throw new JError('limit error');
      }
    }
    const newShape = this.shape.length === 0 ? [total] : [total, ...this.shape.slice(1)];

    const itemSize = this.itemSize();
    const sourceIndices = this.sparseIndices;
    const sourceValues = this.sparseValues;
    const indices: number[] = [];
    const values: Atom[] = [];
    let entry = 0;
    let destinationItem = 0;
    for (let item = 0; item < this.itemCount; item++) {
      const sourceStart = item * itemSize;
      const sourceEnd = sourceStart + itemSize;
      while (entry < sourceIndices.length && sourceIndices[entry] < sourceStart) {
        entry++;
      }
      for (let copy = 0; copy < copies[item]; copy++) {
        const base = (destinationItem + copy) * itemSize;
        let scan = entry;
        while (scan < sourceIndices.length && sourceIndices[scan] < sourceEnd) {
          indices.push(base + sourceIndices[scan] - sourceStart);
          values.push(sourceValues[scan]);
          scan++;
        }
      }
      destinationItem += copies[item] + fills[item];
    }
    return JArray.sparse(this.type, newShape, this.sparseStorage!.fill, indices, values);
  }

  /**
   * Structural equality for one stored atom. Box atoms are arrays, so their
   * shape and contents are compared rather than their identity.
   */
  static atomEquals(left: Atom, right: Atom): boolean {
    if (left instanceof JArray && right instanceof JArray) {
      if (left.type !== right.type || !left.sameShape(right)) {
        return false;
      }
      for (let atom = 0; atom < left.size; atom++) {
        if (!JArray.atomEquals(left.atomAt(atom), right.atomAt(atom))) {
          return false;
        }
      }
      return true;
    }
    if (left instanceof Rational && right instanceof Rational) {
      return left.numerator === right.numerator && left.denominator === right.denominator;
    }
    if (left instanceof Complex && right instanceof Complex) {
      return left.real === right.real && left.imaginary === right.imaginary;
    }
    if (typeof left === 'number' && typeof right === 'number') {
      return Object.is(left, right);
    }
    return left === right;
  }

  /** The default fill atom: zero, space, or an empty box. */
  static fillScalar(type: JType): JArray {
    return JArray.scalar(type, fillObject(type));
  }
}

function filled(type: JType, shape: number[]): JArray {
  const size = sizeOf(shape);
  return new JArray(type, shape, new Array<Atom>(size).fill(fillObject(type)));
}

/** The raw default fill atom for a JType. */
export function fillObject(type: JType): Atom {
  switch (type) {
    case JType.INT:
      return 0;
    case JType.BIGINT:
      return 0n;
    case JType.RATIONAL:
      return Rational.ZERO;
    case JType.FLOAT:
      return 0;
    case JType.COMPLEX:
      return Complex.ZERO;
    case JType.CHAR:
      return ' ';
    case JType.BOX:
      return JArray.empty(JType.INT, [0]);
  }
}

/** Numeric promotion; characters and boxes only combine with themselves. */
export function promote(left: JType, right: JType): JType {
  if (left === right) {
    return left;
  }
  if (!isNumericType(left) || !isNumericType(right)) {
    throw new JError('domain error');
  }
  return numericRank(left) >= numericRank(right) ? left : right;
}

/** Converts one atom to another JType, widening numeric values as needed. */
export function convertAtom(atom: Atom, target: JType): Atom {
  switch (target) {
    case JType.INT:
      if (typeof atom === 'number' && Number.isInteger(atom)) {
        return atom;
      }
      if (typeof atom === 'bigint' && isSafeBig(atom)) {
        return Number(atom);
      }
      if (atom instanceof Rational && atom.isInteger() && isSafeBig(atom.numerator)) {
        return Number(atom.numerator);
      }
      throw new Error('cannot convert to INT');
    case JType.BIGINT:
      if (typeof atom === 'bigint') {
        return atom;
      }
      if (typeof atom === 'number' && Number.isInteger(atom)) {
        return BigInt(atom);
      }
      if (atom instanceof Rational && atom.isInteger()) {
        return atom.numerator;
      }
      throw new Error('cannot convert to BIGINT');
    case JType.RATIONAL:
      if (typeof atom === 'number' && Number.isInteger(atom)) {
        return new Rational(BigInt(atom), 1n);
      }
      if (typeof atom === 'bigint') {
        return new Rational(atom, 1n);
      }
      if (atom instanceof Rational) {
        return atom;
      }
      throw new Error('cannot convert to RATIONAL');
    case JType.FLOAT:
      if (typeof atom === 'number') {
        return atom;
      }
      if (typeof atom === 'bigint') {
        return Number(atom);
      }
      if (atom instanceof Rational) {
        return atom.toDouble();
      }
      throw new Error('cannot convert to FLOAT');
    case JType.COMPLEX:
      if (atom instanceof Complex) {
        return atom;
      }
      if (typeof atom === 'number') {
        return new Complex(atom, 0);
      }
      if (typeof atom === 'bigint') {
        return new Complex(Number(atom), 0);
      }
      if (atom instanceof Rational) {
        return new Complex(atom.toDouble(), 0);
      }
      throw new Error('cannot convert to COMPLEX');
    case JType.CHAR:
      if (typeof atom === 'string') {
        return atom;
      }
      throw new Error('cannot convert to CHAR');
    case JType.BOX:
      if (atom instanceof JArray) {
        return atom;
      }
      throw new Error('cannot convert to BOX');
  }
}

function isSafeBig(value: bigint): boolean {
  return value >= BigInt(Number.MIN_SAFE_INTEGER) && value <= BigInt(Number.MAX_SAFE_INTEGER);
}

function reversedAxes(rank: number): number[] {
  return Array.from({ length: rank }, (_, axis) => rank - 1 - axis);
}
