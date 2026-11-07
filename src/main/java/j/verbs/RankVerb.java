package j.verbs;

import j.JError;
import j.array.JArray;
import j.array.JFormat;
import j.array.JType;
import j.eval.Rank;

/** {@code u\"r}: apply {@code u} to cells of the specified rank. */
public final class RankVerb extends Verb {
    private final Verb operand;
    private final int monadRank;
    private final int leftRank;
    private final int rightRank;

    public RankVerb(Verb operand, JArray rankSpec) {
        super(operand.repr() + "\"" + JFormat.format(rankSpec));
        int[] ranks = toRanks(rankSpec);
        this.operand = operand;
        this.monadRank = ranks[0];
        this.leftRank = ranks[1];
        this.rightRank = ranks[2];
    }

    @Override
    public int monadRank() {
        return monadRank;
    }

    @Override
    public int leftRank() {
        return leftRank;
    }

    @Override
    public int rightRank() {
        return rightRank;
    }

    @Override
    public JArray monadCell(JArray y) {
        return Rank.applyMonad(operand, y);
    }

    @Override
    public JArray dyadCell(JArray x, JArray y) {
        return Rank.applyDyad(operand, x, y);
    }

    private static int[] toRanks(JArray spec) {
        if (spec.rank() > 1) {
            throw new JError("rank error");
        }
        int[] values = new int[spec.size()];
        for (int atom = 0; atom < values.length; atom++) {
            if (spec.type() == JType.INT) {
                long rank = spec.longAt(atom);
                if (rank < 0) {
                    throw new JError("rank error");
                }
                values[atom] = rank > Integer.MAX_VALUE ? WHOLE : (int) rank;
            } else if (spec.type() == JType.FLOAT
                    && spec.doubleAt(atom) == Double.POSITIVE_INFINITY) {
                values[atom] = WHOLE;
            } else {
                throw new JError("rank error");
            }
        }

        if (values.length == 0) {
            throw new JError("rank error");
        }
        if (values.length == 1) {
            return new int[]{values[0], values[0], values[0]};
        }
        if (values.length == 2) {
            return new int[]{values[0], values[1], values[1]};
        }
        if (values.length == 3) {
            return new int[]{values[0], values[1], values[2]};
        }
        throw new JError("rank error");
    }
}
