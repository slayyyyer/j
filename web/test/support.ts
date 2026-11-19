import { strict as assert } from 'node:assert';
import { JError } from '../src/j/atoms.ts';
import type { JArray } from '../src/j/array.ts';
import { Environment } from '../src/j/environment.ts';
import { Evaluator } from '../src/j/evaluator.ts';
import { format } from '../src/j/format.ts';
import { Parser } from '../src/j/parser.ts';

/** Evaluates one sentence in a fresh environment, as the Java verb tests do. */
export function evalSentence(source: string): JArray {
  const environment = new Environment();
  const phrase = Parser.parse(source, (name) => environment.isVerb(name));
  const value = new Evaluator(environment).evaluate(phrase);
  if (value.kind !== 'noun') {
    assert.fail(`expected a noun from ${JSON.stringify(source)}`);
  }
  return value.array;
}

/** The display form of a sentence's noun result. */
export function display(source: string): string {
  return format(evalSentence(source));
}

/** Asserts that an action throws a J error, optionally with a given message. */
export function expectJError(action: () => unknown, message?: string): JError {
  try {
    action();
  } catch (error) {
    if (!(error instanceof JError)) {
      assert.fail(`expected a JError, got ${String(error)}`);
    }
    if (message !== undefined) {
      assert.equal(error.message, message);
    }
    return error;
  }
  assert.fail('expected a JError');
}
