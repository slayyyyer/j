import { JError } from './atoms.ts';
import type { Expr, NameClassifier, Statement } from './ast.ts';
import { Lexer } from './lexer.ts';
import { Parser } from './parser.ts';

/**
 * Parses the lines of a `3 : 0` or multi-line `{{ }}` body into statements.
 *
 * Control words are recognized at the start of a line: `if.`/`else.`/`end.`,
 * `while.`/`end.`, `for_i.`/`end.` and `return.`. Nested structures recurse
 * through the same block parser. Blank lines and `NB.` comments are skipped.
 */
export class StatementParser {
  private readonly lines: string[];
  private readonly names: NameClassifier;
  private readonly locals = new Set<string>();
  private index = 0;

  private constructor(lines: string[], names: NameClassifier) {
    this.lines = [...lines];
    this.names = names;
  }

  static parse(lines: string[], names: NameClassifier): Statement[] {
    const parser = new StatementParser(lines, names);
    const body = parser.parseBlock(new Set());
    if (parser.index < parser.lines.length) {
      throw new JError('syntax error');
    }
    return body;
  }

  private parseBlock(terminators: Set<string>): Statement[] {
    const statements: Statement[] = [];
    for (;;) {
      const line = this.currentLine();
      if (line === null) {
        if (terminators.size > 0) {
          throw new JError('syntax error');
        }
        return statements;
      }
      if (isTerminator(line, terminators)) {
        return statements;
      }
      statements.push(this.parseStatement(line));
    }
  }

  private parseStatement(line: string): Statement {
    if (line.startsWith('if.')) {
      return this.parseIf(line);
    }
    if (line.startsWith('while.')) {
      return this.parseWhile(line);
    }
    if (line.startsWith('for.') || line.startsWith('for_')) {
      return this.parseFor(line);
    }
    if (line.startsWith('return.')) {
      this.index++;
      const value = line.slice('return.'.length).trim();
      return { kind: 'return', value: value === '' ? null : this.parseExpr(value) };
    }
    if (line.startsWith('else.') || line.startsWith('end.')) {
      throw new JError('syntax error');
    }

    this.index++;
    const phrase = new Parser(Lexer.lex(line), (name) => this.isVerb(name)).parseSentence();
    if (phrase.kind === 'verb') {
      throw new JError('syntax error');
    }
    return { kind: 'sentence', phrase };
  }

  private parseIf(line: string): Statement {
    this.index++;
    const condition = controlExpression(line, 'if.');
    const thenBody = this.parseBlock(new Set(['else.', 'end.']));
    const terminator = this.currentLine();
    if (terminator !== null && terminator.startsWith('else.')) {
      this.index++;
      const elseBody = this.parseBlock(new Set(['end.']));
      this.expectEnd();
      return { kind: 'if', condition: this.parseExpr(condition), thenBody, elseBody };
    }
    this.expectEnd();
    return { kind: 'if', condition: this.parseExpr(condition), thenBody, elseBody: [] };
  }

  private parseWhile(line: string): Statement {
    this.index++;
    const condition = controlExpression(line, 'while.');
    const body = this.parseBlock(new Set(['end.']));
    this.expectEnd();
    return { kind: 'while', condition: this.parseExpr(condition), body };
  }

  private parseFor(line: string): Statement {
    this.index++;
    const dot = line.indexOf('.');
    if (dot < 0) {
      throw new JError('syntax error');
    }
    const header = line.slice(3, dot).trim();
    const variable = header.startsWith('_') && header.length > 1 ? header.slice(1) : 'i';
    if (!/^[A-Za-z][A-Za-z0-9_]*$/.test(variable)) {
      throw new JError('syntax error');
    }

    const rest = line.slice(dot + 1).trim();
    if (!rest.endsWith('do.')) {
      throw new JError('syntax error');
    }
    const valuesText = rest.slice(0, rest.length - 'do.'.length).trim();
    const values = this.parseExpr(valuesText);

    this.locals.add(variable);
    this.locals.add(`${variable}_index`);
    try {
      const body = this.parseBlock(new Set(['end.']));
      this.expectEnd();
      return { kind: 'for', variable, values, body };
    } finally {
      this.locals.delete(variable);
      this.locals.delete(`${variable}_index`);
    }
  }

  private expectEnd(): void {
    const line = this.currentLine();
    if (line === null || !line.startsWith('end.')) {
      throw new JError('syntax error');
    }
    this.index++;
  }

  private parseExpr(source: string): Expr {
    if (source === '') {
      throw new JError('syntax error');
    }
    const phrase = Parser.parse(source, (name) => this.isVerb(name));
    if (phrase.kind !== 'noun') {
      throw new JError('syntax error');
    }
    return phrase.expr;
  }

  private isVerb(name: string): boolean {
    return (
      name !== 'x' && name !== 'y' && !this.locals.has(name) && this.names(name)
    );
  }

  private currentLine(): string | null {
    while (this.index < this.lines.length) {
      const line = this.lines[this.index].trim();
      if (line === '' || line.startsWith('NB.')) {
        this.index++;
        continue;
      }
      return line;
    }
    return null;
  }
}

function controlExpression(line: string, keyword: string): string {
  const rest = line.slice(keyword.length).trim();
  if (!rest.endsWith('do.')) {
    throw new JError('syntax error');
  }
  return rest.slice(0, rest.length - 'do.'.length).trim();
}

function isTerminator(line: string, terminators: Set<string>): boolean {
  for (const terminator of terminators) {
    if (line.startsWith(terminator)) {
      return true;
    }
  }
  return false;
}
