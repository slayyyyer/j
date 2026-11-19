import type { RunLine } from './j/session.ts';

/** A completed (or abandoned) sentence from the interpreter worker. */

export interface RunOutcome {
  lines: RunLine[];
  tripped: boolean;
  truncated: boolean;
  elapsedMs: number;
}

interface Pending {
  resolve: (outcome: RunOutcome) => void;
  reject: (error: Error) => void;
}

const RUN_BUDGET_MS = 1500;
const MAX_OUTPUT_CHARS = 60_000;

/**
 * Main-thread handle to the interpreter worker, with per-sentence budgets and
 * a hard restart for Stop. Restarting rejects every in-flight request, so
 * callers must treat cancellation as "do not record this line".
 */

export class Runner {
  private worker: Worker;
  private readonly pending = new Map<number, Pending>();
  private nextId = 1;

  constructor() {
    this.worker = this.spawn();
  }

  private spawn(): Worker {
    const worker = new Worker(new URL('./worker.ts', import.meta.url), {
      type: 'module',
    });
    worker.addEventListener('message', (event: MessageEvent<unknown>) => {
      const message = event.data as
        | ({ type: 'result'; id: number } & RunOutcome)
        | { type: 'failure'; id: number; message: string };
      const entry = this.pending.get(message.id);
      if (entry === undefined) {
        return;
      }
      this.pending.delete(message.id);
      if (message.type === 'result') {
        entry.resolve({
          lines: message.lines,
          tripped: message.tripped,
          truncated: message.truncated,
          elapsedMs: message.elapsedMs,
        });
      } else {
        entry.reject(new Error(message.message));
      }
    });
    worker.addEventListener('error', () => {
      this.failPending(new Error('The interpreter worker stopped unexpectedly.'));
    });
    return worker;
  }

  private failPending(error: Error): void {
    for (const entry of this.pending.values()) {
      entry.reject(error);
    }
    this.pending.clear();
  }

  run(source: string): Promise<RunOutcome> {
    const id = this.nextId;
    this.nextId += 1;
    return new Promise<RunOutcome>((resolve, reject) => {
      this.pending.set(id, { resolve, reject });
      this.worker.postMessage({
        type: 'run',
        id,
        source,
        budgetMs: RUN_BUDGET_MS,
        maxChars: MAX_OUTPUT_CHARS,
      });
    });
  }

  reset(): void {
    this.worker.postMessage({ type: 'reset' });
  }

  /** Terminates the worker and rejects everything in flight. */
  restart(): void {
    this.worker.terminate();
    this.failPending(new Error('cancelled'));
    this.worker = this.spawn();
  }

  dispose(): void {
    this.worker.terminate();
    this.failPending(new Error('cancelled'));
  }
}
