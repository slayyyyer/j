# J subset interpreter

A from-scratch interpreter for a practical subset of the
[J programming language](https://www.jsoftware.com/), written in Java 21.
The goal is a small, readable codebase for learning how J's array model,
right-to-left evaluation, adverbs, conjunctions, trains and explicit
definitions fit together. It does not wrap an existing implementation.

## Status

The interpreter covers the documented practical subset: arrays and display,
tokenization, parsing, evaluation, exact and complex numeric types, boxing,
sparse arrays, structural verbs, adverbs `/ \ ~`, the rank conjunction `"`, trains,
assignment, explicit verbs with control words, catalog, generalized transpose and
multi-axis rotate.

This is still a teaching subset, not a full J replacement. Locales, system
facilities, the remaining conjunctions and a few exact display details are outside
the current scope; see [Limitations](#limitations).

| Milestone | Description | Status |
|-----------|-------------|--------|
| 1 | `JArray` + J display format + tests | done |
| 2 | Tokenizer (`_` negatives, suffixes, `NB.` comments, strings) | done |
| 3 | Parser/evaluator for nouns and verbs, REPL | done |
| 4 | Arithmetic and comparison with scalar extension | done |
| 5 | Structural verbs, copy fills, catalog, transpose, rotate | done |
| 6 | Adverbs `/ \ ~` and insert identities | done |
| 7 | Rank conjunction `"` | done |
| 8 | Trains (hooks and forks) | done |
| 9 | Assignment, boxing, exact types and explicit verbs | done |

## Requirements and build

- JDK 21
- Gradle (or use the included wrapper)

```sh
./gradlew test          # run the test suite
./gradlew build         # compile and test
./gradlew run           # start the REPL
```

End-of-input (Ctrl-D) exits the REPL.

## Quick start

```
   2 * 3 + 4
14
   i. 5
0 1 2 3 4
   +/ i. 5
10
   +/ i. 3 4
12 15 18 21
   +/\"1 i. 3 4
6 22 38
   (+/ % #) 1 2 3 4
2.5
   avg =: +/ % #
   avg 1 2 3 4
2.5
   inc =: {{ y + 1 }}
   inc 2
3
   f =: 3 : 0
   if. y > 0 do.
     y * 2
   else.
     y - 1
   end.
   )
   f 3
6
```

## What works now

### Arrays and display

`JArray` models a J noun as flat `Object[]` storage plus a shape. The supported
atom types are:

- `INT` (`long`), `BIGINT` (`BigInteger`), `RATIONAL`, `FLOAT` (`double`),
  `COMPLEX`, `CHAR` and `BOX` (a nested `JArray`).

```java
JArray m = JArray.ints(new int[]{2, 3}, 1, 2, 3, 4, 5, 6);
System.out.println(m);
```

```
1 2 3
4 5 6
```

- rank 0 scalars, vectors, matrices and higher ranks;
- empty arrays (a shape containing a zero);
- numeric promotion `INT -> BIGINT -> RATIONAL -> FLOAT -> COMPLEX`;
- character arrays remain separate from numeric arrays;
- dense and sparse storage, including boxed sparse arrays; sparse arrays retain
  only non-fill atoms plus a fill atom;
- boxed arrays, including nested boxes, drawn as bordered cells;
- J display: `_` negatives, `_`/`__` infinities, `_.` NaN, aligned matrix
  columns, blank lines between rank 3+ planes, and character arrays shown as text.

### Tokenizer

`j.lex` handles integer and float literals (`_3`, `_3.5`, `1e_5`, `_`, `__`, `_.`),
names, quoted strings with doubled-quote escapes, `NB.` comments, parentheses,
`=:`/`=.` and `{{`/`}}` delimiters. Numeric suffixes select the wider types:

- `123x` — extended integer;
- `1r2` — rational;
- `3j4` — complex.

An integer literal outside the `long` range promotes to `BIGINT` instead of
reporting a limit error. Primitives are matched longest-first, and adverbs stay
separate (`+/` is the verb `+` and the adverb `/`). Tokens record 1-based
columns; malformed input reports `spelling error`.

### Parser and evaluator

J is evaluated strictly right to left with no operator precedence:

```
   2 + 3
5
   1 2 3 + 10
11 12 13
   - 2 + 3
_5
   10 - 3 - 2
9
```

- numeric and character literals, and strands such as `1 2 3`;
- parentheses, including assignments used as noun expressions:
  `(a =. 3) + 2` returns `5` and binds `a`;
- monadic and dyadic application, selected by whether a noun is on the left;
- verb values, displayed by their spelling (`+`, `+/`, `+/"1`);
- rank-aware application of atom verbs, including scalar extension;
- J-style errors without crashing the REPL: `syntax error`, `value error`,
  `domain error`, `length error`, `rank error`, `index error`, `valence error`,
  `limit error`.

### Arithmetic and comparisons

- `+` identity/addition, `-` negation/subtraction, `*` signum/multiplication,
  `%` reciprocal/division, `^` exponential/power;
- integer, extended, rational, float and complex arithmetic with promotion;
- `= ~: < > <: >:` return integer 0/1 atoms;
- numeric comparisons work across numeric types; character comparisons are
  atom-wise; boxes can be compared for equality;
- `<:` and `>:` also decrement and increment monadically;
- `=` self-classifies items monadically, and `~:` produces the nub sieve;
- monadic `<` boxes and monadic `>` opens one level;
- `%` and monadic `^` produce floating-point results for real arguments;
  division by zero follows IEEE (`1 % 0` is `_`, `0 % 0` is `_.`);
- integer arithmetic beyond `long` promotes to extended integers instead of
  reporting a limit error.

### Structural verbs

Unless noted, these operate on the leading axis:

- `i.` — integers monadically, including shape-vector forms such as `i. 3 4`,
  and index-of dyadically;
- `$` — shape-of monadically and reshape dyadically;
- `#` — tally monadically and copy dyadically;
- `,` — ravel monadically and catenate dyadically;
- `|.` — reverse monadically and rotate dyadically; a vector left argument
  rotates successive axes;
- `{` — catalog monadically and index dyadically (negative indices work);
- `{.` — head monadically and take dyadically, including overtake fill;
- `}.` — behead monadically and drop dyadically;
- `|:` — transpose monadically and generalized transpose dyadically with an axis
  list;
- `$.` — convert a dense array to sparse storage monadically; `0 $. y` toggles
  between sparse and dense; `$.^:_1` densifies a sparse array.

`i.` matches items and returns `#x` for a missing item. `#` supports a scalar
count, per-item count vectors, and J complex counts `a j. b`, which copy an item
`a` times and then insert `b` fills. Negative counts are a `domain error`, as in J.
A vector left argument to `{.` or `}.` takes or drops along successive axes.

Monadic `{` on a boxed list forms the Cartesian product of the box contents. For
example, `{ (<1 2),(<3 4)` produces:

```
1 3
1 4
2 3
2 4
```

A sparse array displays its flat index coordinates, a vertical bar, and the
stored values. For example, `$. 3 4 $ 0 75 0 53 0 0 67 67 93 0 51 83` displays:

```
0 1 | 75
0 3 | 53
1 2 | 67
1 3 | 67
2 0 | 93
2 2 | 51
2 3 | 83
```

`0 $. s` returns the dense matrix again. Sparse arrays support the same shape,
tally, display, and atom-wise arithmetic operations as dense arrays. Structural
verbs remap stored entries directly, so reversing, transposing, taking, dropping,
copying, reshaping, appending, and raveling sparse arrays keep sparse storage.
Reductions and prefixes (`u/` and `u\`) also fold and assemble sparse items without
densifying; even scalar reductions retain sparse storage. Sparse storage supports
boxed atoms, using an empty box as its fill, and `$.^:_1 y` is the inverse spelling
that returns a dense array.

### Adverbs, rank and trains

- `/` — insert: `+/ i. 5` → `10`, `+/ i. 3 4` → `12 15 18 21`;
- `\` — prefix: `+/\ 1 2 3` → `1 3 6`;
- `~` — reflex monadically and passive dyadically: `+~ 1 2 3` → `2 4 6`;
- `"` — rank, including negative ranks and one-, two- and three-number specs:
  `+/"1 i. 3 4` → `6 22 38`;
- hooks and forks, so `(+/ % #) 1 2 3 4` → `2.5`.

Insert identities follow the implemented primitive verbs:

- `0` for `<`, `>`, `+`, `-` and `~:`;
- `1` for `=`, `<:`, `>:`, `*`, `%` and `^`.

Thus `-/ i. 0` is `0`, `%/ i. 0` is `1`, `=/ i. 0` is `1`, and so on.

### Assignment and user-defined verbs

- `name =: value` and `name =. value` assign nouns or verbs; assignment produces no
  output, and the name's noun/verb class is visible to later parsing.
- Assignments chain right-associatively: `b =: a =: 7`.
- At top level, `=.` and `=:` share the session table; inside an explicit verb,
  `=.` binds a local and `=:` writes the session root.
- `{{ ... }}` defines a verb inline from one expression, with local `x` and `y`:
  `times =: {{ x * y }}`.
- In the REPL, `name =: {{` reads body lines until `}}`, and
  `name =: 3 : 0` reads body lines until `)`. Both forms use the same statement
  parser, so control words and local assignments work in either.
- Explicit bodies support `if. condition do. ... else. ... end.`,
  `while. condition do. ... end.`, `for_i. values do. ... end.` and
  `return. value`.
- `f =: +/` stores a derived verb, and `avg =: +/ % #` stores a train.

## Limitations

This is intentionally a subset; the following are not implemented or are
deliberately conservative:

- Locales, system facilities and file/foreign conjunctions.
- The remaining conjunctions: inner product `+/ .*`, `@`, `&`, bond and related
  forms.
- `#!.f` fit (copy with a specified fill) is not implemented; complex copy counts
  use the type's default fill.
- Only the control words `if.`, `else.`, `while.`, `for.` and `return.` are
  implemented. `select.`, `try.`, `throw.` and the other J control words are not.
- Direct definitions are multi-line when started with `{{`; an inline
  `{{ ... }}` contains one expression.
- Insert identities exist only for the implemented primitive verbs listed above.
- Integer power with a huge negative or non-integer exponent still uses floating
  point; exact complex powers are approximate.
- Float display uses five significant digits but tie rounding and exponent
  cutovers are approximate.
- The exact generalized dyadic-transpose and multi-axis-rotate rank conventions
  have not been exhaustively checked against every J release.

## Project layout

```
src/main/java/j         JError (shared language error)
src/main/java/j/array   JArray, JType, Rational, Complex, Sparse, Shapes, JFormat
src/main/java/j/lex     Lexer, Token, TokenType
src/main/java/j/parse   Expr, VerbExpr, Phrase, Statement, StatementParser,
                        Adverb, Parser, NameClassifier
src/main/java/j/eval    Evaluator, Environment, Value, Rank, Assembly
src/main/java/j/verbs   primitive, structural, derived and explicit verbs
src/main/java/j/repl    Session, Repl
src/test/java/...       JUnit 5 tests, including AcceptanceTest
```

## Tests

The suite covers the array model, display, lexer, parser, evaluator, arithmetic,
comparisons, structural verbs, boxes, exact and complex types, sparse conversion,
display, boxed sparse conversion, inverse sparse spelling, arithmetic and structural remapping, insert reductions, prefix scans, copy fills, catalog, transpose, rotate, derived verbs, insert
identities, assignment, explicit control flow and the REPL. `AcceptanceTest`
checks every example from the project brief.

```sh
./gradlew test
```

## Project-brief acceptance examples

All of these are covered by `AcceptanceTest`:

```
2 * 3 + 4          NB. 14
i. 5               NB. 0 1 2 3 4
+/ i. 5            NB. 10
i. 3 4             NB. 3x4 matrix of 0..11
+/"1 i. 3 4        NB. 6 22 38
+/ i. 3 4          NB. 12 15 18 21
(+/ % #) 1 2 3 4   NB. 2.5
|. 1 2 3           NB. 3 2 1
2 3 $ 1 2 3 4 5 6  NB. 2x3 matrix
1 2 3 + 10         NB. 11 12 13
1 2 3 + 1 2        NB. length error
```

## Display notes

J's default display uses 5 significant digits for floats. This implementation
rounds and trims in J's notation, but the precise tie-breaking and exponent
thresholds are approximate. Rank 3+ layout (one blank line between planes) and
the padding of matrix columns are implementation decisions. Sparse display uses
the flat index coordinates for every axis, followed by ` | ` and the stored value.
