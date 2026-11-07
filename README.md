# J subset interpreter

A from-scratch interpreter for a practical subset of the
[J programming language](https://www.jsoftware.com/), written in Java 21.
The goal is a small, readable codebase for learning how J's array model,
right-to-left evaluation, adverbs, conjunctions and trains fit together. It does not
aim for full J compatibility and does not wrap an existing implementation.

## Status

**All nine planned milestones are implemented for the documented subset.** The
interpreter now covers arrays and display, tokenization, parsing, evaluation,
arithmetic and comparisons, core structural verbs, adverbs `/ \ ~`, the rank
conjunction `"`, trains, assignment, and explicit verbs.

This is a teaching subset, not a full J replacement. Boxing, complex numbers,
extended integers, the remaining structural dyads and several advanced conjunctions
are outside the current scope; see [Limitations](#limitations).

| Milestone | Description | Status |
|-----------|-------------|--------|
| 1 | `JArray` + J display format + tests | done |
| 2 | Tokenizer (`_` negatives, `NB.` comments, strings) | done |
| 3 | Parser/evaluator for nouns and verbs, REPL | done |
| 4 | Arithmetic and comparison with scalar extension | done |
| 5 | Structural verbs: iota, shape, ravel, tally, reverse, index, take/drop | done (core) |
| 6 | Adverbs `/ \ ~` | done |
| 7 | Rank conjunction `"` | done |
| 8 | Trains (hooks and forks) | done |
| 9 | Assignment and user-defined verbs | done (subset) |

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
   +/"1 i. 3 4
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
   y + 10
   )
   f 5
15
```

## What works now

### Arrays and display

`JArray` models a J noun as flat storage plus a shape:

```java
JArray m = JArray.ints(new int[]{2, 3}, 1, 2, 3, 4, 5, 6);
System.out.println(m);
```

```
1 2 3
4 5 6
```

- integer (`long`), floating point (`double`) and character (`char`) arrays;
- rank 0 scalars, vectors, matrices and higher ranks;
- empty arrays (a shape containing a zero);
- integer-to-float promotion;
- J display: `_` negatives, `_`/`__` infinities, `_.` NaN, aligned matrix columns,
  blank lines between rank 3+ planes, and character arrays shown as text.

### Tokenizer

`j.lex` handles integer and float literals (`_3`, `_3.5`, `1e_5`, `_`, `__`, `_.`),
names, quoted strings with doubled-quote escapes, `NB.` comments, parentheses,
`=:`/`=.` and `{{`/`}}` delimiters. Primitives are matched longest-first, and
adverbs stay separate (`+/` is the verb `+` and the adverb `/`). Tokens record
1-based columns; malformed input reports `spelling error`.

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
- parentheses;
- monadic and dyadic application, selected by whether a noun is on the left;
- verb values, displayed by their spelling (`+`, `+/`, `+/"1`);
- rank-aware application of atom verbs, including scalar extension;
- J-style errors without crashing the REPL: `syntax error`, `value error`,
  `domain error`, `length error`, `rank error`, `index error`, `valence error`,
  `limit error`.

### Arithmetic and comparisons

- `+` identity/addition, `-` negation/subtraction, `*` signum/multiplication,
  `%` reciprocal/division, `^` exponential/power;
- `= ~: < > <: >:` return integer 0/1 atoms;
- numeric comparisons work across int/float; character comparisons are atom-wise;
- `<:` and `>:` also decrement and increment monadically;
- `%` and monadic `^` produce floating-point results; division by zero follows
  IEEE (`1 % 0` is `_`, `0 % 0` is `_.`);
- integer arithmetic overflow reports `limit error`.

### Structural verbs

All operate on the leading axis unless noted:

- `i.` — integers, including shape-vector forms such as `i. 3 4`;
- `$` — shape-of monadically and reshape dyadically;
- `#` — tally monadically;
- `,` — ravel monadically and catenate dyadically;
- `|.` — reverse monadically and rotate dyadically;
- `{` — index items by a scalar or array of indices (negative indices work);
- `{.` — head monadically and take dyadically;
- `}.` — behead monadically and drop dyadically;
- `|:` — transpose monadically.

### Adverbs, rank and trains

- `/` — insert: `+/ i. 5` → `10`, `+/ i. 3 4` → `12 15 18 21`;
- `\` — prefix: `+/\ 1 2 3` → `1 3 6`;
- `~` — reflex monadically and passive dyadically: `+~ 1 2 3` → `2 4 6`;
- `"` — rank: `+/"1 i. 3 4` → `6 22 38`;
- hooks and forks, so `(+/ % #) 1 2 3 4` → `2.5`.

### Assignment and user-defined verbs

- `name =: value` and `name =. value` assign nouns or verbs; assignment produces no
  output, and the name's noun/verb class is visible to later parsing.
- Assignments chain right-associatively: `b =: a =: 7`.
- `{{ ... }}` defines a verb inline from one expression, with local `x` and `y`:
  `times =: {{ x * y }}`.
- In the REPL, `name =: 3 : 0` reads body lines until `)`. Each non-empty body line
  is one expression; local `x` and `y` are available and the last expression is the
  result.
- `f =: +/` stores a derived verb, and `avg =: +/ % #` stores a train.

## Limitations

This is intentionally a subset; the following are not implemented or are
deliberately conservative:

- Boxing/heterogeneous arrays, complex numbers, rationals, extended integers,
  sparse arrays, locales and system facilities.
- Structural dyads not covered by the core: `i.` index-of, `#` copy, dyadic `|:`,
  monadic `{` catalog, multi-axis take/drop/rotate, and J's fill behavior when take
  asks for more items than exist.
- The non-arithmetic monadic meanings of `<` (box), `>` (open), `=` (self-classify)
  and `~:` (nub sieve) remain `valence error`; monadic `<:`/`>:` are decrement and
  increment.
- Insert on an empty array reports `domain error` because verb identities are not
  modeled.
- Rank has no negative-rank support; two-element rank specifications are
  interpreted as monadic rank plus both dyadic ranks. Train grouping beyond three
  verbs follows the documented right-grouping rule but needs checking against J.
- Explicit verbs support single-expression `{{ ... }}` and expression-per-line
  `3 : 0` bodies. Local assignments, control words and multi-line direct
  definitions are not implemented. Top-level `=.` currently behaves like `=:`.
- Assignments are recognized at sentence level, not inside parentheses.
- Integer overflow reports `limit error`; J would promote to extended integers.
- Float display uses five significant digits but tie rounding and exponent
  cutovers are approximate.

## Project layout

```
src/main/java/j         JError (shared language error)
src/main/java/j/array   JArray, JType, Shapes, JFormat
src/main/java/j/lex     Lexer, Token, TokenType
src/main/java/j/parse   Expr, VerbExpr, Phrase, Adverb, Parser, NameClassifier
src/main/java/j/eval    Evaluator, Environment, Value, Rank, Assembly
src/main/java/j/verbs   primitive, structural, derived and explicit verbs
src/main/java/j/repl    Session, Repl
src/test/java/...       JUnit 5 tests, including AcceptanceTest
```

## Tests

The suite covers the array model, display, lexer, parser, evaluator, arithmetic,
comparisons, structural verbs, derived verbs, assignment, explicit verbs and the
REPL. `AcceptanceTest` checks every example from the project brief, including the
`length error` case.

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

J's default display uses 5 significant digits for floats. This implementation rounds
and trims in J's notation, but the precise tie-breaking and exponent thresholds are
**unverified** against a real interpreter. Rank 3+ layout (one blank line between
planes) and the padding of the first matrix column are also implementation decisions
to confirm.
