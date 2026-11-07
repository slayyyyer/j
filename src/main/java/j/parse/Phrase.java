package j.parse;

/** A parsed sentence: either a noun expression or a verb (a verb value). */
public sealed interface Phrase permits Phrase.Noun, Phrase.Verb {
    record Noun(Expr expr) implements Phrase {
    }

    record Verb(VerbExpr verb) implements Phrase {
    }
}
