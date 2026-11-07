package j.eval;

import java.util.ArrayList;
import java.util.List;

import j.JError;
import j.array.JArray;
import j.parse.Expr;
import j.parse.Phrase;
import j.parse.VerbExpr;
import j.verbs.ExplicitVerb;
import j.verbs.Insert;
import j.verbs.Prefix;
import j.verbs.RankVerb;
import j.verbs.Reflex;
import j.verbs.TrainVerb;
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
            case Phrase.Assign assign -> {
                Value value = evaluate(assign.value());
                Environment target = assign.global() ? environment.root() : environment;
                target.assign(assign.name(), value);
                yield value;
            }
        };
    }

    /** Evaluates an expression with this evaluator's environment. */
    public JArray evaluateExpression(Expr expr) {
        return evaluateExpr(expr);
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
            case Expr.Assign assign -> {
                Value value = evaluate(assign.value());
                Environment target = assign.global() ? environment.root() : environment;
                target.assign(assign.name(), value);
                if (value instanceof Value.Noun noun) {
                    yield noun.array();
                }
                throw new JError("domain error");
            }
        };
    }

    private Verb evaluateVerb(VerbExpr verb) {
        return switch (verb) {
            case VerbExpr.Lit lit -> lit.verb();
            case VerbExpr.Ref ref -> asVerb(environment.lookup(ref.name()));
            case VerbExpr.WithAdverb withAdverb -> {
                Verb operand = evaluateVerb(withAdverb.operand());
                yield switch (withAdverb.adverb()) {
                    case INSERT -> new Insert(operand);
                    case PREFIX -> new Prefix(operand);
                    case REFLEX -> new Reflex(operand);
                };
            }
            case VerbExpr.WithRank withRank -> new RankVerb(
                    evaluateVerb(withRank.operand()),
                    evaluateExpr(withRank.ranks()));
            case VerbExpr.Train train -> {
                List<Verb> verbs = new ArrayList<>(train.verbs().size());
                for (VerbExpr part : train.verbs()) {
                    verbs.add(evaluateVerb(part));
                }
                yield verbs.size() == 1 ? verbs.get(0) : new TrainVerb(verbs);
            }
            case VerbExpr.Explicit explicit -> new ExplicitVerb(explicit.body(), environment);
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
