package j.verbs;

import java.util.List;

import j.array.JArray;
import j.eval.Rank;

/** A hook or fork built from two or more verbs. */
public final class TrainVerb extends Verb {
    private final List<Verb> parts;

    public TrainVerb(List<Verb> verbs) {
        super(repr(verbs));
        if (verbs.size() < 2) {
            throw new IllegalArgumentException("a train needs at least two verbs");
        }
        this.parts = group(List.copyOf(verbs));
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
        if (parts.size() == 2) {
            JArray right = Rank.applyMonad(parts.get(1), y);
            return Rank.applyDyad(parts.get(0), y, right);
        }
        JArray left = Rank.applyMonad(parts.get(0), y);
        JArray right = Rank.applyMonad(parts.get(2), y);
        return Rank.applyDyad(parts.get(1), left, right);
    }

    @Override
    public JArray dyadCell(JArray x, JArray y) {
        if (parts.size() == 2) {
            JArray right = Rank.applyMonad(parts.get(1), y);
            return Rank.applyDyad(parts.get(0), x, right);
        }
        JArray left = Rank.applyDyad(parts.get(0), x, y);
        JArray right = Rank.applyDyad(parts.get(2), x, y);
        return Rank.applyDyad(parts.get(1), left, right);
    }

    private static List<Verb> group(List<Verb> verbs) {
        if (verbs.size() <= 3) {
            return verbs;
        }
        if (verbs.size() % 2 == 0) {
            return List.of(verbs.get(0), new TrainVerb(verbs.subList(1, verbs.size())));
        }
        return List.of(
                verbs.get(0),
                verbs.get(1),
                new TrainVerb(verbs.subList(2, verbs.size())));
    }

    private static String repr(List<Verb> verbs) {
        return "(" + String.join(" ", verbs.stream().map(Verb::repr).toList()) + ")";
    }
}
