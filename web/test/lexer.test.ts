/**
 * Ported from src/test/java/j/lex/LexerTest.java.
 *
 * The port uses JavaScript's safe-integer range, so integer literals at the
 * 64-bit minimum are BIGINT rather than INT; that adaptation is called out in
 * the affected tests.
 */
import { strict as assert } from 'node:assert';
import { describe, test } from 'node:test';
import { JType } from '../src/j/atoms.ts';
import type { JArray } from '../src/j/array.ts';
import { Lexer, TokenType, type Token } from '../src/j/lexer.ts';
import { expectJError } from './support.ts';

function literal(token: Token): JArray {
  if (!token.literal) {
    assert.fail('token has no literal');
  }
  return token.literal;
}

function assertInt(token: Token, expected: number): void {
  assert.equal(token.type, TokenType.NUMBER);
  const value = literal(token);
  assert.equal(value.type, JType.INT);
  assert.equal(value.isScalar, true);
  assert.equal(value.longAt(0), expected);
}

function assertFloat(token: Token, expected: number): void {
  assert.equal(token.type, TokenType.NUMBER);
  const value = literal(token);
  assert.equal(value.type, JType.FLOAT);
  assert.equal(value.isScalar, true);
  if (Number.isNaN(expected)) {
    assert.ok(Number.isNaN(value.doubleAt(0)));
  } else {
    assert.equal(value.doubleAt(0), expected);
  }
}

function stringValue(token: Token): string {
  const characters = literal(token);
  let value = '';
  for (let index = 0; index < characters.size; index++) {
    value += characters.charAt(index);
  }
  return value;
}

function texts(tokens: Token[]): string[] {
  return tokens.filter((token) => token.type !== TokenType.EOF).map((token) => token.text);
}

describe('Lexer', () => {
  test('empty input produces only EOF', () => {
    const tokens = Lexer.lex('');

    assert.equal(tokens.length, 1);
    assert.equal(tokens[0].type, TokenType.EOF);
    assert.equal(tokens[0].column, 1);
    assert.equal(tokens[0].text, '');
  });

  test('lexes integer literals', () => {
    const tokens = Lexer.lex('42 _7 0');

    assertInt(tokens[0], 42);
    assertInt(tokens[1], -7);
    assertInt(tokens[2], 0);
    assert.equal(tokens[0].text, '42');
    assert.equal(tokens[1].text, '_7');
    assert.equal(tokens[3].type, TokenType.EOF);
  });

  test('lexes float literals', () => {
    const tokens = Lexer.lex('3.5 _0.25 1e6 2.5e_3 3.');

    assertFloat(tokens[0], 3.5);
    assertFloat(tokens[1], -0.25);
    assertFloat(tokens[2], 1e6);
    assertFloat(tokens[3], 2.5e-3);
    assertFloat(tokens[4], 3);
    assert.equal(literal(tokens[4]).type, JType.FLOAT);
  });

  test('lexes infinity and NaN', () => {
    const tokens = Lexer.lex('_ __ _.');

    assertFloat(tokens[0], Number.POSITIVE_INFINITY);
    assertFloat(tokens[1], Number.NEGATIVE_INFINITY);
    assertFloat(tokens[2], Number.NaN);
    assert.equal(tokens[0].text, '_');
    assert.equal(tokens[1].text, '__');
    assert.equal(tokens[2].text, '_.');
  });

  test('underscore before number is negative but alone is infinity', () => {
    const tokens = Lexer.lex('_3 _ 3');

    assertInt(tokens[0], -3);
    assertFloat(tokens[1], Number.POSITIVE_INFINITY);
    assertInt(tokens[2], 3);
  });

  test('parses the 64-bit minimum as an extended integer', () => {
    const token = Lexer.lex('_9223372036854775808')[0];

    assert.equal(token.type, TokenType.NUMBER);
    const value = literal(token);
    assert.equal(value.type, JType.BIGINT);
    assert.equal(value.bigAt(0), -9223372036854775808n);
  });

  test('promotes integers outside long range to extended integers', () => {
    const token = Lexer.lex('9223372036854775808')[0];

    assert.equal(literal(token).type, JType.BIGINT);
    assert.equal(literal(token).bigAt(0).toString(), '9223372036854775808');
  });

  test('lexes extended rational and complex literals', () => {
    const tokens = Lexer.lex('123x 1r2 3j4');

    assert.equal(literal(tokens[0]).type, JType.BIGINT);
    assert.equal(literal(tokens[1]).type, JType.RATIONAL);
    assert.equal(literal(tokens[2]).type, JType.COMPLEX);
  });

  test('lexes names', () => {
    const tokens = Lexer.lex('alpha beta_2 x');

    assert.equal(tokens[0].type, TokenType.NAME);
    assert.equal(tokens[0].text, 'alpha');
    assert.equal(tokens[0].column, 1);
    assert.equal(tokens[1].text, 'beta_2');
    assert.equal(tokens[1].column, 7);
    assert.equal(tokens[2].text, 'x');
    assert.equal(tokens[2].column, 14);
  });

  test('tokenizes multi-character primitives as single tokens', () => {
    const tokens = Lexer.lex('{. }. |. |: i. ~: <: >: @:');

    assert.deepEqual(texts(tokens), ['{.', '}.', '|.', '|:', 'i.', '~:', '<:', '>:', '@:']);
    for (let index = 0; index < 9; index++) {
      assert.equal(tokens[index].type, TokenType.PRIMITIVE);
    }
  });

  test('iota is primitive but bare i is a name', () => {
    assert.equal(Lexer.lex('i.')[0].type, TokenType.PRIMITIVE);
    assert.equal(Lexer.lex('i')[0].type, TokenType.NAME);
  });

  test('tokenizes single-character primitives', () => {
    const tokens = Lexer.lex('+ - * % ^ = < > ~ $ , # { ; & . : " / \\');

    assert.deepEqual(texts(tokens), [
      '+', '-', '*', '%', '^', '=', '<', '>', '~', '$', ',', '#',
      '{', ';', '&', '.', ':', '"', '/', '\\',
    ]);
  });

  test('keeps adverbs and conjunctions as separate tokens', () => {
    assert.deepEqual(texts(Lexer.lex('+/')), ['+', '/']);
    assert.deepEqual(texts(Lexer.lex('+/ .*')), ['+', '/', '.', '*']);
    assert.deepEqual(texts(Lexer.lex('+/"1')), ['+', '/', '"', '1']);
    assert.deepEqual(texts(Lexer.lex('+\\i.5')), ['+', '\\', 'i.', '5']);
  });

  test('separates assignments from comparison', () => {
    const tokens = Lexer.lex('a =: b =. c = d');

    assert.equal(tokens[1].type, TokenType.ASSIGN_GLOBAL);
    assert.equal(tokens[1].text, '=:');
    assert.equal(tokens[3].type, TokenType.ASSIGN_LOCAL);
    assert.equal(tokens[3].text, '=.');
    assert.equal(tokens[5].type, TokenType.PRIMITIVE);
    assert.equal(tokens[5].text, '=');
  });

  test('tokenizes parentheses and direct definition delimiters', () => {
    const tokens = Lexer.lex('({{ y }})');

    assert.equal(tokens[0].type, TokenType.LPAREN);
    assert.equal(tokens[1].type, TokenType.LBRACE2);
    assert.equal(tokens[1].text, '{{');
    assert.equal(tokens[2].type, TokenType.NAME);
    assert.equal(tokens[3].type, TokenType.RBRACE2);
    assert.equal(tokens[3].text, '}}');
    assert.equal(tokens[4].type, TokenType.RPAREN);
  });

  test('lexes quoted strings with doubled quote escapes', () => {
    const tokens = Lexer.lex("'abc' 'don''t' '' ''''");

    assert.equal(tokens[0].type, TokenType.STRING);
    assert.equal(tokens[0].text, "'abc'");
    assert.equal(stringValue(tokens[0]), 'abc');
    assert.equal(literal(tokens[0]).type, JType.CHAR);
    assert.equal(literal(tokens[0]).rank, 1);
    assert.equal(literal(tokens[0]).size, 3);

    assert.equal(stringValue(tokens[1]), "don't");
    assert.equal(stringValue(tokens[2]), '');
    assert.equal(stringValue(tokens[3]), "'");
  });

  test('skips comments to end of line', () => {
    const tokens = Lexer.lex('1 + 2 NB. hi\n3');

    assert.deepEqual(texts(tokens), ['1', '+', '2', '3']);
  });

  test('comment only line produces only EOF', () => {
    const tokens = Lexer.lex('NB. just a comment');

    assert.equal(tokens.length, 1);
    assert.equal(tokens[0].type, TokenType.EOF);
  });

  test('comment marker inside string is text', () => {
    const token = Lexer.lex("'NB. hi'")[0];

    assert.equal(stringValue(token), 'NB. hi');
  });

  test('tracks one-based columns', () => {
    const tokens = Lexer.lex('  12 + 3');

    assert.equal(tokens[0].column, 3);
    assert.equal(tokens[1].column, 6);
    assert.equal(tokens[2].column, 8);
    assert.equal(tokens[3].column, 9);
  });

  test('lexes a complete train sentence', () => {
    const tokens = Lexer.lex('mean =: +/ % # NB. train\nmean 1 2 3 4');

    assert.deepEqual(texts(tokens), [
      'mean', '=:', '+', '/', '%', '#',
      'mean', '1', '2', '3', '4',
    ]);
  });

  test('unterminated string reports spelling error at opening quote', () => {
    const error = expectJError(() => Lexer.lex("  'abc"));

    assert.equal(error.message, 'spelling error');
    assert.equal(error.column, 3);
  });

  test('malformed exponent reports spelling error', () => {
    const missingDigits = expectJError(() => Lexer.lex('1e'));
    assert.equal(missingDigits.message, 'spelling error');
    assert.equal(missingDigits.column, 2);

    const missingAfterUnderscore = expectJError(() => Lexer.lex('1e_'));
    assert.equal(missingAfterUnderscore.message, 'spelling error');
  });

  test('unknown character reports spelling error', () => {
    const error = expectJError(() => Lexer.lex('1 ` 2'));

    assert.equal(error.message, 'spelling error');
    assert.equal(error.column, 3);
  });

  test('malformed number suffixes report spelling error', () => {
    expectJError(() => Lexer.lex('1_2'));
    expectJError(() => Lexer.lex('1.5x'));
    expectJError(() => Lexer.lex('1r'));
  });
});
