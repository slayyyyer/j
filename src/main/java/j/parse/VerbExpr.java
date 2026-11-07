package j.parse;

import j.verbs.Verb;

/** A phrase that produces a verb. */
public sealed interface VerbExpr permits VerbExpr.Lit, VerbExpr.Ref {
    /** A primitive or derived verb already built by the parser. */
    record Lit(Verb verb) implements VerbExpr {
    }

    /** A name that holds a verb. */
    record Ref(String name) implements VerbExpr {
    }
}
