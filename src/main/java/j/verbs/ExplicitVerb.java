package j.verbs;

import java.util.List;

import j.array.JArray;
import j.eval.Environment;
import j.eval.Evaluator;
import j.eval.Value;
import j.parse.Expr;

/** A user-defined verb from {@code {{ ... }}} or {@code 3 : 0}. */
public final class ExplicitVerb extends Verb {
    private final List<Expr> body;
    private final Environment globals;

    public ExplicitVerb(List<Expr> body, Environment globals) {
        super("{{...}}");
        if (body.isEmpty()) {
            throw new IllegalArgumentException("an explicit verb needs a body");
        }
        this.body = List.copyOf(body);
        this.globals = globals;
    }

    @Override
    public int monadRank() {
        return WHOLE;
    }

    @Override
    public int leftRank() {
        return WHOLE;
    }

    @Override
    public int rightRank() {
        return WHOLE;
    }

    @Override
    public JArray monadCell(JArray y) {
        return evaluate(y, null);
    }

    @Override
    public JArray dyadCell(JArray x, JArray y) {
        return evaluate(y, x);
    }

    private JArray evaluate(JArray y, JArray x) {
        Environment locals = new Environment(globals);
        locals.assign("y", new Value.Noun(y));
        if (x != null) {
            locals.assign("x", new Value.Noun(x));
        }

        Evaluator evaluator = new Evaluator(locals);
        JArray result = null;
        for (Expr sentence : body) {
            result = evaluator.evaluateExpression(sentence);
        }
        return result;
    }
}
