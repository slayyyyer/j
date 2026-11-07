package j.verbs;

import j.array.JArray;

/** {@code <}: monadic box (not implemented yet), dyadic less than. */
public final class Less extends AtomVerb {
    public Less() {
        super("<");
    }

    @Override
    public JArray dyadCell(JArray x, JArray y) {
        return Comparison.test(Comparison.Kind.LESS, x, y);
    }
}
