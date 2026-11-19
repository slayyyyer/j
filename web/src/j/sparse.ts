import type { Atom } from './array.ts';

/**
 * Sparse storage: a fill atom plus sorted flat indices and their values.
 * Indices refer to row-major positions of the enclosing array.
 */
export class Sparse {
  readonly fill: Atom;
  readonly indices: number[];
  readonly values: Atom[];

  constructor(fill: Atom, indices: number[], values: Atom[]) {
    if (indices.length !== values.length) {
      throw new Error('sparse indices and values must have equal length');
    }
    this.fill = fill;
    this.indices = [...indices];
    this.values = [...values];
    for (let entry = 0; entry < indices.length; entry++) {
      if (indices[entry] < 0) {
        throw new Error('sparse index must be non-negative');
      }
      if (entry > 0 && indices[entry] <= indices[entry - 1]) {
        throw new Error('sparse indices must be strictly increasing');
      }
      if (values[entry] === undefined || values[entry] === null) {
        throw new Error('a sparse value must not be null');
      }
    }
  }

  get count(): number {
    return this.indices.length;
  }

  /** The stored atom at a flat index, or the fill atom when absent. */
  atomAt(index: number): Atom {
    let low = 0;
    let high = this.indices.length - 1;
    while (low <= high) {
      const middle = (low + high) >> 1;
      const candidate = this.indices[middle];
      if (candidate === index) {
        return this.values[middle];
      }
      if (candidate < index) {
        low = middle + 1;
      } else {
        high = middle - 1;
      }
    }
    return this.fill;
  }
}

