import { JError } from './atoms.ts';

/**
 * Cooperative execution budget for untrusted sessions.
 *
 * The interpreter is synchronous, so a sentence such as `while. 1 do. end.`
 * would otherwise occupy the thread forever. Loops call `checkBudget`, which
 * is a single boolean test when no budget is active, and raises J's `limit
 * error` once the step or time allowance is exhausted. The TypeScript tests
 * run without a budget; the browser worker arms one per sentence.
 */

let enabled = false;
let tripped = false;
let steps = 0;
let maxSteps = 0;
let deadline = 0;

/** Time is sampled every this many steps; `Date.now` is not free. */
const SAMPLE_INTERVAL = 1024;

export interface BudgetLimits {
  milliseconds: number;
  /** Step ceiling, for loops that run faster than the clock can sample. */
  steps?: number;
}

export function startBudget(limits: BudgetLimits): void {
  enabled = true;
  tripped = false;
  steps = 0;
  maxSteps = limits.steps ?? 20_000_000;
  deadline = Date.now() + limits.milliseconds;
}

export function endBudget(): void {
  enabled = false;
}

/** Whether the most recent budget was exhausted. */
export function budgetTripped(): boolean {
  return tripped;
}

export function checkBudget(): void {
  if (!enabled) {
    return;
  }
  steps += 1;
  if (
    steps > maxSteps ||
    (steps % SAMPLE_INTERVAL === 0 && Date.now() > deadline)
  ) {
    tripped = true;
    throw new JError('limit error');
  }
}
