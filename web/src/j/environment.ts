import { JError } from './atoms.ts';
import type { JArray } from './array.ts';
import type { Verb } from './verbs.ts';

/** A value produced by evaluation: a noun or a verb. */
export type Value =
  | { kind: 'noun'; array: JArray }
  | { kind: 'verb'; verb: Verb };

/**
 * The name table. Names hold either nouns or verbs, and that class is what the
 * parser consults while reading later sentences. An environment may have a
 * parent, so an explicit verb can bind local names while falling back to
 * session globals.
 */
export class Environment {
  private readonly names = new Map<string, Value>();
  private readonly parent: Environment | null;

  constructor(parent: Environment | null = null) {
    this.parent = parent;
  }

  isVerb(name: string): boolean {
    const local = this.names.get(name);
    if (local) {
      return local.kind === 'verb';
    }
    return this.parent ? this.parent.isVerb(name) : false;
  }

  lookup(name: string): Value {
    const value = this.names.get(name);
    if (value) {
      return value;
    }
    if (this.parent) {
      return this.parent.lookup(name);
    }
    throw new JError('value error');
  }

  /** Stores a noun or verb. A later sentence sees the new part of speech. */
  assign(name: string, value: Value): void {
    this.names.set(name, value);
  }

  /** The outermost scope, where `=:` stores names inside an explicit body. */
  root(): Environment {
    return this.parent ? this.parent.root() : this;
  }
}
