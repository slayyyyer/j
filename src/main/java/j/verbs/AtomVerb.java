package j.verbs;

/** Base class for verbs whose cell function works on atoms (rank 0). */
public abstract class AtomVerb extends Verb {
    protected AtomVerb(String repr) {
        super(repr);
    }

    @Override
    public final int monadRank() {
        return 0;
    }

    @Override
    public final int leftRank() {
        return 0;
    }

    @Override
    public final int rightRank() {
        return 0;
    }
}
