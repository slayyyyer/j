import { strict as assert } from 'node:assert';
import { describe, test } from 'node:test';
import { budgetTripped, endBudget, startBudget } from '../src/j/budget.ts';
import { Session, runSource } from '../src/j/session.ts';
import { runWithBudget } from '../src/run-request.ts';

function render(source: string, session = new Session()): string {
  return runSource(session, source)
    .map((line) => line.text)
    .join('\n');
}

describe('execution budget', () => {
  test('stops an endless explicit while loop', () => {
    startBudget({ milliseconds: 2000, steps: 10_000 });
    try {
      const source = 'loop =: 3 : 0\nwhile. 1 do.\nend.\n)\nloop 0';
      assert.match(render(source), /\|limit error/);
      assert.equal(budgetTripped(), true);
    } finally {
      endBudget();
    }
  });

  test('step ceiling catches large reductions', () => {
    startBudget({ milliseconds: 10_000, steps: 1000 });
    try {
      assert.match(render('+/ i. 100000'), /\|limit error/);
      assert.equal(budgetTripped(), true);
    } finally {
      endBudget();
    }
  });

  test('disarms cleanly so later runs are unbounded', () => {
    startBudget({ milliseconds: 10_000, steps: 1000 });
    try {
      render('+/ i. 100000');
    } finally {
      endBudget();
    }
    assert.equal(budgetTripped(), true);
    assert.equal(render('+/ i. 5'), '10');
  });

  test('a normal sentence completes without tripping', () => {
    const response = runWithBudget(new Session(), {
      source: '2 * 3 + 4',
      budgetMs: 1000,
      maxChars: 10_000,
    });
    assert.deepEqual(
      response.lines.map((line) => line.text),
      ['14'],
    );
    assert.equal(response.tripped, false);
    assert.equal(response.truncated, false);
  });

  test('caps oversized output and flags truncation', () => {
    const response = runWithBudget(new Session(), {
      source: 'i. 1000',
      budgetMs: 2000,
      maxChars: 50,
    });
    assert.equal(response.truncated, true);
    const shown = response.lines.reduce((total, line) => total + line.text.length, 0);
    assert.ok(shown <= 50, `expected at most 50 characters, got ${shown}`);
  });

  test('a budgeted trip is reported and does not escape endBudget', () => {
    const session = new Session();
    const response = runWithBudget(session, {
      source: 'loop =: 3 : 0\nwhile. 1 do.\nend.\n)\nloop 0',
      budgetMs: 2000,
      maxChars: 10_000,
      maxSteps: 5000,
    });
    assert.equal(response.tripped, true);
    assert.match(
      response.lines.map((line) => line.text).join('\n'),
      /\|limit error/,
    );
    // The budget is off again: this ordinary sentence must run.
    assert.equal(render('1 + 1', session), '2');
  });
});
