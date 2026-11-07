package j.verbs;

import java.util.List;

import j.JError;
import j.array.JArray;
import j.array.JType;
import j.eval.Environment;
import j.eval.Evaluator;
import j.eval.Value;
import j.parse.Statement;

/** A user-defined verb from {@code {{ ... }}} or {@code 3 : 0}. */
public final class ExplicitVerb extends Verb {
    private final List<Statement> body;
    private final Environment globals;

    public ExplicitVerb(List<Statement> body, Environment globals) {
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
        try {
            Value result = execute(body, locals, evaluator);
            if (result instanceof Value.Noun noun) {
                return noun.array();
            }
            throw new JError("domain error");
        } catch (ReturnSignal signal) {
            if (signal.value instanceof Value.Noun noun) {
                return noun.array();
            }
            throw new JError("domain error");
        }
    }

    private Value execute(List<Statement> statements, Environment locals, Evaluator evaluator) {
        Value result = null;
        for (Statement statement : statements) {
            result = switch (statement) {
                case Statement.Sentence sentence -> evaluator.evaluate(sentence.phrase());
                case Statement.If branch -> {
                    boolean truth = truth(evaluator.evaluateExpression(branch.condition()));
                    yield truth
                            ? execute(branch.thenBody(), locals, evaluator)
                            : execute(branch.elseBody(), locals, evaluator);
                }
                case Statement.While loop -> {
                    Value last = null;
                    while (truth(evaluator.evaluateExpression(loop.condition()))) {
                        last = execute(loop.body(), locals, evaluator);
                    }
                    yield last;
                }
                case Statement.For loop -> {
                    JArray values = evaluator.evaluateExpression(loop.values());
                    Value last = null;
                    for (int item = 0; item < values.itemCount(); item++) {
                        locals.assign(loop.variable(), new Value.Noun(values.item(item)));
                        locals.assign(
                                loop.variable() + "_index",
                                new Value.Noun(JArray.scalar(item)));
                        last = execute(loop.body(), locals, evaluator);
                    }
                    yield last;
                }
                case Statement.Return returned -> {
                    Value value = returned.value() == null
                            ? null
                            : new Value.Noun(evaluator.evaluateExpression(returned.value()));
                    throw new ReturnSignal(value);
                }
            };
        }
        return result;
    }

    private static boolean truth(JArray value) {
        if (!value.isScalar()) {
            throw new JError("length error");
        }
        if (value.type() == JType.INT) {
            return value.longAt(0) != 0;
        }
        if (value.type() == JType.BIGINT) {
            return value.bigAt(0).signum() != 0;
        }
        if (value.type() == JType.RATIONAL) {
            return value.rationalAt(0).numerator().signum() != 0;
        }
        if (value.type() == JType.FLOAT) {
            double number = value.doubleAt(0);
            return number != 0.0 && !Double.isNaN(number);
        }
        if (value.type() == JType.COMPLEX) {
            var number = value.complexAt(0);
            return number.real() != 0.0 || number.imaginary() != 0.0;
        }
        throw new JError("domain error");
    }

    private static final class ReturnSignal extends RuntimeException {
        private final Value value;

        private ReturnSignal(Value value) {
            this.value = value;
        }
    }
}
