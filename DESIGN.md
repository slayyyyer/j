# Design

A from-scratch interpreter for a useful subset of J, in Java 21. The aim is a clean,
readable implementation that can be checked against a real J interpreter, not full J
compatibility. No runtime dependencies; JUnit 5 for tests.

## 1. Data model (`j.array`)

`JArray` is the only noun type. A scalar is simply rank 0.

- `JType type`: `INT` (`long`), `BIGINT` (`BigInteger`), `RATIONAL`
  (`Rational`), `FLOAT` (`double`), `COMPLEX` (`Complex`), `CHAR` (`char`) or
  `BOX` (a nested `JArray`).
- `int[] shape`: dimensions, row-major. `[]` is a scalar, `[n]` a vector,
  `[r, c]` a matrix.
- flat `Object[] data` for dense arrays, length = product(shape). Boxed storage
  keeps numeric promotion and heterogeneous boxes in one representation.
- a nullable `Sparse sparse` for sparse arrays. `Sparse` stores a fill atom plus
  strictly increasing flat indices and their values; `atomAt` returns the fill for
  absent positions. Sparse arrays have the same type and shape rules as dense ones.
- Invariants: dimensions are non-negative, the atom count equals the shape
  product, and the array is immutable. Factories copy caller arrays; `shape()`
  returns a copy.

Numeric promotion is `INT < BIGINT < RATIONAL < FLOAT < COMPLEX`. Characters and
boxes combine only with the same type. An integer literal outside the `long`
range becomes `BIGINT`; arithmetic that overflows a `long` also promotes rather
than reporting a limit error. `JArray.toSparse` filters the default fill atom
(zero, space, or empty box) into `Sparse`; `dense()` reconstructs a dense array.
Sparse scalars stay dense, matching J's representation of scalars.

`Rational` stores a normalized numerator/denominator and supports exact
arithmetic. `Complex` stores double real and imaginary parts. `JArray` exposes
typed accessors (`longAt`, `bigAt`, `rationalAt`, `doubleAt`, `complexAt`,
`charAt`, `boxAt`, `atomAt`) plus generic `convertAtom` and `promote` helpers.

`Shapes` holds shape arithmetic (`size`, row-major `strides`, formatting) so that
`JArray` stays about storage and `JFormat` about presentation. `JArray` also owns
the leading-axis operations (`item`, `reshape`, `ravel`, reverse/rotate,
take/drop, append, copy and transpose) used by the structural verbs.

### Display

`JFormat` renders J's display notation:

- negative numbers use `_` (e.g. `_3`, `_0.5`); infinities are `_`/`__`; NaN is `_.`;
- floats use J's default print precision of **5 significant digits**, trimmed of
  trailing zeros, switching to power notation at exponent >= 6 or < -4
  (`0.33333`, `1e6`, `1e_5`);
- exact extended integers and rationals print as `123` and `1r2`;
- complex numbers print as `3j4`;
- a numeric matrix is rows with per-column right alignment; planes of rank 3+ arrays
  are separated by a blank line;
- character arrays are printed as text, without quotes;
- sparse arrays display their flat index coordinates, ` | `, and stored values;
- boxed arrays are drawn as bordered cells, recursively for nested boxes;
- assignments do not print, but that is the REPL's job, not the formatter's.

The exact edges of J's float formatting (tie rounding, exponent thresholds, and the
leading space jconsole may add) are approximate.

## 2. Lexer (`j.lex`)

`Lexer` turns a line into tokens. Token kinds: `NUMBER`, `STRING`, `NAME`,
`PRIMITIVE`, `LPAREN`, `RPAREN`, `ASSIGN_GLOBAL` (`=:`), `ASSIGN_LOCAL` (`=.`),
`LBRACE2`/`RBRACE2` (`{{`/`}}`, for direct definition), and `EOF`.

Each token records its exact spelling, a 1-based column, and a parsed `JArray` value
for `NUMBER` and `STRING` tokens. The lexer throws `j.JError` with `spelling error`
(and the offending column) for malformed literals, unterminated strings, unsupported
numeric suffixes, or unknown characters. Numeric literals must be separated from
adjacent words by whitespace or punctuation: `1_2` is not a valid negative form.

- Primitive words are matched longest-first from an explicit table: `=:` `=.` `{.`
  `}.` `|.` `|:` `i.` `~:` `<:` `>:` `@:` `@` `&` `$.` `.` `:` `"` `/` `\` `~` ...
  plus the one-character arithmetic and structural primitives.
- `/` and `\` are adverbs, not part of a verb name: `+/` lexes as `+`, `/`.
- Numbers: `3`, `_3`, `3.5`, `_3.5`, `1e6`, `1e_5`, `_` (infinity), `__` (negative
  infinity), `_.` (NaN).
- Suffixes choose exact and complex types: `123x` is `BIGINT`, `1r2` is
  `RATIONAL`, and `3j4` is `COMPLEX`.
- A plain integer outside the `long` range promotes to `BIGINT`.
- Strings: `'abc'` yields a rank-1 `CHAR` array; a doubled quote is a literal quote
  (`'don''t'`). Unterminated strings are a spelling error.
- `NB.` outside a string starts a comment running to end of line.
- Comments and whitespace are skipped; a line may produce no tokens.

Lexing is context-free. There is no `-`-as-negative ambiguity because J writes
negative literals with `_`; `-` is always the verb.

## 3. Parser and AST (`j.parse`)

J has no precedence; a sentence is evaluated right to left. The parser builds an AST
that makes that order explicit rather than evaluating while scanning.

Words are classified as noun, verb, adverb, conjunction, copula, or parenthesis.
A name's class comes from the name table (as in J): names assigned a verb are verbs,
names assigned a noun are nouns, and unbound names are treated as nouns and fail at
evaluation with `value error`. Redefinition changes the class for later sentences.
Inside explicit bodies, `x`, `y`, and `for.` variables are forced to nouns.

### Syntax handling

1. Parentheses are parsed recursively; the inner phrase is classified as a noun
   phrase or a verb phrase by its result. An assignment inside parentheses becomes
   an `Expr.Assign`, so `(a =. 3) + 2` both binds and returns `3`.
2. Adjacent nouns form one noun phrase (a strand), e.g. `3 4` or `1 2 3`.
3. Adverbs and the rank conjunction are applied while scanning, because they bind
   tighter than application: `+/"1`, `+/\`. Their operands are kept as AST nodes
   and evaluated when the derived verb value is built. `/`, `\` and `~` are
   adverbs; `"` takes a noun rank specification. The other conjunctions (`@`,
   `&`, inner product) are outside the implemented subset.
4. The remaining terms alternate between noun phrases and verbs. Reduce from the
   right:
   - `verb noun` -> `Monad(verb, noun)`;
   - `noun verb noun` -> `Dyad(noun, verb, noun)`;
   - a remaining phrase containing only verbs is a train (a verb value).
   This yields right-to-left evaluation with no precedence.
5. `name =: expr` and `name =. expr` become assignment nodes. The right side may be
   a noun or a verb, and chained assignment is right-associative.
6. `{{ ... }}` is parsed inline as an explicit verb. A multi-line `name =: {{`
   definition is collected by the REPL until `}}`, and `name =: 3 : 0` is collected
   until `)`. Both forms feed raw body lines to `StatementParser`.

### AST

```java
sealed interface Expr permits Expr.Lit, Expr.Ref, Expr.Strand, Expr.Monad, Expr.Dyad,
        Expr.Assign {
    record Lit(JArray value) implements Expr {}
    record Ref(String name) implements Expr {}
    record Strand(List<Expr> parts) implements Expr {}       // juxtaposed nouns
    record Monad(VerbExpr verb, Expr y) implements Expr {}
    record Dyad(Expr x, VerbExpr verb, Expr y) implements Expr {}
    record Assign(String name, boolean global, Phrase value) implements Expr {}
}

sealed interface VerbExpr permits VerbExpr.Lit, VerbExpr.Ref, VerbExpr.WithAdverb,
        VerbExpr.WithRank, VerbExpr.Train, VerbExpr.Explicit {
    record Lit(Verb verb) implements VerbExpr {}
    record Ref(String name) implements VerbExpr {}
    record WithAdverb(VerbExpr operand, Adverb adverb) implements VerbExpr {}
    record WithRank(VerbExpr operand, Expr ranks) implements VerbExpr {}
    record Train(List<VerbExpr> verbs) implements VerbExpr {}
    record Explicit(List<Statement> body) implements VerbExpr {}
}

sealed interface Phrase permits Phrase.Noun, Phrase.Verb, Phrase.Assign {
    record Noun(Expr expr) implements Phrase {}
    record Verb(VerbExpr verb) implements Phrase {}
    record Assign(String name, boolean global, Phrase value) implements Phrase {}
}

sealed interface Statement permits Statement.Sentence, Statement.If, Statement.While,
        Statement.For, Statement.Return {
    record Sentence(Phrase phrase) implements Statement {}
    record If(Expr condition, List<Statement> thenBody, List<Statement> elseBody)
            implements Statement {}
    record While(Expr condition, List<Statement> body) implements Statement {}
    record For(String variable, Expr values, List<Statement> body) implements Statement {}
    record Return(Expr value) implements Statement {}
}
```

`Expr` denotes phrases that produce a noun; `VerbExpr` denotes a phrase that
produces a verb; `Phrase` wraps either result at the top level or an assignment.
Derived verbs, trains, assignments and explicit definitions are represented
directly rather than evaluated during parsing.

Examples:

| source               | AST shape                                             |
|----------------------|-------------------------------------------------------|
| `2 * 3 + 4`          | `Dyad(2, *, Dyad(3, +, 4))`                           |
| `- 2 + 3`            | `Monad(-, Dyad(2, +, 3))`                             |
| `(+/ % #) 1 2 3 4`   | `Monad(Train(+/ , %, #), Strand(1 2 3 4))`            |
| `+/ i. 3 4`          | `Monad(+/ , Monad(i., Strand(3, 4)))`                 |
| `+/"1 i. 3 4`        | `Monad(WithRank(+/ , 1), Monad(i., 3 4))`             |
| `avg =: +/ % #`      | `Assign(avg, Verb(Train(+/ , %, #)))`                 |

### Statements and control flow

`StatementParser` splits an explicit body into statements. Control words are
recognized at the beginning of a trimmed line:

- `if. condition do. ... [else. ...] end.`
- `while. condition do. ... end.`
- `for_i. values do. ... end.` (the variable is `i`, or the name after `for_`)
- `return. value` and bare `return.`

Other lines are parsed as ordinary sentences. A `for.` variable and its
`<variable>_index` companion are treated as nouns while the body is parsed.
Nested control structures recurse through the same block parser. Blank lines and
`NB.` comments are skipped. The statement parser is deliberately smaller than J's
full control-word set.

### Trains

A train is a verb phrase containing only verbs. 2 verbs form a hook, 3 a fork:

```
(f g) y       = y f (g y)          x (f g) y     = x f (g y)
(f g h) y     = (f y) g (h y)      x (f g h) y   = (x f y) g (x h y)
```

Longer trains are grouped from the right: an odd-length train is a fork of its
first two verbs and the remaining train, and an even-length train is a hook of its
first verb and the remaining train. Without parentheses, `- - 5` is two monads
(`5`), not a hook, because the noun argument is present.

## 4. Verbs (`j.verbs`)

Every verb provides a monadic and dyadic definition and a rank triple:

```java
public abstract class Verb {
    public abstract int monadRank();   // Verb.WHOLE = unbounded
    public abstract int leftRank();
    public abstract int rightRank();
    public JArray monadCell(JArray y) { throw new JError("valence error"); }
    public JArray dyadCell(JArray x, JArray y) { throw new JError("valence error"); }
    public JArray identity(JType type) { throw new JError("domain error"); }
    public final String repr();        // J spelling, for display of verb values
}
```

Cell methods receive arguments already cut to the verb's ranks. The rank machinery
in `j.eval` maps over the frame, so base verbs contain only atom/cell logic. Scalar
extension is not special-cased in arithmetic: `+` has ranks `0 0 0`, and frame
agreement broadcasts a scalar frame.

Derived verbs implement the same interface and delegate to their operands:
`Insert` (`/`), `Prefix` (`\`), `Reflex` (`~`), `RankVerb` (`"`), `TrainVerb`
(hooks/forks) and `ExplicitVerb` (`{{ }}` / `3 : 0`). Adverbs, the rank
conjunction and trains are therefore just verb constructors. `Bond`, `Compose`,
inner product and the remaining conjunctions are outside the subset.

A verb with no definition for a valence throws `valence error`. Character arguments
to numeric arithmetic throw `domain error`. Numeric arithmetic promotes to exact
or complex types as needed.

Arithmetic and comparison verbs are rank `0 0 0` and share the `AtomVerb` base.
`+ - * % ^` handle numeric promotion through `Numeric`; `%` returns floating point
for real arguments; comparisons return integer 0/1 atoms and accept numeric atoms
or characters, but not a character/numeric mix. Monadic `<:` and `>:` are decrement
and increment. Monadic `<` boxes; monadic `>` opens one level. `=` self-classify
returns the nub-by-items boolean table, and `~:` returns the nub sieve.

Structural verbs use explicit ranks. `i.` is monadic rank 1 and dyadic rank
`_ _`; its dyadic case matches cells whose rank is the rank of an item of `x`.
`#` is `_ 1 _` (tally/copy), take and drop have a rank-1 left operand and fill or
slice successive axes, and `$`/`|.`/`{` keep their own rank rules. Monadic `{`
catalog has rank 1 and requires a boxed list. `|:` uses generalized axis
permutation; dyadic `|.` with a vector left argument rotates successive axes.
`#` accepts exact integer counts and complex `a j. b` counts: `a` copies the item
and `b` appends default fills. Negative counts are a domain error, matching J.
`$.` is the sparse conversion verb: `$. y` returns sparse storage for a dense
array, and `0 $. y` toggles dense/sparse. Sparse-aware atom verbs preserve the fill
and stored entries, and structural verbs remap the stored indices directly. The
slice and assembly paths also preserve sparse cells, so rank-0 operations and
indexed selections do not densify merely to read a cell.

Insert asks its operand for a type-specific identity when the argument has no items.
The implemented identities are `0` for `< > + - ~:` and `1` for
`= <: >: * % ^`; other operands retain `domain error`. A verb value is displayed
by its `repr` (e.g. entering `+` prints `+`).

## 5. Evaluator (`j.eval`)

- `Evaluator` resolves `Ref` and `VerbRef` through an `Environment`, evaluates
  `Monad`/`Dyad` by calling the rank wrapper, and stores assignment results.
  `Expr.Assign` evaluates its right side, stores it in the local or root
  environment, and yields the assigned noun value.
- A sentence's value is a noun or a verb; the REPL prints nouns with `JFormat` and
  verbs with `repr()`. Assignment sentences are marked "no display".
- Rank application for a monad:
  1. resolve the verb rank against the argument; a negative rank `r` becomes
     `max(0, rank(y) + r)`, then cap at `rank(y)`;
  2. frame shape = leading `rank(y) - r` axes; cell shape = trailing `r` axes;
  3. apply the cell verb to every cell, then assemble results as
     `frameShape + resultCellShape`; all cells must produce the same shape.
  A two-number spec `n0 n1` sets monadic rank `n1`, left rank `n0` and right rank
  `n1`; a three-number spec is monadic/left/right.
- Dyad: compute both frames/cells; frames must have equal shape, or one frame must
  be empty (a scalar) and is broadcast. Unequal frames -> `length error`.
- Derived verbs are built during evaluation: `WithAdverb` becomes Insert, Prefix or
  Reflex; `WithRank` becomes RankVerb; `Train` becomes TrainVerb; `Explicit`
  becomes ExplicitVerb.
- Strands stack their evaluated parts: all parts need compatible shapes, and
  numeric parts promote; char/box mixes are a `domain error`.
- An empty frame is handled with a prototype cell (type fill) so the cell verb can
  determine the result shape without real data.
- Errors are `j.JError` with J's wording: `domain error`, `length error`,
  `rank error`, `index error`, `value error`, `valence error`, `syntax error`,
  `spelling error`, `limit error`. Lexical and syntax errors carry a 1-based column.
  The REPL catches them, prints the message, and keeps going.

Names are stored in `Environment`. An `Environment` may have a parent scope, which
is how `ExplicitVerb` binds local `x` and `y` while falling back to session globals.
Assignment stores either a noun or a verb, and the parser's `isVerb` classification
changes with it. `Phrase.Assign` evaluation is suppressed by `Session` so
definitions do not print. In an explicit body, `=.` assigns in the child scope and
`=:` writes the root scope; at top level both share the session table.

`ExplicitVerb` executes statements with a local environment:
`Statement.Sentence` evaluates normally; `If` evaluates its condition as a scalar
truth value and executes one branch; `While` repeats until the condition is false;
`For` binds the variable to each item and `<variable>_index` to its index; and
`Return` unwinds to the explicit verb with its noun result. The last statement's
noun is returned when no `return.` executes.

## 6. Primitive ranks

Base arithmetic and comparisons are atom-wise (`0 0 0`). Structural ranks are
`i.` = `1 _ _`, `#` = `_ 1 _`, take/drop = `_ 1 _`, `$` = `_ 1 _`, `|.` = `_ 0 _`,
`{` = `1 _ _` for catalog and `_ _ _` for indexing, and `|:` uses whole
arguments. `i.` and take/drop receive the whole right argument and do their own
cell/axis handling. `"` overrides any of these ranks. Exact J ranks for the
still-deferred structural meanings remain to be checked.

## 7. REPL (`j.repl`)

`Session` owns the environment and turns a line into display text. `Repl` supplies
J's three-space prompt and prints `|error message` for a `JError`, then continues;
end-of-input exits. Empty and comment-only lines produce no output. Assignment
sentences are evaluated but not printed.

`Session.isExplicitDefinitionStart` recognizes `name =: 3 : 0`/`=.` and `Repl`
collects body lines through `)`. `Session.isDirectDefinitionStart` recognizes
`name =: {{`/`=.` and collects until `}}`. Both definitions are parsed by
`StatementParser` and stored as `ExplicitVerb`s. Inline `{{ ... }}` definitions
remain single sentences and need no special REPL handling.

## 8. Testing and milestones

JUnit 5 tests live beside each package; display is tested as exact strings. An
acceptance suite mirrors the examples in the project brief. Work proceeds milestone
by milestone, running the suite and committing after each.

All nine milestones are implemented for the documented subset. `AcceptanceTest`
covers the brief's examples; the remaining limitations are listed in the README.

1. `JArray` + display + tests;
2. tokenizer + tests;
3. parser/evaluator for nouns and simple verbs + REPL;
4. arithmetic/comparison with scalar extension;
5. structural verbs (`i.`, `$`, `,`, `#`, `|.`, `{`, `{.`, `}.`, `|:`);
6. adverbs `/ \ ~` and insert identities;
7. rank conjunction `"`;
8. trains;
9. assignment, boxes, exact/complex types and explicit definitions.

## 9. Known uncertainties

- exact float display rounding and exponent cutovers;
- rank >= 3 display grouping and blank lines;
- whether J's matrix display pads the first column;
- exhaustive generalized dyadic-transpose and multi-axis-rotate conventions;
- insert identities for primitives not implemented here;
- exact ranks for deferred structural meanings;
- signum's result type for float arguments and its NaN handling;
- NaN comparison (currently IEEE: all false except `~:`) and mixed
  character/numeric comparison (currently `domain error`);
- integer power overflow and complex results;
- stacking behavior for strands of non-scalar nouns;
- whether monadic `+` accepts character arrays (currently a `domain error`);
- control words outside `if.`/`while.`/`for.`/`return.`;
- top-level `=.` sharing the session table with `=:`;
- `$.^:_1` is not implemented as an inverse spelling; use `0 $. y`.
- boxed sparse conversion is not implemented (numeric and character sparse arrays are).
- inner product `+/ .*`, `@`, `&` and related conjunctions (not implemented).
