package j.verbs;

import j.array.JArray;

/** {@code >}: monadic open (not implemented yet), dyadic greater than. */
public final class Greater extends AtomVerb {
    public Greater() {
        super(">");
    }

    @Override
    public JArray dyadCell(JArray x, JArray y) {
        return Comparison.test(Comparison.Kind.GREATER, x, y);
    }
}
