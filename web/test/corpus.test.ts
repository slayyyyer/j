import { strict as assert } from 'node:assert';
import { readFileSync } from 'node:fs';
import { describe, test } from 'node:test';
import { Session, runSource } from '../src/j/session.ts';
import { parseCorpus, parseDivergences } from './corpus/format.ts';

const corpus = parseCorpus(
  readFileSync(new URL('./corpus/j-corpus.tsv', import.meta.url), 'utf8'),
);
const known = parseDivergences(
  readFileSync(
    new URL('./corpus/known-divergences.tsv', import.meta.url),
    'utf8',
  ),
);

function evaluate(source: string): string {
  return runSource(new Session(), source)
    .map((line) => line.text)
    .join('\n');
}

describe('J corpus captured from real J', () => {
  for (const item of corpus) {
    const reason = known.get(item.id);
    if (reason !== undefined) {
      test(item.id, { skip: `known divergence: ${reason}` }, () => {});
      continue;
    }
    test(item.id, () => {
      assert.equal(evaluate(item.source), item.expected);
    });
  }

  test('every known divergence is still listed in the corpus', () => {
    const ids = new Set(corpus.map((item) => item.id));
    for (const id of known.keys()) {
      assert.ok(ids.has(id), `${id} is not a corpus case`);
    }
  });

  test('known divergences are still real divergences', () => {
    for (const [id, reason] of known) {
      const item = corpus.find((candidate) => candidate.id === id);
      assert.ok(item, `${id} is not a corpus case`);
      assert.notEqual(
        evaluate(item.source),
        item.expected,
        `${id} now matches J (${reason}); remove it from known-divergences.tsv`,
      );
    }
  });
});
