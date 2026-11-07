# J subset interpreter

A from-scratch interpreter for a practical subset of the
[J programming language](https://www.jsoftware.com/), written in Java 21.
The goal is a small, readable codebase for learning how J's array model, right-to-left
evaluation, adverbs, conjunctions and trains fit together. It does not aim for full J
compatibility and does not wrap an existing implementation.

## Status

**Milestones 1–3 of 9 complete.** The array model and display, the tokenizer, and a
parser/evaluator/REPL are working. Only `+` and `-` are registered so far; full
arithmetic, structural verbs, adverbs, rank, trains and assignment are still to come.

| Milestone | Description | Status |
|-----------|-------------|--------|
| 1 | `JArray` + J display format + tests | done |
| 2 | Tokenizer (`_` negatives, `NB.` comments, strings) | done |
| 3 | Parser/evaluator for nouns and verbs, REPL | done |
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
./gradlew run           # start the REPL
```

End-of-input (Ctrl-D) exits the REPL.

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
- empty arrays (shape with a zero dimension);
- integer-to-float promotion with `toFloat()`;
- J display: `_` for negatives, `_`/`__` for infinities, `_.` for NaN, aligned
  matrix columns, blank lines between the planes of rank 3+ arrays, and character
  arrays shown as text.

### Tokenizer

`j.lex` handles integer and float literals (`_3`, `_3.5`, `1e_5`, `_`, `__`, `_.`),
names, quoted strings with doubled-quote escapes, `NB.` comments, parentheses,
`=:`/`=.` and `{{`/`}}` delimiters. Primitives are matched longest-first, and
adverbs stay separate (`+/` is the verb `+` and the adverb `/`). Tokens record
1-based columns; malformed input reports `spelling error`.

### Parser, evaluator and REPL

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
   1 +
|syntax error
   2 + 3
5
```

Supported in the interpreter:

- numeric and character literals, and strands such as `1 2 3`;
- parentheses;
- monadic and dyadic application, selected by whether a noun is on the left;
- `+` (conjugate/identity; addition) and `-` (negation; subtraction), applied
  atom-wise with scalar extension;
- arrays of any rank (rank-0 verbs map over their frames);
- verb values, displayed by their spelling (`+`);
- J-style errors without crashing the REPL: `syntax error`, `value error`,
  `domain error`, `length error`, `rank error`, `valence error`, `limit error`.

Strands of equal-shaped nouns are stacked into a higher-rank array, so
`(1 2) (3 4)` gives a 2x2 matrix. Mixed character/numeric strands are a
`domain error` until boxing is implemented.

Not yet implemented: the remaining arithmetic and comparison verbs, structural
verbs, adverbs, the rank conjunction, trains, assignment, and explicit verbs.

## Project layout

```
src/main/java/j         JError (shared language error)
src/main/java/j/array   JArray, JType, Shapes, JFormat
src/main/java/j/lex     Lexer, Token, TokenType
src/main/java/j/parse   Expr, VerbExpr, Phrase, Parser, NameClassifier
src/main/java/j/eval    Evaluator, Environment, Value, Rank, Assembly
src/main/java/j/verbs   Verb, Plus, Minus, Primitives
src/main/java/j/repl    Session, Repl
src/test/java/...       JUnit 5 tests
```

## Display notes

J's default display uses 5 significant digits for floats. This implementation rounds
and trims in J's notation, but the precise tie-breaking and exponent thresholds are
**unverified** against a real interpreter. Rank 3+ layout (one blank line between
planes) and the padding of the first matrix column are also implementation decisions
to confirm.

## Subset limitations

- Only `+` and `-` are registered as verbs; other primitive spellings currently
  produce `syntax error` until their milestone.
- Integer arithmetic overflow reports `limit error`; J would promote to extended
  integers.
- Boxing is not implemented, so mixed character/numeric arrays are rejected.
- Stacking non-scalar nouns in a strand follows the natural "new leading axis"
  rule but is **unverified** against J.
- Assignment, explicit definitions and the REPL's assignment-output suppression
  are not implemented yet.

## Feature checklist

Core:

- [x] `JArray` model and J display format
- [x] Tokenizer (`_` negatives, `NB.` comments, strings, primitives, columns)
- [x] Parser and right-to-left evaluator
- [x] Working REPL with J-style error recovery

Primitives (monadic / dyadic where applicable):

- [x] `+ -` (initial arithmetic verbs)
- [ ] `* % ^`
- [ ] `= < > <: >: ~:`
- [ ] `i.` `$` `,` `#` `|.` `{` `{.` `}.`
- [ ] `|:` transpose, inner product, boxing, `@`/`@:`, `&`
- [ ] Adverbs `/ \ ~`
- [ ] Conjunction `"` (rank)
- [ ] Assignment `=:` / `=.`
- [ ] Explicit verbs `3 : 0` and `{{ }}`
- [ ] Trains (hooks and forks)

Full-project acceptance examples, with the remaining verbs arriving later:

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
