# J subset interpreter

A from-scratch interpreter for a practical subset of the
[J programming language](https://www.jsoftware.com/), written in Java 21.
The goal is a small, readable codebase for learning how J's array model, right-to-left
evaluation, adverbs, conjunctions and trains fit together. It does not aim for full J
compatibility and does not wrap an existing implementation.

## Status

**Milestone 1 of 9 complete**: the array data model and J display formatting.
There is no tokenizer, evaluator or REPL yet. See [DESIGN.md](DESIGN.md) for the
architecture and the plan.

| Milestone | Description | Status |
|-----------|-------------|--------|
| 1 | `JArray` + J display format + tests | done |
| 2 | Tokenizer (`_` negatives, `NB.` comments, strings) | planned |
| 3 | Parser/evaluator for nouns and verbs, REPL | planned |
| 4 | Arithmetic and comparison with scalar extension | planned |
| 5 | Structural verbs (iota, shape, ravel, tally, reverse/rotate, index, take/drop) | planned |
| 6 | Adverbs `/ \ ~` | planned |
| 7 | Rank conjunction `"` | planned |
| 8 | Trains (hooks and forks) | planned |
| 9 | Assignment and user-defined verbs | planned |

## Requirements and build

- JDK 21
- Gradle (or use the included wrapper)

```sh
./gradlew test          # run the test suite
./gradlew build         # compile and test
```

The REPL will be launched with `./gradlew run` once milestone 3 lands.

## What works now

The `j.array` package models a J noun as flat storage plus a shape:

```java
JArray m = JArray.ints(new int[]{2, 3}, 1, 2, 3, 4, 5, 6);
System.out.println(m);
```

```
1 2 3
4 5 6
```

Supported:

- integer (`long`), floating point (`double`) and character (`char`) arrays;
- rank 0 scalars, vectors, matrices and higher ranks;
- empty arrays (shape with a zero dimension);
- integer-to-float promotion with `toFloat()`;
- J display: `_` for negatives, `_`/`__` for infinities, `_.` for NaN, aligned
  matrix columns, blank lines between the planes of rank 3+ arrays, and character
  arrays shown as text.

Not yet implemented: tokens, parsing, evaluation, verbs, assignment, the REPL.

## Project layout

```
src/main/java/j/array   JArray, JType, Shapes, JFormat
src/main/java/j/lex     tokenizer and tokens
src/main/java/j/parse   AST and parser
src/main/java/j/eval    evaluator, environment, rank machinery, JError
src/main/java/j/verbs   primitive and derived verbs
src/main/java/j/repl    command-line REPL
src/test/java/...       JUnit 5 tests
```

Only `j.array` exists today; the other packages arrive with their milestones.

## Display notes

J's default display uses 5 significant digits for floats. This implementation rounds
and trims in J's notation, but the precise tie-breaking and exponent thresholds are
**unverified** against a real interpreter. Rank 3+ layout (one blank line between
planes) and the padding of the first matrix column are also implementation decisions
to confirm. These will be checked with J and corrected as the project grows.

## Feature checklist

Primitives (monadic / dyadic where applicable):

- [ ] `+ - * % ^`
- [ ] `= < > <: >: ~:`
- [ ] `i.` `$` `,` `#` `|.` `{` `{.` `}.`
- [ ] `|:` transpose, inner product, boxing, `@`/`@:`, `&`
- [ ] Adverbs `/ \ ~`
- [ ] Conjunction `"` (rank)
- [ ] Assignment `=:` / `=.`
- [ ] Explicit verbs `3 : 0` and `{{ }}`
- [ ] Trains (hooks and forks)

Acceptance examples targeted by the full project:

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
