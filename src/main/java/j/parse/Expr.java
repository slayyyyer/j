package j.parse;

import java.util.List;

import j.array.JArray;

/**
 * A phrase that produces a noun.
 *
 * <p>Applications are right-associative, which makes J's right-to-left evaluation
 * explicit in the tree: {@code 2 + 3 + 4} is a dyad whose right argument is another
 * dyad.
 */
public sealed interface Expr permits Expr.Lit, Expr.Ref, Expr.Strand, Expr.Monad, Expr.Dyad,
        Expr.Assign {
    /** A literal noun. */
    record Lit(JArray value) implements Expr {
    }

    /** A name that holds a noun. */
    record Ref(String name) implements Expr {
    }

    /** Juxtaposed nouns, e.g. {@code 1 2 3}, assembled into one array. */
    record Strand(List<Expr> parts) implements Expr {
        public Strand {
            parts = List.copyOf(parts);
        }
    }

    /** Monadic application: {@code verb y}. */
    record Monad(VerbExpr verb, Expr y) implements Expr {
    }

    /** Dyadic application: {@code x verb y}. */
    record Dyad(Expr x, VerbExpr verb, Expr y) implements Expr {
    }

    /** An assignment used as a parenthesized noun expression. */
    record Assign(String name, boolean global, Phrase value) implements Expr {
    }
}
