import { strict as assert } from 'node:assert';
import { describe, test } from 'node:test';
import {
  createShareUrl,
  decodeTranscript,
  encodeTranscript,
  SHARE_HASH_PREFIX,
  transcriptFromHash,
} from '../src/share.ts';

describe('share links', () => {
  test('round trips a transcript, including non-ASCII sources', () => {
    const lines = [
      'a =: 2 3 $ i. 6',
      'a',
      "note =: 'héllo'",
      'note',
    ];
    const encoded = encodeTranscript(lines);

    assert.ok(encoded);
    assert.deepEqual(decodeTranscript(encoded), lines);
  });

  test('builds a fragment that preserves the page and query', () => {
    const url = createShareUrl('https://example.test/?mode=demo#guide', [
      '+/ i. 5',
      '10 - 3 - 2',
    ]);

    assert.ok(url);
    const parsed = new URL(url);
    assert.equal(parsed.origin, 'https://example.test');
    assert.equal(parsed.pathname, '/');
    assert.equal(parsed.search, '?mode=demo');
    assert.ok(parsed.hash.startsWith(SHARE_HASH_PREFIX));
    assert.deepEqual(transcriptFromHash(parsed.hash), [
      '+/ i. 5',
      '10 - 3 - 2',
    ]);
  });

  test('rejects malformed, foreign and unsupported payloads', () => {
    assert.equal(decodeTranscript('not base64!'), null);
    assert.equal(decodeTranscript(''), null);
    assert.equal(transcriptFromHash('#guide'), null);
    assert.equal(transcriptFromHash(`${SHARE_HASH_PREFIX}%%%`), null);

    const wrongVersion = btoa(
      JSON.stringify({ version: 2, lines: ['+ / i. 5'] }),
    );
    assert.equal(decodeTranscript(wrongVersion), null);
  });

  test('preserves an empty transcript', () => {
    const encoded = encodeTranscript([]);

    assert.ok(encoded);
    assert.deepEqual(decodeTranscript(encoded), []);
  });

  test('refuses to build links above the sharing limits', () => {
    const tooManyLines = Array.from({ length: 501 }, () => '1');
    const tooManyChars = ['x'.repeat(200_001)];

    assert.equal(createShareUrl('https://example.test/', tooManyLines), null);
    assert.equal(createShareUrl('https://example.test/', tooManyChars), null);
  });
});
