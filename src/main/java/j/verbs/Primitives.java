package j.verbs;

import java.util.Map;

/**
 * The primitive verbs implemented so far.
 *
 * <p>Milestone 4 registers arithmetic ({@code + - * % ^}) and comparisons
 * ({@code = < > <: >: ~:}). Structural and derived verbs arrive later.
 */
public final class Primitives {
    private static final Map<String, Verb> VERBS = Map.ofEntries(
            Map.entry("+", new Plus()),
            Map.entry("-", new Minus()),
            Map.entry("*", new Times()),
            Map.entry("%", new Divide()),
            Map.entry("^", new Power()),
            Map.entry("=", new Equal()),
            Map.entry("<", new Less()),
            Map.entry(">", new Greater()),
            Map.entry("<:", new LessEqual()),
            Map.entry(">:", new GreaterEqual()),
            Map.entry("~:", new NotEqual()));

    private Primitives() {
    }

    /** The verb for a primitive spelling, or null if it is not implemented yet. */
    public static Verb lookup(String spelling) {
        return VERBS.get(spelling);
    }
}
