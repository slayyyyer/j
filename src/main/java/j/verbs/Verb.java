package j.verbs;

import j.JError;
import j.array.JArray;

/**
 * A J verb.
 *
 * <p>Every verb is ambivalent: it has a monadic and a dyadic definition, selected
 * by the evaluator from the sentence structure. A verb declares its monadic, left
 * and right ranks; {@link j.eval.Rank} applies the cell function over the argument
 * frames. A valence the verb does not support throws {@code valence error}.
 */
public abstract class Verb {
    /** A rank meaning "the whole argument". */
    public static final int WHOLE = Integer.MAX_VALUE;

    private final String repr;

    protected Verb(String repr) {
        this.repr = repr;
    }

    /** The verb's J spelling, used when a verb value is displayed. */
    public final String repr() {
        return repr;
    }

    public abstract int monadRank();

    public abstract int leftRank();

    public abstract int rightRank();

    /** Applies the verb to one cell of a monadic argument. */
    public JArray monadCell(JArray y) {
        throw new JError("valence error");
    }

    /** Applies the verb to one pair of dyadic argument cells. */
    public JArray dyadCell(JArray x, JArray y) {
        throw new JError("valence error");
    }

    @Override
    public String toString() {
        return repr;
    }
}
