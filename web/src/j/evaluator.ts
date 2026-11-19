import { JError } from './atoms.ts';
import type { Expr, Phrase, VerbExpr } from './ast.ts';
import { JArray } from './array.ts';
import { stackAssembly } from './assembly.ts';
import { Environment } from './environment.ts';
import type { Value } from './environment.ts';
import { applyDyad, applyMonad } from './rank.ts';
import { ExplicitVerb, Insert, Prefix, RankVerb, Reflex, TrainVerb } from './derived.ts';
import type { Verb } from './verbs.ts';

/** Evaluates a parsed phrase into a Value. */
export class Evaluator {
  private readonly environment: Environment;

  constructor(environment: Environment) {
    this.environment = environment;
  }

  evaluate(phrase: Phrase): Value {
    switch (phrase.kind) {
      case 'noun':
        return { kind: 'noun', array: this.evaluateExpr(phrase.expr) };
      case 'verb':
        return { kind: 'verb', verb: this.evaluateVerb(phrase.verb) };
      case 'assign': {
        const value = this.evaluate(phrase.value);
        this.assign(phrase.name, phrase.global, value);
        return value;
      }
    }
  }

  /** Evaluates an expression with this evaluator's environment. */
  evaluateExpression(expr: Expr): JArray {
    return this.evaluateExpr(expr);
  }

  private evaluateExpr(expr: Expr): JArray {
    switch (expr.kind) {
      case 'lit':
        return expr.value;
      case 'ref':
        return asNoun(this.environment.lookup(expr.name));
      case 'strand': {
        const parts = expr.parts.map((part) => this.evaluateExpr(part));
        if (parts.length === 1) {
          return parts[0];
        }
        return stackAssembly([parts.length], parts);
      }
      case 'monad':
        return applyMonad(this.evaluateVerb(expr.verb), this.evaluateExpr(expr.y));
      case 'dyad':
        return applyDyad(
          this.evaluateVerb(expr.verb),
          this.evaluateExpr(expr.x),
          this.evaluateExpr(expr.y),
        );
      case 'assign': {
        const value = this.evaluate(expr.value);
        this.assign(expr.name, expr.global, value);
        if (value.kind === 'noun') {
          return value.array;
        }
        throw new JError('domain error');
      }
    }
  }

  private evaluateVerb(verb: VerbExpr): Verb {
    switch (verb.kind) {
      case 'lit':
        return verb.verb;
      case 'ref':
        return asVerb(this.environment.lookup(verb.name));
      case 'adverb': {
        const operand = this.evaluateVerb(verb.operand);
        if (verb.adverb === '/') {
          return new Insert(operand);
        }
        if (verb.adverb === '\\') {
          return new Prefix(operand);
        }
        return new Reflex(operand);
      }
      case 'rank':
        return new RankVerb(this.evaluateVerb(verb.operand), this.evaluateExpr(verb.ranks));
      case 'train': {
        const verbs = verb.verbs.map((part) => this.evaluateVerb(part));
        return verbs.length === 1 ? verbs[0] : new TrainVerb(verbs);
      }
      case 'explicit':
        return new ExplicitVerb(verb.body, this.environment);
    }
  }

  private assign(name: string, global: boolean, value: Value): void {
    const target = global ? this.environment.root() : this.environment;
    target.assign(name, value);
  }
}

function asNoun(value: Value): JArray {
  if (value.kind === 'noun') {
    return value.array;
  }
  throw new JError('domain error');
}

function asVerb(value: Value): Verb {
  if (value.kind === 'verb') {
    return value.verb;
  }
  throw new JError('domain error');
}
