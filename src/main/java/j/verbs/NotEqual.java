package j.verbs;

import j.array.JArray;

/** {@code ~:}: monadic nub sieve, dyadic inequality. */
public final class NotEqual extends Verb {
    public NotEqual() {
        super("~:");
    }

    @Override
    public int monadRank() {
        return WHOLE;
    }

    @Override
    public int leftRank() {
        return 0;
    }

    @Override
    public int rightRank() {
        return 0;
    }

    @Override
    public JArray monadCell(JArray y) {
        return Items.nubSieve(y);
    }

    @Override
    public JArray dyadCell(JArray x, JArray y) {
        return Comparison.test(Comparison.Kind.NOT_EQUAL, x, y);
    }
}
