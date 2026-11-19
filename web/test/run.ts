/**
 * Acceptance checks for the TypeScript J interpreter, mirroring the Java
 * suite's expected strings.
 *
 * Run with: node test/run.ts. Node 24 strips TypeScript types natively, and
 * imports use explicit .ts extensions so no bundler is needed.
 */
import { Session, runSource } from '../src/j/session.ts';

interface Case {
  source: string;
  expected: string;
}

const cases: Case[] = [
  // Right-to-left evaluation
  { source: '2 * 3 + 4', expected: '14' },
  { source: '10 - 3 - 2', expected: '9' },
  { source: '- 2 + 3', expected: '_5' },
  { source: '1 2 3 + 10', expected: '11 12 13' },
  { source: '2 + 3 * 4', expected: '14' },
  { source: '2 3 $ 1 2', expected: '1 2 1\n2 1 2' },

  // Arrays and display
  { source: 'i. 5', expected: '0 1 2 3 4' },
  { source: 'i. _3', expected: '2 1 0' },
  { source: '- i. 5', expected: '0 _1 _2 _3 _4' },
  { source: 'i. 3 4', expected: '0 1  2  3\n4 5  6  7\n8 9 10 11' },
  { source: '2 3 $ i. 6', expected: '0 1 2\n3 4 5' },
  { source: '$ 2 3 $ i. 6', expected: '2 3' },
  { source: '$ i. 5', expected: '5' },
  { source: '# 1 2 3', expected: '3' },
  { source: '# i. 3 4', expected: '3' },
  { source: ', 2 3 $ i. 6', expected: '0 1 2 3 4 5' },
  { source: '2 3 4', expected: '2 3 4' },
  { source: '0 $ 0', expected: '' },
  { source: '1.2346e6', expected: '1.2346e6' },
  { source: '1234567.0', expected: '1.2346e6' },
  { source: '100000.0', expected: '100000' },
  { source: '0.0001', expected: '0.0001' },
  { source: '1e_5', expected: '1e_5' },
  { source: '_1.5e_7', expected: '_1.5e_7' },
  { source: '2.0', expected: '2' },

  // Arithmetic and comparison
  { source: '* _2 0 3', expected: '_1 0 1' },
  { source: '% 2', expected: '0.5' },
  { source: '% 3', expected: '0.33333' },
  { source: '2 % 3', expected: '0.66667' },
  { source: '2 ^ 10', expected: '1024' },
  { source: '^ 1', expected: '2.7183' },
  { source: '1 % 0', expected: '_' },
  { source: '0 % 0', expected: '_.' },
  { source: '1 2 3 + 1 2', expected: '|length error' },
  { source: '1 2 3 = 2', expected: '0 1 0' },
  { source: '1 2 3 = 1 2 3', expected: '1 1 1' },
  { source: '1 2 3 < 2', expected: '1 0 0' },
  { source: '<: 1 2 3', expected: '0 1 2' },
  { source: '>: 1 2 3', expected: '2 3 4' },
  { source: '~: 1 2 1 3', expected: '1 1 0 1' },
  { source: '1r2 + 1r3', expected: '5r6' },
  { source: '3j4 * 3j4', expected: '_7j24' },
  { source: '1 2 3 + 1 2 3', expected: '2 4 6' },

  // Structural verbs
  { source: '|. 1 2 3', expected: '3 2 1' },
  { source: '|. i. 2 3', expected: '3 4 5\n0 1 2' },
  { source: '1 |. 1 2 3', expected: '2 3 1' },
  { source: '_1 |. 1 2 3', expected: '3 1 2' },
  { source: '2 |. 1 2 3 4 5', expected: '3 4 5 1 2' },
  { source: '2 # 1 2 3', expected: '1 1 2 2 3 3' },
  { source: '3 # 1 2 3', expected: '1 1 1 2 2 2 3 3 3' },
  { source: "2 0 1 # 'abc'", expected: 'aac' },
  { source: '2 # i. 2 3', expected: '0 1 2\n0 1 2\n3 4 5\n3 4 5' },
  { source: '2j1 0 1j2 # i. 3 2', expected: '0 1\n0 1\n0 0\n4 5\n0 0\n0 0' },
  { source: '1 0 1 # 10 20 30', expected: '10 30' },
  { source: '{. 1 2 3', expected: '1' },
  { source: '{. i. 2 3', expected: '0 1 2' },
  { source: '}. 1 2 3', expected: '2 3' },
  { source: '1 }. 1 2 3', expected: '2 3' },
  { source: '_1 }. 1 2 3', expected: '1 2' },
  { source: '2 {. 1 2 3', expected: '1 2' },
  { source: '_2 {. 1 2 3', expected: '2 3' },
  { source: '5 {. 1 2 3', expected: '1 2 3 0 0' },
  { source: '3 {. 1 2', expected: '1 2 0' },
  { source: "_5 {. 'abc'", expected: '  abc' },
  { source: '{. 0 $ 0', expected: '0' },
  { source: "{. ''", expected: ' ' },
  { source: '{. i. 0 3', expected: '0 0 0' },
  { source: '2 3 {. i. 4 5', expected: '0 1 2\n5 6 7' },
  { source: '_2 _3 {. i. 4 5', expected: '12 13 14\n17 18 19' },
  { source: '1 2 }. i. 4 5', expected: ' 7  8  9\n12 13 14\n17 18 19' },
  { source: '_1 _2 }. i. 4 5', expected: ' 0  1  2\n 5  6  7\n10 11 12' },
  { source: '1 2 3 , 4 5', expected: '1 2 3 4 5' },
  { source: '1 , 2 3', expected: '1 2 3' },
  { source: '1 2 , i. 2 3', expected: '|rank error' },
  { source: '1 2 3 { 10 20 30 40', expected: '20 30 40' },
  { source: '_1 { 10 20 30', expected: '30' },
  { source: '0 2 { 10 20 30', expected: '10 30' },
  { source: '1 { i. 3 4', expected: '4 5 6 7' },
  { source: '2 { 1 2', expected: '|index error' },
  { source: '1 2 3 { 1 2', expected: '|index error' },
  { source: '1r2 { 10 20', expected: '|domain error' },
  { source: '1 2 3 # 1 2', expected: '|length error' },
  { source: '_1 # 1 2 3', expected: '|domain error' },
  { source: '2 3 4 i. 3 4', expected: '1 2' },
  { source: '10 20 30 i. 20 99', expected: '1 3' },
  { source: '3 1 4 1 5 9 i. 5', expected: '4' },
  { source: '3 1 4 1 5 9 i. 7', expected: '6' },
  { source: '1 2 3 i. 2 3 4', expected: '1 2 3' },
  { source: '(i. 4 3) i. 6 7 8', expected: '2' },
  { source: "'abc' i. 'b'", expected: '1' },
  { source: "'abc' i. 'd'", expected: '3' },
  { source: '|: 2 3 $ i. 6', expected: '0 3\n1 4\n2 5' },
  { source: '1 0 |: 2 3 $ i. 6', expected: '0 3\n1 4\n2 5' },
  { source: '1 1 |. i. 3 4', expected: '5  6  7 4\n9 10 11 8\n1  2  3 0' },
  { source: "i. 'a'", expected: '|domain error' },

  // Adverbs, rank and trains
  { source: '+/ i. 5', expected: '10' },
  { source: '+/ i. 3 4', expected: '12 15 18 21' },
  { source: '+/"1 i. 3 4', expected: '6 22 38' },
  { source: '+/"0 1 i. 3 4', expected: '6 22 38' },
  { source: '+/"_1 i. 3 4', expected: '6 22 38' },
  { source: '+/ i. 0 3', expected: '0 0 0' },
  { source: '+/ i. 0', expected: '0' },
  { source: '*/ i. 0', expected: '1' },
  { source: '-/ i. 0', expected: '0' },
  { source: '%/ i. 0', expected: '1' },
  { source: '=/ i. 0', expected: '1' },
  { source: '~:/ i. 0', expected: '0' },
  { source: '+/\\ 1 2 3', expected: '1 3 6' },
  { source: '+~ 1 2 3', expected: '2 4 6' },
  { source: '2 +~ 3', expected: '5' },
  { source: '3 +~ 10', expected: '13' },
  { source: '(+ / % #) 1 2 3 4', expected: '2.5' },
  { source: '(+ / % #) i. 3 4', expected: '4 5 6 7' },
  { source: '(+ -) 5', expected: '0' },
  { source: '+/ 1 2 3', expected: '6' },
  { source: '-/ 1 2 3', expected: '2' },
  { source: '(<"0) 1 2 3', expected: '+-+-+-+\n|1|2|3|\n+-+-+-+' },

  // Characters and boxes
  { source: "'hello'", expected: 'hello' },
  { source: "'don''t'", expected: "don't" },
  { source: "# 'hello'", expected: '5' },
  { source: '+/ (2 2 $ 1 2 3 4)', expected: '4 6' },
  { source: '{ (<1 2),(<3 4)', expected: '1 3\n1 4\n2 3\n2 4' },
  { source: '< 5', expected: '+-+\n|5|\n+-+' },
  { source: '< 1 2 3', expected: '+-----+\n|1 2 3|\n+-----+' },
  { source: '> < 5', expected: '5' },
  { source: '= 1 2 1 3', expected: '1 0 1 0\n0 1 0 0\n0 0 0 1' },

  // Assignment and explicit verbs
  { source: 'a =: 6', expected: '' },
  { source: 'a * 7', expected: '42' },
  { source: 'b =: a =: 7', expected: '' },
  { source: 'b', expected: '7' },
  { source: '(c =. 3) + 2', expected: '5' },
  { source: 'c', expected: '3' },
  { source: 'avg =: +/ % #', expected: '' },
  { source: 'avg 1 2 3 4', expected: '2.5' },
  { source: 'inc =: {{ y + 1 }}', expected: '' },
  { source: 'inc 41', expected: '42' },
  { source: 'plus =: {{ x + y }}', expected: '' },
  { source: '3 plus 4', expected: '7' },
  { source: 'f =: +/', expected: '' },
  { source: 'f 1 2 3 4', expected: '10' },

  // Errors
  { source: 'foo', expected: '|value error' },
  { source: "1 + 'a'", expected: '|domain error' },
  { source: '+ /', expected: '+/' },
  { source: '1 2 3 4 5 { 1 2', expected: '|index error' },

  // Sparse arrays
  {
    source: '$. 3 4 $ 0 75 0 53 0 0 67 67 93 0 51 83',
    expected: '0 1 | 75\n0 3 | 53\n1 2 | 67\n1 3 | 67\n2 0 | 93\n2 2 | 51\n2 3 | 83',
  },
  { source: '$.^:_1 $. 2 2 $ 1 0 0 2', expected: '1 0\n0 2' },
  { source: '0 $. $. 2 2 $ 1 0 0 2', expected: '1 0\n0 2' },
  { source: '# $. 2 3 $ 1 0 0 0 0 2', expected: '2' },
];

const multiLine: Case[] = [
  {
    source: `f =: 3 : 0
if. y > 0 do.
  y * 2
else.
  y - 1
end.
)
f 3`,
    expected: '6',
  },
  {
    source: `f =: 3 : 0
if. y > 0 do.
  y * 2
else.
  y - 1
end.
)
f 0`,
    expected: '_1',
  },
  {
    source: `sumto =: 3 : 0
total =. 0
i =. 1
while. i <: y do.
  total =. total + i
  i =. i + 1
end.
total
)
sumto 10`,
    expected: '55',
  },
  {
    source: `squares =: 3 : 0
out =. 0 $ 0
for_i. i. y do.
  out =. out , i * i
end.
out
)
squares 5`,
    expected: '0 1 4 9 16',
  },
  {
    source: `add =: {{ x + y }}
20 add 22`,
    expected: '42',
  },
];

let failures = 0;

function check(session: Session, testCase: Case): void {
  const output = runSource(session, testCase.source);
  const rendered = output.map((line) => line.text).join('\n');
  if (rendered !== testCase.expected) {
    failures++;
    console.error(`FAIL: ${JSON.stringify(testCase.source)}`);
    console.error(`  expected: ${JSON.stringify(testCase.expected)}`);
    console.error(`  actual:   ${JSON.stringify(rendered)}`);
  }
}

const transcript = new Session();
for (const testCase of cases) {
  check(transcript, testCase);
}

for (const testCase of multiLine) {
  check(transcript, testCase);
}

// Sessions keep their names across inputs.
{
  const session = new Session();
  const first = runSource(session, 'x =: 3 4 5');
  const second = runSource(session, '+/ x');
  if (first.length !== 0 || second.map((line) => line.text).join('\n') !== '12') {
    failures++;
    console.error('FAIL: session persistence');
  }
}

const total = cases.length + multiLine.length + 1;
if (failures === 0) {
  console.log(`ok — ${total} checks passed`);
} else {
  console.error(`${failures} of ${total} checks failed`);
  process.exitCode = 1;
}
