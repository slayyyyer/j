package j.eval;

import java.util.ArrayList;
import java.util.List;

import j.JError;
import j.array.JArray;
import j.parse.Expr;
import j.parse.Phrase;
import j.parse.VerbExpr;
import j.verbs.Verb;

/** Evaluates a parsed phrase into a {@link Value}. */
public final class Evaluator {
    private final Environment environment;

    public Evaluator(Environment environment) {
        this.environment = environment;
    }

    public Value evaluate(Phrase phrase) {
        return switch (phrase) {
            case Phrase.Noun noun -> new Value.Noun(evaluateExpr(noun.expr()));
            case Phrase.Verb verb -> new Value.VerbValue(evaluateVerb(verb.verb()));
        };
    }

    private JArray evaluateExpr(Expr expr) {
        return switch (expr) {
            case Expr.Lit lit -> lit.value();
            case Expr.Ref ref -> asNoun(environment.lookup(ref.name()));
            case Expr.Strand strand -> evaluateStrand(strand);
            case Expr.Monad monad ->
                    Rank.applyMonad(evaluateVerb(monad.verb()), evaluateExpr(monad.y()));
            case Expr.Dyad dyad ->
                    Rank.applyDyad(evaluateVerb(dyad.verb()), evaluateExpr(dyad.x()), evaluateExpr(dyad.y()));
        };
    }

    private Verb evaluateVerb(VerbExpr verb) {
        return switch (verb) {
            case VerbExpr.Lit lit -> lit.verb();
            case VerbExpr.Ref ref -> asVerb(environment.lookup(ref.name()));
        };
    }

    private JArray evaluateStrand(Expr.Strand strand) {
        List<JArray> parts = new ArrayList<>(strand.parts().size());
        for (Expr part : strand.parts()) {
            parts.add(evaluateExpr(part));
        }
        if (parts.size() == 1) {
            return parts.get(0);
        }
        return Assembly.stack(new int[]{parts.size()}, parts);
    }

    private static JArray asNoun(Value value) {
        return switch (value) {
            case Value.Noun noun -> noun.array();
            case Value.VerbValue ignored -> throw new JError("domain error");
        };
    }

    private static Verb asVerb(Value value) {
        return switch (value) {
            case Value.VerbValue verb -> verb.verb();
            case Value.Noun ignored -> throw new JError("domain error");
        };
    }
}
