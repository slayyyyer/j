package j.verbs;

import java.util.Map;

/**
 * The primitive verbs implemented so far.
 *
 * <p>Milestone 3 registers only {@code +} and {@code -}; the remaining arithmetic,
 * comparison and structural verbs arrive with later milestones.
 */
public final class Primitives {
    private static final Map<String, Verb> VERBS = Map.of(
            "+", new Plus(),
            "-", new Minus());

    private Primitives() {
    }

    /** The verb for a primitive spelling, or null if it is not implemented yet. */
    public static Verb lookup(String spelling) {
        return VERBS.get(spelling);
    }
}
