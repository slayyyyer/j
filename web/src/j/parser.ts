import { JError } from './atoms.ts';
import type { Expr, NameClassifier, Phrase, Statement, VerbExpr } from './ast.ts';
import { Lexer, TokenType, type Token } from './lexer.ts';
import { lookupPrimitive } from './verbs.ts';

type Term =
  | { kind: 'noun'; expr: Expr; column: number }
  | { kind: 'verb'; verb: VerbExpr; column: number };

/**
 * Parses one J sentence.
 *
 * J has no operator precedence. The parser gathers noun and verb phrases,
 * combines adjacent nouns into strands, then reduces applications from the
 * right: `verb noun` is a monad and `noun verb noun` is a dyad. That is what
 * makes `2 * 3 + 4` mean `2 * (3 + 4)`.
 *
 * Adverbs and the rank conjunction bind tighter than application, so `+/"1`
 * is built as a single verb phrase. A phrase containing only verbs is a verb
 * value: one verb alone, or a hook/fork train for two or more.
 */
export class Parser {
  private readonly tokens: Token[];
  private readonly names: NameClassifier;
  private index = 0;

  constructor(tokens: Token[], names: NameClassifier) {
    this.tokens = tokens;
    this.names = names;
  }

  static parse(source: string, names: NameClassifier): Phrase {
    return new Parser(Lexer.lex(source), names).parseSentence();
  }

  parseSentence(): Phrase {
    const first = this.peek();
    if (first.type === TokenType.EOF) {
      throw syntax(first);
    }
    const phrase = this.parseEntry();
    const trailing = this.peek();
    if (trailing.type !== TokenType.EOF) {
      throw syntax(trailing);
    }
    return phrase;
  }

  private parseEntry(): Phrase {
    if (this.peek().type === TokenType.NAME && isAssignment(this.peekAt(1))) {
      return this.parseAssignment();
    }
    return this.parsePhrase();
  }

  private parseAssignment(): Phrase {
    const name = this.next();
    const operator = this.next();
    const valueToken = this.peek();
    if (valueToken.type === TokenType.EOF || valueToken.type === TokenType.RPAREN) {
      throw syntax(valueToken);
    }
    const value = this.parseEntry();
    return {
      kind: 'assign',
      name: name.text,
      global: operator.type === TokenType.ASSIGN_GLOBAL,
      value,
    };
  }

  private parsePhrase(): Phrase {
    if (this.peek().type === TokenType.NAME && isAssignment(this.peekAt(1))) {
      return this.parseAssignment();
    }

    const terms: Term[] = [];

    while (this.peek().type !== TokenType.EOF && this.peek().type !== TokenType.RPAREN) {
      const token = this.next();
      switch (token.type) {
        case TokenType.LPAREN: {
          const inner = this.parsePhrase();
          const close = this.peek();
          if (close.type !== TokenType.RPAREN) {
            throw syntax(close);
          }
          this.next();
          terms.push(asTerm(inner, token.column));
          break;
        }
        case TokenType.NUMBER:
        case TokenType.STRING:
          terms.push({ kind: 'noun', expr: { kind: 'lit', value: token.literal! }, column: token.column });
          break;
        case TokenType.NAME:
          if (this.names(token.text)) {
            terms.push({ kind: 'verb', verb: { kind: 'ref', name: token.text }, column: token.column });
          } else {
            terms.push({ kind: 'noun', expr: { kind: 'ref', name: token.text }, column: token.column });
          }
          break;
        case TokenType.LBRACE2:
          terms.push({ kind: 'verb', verb: this.parseExplicitVerb(token), column: token.column });
          break;
        case TokenType.PRIMITIVE: {
          const spelling = token.text;
          if (spelling === '/' || spelling === '\\' || spelling === '~') {
            this.applyAdverb(terms, spelling, token);
          } else if (spelling === '"') {
            this.applyRank(terms, token);
          } else {
            const verb = lookupPrimitive(spelling);
            if (!verb) {
              throw syntax(token);
            }
            terms.push({ kind: 'verb', verb: { kind: 'lit', verb }, column: token.column });
          }
          break;
        }
        default:
          throw syntax(token);
      }
    }

    if (terms.length === 0) {
      throw syntax(this.peek());
    }
    return this.reduce(terms);
  }

  private reduce(rawTerms: Term[]): Phrase {
    let terms = groupNouns(rawTerms);

    if (terms.every((term) => term.kind === 'verb')) {
      const verbs = terms.map((term) => (term as { kind: 'verb'; verb: VerbExpr }).verb);
      const verb = verbs.length === 1 ? verbs[0] : { kind: 'train' as const, verbs };
      return { kind: 'verb', verb };
    }

    while (terms.length > 1) {
      const verbIndex = lastVerbIndex(terms);
      if (verbIndex < 0) {
        throw syntaxAt(terms[terms.length - 1].column);
      }
      if (verbIndex === terms.length - 1) {
        throw syntaxAt(terms[verbIndex].column);
      }

      const verbTerm = terms[verbIndex] as { kind: 'verb'; verb: VerbExpr; column: number };
      const right = terms[verbIndex + 1];
      if (right.kind !== 'noun') {
        throw syntaxAt(right.column);
      }
      const left = verbIndex > 0 ? terms[verbIndex - 1] : null;

      let start: number;
      let result: Expr;
      if (left && left.kind === 'noun') {
        result = { kind: 'dyad', x: left.expr, verb: verbTerm.verb, y: right.expr };
        start = verbIndex - 1;
      } else {
        result = { kind: 'monad', verb: verbTerm.verb, y: right.expr };
        start = verbIndex;
      }

      terms = [
        ...terms.slice(0, start),
        { kind: 'noun', expr: result, column: terms[start].column },
        ...terms.slice(verbIndex + 2),
      ];
    }

    const only = terms[0];
    if (only.kind === 'noun') {
      return { kind: 'noun', expr: only.expr };
    }
    return { kind: 'verb', verb: only.verb };
  }

  /** Parses an inline `{{ ... }}` explicit verb (one expression). */
  private parseExplicitVerb(open: Token): VerbExpr {
    let depth = 1;
    let cursor = this.index;
    while (cursor < this.tokens.length) {
      const token = this.tokens[cursor];
      if (token.type === TokenType.LBRACE2) {
        depth++;
      } else if (token.type === TokenType.RBRACE2) {
        depth--;
        if (depth === 0) {
          break;
        }
      } else if (token.type === TokenType.EOF) {
        break;
      }
      cursor++;
    }

    if (cursor >= this.tokens.length || this.tokens[cursor].type !== TokenType.RBRACE2) {
      throw syntax(open);
    }

    const bodyTokens = this.tokens.slice(this.index, cursor);
    bodyTokens.push({ type: TokenType.EOF, text: '', literal: null, column: open.column });
    this.index = cursor + 1;

    const bodyNames: NameClassifier = (name) =>
      name !== 'x' && name !== 'y' && this.names(name);
    const body = new Parser(bodyTokens, bodyNames).parseSentence();
    if (body.kind === 'verb') {
      throw syntax(open);
    }
    const statement: Statement = { kind: 'sentence', phrase: body };
    return { kind: 'explicit', body: [statement] };
  }

  private applyAdverb(terms: Term[], adverb: '/' | '\\' | '~', token: Token): void {
    const last = terms[terms.length - 1];
    if (!last || last.kind !== 'verb') {
      throw syntax(token);
    }
    terms[terms.length - 1] = {
      kind: 'verb',
      verb: { kind: 'adverb', operand: last.verb, adverb },
      column: last.column,
    };
  }

  private applyRank(terms: Term[], token: Token): void {
    const last = terms[terms.length - 1];
    if (!last || last.kind !== 'verb') {
      throw syntax(token);
    }
    const ranks = this.parseRankSpec();
    terms[terms.length - 1] = {
      kind: 'verb',
      verb: { kind: 'rank', operand: last.verb, ranks },
      column: last.column,
    };
  }

  private parseRankSpec(): Expr {
    const parts: Expr[] = [];

    rankSpec: while (true) {
      const token = this.peek();
      switch (token.type) {
        case TokenType.NUMBER:
        case TokenType.STRING:
          this.next();
          parts.push({ kind: 'lit', value: token.literal! });
          break;
        case TokenType.NAME:
          if (this.names(token.text)) {
            break rankSpec;
          }
          this.next();
          parts.push({ kind: 'ref', name: token.text });
          break;
        case TokenType.LPAREN: {
          this.next();
          const inner = this.parsePhrase();
          const close = this.peek();
          if (close.type !== TokenType.RPAREN) {
            throw syntax(close);
          }
          this.next();
          if (inner.kind !== 'noun') {
            throw syntax(token);
          }
          parts.push(inner.expr);
          break;
        }
        default:
          break rankSpec;
      }
    }

    return finishRankExpr(parts, this.peek());
  }

  // --------------------------------------------------------------- helpers

  private next(): Token {
    return this.tokens[this.index++];
  }

  private peek(): Token {
    return this.tokens[this.index];
  }

  private peekAt(offset: number): Token {
    return this.tokens[Math.min(this.index + offset, this.tokens.length - 1)];
  }
}

function finishRankExpr(parts: Expr[], fallback: Token): Expr {
  if (parts.length === 0) {
    throw syntax(fallback);
  }
  return parts.length === 1 ? parts[0] : { kind: 'strand', parts };
}

function groupNouns(terms: Term[]): Term[] {
  const grouped: Term[] = [];
  let run: Expr[] = [];
  let runColumn = 0;

  for (const term of terms) {
    if (term.kind === 'noun') {
      if (run.length === 0) {
        runColumn = term.column;
      }
      run.push(term.expr);
    } else {
      flushNounRun(run, runColumn, grouped);
      run = [];
      grouped.push(term);
    }
  }
  flushNounRun(run, runColumn, grouped);
  return grouped;
}

function flushNounRun(run: Expr[], column: number, grouped: Term[]): void {
  if (run.length === 0) {
    return;
  }
  const expr: Expr = run.length === 1 ? run[0] : { kind: 'strand', parts: run };
  grouped.push({ kind: 'noun', expr, column });
}

function lastVerbIndex(terms: Term[]): number {
  for (let index = terms.length - 1; index >= 0; index--) {
    if (terms[index].kind === 'verb') {
      return index;
    }
  }
  return -1;
}

function asTerm(phrase: Phrase, column: number): Term {
  switch (phrase.kind) {
    case 'noun':
      return { kind: 'noun', expr: phrase.expr, column };
    case 'verb':
      return { kind: 'verb', verb: phrase.verb, column };
    case 'assign':
      return {
        kind: 'noun',
        expr: { kind: 'assign', name: phrase.name, global: phrase.global, value: phrase.value },
        column,
      };
  }
}

function isAssignment(token: Token): boolean {
  return token.type === TokenType.ASSIGN_GLOBAL || token.type === TokenType.ASSIGN_LOCAL;
}

function syntax(token: Token): JError {
  return new JError('syntax error', token.column);
}

function syntaxAt(column: number): JError {
  return new JError('syntax error', column);
}
