import { JError, JType, Complex, Rational } from './atoms.ts';
import { JArray } from './array.ts';

/** The lexical classes of J words and punctuation. */
export const TokenType = {
  NUMBER: 'NUMBER',
  STRING: 'STRING',
  NAME: 'NAME',
  PRIMITIVE: 'PRIMITIVE',
  LPAREN: 'LPAREN',
  RPAREN: 'RPAREN',
  ASSIGN_GLOBAL: 'ASSIGN_GLOBAL',
  ASSIGN_LOCAL: 'ASSIGN_LOCAL',
  LBRACE2: 'LBRACE2',
  RBRACE2: 'RBRACE2',
  EOF: 'EOF',
} as const;

export type TokenType = (typeof TokenType)[keyof typeof TokenType];

export interface Token {
  type: TokenType;
  /** Exact source spelling (a string token includes its quotes). */
  text: string;
  /** Parsed value for NUMBER and STRING tokens. */
  literal: JArray | null;
  /** 1-based starting column. */
  column: number;
}

/**
 * Primitive spellings, longest first. `/` and `\` are adverbs, not part of a
 * verb name, so `+/` intentionally becomes two tokens.
 */
const PRIMITIVES = [
  '$.^:_1',
  '{{',
  '}}',
  '=:',
  '=.',
  '{.',
  '}.',
  '|.',
  '|:',
  'i.',
  '~:',
  '<:',
  '>:',
  '@:',
  '$.',
  ':',
  '+',
  '-',
  '*',
  '%',
  '^',
  '=',
  '<',
  '>',
  '~',
  '$',
  ',',
  '#',
  '{',
  ';',
  '@',
  '&',
  '.',
  '"',
  '/',
  '\\',
];

const SORTED_PRIMITIVES = [...PRIMITIVES].sort((a, b) => b.length - a.length);

/**
 * Tokenizes one J sentence.
 *
 * Negative literals use `_`, so there is no ambiguity with the `-` verb.
 * Malformed literals and unknown characters report `spelling error` with a
 * 1-based column. Numeric suffixes select exact and complex types: `123x`,
 * `1r2`, `3j4`.
 */
export class Lexer {
  private readonly source: string;
  private position = 0;

  private constructor(source: string) {
    this.source = source;
  }

  static lex(source: string): Token[] {
    return new Lexer(source).tokenize();
  }

  private tokenize(): Token[] {
    const tokens: Token[] = [];
    while (!this.atEnd()) {
      const current = this.peek();
      if (/\s/.test(current)) {
        this.position++;
      } else if (this.source.startsWith('NB.', this.position)) {
        this.skipComment();
      } else if (current === "'") {
        tokens.push(this.lexString());
      } else if (current === '(') {
        tokens.push(this.symbol(TokenType.LPAREN, '('));
      } else if (current === ')') {
        tokens.push(this.symbol(TokenType.RPAREN, ')'));
      } else if (current === '_' || this.isDigit(current)) {
        tokens.push(this.lexNumber());
      } else {
        const primitive = this.lexPrimitive();
        if (primitive) {
          tokens.push(primitive);
        } else if (this.isNameStart(current)) {
          tokens.push(this.lexName());
        } else {
          throw new JError('spelling error', this.position + 1);
        }
      }
    }
    tokens.push({ type: TokenType.EOF, text: '', literal: null, column: this.source.length + 1 });
    return tokens;
  }

  // ------------------------------------------------------------- literals

  private lexNumber(): Token {
    const start = this.position;

    if (this.peek() === '_') {
      if (this.position + 1 < this.source.length) {
        const next = this.source[this.position + 1];
        if (next === '_') {
          return this.numberToken(start, this.position + 2, Number.NEGATIVE_INFINITY);
        }
        if (next === '.') {
          return this.numberToken(start, this.position + 2, Number.NaN);
        }
        if (!this.isDigit(next)) {
          return this.numberToken(start, this.position + 1, Number.POSITIVE_INFINITY);
        }
      } else {
        return this.numberToken(start, this.position + 1, Number.POSITIVE_INFINITY);
      }
      this.position++; // leading underscore of a negative literal
    }

    const integerStart = this.position;
    while (!this.atEnd() && this.isDigit(this.peek())) {
      this.position++;
    }
    if (this.position === integerStart) {
      throw new JError('spelling error', start + 1);
    }

    let isFloat = false;
    if (!this.atEnd() && this.peek() === '.') {
      isFloat = true;
      this.position++;
      while (!this.atEnd() && this.isDigit(this.peek())) {
        this.position++;
      }
    }

    if (!this.atEnd() && this.peek() === 'e') {
      isFloat = true;
      const exponent = this.position;
      this.position++;
      if (!this.atEnd() && this.peek() === '_') {
        this.position++;
      }
      const exponentDigits = this.position;
      while (!this.atEnd() && this.isDigit(this.peek())) {
        this.position++;
      }
      if (this.position === exponentDigits) {
        throw new JError('spelling error', exponent + 1);
      }
    }

    const end = this.position;

    if (!this.atEnd() && this.peek() === 'x') {
      if (isFloat) {
        throw new JError('spelling error', start + 1);
      }
      this.position++;
      this.ensureNumberBoundary(this.position);
      const text = this.source.slice(start, this.position);
      return this.literalToken(
        start,
        this.position,
        JArray.scalar(JType.BIGINT, this.parseBigInteger(text.slice(0, -1), start)),
      );
    }

    if (!this.atEnd() && this.peek() === 'r') {
      if (isFloat) {
        throw new JError('spelling error', start + 1);
      }
      const numeratorText = this.source.slice(start, end);
      this.position++;
      const denominatorStart = this.position;
      while (!this.atEnd() && this.isDigit(this.peek())) {
        this.position++;
      }
      if (this.position === denominatorStart) {
        throw new JError('spelling error', start + 1);
      }
      this.ensureNumberBoundary(this.position);
      const numerator = this.parseBigInteger(numeratorText, start);
      const denominator = this.parseBigInteger(
        this.source.slice(denominatorStart, this.position),
        denominatorStart,
      );
      if (denominator === 0n) {
        throw new JError('spelling error', denominatorStart + 1);
      }
      return this.literalToken(
        start,
        this.position,
        JArray.scalar(JType.RATIONAL, new Rational(numerator, denominator)),
      );
    }

    if (!this.atEnd() && this.peek() === 'j') {
      const realText = this.source.slice(start, end);
      this.position++;
      const imaginaryStart = this.position;
      if (!this.atEnd() && this.peek() === '_') {
        this.position++;
      }
      const imaginaryDigits = this.position;
      while (!this.atEnd() && this.isDigit(this.peek())) {
        this.position++;
      }
      if (this.position === imaginaryDigits) {
        throw new JError('spelling error', start + 1);
      }
      if (!this.atEnd() && this.peek() === '.') {
        this.position++;
        while (!this.atEnd() && this.isDigit(this.peek())) {
          this.position++;
        }
      }
      if (!this.atEnd() && this.peek() === 'e') {
        const exponent = this.position;
        this.position++;
        if (!this.atEnd() && this.peek() === '_') {
          this.position++;
        }
        const exponentDigits = this.position;
        while (!this.atEnd() && this.isDigit(this.peek())) {
          this.position++;
        }
        if (this.position === exponentDigits) {
          throw new JError('spelling error', exponent + 1);
        }
      }
      this.ensureNumberBoundary(this.position);
      const real = this.parseDouble(realText);
      const imaginary = this.parseDouble(this.source.slice(imaginaryStart, this.position));
      return this.literalToken(
        start,
        this.position,
        JArray.scalar(JType.COMPLEX, new Complex(real, imaginary)),
      );
    }

    this.ensureNumberBoundary(end);
    const text = this.source.slice(start, end);
    const literal = isFloat
      ? JArray.scalar(JType.FLOAT, this.parseDouble(text))
      : this.integerLiteral(text);
    return { type: TokenType.NUMBER, text, literal, column: start + 1 };
  }

  private numberToken(start: number, end: number, value: number): Token {
    this.ensureNumberBoundary(end);
    this.position = end;
    return {
      type: TokenType.NUMBER,
      text: this.source.slice(start, end),
      literal: JArray.scalar(JType.FLOAT, value),
      column: start + 1,
    };
  }

  private literalToken(start: number, end: number, literal: JArray): Token {
    this.position = end;
    return { type: TokenType.NUMBER, text: this.source.slice(start, end), literal, column: start + 1 };
  }

  /**
   * J requires a delimiter between a numeric literal and a following word.
   * Adjacent letters, digits, underscores or dots are unsupported forms
   * (for example `1_2`), not two literals.
   */
  private ensureNumberBoundary(end: number): void {
    if (end >= this.source.length) {
      return;
    }
    const next = this.source[end];
    if (this.isNamePart(next) || next === '.') {
      throw new JError('spelling error', end + 1);
    }
  }

  private integerLiteral(text: string): JArray {
    const value = Number(text.replace(/_/g, '-'));
    if (Number.isSafeInteger(value)) {
      return JArray.scalar(JType.INT, value);
    }
    return JArray.scalar(JType.BIGINT, this.parseBigInteger(text, 0));
  }

  private parseBigInteger(text: string, start: number): bigint {
    try {
      return BigInt(text.replace(/_/g, '-'));
    } catch {
      throw new JError('spelling error', start + 1);
    }
  }

  private parseDouble(text: string): number {
    return Number(text.replace(/_/g, '-'));
  }

  private lexString(): Token {
    const start = this.position;
    this.position++;
    let value = '';

    while (!this.atEnd()) {
      const current = this.peek();
      if (current !== "'") {
        value += current;
        this.position++;
      } else if (this.position + 1 < this.source.length && this.source[this.position + 1] === "'") {
        value += "'";
        this.position += 2;
      } else {
        this.position++;
        return {
          type: TokenType.STRING,
          text: this.source.slice(start, this.position),
          literal: JArray.text(value),
          column: start + 1,
        };
      }
    }

    throw new JError('spelling error', start + 1);
  }

  // ---------------------------------------------------------------- words

  private lexName(): Token {
    const start = this.position;
    this.position++;
    while (!this.atEnd() && this.isNamePart(this.peek())) {
      this.position++;
    }
    return { type: TokenType.NAME, text: this.source.slice(start, this.position), literal: null, column: start + 1 };
  }

  private lexPrimitive(): Token | null {
    for (const spelling of SORTED_PRIMITIVES) {
      if (!this.source.startsWith(spelling, this.position)) {
        continue;
      }
      let type: TokenType = TokenType.PRIMITIVE;
      if (spelling === '=:') {
        type = TokenType.ASSIGN_GLOBAL;
      } else if (spelling === '=.') {
        type = TokenType.ASSIGN_LOCAL;
      } else if (spelling === '{{') {
        type = TokenType.LBRACE2;
      } else if (spelling === '}}') {
        type = TokenType.RBRACE2;
      }
      return this.symbol(type, spelling);
    }
    return null;
  }

  private symbol(type: TokenType, text: string): Token {
    const token: Token = { type, text, literal: null, column: this.position + 1 };
    this.position += text.length;
    return token;
  }

  private skipComment(): void {
    while (!this.atEnd() && this.peek() !== '\n') {
      this.position++;
    }
  }

  // --------------------------------------------------------------- helpers

  private atEnd(): boolean {
    return this.position >= this.source.length;
  }

  private peek(): string {
    return this.source[this.position];
  }

  private isDigit(value: string): boolean {
    return value >= '0' && value <= '9';
  }

  private isNameStart(value: string): boolean {
    return (value >= 'a' && value <= 'z') || (value >= 'A' && value <= 'Z');
  }

  private isNamePart(value: string): boolean {
    return this.isNameStart(value) || this.isDigit(value) || value === '_';
  }
}

