/**
 * Transcript codec for share links and the local autosave.
 *
 * A transcript is JSON, UTF-8 encoded and base64url-encoded so it can sit in a
 * URL fragment without escaping. The autosave stores the same payload, so
 * restoring a share link and restoring a session are one code path. This
 * module is DOM-free so it can be tested under Node.
 */

const PAYLOAD_VERSION = 1;
const SHARE_HASH_PARAM = 's=';
export const SHARE_HASH_PREFIX = `#${SHARE_HASH_PARAM}`;

/** Sharing limits keep generated links short enough to paste anywhere. */
const MAX_URL_LINES = 500;
const MAX_URL_CHARS = 200_000;

interface TranscriptPayload {
  version: number;
  lines: string[];
}

function isStringArray(value: unknown): value is string[] {
  return (
    Array.isArray(value) && value.every((line) => typeof line === 'string')
  );
}

function encodeBase64Url(bytes: Uint8Array): string {
  let binary = '';
  const chunkSize = 0x8000;
  for (let start = 0; start < bytes.length; start += chunkSize) {
    binary += String.fromCharCode(...bytes.subarray(start, start + chunkSize));
  }
  return btoa(binary).replace(/\+/g, '-').replace(/\//g, '_').replace(/=+$/, '');
}

function decodeBase64Url(value: string): Uint8Array {
  const padding = (4 - (value.length % 4)) % 4;
  const binary = atob(
    value.replace(/-/g, '+').replace(/_/g, '/') + '='.repeat(padding),
  );
  const bytes = new Uint8Array(binary.length);
  for (let index = 0; index < binary.length; index++) {
    bytes[index] = binary.charCodeAt(index);
  }
  return bytes;
}

/** Encodes a transcript. */
export function encodeTranscript(lines: readonly string[]): string {
  const payload: TranscriptPayload = {
    version: PAYLOAD_VERSION,
    lines: [...lines],
  };
  return encodeBase64Url(new TextEncoder().encode(JSON.stringify(payload)));
}

/** Decodes a payload, returning null for malformed or unsupported links. */
export function decodeTranscript(encoded: string): string[] | null {
  if (encoded.length === 0) {
    return null;
  }

  try {
    const payload: unknown = JSON.parse(
      new TextDecoder().decode(decodeBase64Url(encoded)),
    );
    if (typeof payload !== 'object' || payload === null) {
      return null;
    }
    const candidate = payload as { version?: unknown; lines?: unknown };
    if (
      candidate.version !== PAYLOAD_VERSION ||
      !isStringArray(candidate.lines)
    ) {
      return null;
    }
    return [...candidate.lines];
  } catch {
    return null;
  }
}

/** Reads a transcript from a fragment such as `#s=...`. */
export function transcriptFromHash(hash: string): string[] | null {
  if (!hash.startsWith(SHARE_HASH_PREFIX)) {
    return null;
  }
  return decodeTranscript(hash.slice(SHARE_HASH_PREFIX.length));
}

/** Builds a full URL whose fragment replays the given transcript. */
export function createShareUrl(
  href: string,
  lines: readonly string[],
): string | null {
  const chars = lines.reduce((total, line) => total + line.length, 0);
  if (lines.length > MAX_URL_LINES || chars > MAX_URL_CHARS) {
    return null;
  }
  const url = new URL(href);
  url.hash = `${SHARE_HASH_PARAM}${encodeTranscript(lines)}`;
  return url.toString();
}
