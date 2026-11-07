package j.verbs;

import java.util.Map;

/**
 * The primitive verbs implemented so far.
 *
 * <p>Milestones 4–5 register arithmetic ({@code + - * % ^}), comparisons
 * ({@code = < > <: >: ~:}), and the initial structural verbs. Derived verbs and
 * the remaining structural meanings arrive later.
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
            Map.entry("~:", new NotEqual()),
            Map.entry("i.", new Iota()),
            Map.entry("$", new Shape()),
            Map.entry("#", new Tally()),
            Map.entry("|.", new Reverse()),
            Map.entry(",", new Ravel()),
            Map.entry("{", new From()),
            Map.entry("{.", new Take()),
            Map.entry("}.", new Drop()),
            Map.entry("|:", new Transpose()));

    private Primitives() {
    }

    /** The verb for a primitive spelling, or null if it is not implemented yet. */
    public static Verb lookup(String spelling) {
        return VERBS.get(spelling);
    }
}
