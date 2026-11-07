package j.verbs;

import j.array.JArray;

/** {@code =}: monadic self-classify (not implemented yet), dyadic equality. */
public final class Equal extends AtomVerb {
    public Equal() {
        super("=");
    }

    @Override
    public JArray dyadCell(JArray x, JArray y) {
        return Comparison.test(Comparison.Kind.EQUAL, x, y);
    }
}
