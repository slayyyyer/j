export interface Example {
  title: string;
  code: string;
}

export interface ExampleGroup {
  label: string;
  examples: Example[];
}

/**
 * Runnable snippets shown beside the REPL. Multi-line snippets are fine:
 * the site feeds them to `runSource`, which understands explicit definitions.
 */
export const EXAMPLE_GROUPS: ExampleGroup[] = [
  {
    label: 'First sentences',
    examples: [
      { title: 'Right to left', code: '2 * 3 + 4' },
      { title: 'No precedence', code: '10 - 3 - 2' },
      { title: 'Scalar extension', code: '1 2 3 + 10' },
      { title: 'Negation', code: '- 2 + 3' },
    ],
  },
  {
    label: 'Arrays',
    examples: [
      { title: 'Integers', code: 'i. 5' },
      { title: 'A matrix', code: 'i. 3 4' },
      { title: 'Reshape', code: '2 3 $ i. 6' },
      { title: 'Shape and tally', code: '$ i. 3 4\n# i. 3 4' },
      { title: 'Negative iota', code: 'i. _3' },
    ],
  },
  {
    label: 'Arithmetic',
    examples: [
      { title: 'Reciprocal', code: '% 3' },
      { title: 'Power', code: '2 ^ 10' },
      { title: 'Comparisons', code: '1 2 3 = 2\n1 2 3 < 2' },
      { title: 'Exact rationals', code: '1r2 + 1r3' },
      { title: 'Complex numbers', code: '3j4 * 3j4' },
      { title: 'Infinity and NaN', code: '1 % 0\n0 % 0' },
    ],
  },
  {
    label: 'Rearranging',
    examples: [
      { title: 'Reverse', code: '|. 1 2 3' },
      { title: 'Rotate', code: '2 |. 1 2 3 4 5' },
      { title: 'Copy', code: '1 0 1 # 10 20 30' },
      { title: 'Take with fill', code: '5 {. 1 2 3' },
      { title: 'Catenate', code: '1 2 3 , 4 5' },
      { title: 'Index', code: '1 2 3 { 10 20 30 40' },
      { title: 'Transpose', code: '|: 2 3 $ i. 6' },
      { title: 'Index of', code: '3 1 4 1 5 9 i. 5' },
    ],
  },
  {
    label: 'Adverbs & rank',
    examples: [
      { title: 'Insert', code: '+/ i. 5' },
      { title: 'Sum the columns', code: '+/ i. 3 4' },
      { title: 'Sum each row', code: '+/"1 i. 3 4' },
      { title: 'Prefixes', code: '+/\\ 1 2 3' },
      { title: 'Reflex', code: '+~ 1 2 3' },
      { title: 'Passive', code: '3 +~ 10' },
      { title: 'Empty identities', code: '+/ i. 0\n*/ i. 0' },
    ],
  },
  {
    label: 'Trains',
    examples: [
      { title: 'Mean as a fork', code: '(+ / % #) 1 2 3 4' },
      { title: 'A hook', code: '(+ -) 5' },
      { title: 'Named train', code: 'avg =: +/ % #\navg 1 2 3 4' },
    ],
  },
  {
    label: 'Words & boxes',
    examples: [
      { title: 'A string', code: "'hello'" },
      { title: 'Quote escape', code: "'don''t'" },
      { title: 'Length of a string', code: "# 'hello'" },
      { title: 'Box a list', code: '< 1 2 3' },
      { title: 'Open one level', code: '> < 1 2 3' },
      { title: 'Catalog', code: '{ (<1 2),(<3 4)' },
    ],
  },
  {
    label: 'Defining verbs',
    examples: [
      { title: 'Inline verb', code: 'inc =: {{ y + 1 }}\ninc 41' },
      { title: 'Dyadic verb', code: 'plus =: {{ x + y }}\n20 plus 22' },
      {
        title: 'if. / else. / end.',
        code: `sign =: 3 : 0
if. y > 0 do.
  1
else.
  _1
end.
)
sign _5`,
      },
      {
        title: 'while. loop',
        code: `sumto =: 3 : 0
total =. 0
i =. 1
while. i <: y do.
  total =. total + i
  i =. i + 1
end.
total
)
sumto 10`,
      },
      {
        title: 'for. loop',
        code: `squares =: 3 : 0
out =. 0 $ 0
for_i. i. y do.
  out =. out , i * i
end.
out
)
squares 5`,
      },
    ],
  },
  {
    label: 'Sparse arrays',
    examples: [
      {
        title: 'Sparse display',
        code: '$. 3 4 $ 0 75 0 53 0 0 67 67 93 0 51 83',
      },
      { title: 'Toggle back to dense', code: '$.^:_1 $. 2 2 $ 1 0 0 2' },
      { title: 'Copy a sparse array', code: '2 # $. 3 $ 1 0 2' },
    ],
  },
];
