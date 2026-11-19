/**
 * Format of `j-corpus.tsv`, shared by the capture tool and the TypeScript
 * suite (the Java suite parses the same file with the same rules).
 *
 * One case per line: `id <TAB> source <TAB> expected`. Backslash, newline and
 * tab inside a field are escaped as `\\`, `\n` and `\t`; everything else is
 * literal, including the trailing spaces J uses to fill character arrays.
 */

export interface CorpusCase {
  id: string;
  source: string;
  expected: string;
}

export function escapeField(value: string): string {
  return value
    .replace(/\\/g, '\\\\')
    .replace(/\n/g, '\\n')
    .replace(/\t/g, '\\t');
}

export function unescapeField(value: string): string {
  let result = '';
  for (let index = 0; index < value.length; index++) {
    const character = value[index];
    if (character !== '\\') {
      result += character;
      continue;
    }
    const escape = value[++index];
    if (escape === 'n') {
      result += '\n';
    } else if (escape === 't') {
      result += '\t';
    } else if (escape === '\\') {
      result += '\\';
    } else {
      throw new Error(`unsupported escape \\${escape ?? ''}`);
    }
  }
  return result;
}

export function parseCorpus(text: string): CorpusCase[] {
  const cases: CorpusCase[] = [];
  for (const line of text.split(/\r?\n/)) {
    if (line.trim() === '' || line.startsWith('#')) {
      continue;
    }
    const fields = line.split('\t');
    if (fields.length !== 3) {
      throw new Error(`corpus line needs three fields: ${line}`);
    }
    cases.push({
      id: fields[0],
      source: unescapeField(fields[1]),
      expected: unescapeField(fields[2]),
    });
  }
  return cases;
}

export function serializeCorpus(
  cases: readonly CorpusCase[],
  header: readonly string[],
): string {
  const lines = header.map((line) => `# ${line}`);
  for (const item of cases) {
    lines.push(
      [
        item.id,
        escapeField(item.source),
        escapeField(item.expected),
      ].join('\t'),
    );
  }
  return `${lines.join('\n')}\n`;
}

/** Reads `known-divergences.tsv`: one `id <TAB> reason` per line. */
export function parseDivergences(text: string): Map<string, string> {
  const known = new Map<string, string>();
  for (const line of text.split(/\r?\n/)) {
    if (line.trim() === '' || line.startsWith('#')) {
      continue;
    }
    const [id, reason = 'known divergence'] = line.split('\t');
    known.set(id, reason);
  }
  return known;
}
