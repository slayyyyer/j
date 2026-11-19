import { JError, concatShapes, sizeOf } from './atoms.ts';
import { JArray, convertAtom, promote } from './array.ts';
import type { Atom } from './array.ts';

/** Stacks equal-shaped cells into one array whose shape is frame + cell shape. */
export function stackAssembly(frameShape: number[], cells: JArray[]): JArray {
  if (cells.length === 0) {
    throw new Error('cannot assemble zero cells');
  }

  const first = cells[0];
  const cellShape = first.shape;
  let type = first.type;
  for (const cell of cells) {
    if (!cell.sameShape(first)) {
      throw new JError('length error');
    }
    type = promote(type, cell.type);
  }

  const resultShape = concatShapes(frameShape, cellShape);
  const total = sizeOf(resultShape);
  const cellSize = sizeOf(cellShape);

  let allSparse = true;
  let entries = 0;
  for (const cell of cells) {
    if (!cell.isSparse || cell.type !== type || !JArray.atomEquals(cell.sparseFill!, first.sparseFill!)) {
      allSparse = false;
      break;
    }
    entries += cell.sparseCount;
  }

  if (allSparse) {
    const indices: number[] = [];
    const values: Atom[] = [];
    let cellIndex = 0;
    for (const cell of cells) {
      const base = cellIndex * cellSize;
      const cellIndices = cell.sparseIndices;
      const cellValues = cell.sparseValues;
      for (let entry = 0; entry < cellIndices.length; entry++) {
        indices.push(base + cellIndices[entry]);
        values.push(cellValues[entry]);
      }
      cellIndex++;
    }
    return JArray.sparse(type, resultShape, first.sparseFill!, indices, values);
  }

  const data = new Array<Atom>(total);
  let at = 0;
  for (const cell of cells) {
    for (let atom = 0; atom < cellSize; atom++) {
      data[at++] = convertAtom(cell.atomAt(atom), type);
    }
  }
  return JArray.of(type, resultShape, data);
}

