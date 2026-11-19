import { budgetTripped, endBudget, startBudget } from './j/budget.ts';
import { runSource, type RunLine, type Session } from './j/session.ts';

/**
 * One budgeted interpreter request, independent of the Worker shell so it can
 * be exercised under Node. Errors are returned as output lines by
 * `runSource`; unexpected failures propagate.
 */

export interface RunRequest {
  source: string;
  budgetMs: number;
  /** Display cap; formatted results can be far larger than the screen. */
  maxChars: number;
  /** Optional step ceiling for deterministic tests. */
  maxSteps?: number;
}

export interface RunResponse {
  lines: RunLine[];
  /** The sentence was abandoned because it exhausted its budget. */
  tripped: boolean;
  /** The display text exceeded `maxChars`. */
  truncated: boolean;
  elapsedMs: number;
}

export function runWithBudget(session: Session, request: RunRequest): RunResponse {
  const started = Date.now();
  startBudget({ milliseconds: request.budgetMs, steps: request.maxSteps });

  let lines: RunLine[] = [];
  let tripped = false;
  try {
    lines = runSource(session, request.source);
  } finally {
    tripped = budgetTripped();
    endBudget();
  }

  const capped = capLines(lines, request.maxChars);
  return {
    lines: capped.lines,
    tripped,
    truncated: capped.truncated,
    elapsedMs: Date.now() - started,
  };
}

function capLines(
  lines: readonly RunLine[],
  maxChars: number,
): { lines: RunLine[]; truncated: boolean } {
  const result: RunLine[] = [];
  let used = 0;
  for (const line of lines) {
    const room = maxChars - used;
    if (room <= 0) {
      return { lines: result, truncated: true };
    }
    if (line.text.length > room) {
      result.push({ ...line, text: line.text.slice(0, room) });
      return { lines: result, truncated: true };
    }
    result.push(line);
    used += line.text.length;
  }
  return { lines: result, truncated: false };
}
