import { strict as assert } from 'node:assert';
import { readFileSync } from 'node:fs';
import { describe, test } from 'node:test';
import { Session, runSource } from '../src/j/session.ts';

/**
 * Keeps the guide's executable examples honest.
 *
 * Each `<pre data-run>` block is what the page's Run button passes to
 * `runSource`, so the whole block is replayed here too. In addition, every
 * `NB.` annotation that states a result is checked against a fresh prefix of
 * the snippet, which ties the claim to that sentence while preserving earlier
 * definitions and assignments. Deliberate errors are checked as errors.
 *
 * Explanatory annotations are listed in `COMMENTARY`; they are still run with
 * the block. A new annotation that is neither a parseable result nor listed
 * here fails the suite, so it cannot silently go unchecked.
 */

const html = readFileSync(new URL('../index.html', import.meta.url), 'utf8').replace(
  /\r\n/g,
  '\n',
);

interface Block {
  source: string;
  /** One-based line in index.html where the block starts. */
  line: number;
}

interface Expectation {
  kind: 'output' | 'error';
  text: string;
}

/** `NB.` comments that explain the example rather than state its result. */
const COMMENTARY = new Set([
  'a 3x4 table',
  'a 3x4 classification table',
  '1 + (2 + 3)',
  '1 - (2 - 3)',
  'same, expressed negatively',
  'box every atom',
  'dense again',
  'the same, by inverse',
  'copy keeps sparse storage',
]);

const ERROR_TEXT =
  /^(spelling|syntax|value|domain|length|rank|index|valence|limit) error$/;

function decodeEntities(text: string): string {
  return text
    .replace(/&lt;/g, '<')
    .replace(/&gt;/g, '>')
    .replace(/&quot;/g, '"')
    .replace(/&#39;|&#x27;/g, "'")
    .replace(/&nbsp;/g, ' ')
    .replace(/&#(\d+);/g, (_, code: string) =>
      String.fromCodePoint(Number(code)),
    )
    .replace(/&amp;/g, '&');
}

function extractBlocks(): Block[] {
  const pattern = /<pre\b[^>]*\bdata-run\b[^>]*>([\s\S]*?)<\/pre>/g;
  const blocks: Block[] = [];
  for (const match of html.matchAll(pattern)) {
    blocks.push({
      source: decodeEntities(match[1]),
      line: html.slice(0, match.index).split('\n').length,
    });
  }
  return blocks;
}

/**
 * Finds an `NB.` comment at the top level, ignoring string literals (including
 * J's doubled-quote escape) so prose inside `'...'` is not mistaken for a
 * comment.
 */
function annotationOf(line: string): string | null {
  let quoted = false;
  for (let index = 0; index < line.length; index++) {
    const character = line[index];
    if (character === "'") {
      if (quoted && line[index + 1] === "'") {
        index += 1;
        continue;
      }
      quoted = !quoted;
      continue;
    }
    if (
      !quoted &&
      line.startsWith('NB.', index) &&
      (index === 0 || /\s/.test(line[index - 1]))
    ) {
      return line.slice(index + 3).trim();
    }
  }
  return null;
}

/** Turns `a / b` in a guide annotation into the two display rows. */
function displayRows(text: string): string {
  return text.replaceAll(' / ', '\n');
}

function expectationOf(comment: string): Expectation | null {
  if (COMMENTARY.has(comment)) {
    return null;
  }
  if (ERROR_TEXT.test(comment)) {
    return { kind: 'error', text: `|${comment}` };
  }

  // "sum divided by tally: 2.5" and "whole table: 12 15 18 21".
  const colon = comment.lastIndexOf(': ');
  if (colon >= 0) {
    return { kind: 'output', text: displayRows(comment.slice(colon + 2)) };
  }

  // "5, and c is bound": the result precedes the explanation.
  const aside = comment.indexOf(', and ');
  if (aside >= 0) {
    return { kind: 'output', text: displayRows(comment.slice(0, aside)) };
  }

  return { kind: 'output', text: displayRows(comment) };
}

const blocks = extractBlocks();
const rawBlockCount = (html.match(/<pre\b[^>]*\bdata-run\b[^>]*>/g) ?? []).length;

describe('guide snippets', () => {
  test('the guide still contains every data-run block', () => {
    assert.ok(rawBlockCount > 0, 'no data-run blocks found in index.html');
    assert.equal(blocks.length, rawBlockCount);
  });

  test('every block runs without unannotated errors', () => {
    const failures: string[] = [];

    for (const block of blocks) {
      const expectedErrors = new Set<string>();
      for (const line of block.source.split('\n')) {
        const comment = annotationOf(line);
        if (comment !== null && ERROR_TEXT.test(comment)) {
          expectedErrors.add(`|${comment}`);
        }
      }

      for (const line of runSource(new Session(), block.source)) {
        if (line.kind === 'error' && !expectedErrors.has(line.text)) {
          failures.push(
            `index.html:${block.line}: unexpected ${line.text}\n${block.source}`,
          );
        }
      }
    }

    assert.deepEqual(failures, []);
  });

  test('every stated NB. result matches the interpreter', () => {
    const failures: string[] = [];
    const seenCommentary = new Set<string>();
    let checked = 0;

    for (const block of blocks) {
      const lines = block.source.split('\n');
      for (let index = 0; index < lines.length; index++) {
        const comment = annotationOf(lines[index]);
        if (comment === null) {
          continue;
        }

        const expectation = expectationOf(comment);
        if (expectation === null) {
          seenCommentary.add(comment);
          continue;
        }

        const prefix = lines.slice(0, index + 1).join('\n');
        const output = runSource(new Session(), prefix).at(-1);
        const where = `index.html:${block.line + index} ${JSON.stringify(lines[index])}`;

        if (output === undefined) {
          failures.push(
            `${where}: expected ${expectation.kind} ${JSON.stringify(expectation.text)}, got no output`,
          );
        } else if (
          output.kind !== expectation.kind ||
          output.text !== expectation.text
        ) {
          failures.push(
            `${where}: expected ${expectation.kind} ${JSON.stringify(expectation.text)}, got ${output.kind} ${JSON.stringify(output.text)}`,
          );
        } else {
          checked += 1;
        }
      }
    }

    for (const comment of COMMENTARY) {
      if (!seenCommentary.has(comment)) {
        failures.push(
          `commentary annotation is no longer in the guide: ${JSON.stringify(comment)}`,
        );
      }
    }

    assert.deepEqual(failures, []);
    assert.ok(checked >= 30, `expected at least 30 stated results, checked ${checked}`);
  });
});
