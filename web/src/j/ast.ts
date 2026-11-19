import type { JArray } from './array.ts';
import type { Verb } from './verbs.ts';

/**
 * AST for one J sentence.
 *
 * Applications are right-associative, which makes J's right-to-left
 * evaluation explicit: `2 + 3 + 4` is a dyad whose right argument is another
 * dyad.
 */

/** A phrase that produces a noun. */
export type Expr =
  | { kind: 'lit'; value: JArray }
  | { kind: 'ref'; name: string }
  | { kind: 'strand'; parts: Expr[] }
  | { kind: 'monad'; verb: VerbExpr; y: Expr }
  | { kind: 'dyad'; x: Expr; verb: VerbExpr; y: Expr }
  | { kind: 'assign'; name: string; global: boolean; value: Phrase };

/** A phrase that produces a verb. */
export type VerbExpr =
  | { kind: 'lit'; verb: Verb }
  | { kind: 'ref'; name: string }
  | { kind: 'adverb'; operand: VerbExpr; adverb: '/' | '\\' | '~' }
  | { kind: 'rank'; operand: VerbExpr; ranks: Expr }
  | { kind: 'train'; verbs: VerbExpr[] }
  | { kind: 'explicit'; body: Statement[] };

/** A parsed sentence: a noun, a verb, or an assignment. */
export type Phrase =
  | { kind: 'noun'; expr: Expr }
  | { kind: 'verb'; verb: VerbExpr }
  | { kind: 'assign'; name: string; global: boolean; value: Phrase };

/** A statement in an explicit verb body. */
export type Statement =
  | { kind: 'sentence'; phrase: Phrase }
  | { kind: 'if'; condition: Expr; thenBody: Statement[]; elseBody: Statement[] }
  | { kind: 'while'; condition: Expr; body: Statement[] }
  | { kind: 'for'; variable: string; values: Expr; body: Statement[] }
  | { kind: 'return'; value: Expr | null };

/** Tells the parser whether a name currently refers to a verb. */
export type NameClassifier = (name: string) => boolean;
