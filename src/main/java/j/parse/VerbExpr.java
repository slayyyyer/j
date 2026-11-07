package j.parse;

import java.util.List;

import j.verbs.Verb;

/** A phrase that produces a verb. */
public sealed interface VerbExpr
        permits VerbExpr.Lit, VerbExpr.Ref, VerbExpr.WithAdverb, VerbExpr.WithRank,
                VerbExpr.Train, VerbExpr.Explicit {
    /** A primitive verb. */
    record Lit(Verb verb) implements VerbExpr {
    }

    /** A name that holds a verb. */
    record Ref(String name) implements VerbExpr {
    }

    /** An adverb applied to a verb, e.g. {@code +/}. */
    record WithAdverb(VerbExpr operand, Adverb adverb) implements VerbExpr {
    }

    /** A rank conjunction, e.g. {@code +/\"1}. */
    record WithRank(VerbExpr operand, Expr ranks) implements VerbExpr {
    }

    /** Two or more verbs forming a hook or fork. */
    record Train(List<VerbExpr> verbs) implements VerbExpr {
        public Train {
            verbs = List.copyOf(verbs);
        }
    }

    /** An explicit verb, from {@code {{ ... }}} or {@code 3 : 0}. */
    record Explicit(List<Phrase> body) implements VerbExpr {
        public Explicit {
            body = List.copyOf(body);
        }
    }
}
