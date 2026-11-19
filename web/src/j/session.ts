import { JError, JType } from './atoms.ts';
import { format } from './format.ts';
import { Environment } from './environment.ts';
import { Evaluator } from './evaluator.ts';
import { Lexer, TokenType, type Token } from './lexer.ts';
import { Parser } from './parser.ts';
import type { Statement } from './ast.ts';
import { StatementParser } from './statements.ts';
import { ExplicitVerb } from './derived.ts';

/**
 * One interpreter session: owns the name table and evaluates lines.
 *
 * `evaluate` returns the text to display, or null for blank, comment-only and
 * assignment sentences. Multi-line definitions are handled by `runSource`,
 * which mirrors the command-line REPL's collection of `3 : 0` and `{{` bodies.
 */
export class Session {
  private readonly environment = new Environment();
  private readonly evaluator = new Evaluator(this.environment);

  /** Whether a line starts a multi-line `name =: 3 : 0` definition. */
  isExplicitDefinitionStart(line: string): boolean {
    try {
      parseDefinitionHeader(Lexer.lex(line));
      return true;
    } catch (error) {
      if (error instanceof JError) {
        return false;
      }
      throw error;
    }
  }

  /** Whether a line starts a multi-line `name =: {{ ... }}` definition. */
  isDirectDefinitionStart(line: string): boolean {
    try {
      parseDirectHeader(Lexer.lex(line));
      return true;
    } catch (error) {
      if (error instanceof JError) {
        return false;
      }
      throw error;
    }
  }

  /** Defines a verb from a `3 : 0` header and body lines. */
  defineExplicit(header: string, bodyLines: string[]): void {
    this.define(parseDefinitionHeader(Lexer.lex(header)), bodyLines);
  }

  /** Defines a multi-line direct definition. */
  defineDirect(header: string, bodyLines: string[]): void {
    const name = parseDirectHeader(Lexer.lex(header));
    const body = [...bodyLines];
    if (body.length > 0) {
      const last = body.length - 1;
      const close = body[last].indexOf('}}');
      if (close >= 0) {
        body[last] = body[last].slice(0, close);
        if (body[last].trim() === '') {
          body.pop();
        }
      }
    }
    this.define(name, body);
  }

  private define(name: string, bodyLines: string[]): void {
    const body: Statement[] = StatementParser.parse(bodyLines, (name) =>
      this.environment.isVerb(name),
    );
    if (body.length === 0) {
      throw new JError('syntax error');
    }
    this.environment.assign(name, {
      kind: 'verb',
      verb: new ExplicitVerb(body, this.environment),
    });
  }

  evaluate(line: string): string | null {
    const tokens = Lexer.lex(line);
    if (tokens.length === 1) {
      return null;
    }
    const phrase = new Parser(tokens, (name) => this.environment.isVerb(name)).parseSentence();
    if (phrase.kind === 'assign') {
      this.evaluator.evaluate(phrase);
      return null;
    }
    const value = this.evaluator.evaluate(phrase);
    return value.kind === 'noun' ? format(value.array) : value.verb.repr();
  }
}

function parseDefinitionHeader(tokens: Token[]): string {
  if (tokens.length !== 6) {
    throw new JError('syntax error');
  }
  const [name, assignment, three, colon, zero, end] = tokens;
  const assignmentToken =
    assignment.type === TokenType.ASSIGN_GLOBAL || assignment.type === TokenType.ASSIGN_LOCAL;
  if (
    name.type !== TokenType.NAME ||
    !assignmentToken ||
    !isInteger(three, 3) ||
    colon.type !== TokenType.PRIMITIVE ||
    colon.text !== ':' ||
    !isInteger(zero, 0) ||
    end.type !== TokenType.EOF
  ) {
    throw new JError('syntax error');
  }
  return name.text;
}

function parseDirectHeader(tokens: Token[]): string {
  if (tokens.length !== 4) {
    throw new JError('syntax error');
  }
  const [name, assignment, open, end] = tokens;
  const assignmentToken =
    assignment.type === TokenType.ASSIGN_GLOBAL || assignment.type === TokenType.ASSIGN_LOCAL;
  if (
    name.type !== TokenType.NAME ||
    !assignmentToken ||
    open.type !== TokenType.LBRACE2 ||
    end.type !== TokenType.EOF
  ) {
    throw new JError('syntax error');
  }
  return name.text;
}

function isInteger(token: Token, expected: number): boolean {
  const literal = token.literal;
  return (
    token.type === TokenType.NUMBER &&
    literal !== null &&
    literal.type === JType.INT &&
    literal.isScalar &&
    literal.longAt(0) === expected
  );
}

/** One line of output produced by running a block of source. */
export interface RunLine {
  kind: 'output' | 'error';
  text: string;
}

/**
 * Runs a block of source (possibly several lines, including a multi-line
 * definition) and returns the lines it would display in the REPL.
 */
export function runSource(session: Session, source: string): RunLine[] {
  const lines = source.split(/\r?\n/);
  const output: RunLine[] = [];
  let pending: { type: 'explicit' | 'direct'; header: string; body: string[] } | null = null;

  for (const line of lines) {
    try {
      if (pending) {
        if (pending.type === 'explicit') {
          if (line.trim() === ')') {
            session.defineExplicit(pending.header, pending.body);
            pending = null;
          } else {
            pending.body.push(line);
          }
        } else {
          const close = line.indexOf('}}');
          if (close >= 0) {
            pending.body.push(line.slice(0, close));
            session.defineDirect(pending.header, pending.body);
            pending = null;
          } else {
            pending.body.push(line);
          }
        }
      } else if (session.isExplicitDefinitionStart(line)) {
        pending = { type: 'explicit', header: line, body: [] };
      } else if (session.isDirectDefinitionStart(line)) {
        pending = { type: 'direct', header: line, body: [] };
      } else {
        const rendered = session.evaluate(line);
        if (rendered !== null) {
          output.push({ kind: 'output', text: rendered });
        }
      }
    } catch (error) {
      if (error instanceof JError) {
        output.push({ kind: 'error', text: `|${error.message}` });
        pending = null;
      } else {
        throw error;
      }
    }
  }

  if (pending) {
    output.push({ kind: 'error', text: '|syntax error' });
  }
  return output;
}
