import { Complex, JType, Rational } from './atoms.ts';
import { JArray } from './array.ts';
import type { Atom } from './array.ts';
import { checkBudget } from './budget.ts';

/**
 * J's display representation of a noun.
 *
 * Floats use J's five significant digits, negatives are written with `_`,
 * infinities are `_` and `__`, NaN is `_.`, matrices align their columns,
 * rank-3+ planes are separated by a blank line, and boxes are bordered cells.
 */

const PRINT_PRECISION = 5;

export function format(array: JArray): string {
  if (array.isSparse) {
    return formatSparse(array);
  }
  if (array.type === JType.BOX) {
    return formatBoxes(array);
  }
  if (array.rank === 0) {
    return formatAtom(array.type, array.atomAt(0));
  }
  if (array.isEmpty) {
    return '';
  }
  if (array.type === JType.CHAR) {
    return formatCharacters(array);
  }
  return formatNumbers(array);
}

/** Sparse display: aligned flat-index coordinates, `|`, and stored values. */
function formatSparse(array: JArray): string {
  const indices = array.sparseIndices;
  const values = array.sparseValues;
  const rank = array.rank;
  if (indices.length === 0) {
    return rank === 0 ? formatSparseValue(array.type, array.sparseFill!) : '';
  }
  if (rank === 0) {
    return formatSparseValue(array.type, values[0]);
  }

  const strides = array.strides();
  const coordinates = indices.map((index) => {
    let remainder = index;
    return strides.map((stride) => {
      const coordinate = Math.floor(remainder / stride);
      remainder %= stride;
      return coordinate;
    });
  });
  const widths = new Array<number>(rank).fill(0);
  for (const coordinate of coordinates) {
    checkBudget();
    for (let axis = 0; axis < rank; axis++) {
      widths[axis] = Math.max(widths[axis], String(coordinate[axis]).length);
    }
  }

  const renderedValues = values.map((value) => formatSparseValue(array.type, value));
  const valueWidth = renderedValues.reduce((width, text) => Math.max(width, text.length), 0);

  return indices
    .map((_, entry) => {
      checkBudget();
      const coordinate = coordinates[entry]
        .map((value, axis) => padLeft(String(value), widths[axis]))
        .join(' ');
      return `${coordinate} | ${padLeft(renderedValues[entry], valueWidth)}`;
    })
    .join('\n');
}

function formatCharacters(array: JArray): string {
  const rank = array.rank;
  const shape = array.shape;

  if (rank === 1) {
    return characterRow(array, 0, shape[0]);
  }

  const columns = shape[rank - 1];
  const rows = shape[rank - 2];
  const cellSize = rows * columns;
  const planes = array.size / cellSize;
  const lines: string[] = [];

  for (let plane = 0; plane < planes; plane++) {
    checkBudget();
    if (plane > 0) {
      lines.push('');
    }
    for (let row = 0; row < rows; row++) {
      lines.push(characterRow(array, (plane * rows + row) * columns, columns));
    }
  }
  return lines.join('\n');
}

function characterRow(array: JArray, start: number, length: number): string {
  let row = '';
  for (let column = 0; column < length; column++) {
    checkBudget();
    row += array.charAt(start + column);
  }
  return row;
}

function formatNumbers(array: JArray): string {
  const rank = array.rank;
  const shape = array.shape;

  if (rank === 1) {
    const atoms: string[] = [];
    for (let atom = 0; atom < array.size; atom++) {
      checkBudget();
      atoms.push(formatAtom(array.type, array.atomAt(atom)));
    }
    return atoms.join(' ');
  }

  const columns = shape[rank - 1];
  const rows = shape[rank - 2];
  const cellSize = rows * columns;
  const planes = array.size / cellSize;

  const tokens = new Array<string>(array.size);
  const widths = new Array<number>(columns).fill(0);
  for (let atom = 0; atom < array.size; atom++) {
    checkBudget();
    const token = formatAtom(array.type, array.atomAt(atom));
    tokens[atom] = token;
    const column = atom % columns;
    widths[column] = Math.max(widths[column], token.length);
  }

  const lines: string[] = [];
  for (let plane = 0; plane < planes; plane++) {
    if (plane > 0) {
      lines.push('');
    }
    for (let row = 0; row < rows; row++) {
      checkBudget();
      const cells: string[] = [];
      for (let column = 0; column < columns; column++) {
        const token = tokens[(plane * rows + row) * columns + column];
        cells.push(padLeft(token, widths[column]));
      }
      lines.push(cells.join(' '));
    }
  }
  return lines.join('\n');
}

function formatSparseValue(type: JType, value: Atom): string {
  return type === JType.BOX ? format(value as JArray) : formatAtom(type, value);
}

function formatAtom(type: JType, atom: Atom): string {
  switch (type) {
    case JType.INT:
      return formatBig(BigInt(atom as number));
    case JType.FLOAT:
      return formatDouble(atom as number);
    case JType.BIGINT:
      return formatBig(atom as bigint);
    case JType.RATIONAL:
      return formatRational(atom as Rational);
    case JType.COMPLEX:
      return formatComplex(atom as Complex);
    case JType.CHAR:
      return atom as string;
    case JType.BOX:
      throw new Error('a box cell is rendered by the box formatter');
  }
}

function padLeft(text: string, width: number): string {
  return text.padStart(width, ' ');
}

function formatBig(value: bigint): string {
  return value < 0n ? `_${(-value).toString()}` : value.toString();
}

function formatRational(value: Rational): string {
  return `${formatBig(value.numerator)}r${value.denominator}`;
}

function formatComplex(value: Complex): string {
  return `${formatDouble(value.real)}j${formatDouble(value.imaginary)}`;
}

/** Formats a double the way J's default display does. */
function formatDouble(value: number): string {
  if (Number.isNaN(value)) {
    return '_.';
  }
  if (value === Infinity) {
    return '_';
  }
  if (value === -Infinity) {
    return '__';
  }
  if (value === 0) {
    return '0';
  }

  const negative = value < 0;
  const magnitude = Math.abs(value);
  let exponent = Math.floor(Math.log10(magnitude));

  // Round to five significant digits.
  let scaled = magnitude / Math.pow(10, exponent);
  let digits = Math.round(scaled * 10 ** (PRINT_PRECISION - 1));
  if (digits >= 10 ** PRINT_PRECISION) {
    digits = Math.round(digits / 10);
    exponent += 1;
  }

  const significand = digits.toString().padEnd(PRINT_PRECISION, '0');
  let magnitudeText: string;
  if (exponent >= 6 || exponent < -4) {
    const fraction = significand.slice(1).replace(/0+$/, '');
    magnitudeText = `${significand[0]}${fraction ? `.${fraction}` : ''}e${
      exponent < 0 ? `_${-exponent}` : exponent
    }`;
  } else if (exponent >= 0) {
    const all = significand.padEnd(Math.max(PRINT_PRECISION, exponent + 1), '0');
    const whole = all.slice(0, exponent + 1);
    const fraction = all.slice(exponent + 1).replace(/0+$/, '');
    magnitudeText = fraction ? `${whole}.${fraction}` : whole;
  } else {
    magnitudeText = `0.${'0'.repeat(-exponent - 1)}${significand.replace(/0+$/, '')}`;
  }

  return negative ? `_${magnitudeText}` : magnitudeText;
}

// ----------------------------------------------------------------- boxes

function formatBoxes(array: JArray): string {
  if (array.rank === 0) {
    return borderedCells([array.boxAt(0)], 1, 1).join('\n');
  }
  if (array.rank === 1) {
    return borderedCells(boxAtoms(array), 1, array.size).join('\n');
  }
  if (array.rank > 2) {
    const columns = array.shape[array.rank - 1];
    const rows = array.shape[array.rank - 2];
    const planeSize = rows * columns;
    const planes = array.size / planeSize;
    const result: string[] = [];
    for (let plane = 0; plane < planes; plane++) {
      if (plane > 0) {
        result.push('');
      }
      const planeArray = array.slice(plane * planeSize, [rows, columns]);
      result.push(borderedCells(boxAtoms(planeArray), rows, columns).join('\n'));
    }
    return result.join('\n');
  }
  return borderedCells(boxAtoms(array), array.shape[0], array.shape[1]).join('\n');
}

function boxAtoms(array: JArray): JArray[] {
  const atoms: JArray[] = [];
  for (let atom = 0; atom < array.size; atom++) {
    checkBudget();
    atoms.push(array.boxAt(atom));
  }
  return atoms;
}

function borderedCells(cells: JArray[], rows: number, columns: number): string[] {
  const lines: string[] = [];
  const widths: number[][] = Array.from({ length: rows }, () => new Array<number>(columns).fill(0));
  const cellLines: string[][] = [];

  for (let cell = 0; cell < cells.length; cell++) {
    checkBudget();
    const text = format(cells[cell]);
    const split = text.split('\n');
    cellLines.push(split);
    const width = split.reduce((widest, line) => Math.max(widest, line.length), 0);
    widths[Math.floor(cell / columns)][cell % columns] = width;
  }

  lines.push(border(widths[0] ?? []));
  for (let row = 0; row < rows; row++) {
    let height = 1;
    for (let column = 0; column < columns; column++) {
      height = Math.max(height, cellLines[row * columns + column].length);
    }
    for (let line = 0; line < height; line++) {
      let rendered = '|';
      for (let column = 0; column < columns; column++) {
        const content = cellLines[row * columns + column];
        const text = line < content.length ? content[line] : '';
        const width = widths[row][column];
        rendered += text.padEnd(width, ' ') + '|';
      }
      lines.push(rendered);
    }
    lines.push(border(widths[row]));
  }
  return lines;
}

function border(widths: number[]): string {
  return `+${widths.map((width) => '-'.repeat(width)).join('+')}+`;
}
