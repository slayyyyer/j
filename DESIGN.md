# Design

A from-scratch interpreter for a useful subset of J, in Java 21. The aim is a clean,
readable implementation that can be checked against a real J interpreter, not full J
compatibility. No runtime dependencies; JUnit 5 for tests.

## 1. Data model (`j.array`)

`JArray` is the only noun type. A scalar is not special: it is simply rank 0.

- `JType type`: `INT` (`long`), `FLOAT` (`double`), `CHAR` (`char`).
- `int[] shape`: dimensions, row-major. `[]` is a scalar, `[n]` a vector, `[r, c]` a matrix.
- flat `Object data`: `long[]`, `double[]`, or `char[]`, length = product(shape).
- Invariants: dimensions are non-negative, the atom count equals the shape product,
  and the array is immutable. Factories copy caller arrays; `shape()` returns a copy.
- Access is by atom (`longAt`, `doubleAt`, `charAt`) with a `numericAt` widening helper.
  `toFloat()` promotes an `INT` array to `FLOAT`; `CHAR` cannot be promoted.

Boxing is deliberately deferred. Adding it means a `BOX` type whose storage is
`JArray[]`; rank, shape and assembly code stay unchanged, while display and boxing
verbs gain cases.

`Shapes` holds shape arithmetic (`size`, row-major `strides`, formatting) so that
`JArray` stays about storage and `JFormat` about presentation. `JArray` also owns
the leading-axis operations (`item`, `reshape`, `ravel`, reverse/rotate,
take/drop, append and transpose) used by the structural verbs.

### Display

`JFormat` renders J's display notation:

- negative numbers use `_` (e.g. `_3`, `_0.5`); infinities are `_`/`__`; NaN is `_.`;
- floats use J's default print precision of **5 significant digits**, trimmed of
  trailing zeros, switching to power notation at exponent >= 6 or < -4
  (`0.33333`, `1e6`, `1e_5`);
- a numeric matrix is rows with per-column right alignment; planes of rank 3+ arrays
  are separated by a blank line;
- character arrays are printed as text, without quotes;
- assignments do not print, but that is the REPL's job, not the formatter's.

The exact edges of J's float formatting (tie rounding, exponent thresholds, and the
leading space jconsole may add) are noted as **UNCERTAIN** in the README and will be
compared with a real interpreter.

## 2. Lexer (`j.lex`)

`Lexer` turns a line into tokens. Token kinds: `NUMBER`, `STRING`, `NAME`,
`PRIMITIVE`, `LPAREN`, `RPAREN`, `ASSIGN_GLOBAL` (`=:`), `ASSIGN_LOCAL` (`=.`),
`LBRACE2`/`RBRACE2` (`{{`/`}}`, for direct definition), and `EOF`.

Each token records its exact spelling, a 1-based column, and a parsed `JArray` value
for `NUMBER` and `STRING` tokens. The lexer throws `j.JError` with `spelling error`
(and the offending column) for malformed literals, unterminated strings, unsupported
numeric suffixes, or unknown characters. An integer literal outside the `long` range
throws `limit error`. Numeric literals must be separated from adjacent words by
whitespace or punctuation: `3x` is not three followed by a name.

- Primitive words are matched longest-first from an explicit table: `=:` `=.` `{.`
  `}.` `|.` `|:` `i.` `~:` `<:` `>:` `@:` `@` `&` `.` `:` `"` `/` `\` `~` ... plus
  the one-character arithmetic and structural primitives.
- `/` and `\` are adverbs, not part of a verb name: `+/` lexes as `+`, `/`.
  Likewise `+/ .*` lexes as `+`, `/`, `.`, `*`, which is how J's inner product is
  spelled.
- Numbers: `3`, `_3`, `3.5`, `_3.5`, `1e6`, `1e_5`, `_` (infinity), `__` (negative
  infinity), `_.` (NaN). A numeric token without `.` or an exponent is an `INT`.
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

### Syntax handling

1. Parentheses are parsed recursively; the inner phrase is classified as a noun
   phrase or a verb phrase by its result.
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
   a noun or a verb, and chained assignment is right-associative. Assignments are
   recognized at sentence level; both copulas currently share the session-global
   name table.
6. `{{ ... }}` is parsed inline as an explicit verb. `name =: 3 : 0` is recognized
   by the REPL, which collects body lines until `)`. Each line is parsed as one
   sentence with `x` and `y` forced to nouns; the same expression parser handles
   both forms.

### AST

```java
sealed interface Expr permits Expr.Lit, Expr.Ref, Expr.Strand, Expr.Monad, Expr.Dyad {
    record Lit(JArray value) implements Expr {}
    record Ref(String name) implements Expr {}
    record Strand(List<Expr> parts) implements Expr {}       // juxtaposed nouns
    record Monad(VerbExpr verb, Expr y) implements Expr {}
    record Dyad(Expr x, VerbExpr verb, Expr y) implements Expr {}
}

sealed interface VerbExpr permits VerbExpr.Lit, VerbExpr.Ref, VerbExpr.WithAdverb,
        VerbExpr.WithRank, VerbExpr.Train, VerbExpr.Explicit {
    record Lit(Verb verb) implements VerbExpr {}
    record Ref(String name) implements VerbExpr {}
    record WithAdverb(VerbExpr operand, Adverb adverb) implements VerbExpr {}
    record WithRank(VerbExpr operand, Expr ranks) implements VerbExpr {}
    record Train(List<VerbExpr> verbs) implements VerbExpr {}
    record Explicit(List<Expr> body) implements VerbExpr {}
}

sealed interface Phrase permits Phrase.Noun, Phrase.Verb, Phrase.Assign {
    record Noun(Expr expr) implements Phrase {}
    record Verb(VerbExpr verb) implements Phrase {}
    record Assign(String name, boolean global, Phrase value) implements Phrase {}
}
```

`Expr` denotes phrases that produce a noun (applications always produce nouns);
`VerbExpr` denotes a phrase that produces a verb; `Phrase` wraps either result at
the top level or an assignment. Derived verbs, trains, assignments and explicit
definitions are represented directly rather than evaluated during parsing.

Examples:

| source               | AST shape                                             |
|----------------------|-------------------------------------------------------|
| `2 * 3 + 4`          | `Dyad(2, *, Dyad(3, +, 4))`                           |
| `- 2 + 3`            | `Monad(-, Dyad(2, +, 3))`                             |
| `(+/ % #) 1 2 3 4`   | `Monad(Train(+/ , %, #), Strand(1 2 3 4))`            |
| `+/ i. 3 4`          | `Monad(+/ , Monad(i., Strand(3, 4)))`                 |
| `+/"1 i. 3 4`        | `Monad(WithRank(+/ , 1), Monad(i., 3 4))`             |
| `avg =: +/ % #`      | `Assign(avg, Verb(Train(+/ , %, #)))`                 |

### Trains

A train is a verb phrase containing only verbs. 2 verbs form a hook, 3 a fork:

```
(f g) y       = y f (g y)          x (f g) y     = x f (g y)
(f g h) y     = (f y) g (h y)      x (f g h) y   = (x f y) g (x h y)
```

Longer trains are grouped from the right: an odd-length train is a fork of its
first two verbs and the remaining train, and an even-length train is a hook of its
first verb and the remaining train. **UNCERTAIN**: the exact 4+-train grouping needs
checking against J.

Note that without parentheses, `- - 5` is two monads (`5`), not a hook: a train is a
verb phrase with no noun to apply to, whereas `- - 5` has an argument. This follows
from reducing applications from the right before treating leftover verbs as a train.

## 4. Verbs (`j.verbs`)

Every verb provides a monadic and dyadic definition and a rank triple:

```java
public abstract class Verb {
    public abstract int monadRank();   // Verb.WHOLE = unbounded
    public abstract int leftRank();
    public abstract int rightRank();
    public JArray monadCell(JArray y) { throw new JError("valence error"); }
    public JArray dyadCell(JArray x, JArray y) { throw new JError("valence error"); }
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
to arithmetic throw `domain error`. Integer overflow in the current 64-bit atom
verbs throws `limit error` rather than promoting to J's extended integers.

Arithmetic and comparison verbs are rank `0 0 0` and share the `AtomVerb` base.
`+ - * % ^` promote to float when needed, `%` is always floating point, and
integer overflow reports `limit error`. Comparisons return integer 0/1 atoms and
accept numeric atoms (int/float mixed) or character atoms, but not a
character/numeric mix. Monadic `<:` and `>:` are decrement and increment. The
other monadic comparison meanings are deferred: `<` box, `>` open, `=`
self-classify, and `~:` nub sieve raise `valence error`.

Structural verbs use explicit ranks (mostly whole-argument; `$` has a rank-1 left
operand and `|.`/take/drop a rank-0 left operand). They work on the leading axis;
fill behavior and multi-axis forms are documented limitations. Derived verbs use
whole-argument ranks and delegate to the normal rank wrapper, which lets a rank
conjunction cut cells before the derived verb runs. A verb value is displayed by
its `repr` (e.g. entering `+` prints `+`).

## 5. Evaluator (`j.eval`)

- `Evaluator` resolves `Ref` and `VerbRef` through an `Environment`, evaluates
  `Monad`/`Dyad` by calling the rank wrapper, and stores assignment results.
- A sentence's value is a noun or a verb; the REPL prints nouns with `JFormat` and
  verbs with `repr()`. Assignment sentences are marked "no display".
- Rank application for a monad:
  1. `r = min(verb.monadRank(), rank(y))` (unbounded means the whole argument);
  2. frame shape = leading `rank(y) - r` axes; cell shape = trailing `r` axes;
  3. apply the cell verb to every cell, then assemble results as
     `frameShape + resultCellShape`; all cells must produce the same shape.
- Dyad: compute both frames/cells; frames must have equal shape, or one frame must
  be empty (a scalar) and is broadcast. Unequal frames -> `length error`.
- Derived verbs are built during evaluation: `WithAdverb` becomes Insert, Prefix or
  Reflex; `WithRank` becomes RankVerb; `Train` becomes TrainVerb; `Explicit`
  becomes ExplicitVerb.
- Strands stack their evaluated parts: all parts need the same shape, so
  `(1 2) (3 4)` becomes a 2x2 matrix. Int/float parts promote; char/numeric mixes
  are a `domain error` until boxing exists.
- An empty frame is handled with a prototype cell (zeros or NUL characters) so the
  cell verb can determine the result shape without real data.
- Errors are `j.JError` with J's wording: `domain error`, `length error`,
  `rank error`, `index error`, `value error`, `valence error`, `syntax error`,
  `spelling error`, `limit error`. Lexical and syntax errors carry a 1-based column.
  The REPL catches them, prints the message, and keeps going.

Names are stored in `Environment`. An `Environment` may have a parent scope, which
is how `ExplicitVerb` binds local `x` and `y` while falling back to session globals.
Assignment stores either a noun or a verb, and the parser's `isVerb` classification
changes with it. `Phrase.Assign` evaluation is suppressed by `Session` so
definitions do not print. Explicit bodies are evaluated sentence by sentence in a
child environment, and the last result is returned. Top-level `=.` currently
behaves like `=:` because there is no enclosing explicit body yet.

## 6. Primitive ranks (to be confirmed against J)

Base arithmetic and comparisons are atom-wise (`0 0 0`). Structural verbs mostly
use whole-argument ranks, with these implemented exceptions: `$` has a rank-1 left
operand, `|.`/take/drop have a rank-0 left operand, `#` has a rank-1 left operand
for its deferred copy meaning, and `{` indexes leading-axis items. `"` overrides
any of these. Exact J ranks for the remaining structural meanings still need
checking.

## 7. REPL (`j.repl`)

`Session` owns the environment and turns a line into display text. `Repl` supplies
J's three-space prompt and prints `|error message` for a `JError`, then continues;
end-of-input exits. Empty and comment-only lines produce no output. Assignment
sentences are evaluated but not printed. `Session.isExplicitDefinitionStart`
recognizes `name =: 3 : 0`/`=.` and `Repl` collects body lines through `)`;
`Session.defineExplicit` parses each line and stores the resulting `ExplicitVerb`.
Inline `{{ ... }}` definitions need no special REPL handling.

## 8. Testing and milestones

JUnit 5 tests live beside each package; display is tested as exact strings. An
acceptance suite mirrors the examples in the project brief. Work proceeds milestone
by milestone, running the suite and committing after each:

All nine milestones are implemented for the documented subset. `AcceptanceTest`
covers the brief's examples; the remaining limitations are listed in the README.

1. `JArray` + display + tests;
2. tokenizer + tests;
3. parser/evaluator for nouns and simple verbs + REPL;
4. arithmetic/comparison with scalar extension;
5. structural verbs (`i.`, `$`, `,`, `#`, `|.`, `{`, `{.`, `}.`);
6. adverbs `/ \ ~`;
7. rank conjunction `"`;
8. trains;
9. assignment and user-defined verbs.

## 9. Known uncertainties (to check against J)

- exact float display rounding and exponent cutovers;
- rank >= 3 display grouping and blank lines;
- whether J's matrix display pads the first column;
- 4+-verb train grouping;
- rank conjunction two-element specifications (currently monad rank plus both dyad
  ranks) and negative ranks (not supported);
- insert identities on empty arrays (currently `domain error`);
- take beyond the leading axis (currently `length error`, not J's fill);
- ranks and monadic meanings of comparison/structural primitives;
- signum's result type for float arguments and its NaN handling;
- NaN comparison (currently IEEE: all false except `~:`) and mixed
  character/numeric comparison (currently `domain error`);
- integer power overflow and complex results (currently `limit error` or NaN);
- stacking behavior for strands of non-scalar nouns;
- whether monadic `+` accepts character arrays (currently a `domain error`);
- explicit-body scoping and line grouping (top-level `=.` is global, and local
  assignments/control words are not implemented);
- assignments inside parentheses (currently `syntax error`);
- inner product `+/ .*`, `@`, `&` and related conjunctions (not implemented).

These are called out where implemented rather than silently guessed.
