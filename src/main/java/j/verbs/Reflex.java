package j.verbs;

import j.array.JArray;
import j.eval.Rank;

/** {@code u~}: reflex monadically and passive dyadically. */
public final class Reflex extends Verb {
    private final Verb operand;

    public Reflex(Verb operand) {
        super(operand.repr() + "~");
        this.operand = operand;
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
        return Rank.applyDyad(operand, y, y);
    }

    @Override
    public JArray dyadCell(JArray x, JArray y) {
        return Rank.applyDyad(operand, y, x);
    }
}
