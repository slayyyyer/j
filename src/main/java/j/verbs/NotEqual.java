package j.verbs;

import j.array.JArray;

/** {@code ~:}: monadic nub sieve (not implemented yet), dyadic inequality. */
public final class NotEqual extends AtomVerb {
    public NotEqual() {
        super("~:");
    }

    @Override
    public JArray dyadCell(JArray x, JArray y) {
        return Comparison.test(Comparison.Kind.NOT_EQUAL, x, y);
    }
}
