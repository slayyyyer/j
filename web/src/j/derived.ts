import { JError, JType, concatShapes, isExactType } from './atoms.ts';
import { checkBudget } from './budget.ts';
import { JArray } from './array.ts';
import { stackAssembly } from './assembly.ts';
import { format } from './format.ts';
import { applyDyad, applyMonad } from './rank.ts';
import { Environment } from './environment.ts';
import type { Value } from './environment.ts';
import { Evaluator } from './evaluator.ts';
import type { Statement } from './ast.ts';
import { WHOLE, Verb } from './verbs.ts';
import { exactLongAt } from './numeric.ts';

/** `u/`: insert the dyad between items, folding from the right. */
export class Insert extends Verb {
  private readonly operand: Verb;

  constructor(operand: Verb) {
    super(`${operand.repr()}/`);
    this.operand = operand;
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
    if (y.rank === 0) {
      return y;
    }
    const items = y.itemCount;
    if (items === 0) {
      const identity = this.operand.identity(y.type);
      return y.rank > 1 ? identity.reshape(y.itemShape()) : identity;
    }
    let accumulator = y.item(items - 1);
    for (let item = items - 2; item >= 0; item--) {
      checkBudget();
      accumulator = applyDyad(this.operand, y.item(item), accumulator);
    }
    return accumulator;
  }
}

/** `u\`: apply u to each prefix along the leading axis. */
export class Prefix extends Verb {
  private readonly operand: Verb;

  constructor(operand: Verb) {
    super(`${operand.repr()}\\`);
    this.operand = operand;
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
    if (y.rank === 0) {
      return y;
    }
    const items = y.itemCount;
    if (items === 0) {
      const resultShape = concatShapes([0], y.itemShape());
      return y.isSparse
        ? JArray.sparse(y.type, resultShape, y.sparseFill!, [], [])
        : JArray.empty(y.type, resultShape);
    }
    const results: JArray[] = [];
    for (let length = 1; length <= items; length++) {
      checkBudget();
      results.push(applyMonad(this.operand, y.takeItems(length)));
    }
    return stackAssembly([items], results);
  }
}

/** `u~`: reflex monadically and passive dyadically. */
export class Reflex extends Verb {
  private readonly operand: Verb;

  constructor(operand: Verb) {
    super(`${operand.repr()}~`);
    this.operand = operand;
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
    return applyDyad(this.operand, y, y);
  }

  dyadCell(x: JArray, y: JArray): JArray {
    return applyDyad(this.operand, y, x);
  }
}

/** `u"r`: apply u to cells of the specified rank. */
export class RankVerb extends Verb {
  private readonly operand: Verb;
  private readonly monadRankValue: number;
  private readonly leftRankValue: number;
  private readonly rightRankValue: number;

  constructor(operand: Verb, rankSpec: JArray) {
    super(`${operand.repr()}"${format(rankSpec)}`);
    const ranks = toRanks(rankSpec);
    this.operand = operand;
    this.monadRankValue = ranks[0];
    this.leftRankValue = ranks[1];
    this.rightRankValue = ranks[2];
  }

  monadRank(): number {
    return this.monadRankValue;
  }

  leftRank(): number {
    return this.leftRankValue;
  }

  rightRank(): number {
    return this.rightRankValue;
  }

  monadCell(y: JArray): JArray {
    return applyMonad(this.operand, y);
  }

  dyadCell(x: JArray, y: JArray): JArray {
    return applyDyad(this.operand, x, y);
  }
}

function toRanks(spec: JArray): [number, number, number] {
  if (spec.rank > 1) {
    throw new JError('rank error');
  }
  const values = new Array<number>(spec.size);
  for (let atom = 0; atom < values.length; atom++) {
    if (isExactType(spec.type)) {
      values[atom] = exactLongAt(spec, atom);
    } else if (spec.type === JType.FLOAT && spec.doubleAt(atom) === Infinity) {
      values[atom] = WHOLE;
    } else {
      throw new JError('rank error');
    }
  }

  if (values.length === 0) {
    throw new JError('rank error');
  }
  if (values.length === 1) {
    return [values[0], values[0], values[0]];
  }
  if (values.length === 2) {
    // J: n0 n1 means monadic rank n1, left rank n0, right rank n1.
    return [values[1], values[0], values[1]];
  }
  if (values.length === 3) {
    return [values[0], values[1], values[2]];
  }
  throw new JError('rank error');
}

/** A hook or fork built from two or more verbs. */
export class TrainVerb extends Verb {
  private readonly parts: Verb[];

  constructor(verbs: Verb[]) {
    super(`(${verbs.map((verb) => verb.repr()).join(' ')})`);
    if (verbs.length < 2) {
      throw new Error('a train needs at least two verbs');
    }
    this.parts = group(verbs);
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
    if (this.parts.length === 2) {
      const right = applyMonad(this.parts[1], y);
      return applyDyad(this.parts[0], y, right);
    }
    const left = applyMonad(this.parts[0], y);
    const right = applyMonad(this.parts[2], y);
    return applyDyad(this.parts[1], left, right);
  }

  dyadCell(x: JArray, y: JArray): JArray {
    if (this.parts.length === 2) {
      const right = applyMonad(this.parts[1], y);
      return applyDyad(this.parts[0], x, right);
    }
    const left = applyDyad(this.parts[0], x, y);
    const right = applyDyad(this.parts[2], x, y);
    return applyDyad(this.parts[1], left, right);
  }
}

function group(verbs: Verb[]): Verb[] {
  if (verbs.length <= 3) {
    return verbs;
  }
  if (verbs.length % 2 === 0) {
    return [verbs[0], new TrainVerb(verbs.slice(1))];
  }
  return [verbs[0], verbs[1], new TrainVerb(verbs.slice(2))];
}

class ReturnSignal {
  readonly value: Value | null;

  constructor(value: Value | null) {
    this.value = value;
  }
}

/** A user-defined verb from `{{ ... }}` or `3 : 0`. */
export class ExplicitVerb extends Verb {
  private readonly body: Statement[];
  private readonly globals: Environment;

  constructor(body: Statement[], globals: Environment) {
    super('{{...}}');
    if (body.length === 0) {
      throw new Error('an explicit verb needs a body');
    }
    this.body = body;
    this.globals = globals;
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
    return this.evaluate(y, null);
  }

  dyadCell(x: JArray, y: JArray): JArray {
    return this.evaluate(y, x);
  }

  private evaluate(y: JArray, x: JArray | null): JArray {
    const locals = new Environment(this.globals);
    locals.assign('y', { kind: 'noun', array: y });
    if (x !== null) {
      locals.assign('x', { kind: 'noun', array: x });
    }

    const evaluator = new Evaluator(locals);
    try {
      const result = this.execute(this.body, locals, evaluator);
      if (result && result.kind === 'noun') {
        return result.array;
      }
      throw new JError('domain error');
    } catch (signal) {
      if (signal instanceof ReturnSignal) {
        if (signal.value && signal.value.kind === 'noun') {
          return signal.value.array;
        }
        throw new JError('domain error');
      }
      throw signal;
    }
  }

  private execute(statements: Statement[], locals: Environment, evaluator: Evaluator): Value | null {
    let result: Value | null = null;
    for (const statement of statements) {
      checkBudget();
      switch (statement.kind) {
        case 'sentence':
          result = evaluator.evaluate(statement.phrase);
          break;
        case 'if': {
          const truth = truthValue(evaluator.evaluateExpression(statement.condition));
          result = truth
            ? this.execute(statement.thenBody, locals, evaluator)
            : this.execute(statement.elseBody, locals, evaluator);
          break;
        }
        case 'while': {
          let last: Value | null = null;
          while (truthValue(evaluator.evaluateExpression(statement.condition))) {
            checkBudget();
            last = this.execute(statement.body, locals, evaluator);
          }
          result = last;
          break;
        }
        case 'for': {
          const values = evaluator.evaluateExpression(statement.values);
          let last: Value | null = null;
          for (let item = 0; item < values.itemCount; item++) {
            checkBudget();
            locals.assign(statement.variable, { kind: 'noun', array: values.item(item) });
            locals.assign(`${statement.variable}_index`, {
              kind: 'noun',
              array: JArray.scalar(JType.INT, item),
            });
            last = this.execute(statement.body, locals, evaluator);
          }
          result = last;
          break;
        }
        case 'return': {
          const value =
            statement.value === null
              ? null
              : { kind: 'noun' as const, array: evaluator.evaluateExpression(statement.value) };
          throw new ReturnSignal(value);
        }
      }
    }
    return result;
  }
}

function truthValue(value: JArray): boolean {
  if (!value.isScalar) {
    throw new JError('length error');
  }
  switch (value.type) {
    case JType.INT:
      return value.longAt(0) !== 0;
    case JType.BIGINT:
      return value.bigAt(0) !== 0n;
    case JType.RATIONAL:
      return value.rationalAt(0).numerator !== 0n;
    case JType.FLOAT: {
      const number = value.doubleAt(0);
      return number !== 0 && !Number.isNaN(number);
    }
    case JType.COMPLEX: {
      const number = value.complexAt(0);
      return number.real !== 0 || number.imaginary !== 0;
    }
    default:
      throw new JError('domain error');
  }
}

