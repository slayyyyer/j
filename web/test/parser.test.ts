/**
 * Ported from src/test/java/j/parse/ParserTest.java.
 *
 * Java asserted concrete AST classes; the TypeScript AST is a discriminated
 * union, so the helpers below narrow on `kind` and then inspect the same
 * fields.
 */
import { strict as assert } from 'node:assert';
import { describe, test } from 'node:test';
import type { Expr, Phrase, VerbExpr } from '../src/j/ast.ts';
import { Parser } from '../src/j/parser.ts';
import { expectJError } from './support.ts';

function parse(source: string): Phrase {
  return Parser.parse(source, () => false);
}

function nounPhrase(source: string): Extract<Phrase, { kind: 'noun' }> {
  const phrase = parse(source);
  if (phrase.kind !== 'noun') {
    assert.fail(`expected a noun phrase, got ${phrase.kind}`);
  }
  return phrase;
}

function verbPhrase(source: string): Extract<Phrase, { kind: 'verb' }> {
  const phrase = parse(source);
  if (phrase.kind !== 'verb') {
    assert.fail(`expected a verb phrase, got ${phrase.kind}`);
  }
  return phrase;
}

function assignment(source: string): Extract<Phrase, { kind: 'assign' }> {
  const phrase = parse(source);
  if (phrase.kind !== 'assign') {
    assert.fail(`expected an assignment, got ${phrase.kind}`);
  }
  return phrase;
}

function expr(source: string): Expr {
  return nounPhrase(source).expr;
}

function litExpr(exprValue: Expr): Extract<Expr, { kind: 'lit' }> {
  if (exprValue.kind !== 'lit') {
    assert.fail(`expected a literal, got ${exprValue.kind}`);
  }
  return exprValue;
}

function strandExpr(exprValue: Expr): Extract<Expr, { kind: 'strand' }> {
  if (exprValue.kind !== 'strand') {
    assert.fail(`expected a strand, got ${exprValue.kind}`);
  }
  return exprValue;
}

function dyadExpr(exprValue: Expr): Extract<Expr, { kind: 'dyad' }> {
  if (exprValue.kind !== 'dyad') {
    assert.fail(`expected a dyad, got ${exprValue.kind}`);
  }
  return exprValue;
}

function monadExpr(exprValue: Expr): Extract<Expr, { kind: 'monad' }> {
  if (exprValue.kind !== 'monad') {
    assert.fail(`expected a monad, got ${exprValue.kind}`);
  }
  return exprValue;
}

function litVerb(verb: VerbExpr): Extract<VerbExpr, { kind: 'lit' }> {
  if (verb.kind !== 'lit') {
    assert.fail(`expected a literal verb, got ${verb.kind}`);
  }
  return verb;
}

function assertNumber(exprValue: Expr, value: number): void {
  assert.equal(litExpr(exprValue).value.longAt(0), value);
}

describe('Parser', () => {
  test('parses a literal noun', () => {
    assertNumber(expr('42'), 42);
  });

  test('parses adjacent nouns as a strand', () => {
    const strand = strandExpr(expr('1 2 3'));

    assert.equal(strand.parts.length, 3);
    assertNumber(strand.parts[0], 1);
    assertNumber(strand.parts[2], 3);
  });

  test('parses dyads right to left', () => {
    const outer = dyadExpr(expr('2 + 3 + 4'));

    assertNumber(outer.x, 2);
    assert.equal(litVerb(outer.verb).verb.repr(), '+');
    const inner = dyadExpr(outer.y);
    assertNumber(inner.x, 3);
    assertNumber(inner.y, 4);
  });

  test('monad wraps the whole expression to its right', () => {
    const monad = monadExpr(expr('- 2 + 3'));

    assert.equal(litVerb(monad.verb).verb.repr(), '-');
    dyadExpr(monad.y);
  });

  test('parses sequential monads', () => {
    const outer = monadExpr(expr('+ - 5'));

    assert.equal(litVerb(outer.verb).verb.repr(), '+');
    monadExpr(outer.y);
  });

  test('parses a parenthesized noun or verb', () => {
    dyadExpr(expr('(1 + 2)'));

    const verb = verbPhrase('(+)');
    assert.equal(litVerb(verb.verb).verb.repr(), '+');
  });

  test('parses a verb value', () => {
    const verb = verbPhrase('+');

    assert.equal(litVerb(verb.verb).verb.repr(), '+');
  });

  test('groups nouns on both sides of a dyad', () => {
    const dyad = dyadExpr(expr('1 2 3 + 10'));

    strandExpr(dyad.x);
    assertNumber(dyad.y, 10);
  });

  test('reports trailing verb as syntax error', () => {
    const error = expectJError(() => parse('1 +'));

    assert.equal(error.message, 'syntax error');
    assert.equal(error.column, 3);
  });

  test('reports empty parentheses as syntax error', () => {
    const error = expectJError(() => parse('()'));

    assert.equal(error.message, 'syntax error');
    assert.equal(error.column, 2);
  });

  test('reports unclosed parentheses as syntax error', () => {
    expectJError(() => parse('(1 + 2'));
  });

  test('rejects unimplemented primitives', () => {
    expectJError(() => parse('2 & 3'));
  });

  test('parses assignments and chains', () => {
    const global = assignment('a =: 2 + 3');
    assert.equal(global.name, 'a');
    assert.equal(global.global, true);
    assert.equal(global.value.kind, 'noun');

    const chained = assignment('a =: b =: 1');
    assert.equal(chained.value.kind, 'assign');

    const local = assignment('a =. 1');
    assert.equal(local.global, false);
  });
});
