package j.parse;

/** A parsed sentence: a noun, a verb, or an assignment. */
public sealed interface Phrase permits Phrase.Noun, Phrase.Verb, Phrase.Assign {
    record Noun(Expr expr) implements Phrase {
    }

    record Verb(VerbExpr verb) implements Phrase {
    }

    record Assign(String name, boolean global, Phrase value) implements Phrase {
    }
}
