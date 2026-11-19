/// <reference lib="webworker" />
import { Session } from './j/session.ts';
import { runWithBudget, type RunRequest } from './run-request.ts';

/**
 * The interpreter worker. It owns the session so a runaway sentence occupies
 * this thread instead of the page; the main thread can terminate it at any
 * time and rebuild from the recorded transcript.
 */

export interface WorkerRunRequest extends RunRequest {
  type: 'run';
  id: number;
}

export type WorkerRequest = WorkerRunRequest | { type: 'reset' };

interface WorkerScope {
  postMessage(message: unknown): void;
  addEventListener(
    type: 'message',
    listener: (event: MessageEvent<WorkerRequest>) => void,
  ): void;
}

const scope = self as unknown as WorkerScope;
let session = new Session();

scope.addEventListener('message', (event) => {
  const request = event.data;
  if (request.type === 'reset') {
    session = new Session();
    return;
  }

  try {
    const response = runWithBudget(session, request);
    scope.postMessage({ type: 'result', id: request.id, ...response });
  } catch (error) {
    scope.postMessage({
      type: 'failure',
      id: request.id,
      message: error instanceof Error ? error.message : String(error),
    });
  }
});
