package j.eval;

import j.array.JArray;
import j.verbs.Verb;

/** A value produced by evaluation: a noun or a verb. */
public sealed interface Value permits Value.Noun, Value.VerbValue {
    record Noun(JArray array) implements Value {
    }

    record VerbValue(Verb verb) implements Value {
    }
}
