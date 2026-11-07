package j.verbs;

import j.array.JArray;

/**
 * {@code %}: monadic reciprocal, dyadic division. Both are floating point, as in J;
 * division by zero therefore produces infinities or NaN rather than an error.
 */
public final class Divide extends AtomVerb {
    public Divide() {
        super("%");
    }

    @Override
    public JArray monadCell(JArray y) {
        Numeric.requireNumeric(y);
        return JArray.scalar(1.0 / y.numericAt(0));
    }

    @Override
    public JArray dyadCell(JArray x, JArray y) {
        Numeric.requireNumeric(x, y);
        return JArray.scalar(x.numericAt(0) / y.numericAt(0));
    }
}
