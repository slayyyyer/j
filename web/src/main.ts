import './style.css';
import { EXAMPLE_GROUPS } from './examples.ts';
import type { RunLine } from './j/session.ts';
import { Runner, type RunOutcome } from './runner.ts';
import {
  createShareUrl,
  decodeTranscript,
  encodeTranscript,
  SHARE_HASH_PREFIX,
  transcriptFromHash,
} from './share.ts';

function requireElement<K extends keyof HTMLElementTagNameMap>(
  selector: string,
): HTMLElementTagNameMap[K] {
  const node = document.querySelector<HTMLElementTagNameMap[K]>(selector);
  if (!node) {
    throw new Error(`missing element: ${selector}`);
  }
  return node;
}

const output = requireElement<'div'>('#terminal-output');
const form = requireElement<'form'>('#terminal-form');
const input = requireElement<'textarea'>('#terminal-input');
const runButton = requireElement<'button'>('#run-session');
const resetButton = requireElement<'button'>('#reset-session');
const shareButton = requireElement<'button'>('#share-session');
const exampleList = requireElement<'div'>('#example-list');

const AUTOSAVE_KEY = 'jts.transcript.v1';
const DEFAULT_SOURCE = '2 * 3 + 4';
const RESTORE_BUDGET_MS = 6000;

const runner = new Runner();
const transcript: string[] = [];
let historyCursor = 0;
let busy = false;
let epoch = 0;
let runLabelTimer: number | undefined;

// ------------------------------------------------------------------ terminal

function element<K extends keyof HTMLElementTagNameMap>(
  tag: K,
  className?: string,
  text?: string,
): HTMLElementTagNameMap[K] {
  const node = document.createElement(tag);
  if (className) {
    node.className = className;
  }
  if (text !== undefined) {
    node.textContent = text;
  }
  return node;
}

function scrollTerminal(): void {
  output.scrollTop = output.scrollHeight;
}

function appendBanner(): void {
  const banner = element('div', 'terminal-banner');
  banner.append(
    element('strong', undefined, 'j.ts'),
    document.createTextNode(
      ' — a J subset in TypeScript. Enter an expression like ',
    ),
  );
  banner.append(element('code', undefined, 'i. 5'));
  banner.append(document.createTextNode(', or pick an example from the list.'));
  output.append(banner);
}

function appendNotice(text: string): void {
  output.append(element('div', 'terminal-notice', text));
}

function appendEntry(source: string, lines: RunLine[]): void {
  const entry = element('div', 'entry');

  const sourceRow = element('div', 'entry-source');
  sourceRow.append(element('span', 'prompt', '   '));
  sourceRow.append(element('pre', 'entry-source-text', source));
  entry.append(sourceRow);

  for (const line of lines) {
    const rendered = element(
      'pre',
      line.kind === 'error' ? 'entry-line error' : 'entry-line',
      line.text,
    );
    entry.append(rendered);
  }

  output.append(entry);
  scrollTerminal();
}

/** Turns the Run button into Stop only when a run outlives a quick flicker. */
function setBusy(value: boolean): void {
  busy = value;
  if (value) {
    if (runLabelTimer !== undefined) {
      return;
    }
    runLabelTimer = window.setTimeout(() => {
      runLabelTimer = undefined;
      if (!busy) {
        return;
      }
      runButton.textContent = 'Stop';
      runButton.classList.add('stop');
      runButton.setAttribute('aria-label', 'Stop the running evaluation');
    }, 150);
    return;
  }

  if (runLabelTimer !== undefined) {
    window.clearTimeout(runLabelTimer);
    runLabelTimer = undefined;
  }
  runButton.textContent = 'Run';
  runButton.classList.remove('stop');
  runButton.removeAttribute('aria-label');
}

function readAutosave(): string[] | null {
  try {
    const raw = localStorage.getItem(AUTOSAVE_KEY);
    return raw === null ? null : decodeTranscript(raw);
  } catch {
    // Storage can be unavailable in private or restricted browsing modes.
    return null;
  }
}

function saveAutosave(): void {
  try {
    localStorage.setItem(AUTOSAVE_KEY, encodeTranscript(transcript));
  } catch {
    // Storage can be unavailable in private or restricted browsing modes.
  }
}

function clearShareHash(): void {
  if (!location.hash.startsWith(SHARE_HASH_PREFIX)) {
    return;
  }
  history.replaceState(null, '', `${location.pathname}${location.search}`);
}

async function copyText(text: string): Promise<boolean> {
  try {
    await navigator.clipboard.writeText(text);
    return true;
  } catch {
    // The link is in the address bar either way; the button says so.
    return false;
  }
}

let shareTimer: number | undefined;

function flashShareButton(label: string): void {
  shareButton.textContent = label;
  if (shareTimer !== undefined) {
    window.clearTimeout(shareTimer);
  }
  shareTimer = window.setTimeout(() => {
    shareButton.textContent = 'share';
    shareTimer = undefined;
  }, 1800);
}

async function shareSession(): Promise<void> {
  const url = createShareUrl(location.href, transcript);
  if (url === null) {
    flashShareButton('too long');
    return;
  }

  history.replaceState(null, '', url);
  const copied = await copyText(url);
  flashShareButton(copied ? 'copied' : 'in URL');
}

/**
 * Rebuilds the worker and replays the recorded transcript without rendering.
 * Used after Stop or after a sentence exhausts its budget: the aborted line is
 * not in the transcript, so partial state disappears with the worker.
 */
async function rebuild(): Promise<void> {
  const myEpoch = epoch + 1;
  epoch = myEpoch;
  runner.restart();
  setBusy(true);

  for (const source of [...transcript]) {
    if (myEpoch !== epoch) {
      return;
    }
    try {
      const outcome = await runner.run(source);
      if (outcome.tripped) {
        appendNotice('Rebuilt the session up to the last line within the time limit.');
        break;
      }
    } catch {
      if (myEpoch !== epoch) {
        return;
      }
      appendNotice('The session could not be rebuilt.');
      break;
    }
  }

  if (myEpoch === epoch) {
    setBusy(false);
  }
}

function stopRun(): void {
  if (!busy) {
    return;
  }
  appendNotice('Stopped.');
  void rebuild();
}

/** Runs one sentence and records it only when it completes under budget. */
async function runSourceLine(source: string): Promise<boolean> {
  const startEpoch = epoch;
  setBusy(true);

  let outcome: RunOutcome;
  try {
    outcome = await runner.run(source);
  } catch {
    if (startEpoch !== epoch) {
      return false;
    }
    appendNotice('The interpreter failed, so the session was rebuilt.');
    await rebuild();
    return false;
  }

  if (startEpoch !== epoch) {
    return false;
  }

  appendEntry(source, outcome.lines);
  if (outcome.truncated) {
    appendNotice('Output shortened; run a smaller expression to see it all.');
  }
  if (outcome.tripped) {
    appendNotice('That line exceeded the time limit, so it was not kept.');
    await rebuild();
    return false;
  }

  transcript.push(source);
  historyCursor = transcript.length;
  clearShareHash();
  saveAutosave();
  setBusy(false);
  return true;
}

function resizeInput(): void {
  input.style.height = 'auto';
  input.style.height = `${Math.min(input.scrollHeight, 192)}px`;
}

function submit(): void {
  if (busy) {
    return;
  }
  const source = input.value;
  if (source.trim() === '') {
    return;
  }
  input.value = '';
  resizeInput();
  void runSourceLine(source).then((kept) => {
    if (!kept && input.value === '') {
      input.value = source;
      resizeInput();
    }
    input.focus();
  });
}

async function resetSession(): Promise<void> {
  epoch += 1;
  runner.restart();
  transcript.length = 0;
  historyCursor = 0;
  output.replaceChildren();
  clearShareHash();
  appendBanner();
  setBusy(false);
  await runSourceLine(DEFAULT_SOURCE);
  input.value = '';
  resizeInput();
  input.focus();
}

form.addEventListener('submit', (event) => {
  event.preventDefault();
  submit();
});

runButton.addEventListener('click', (event) => {
  if (busy) {
    event.preventDefault();
    stopRun();
  }
});

input.addEventListener('input', resizeInput);

input.addEventListener('keydown', (event) => {
  if (event.key === 'Enter' && !event.shiftKey) {
    event.preventDefault();
    submit();
    return;
  }

  const singleLine = !input.value.includes('\n');
  if (!singleLine || transcript.length === 0) {
    return;
  }

  if (event.key === 'ArrowUp') {
    event.preventDefault();
    historyCursor = Math.max(0, historyCursor - 1);
    input.value = transcript[historyCursor];
    resizeInput();
  } else if (event.key === 'ArrowDown') {
    event.preventDefault();
    historyCursor = Math.min(transcript.length, historyCursor + 1);
    input.value =
      historyCursor === transcript.length ? '' : transcript[historyCursor];
    resizeInput();
  }
});

resetButton.addEventListener('click', () => {
  void resetSession();
});
shareButton.addEventListener('click', () => {
  void shareSession();
});

// ------------------------------------------------------------------ examples

function firstLine(code: string): string {
  const line = code.split('\n')[0].trim();
  return code.includes('\n') ? `${line} …` : line;
}

for (const group of EXAMPLE_GROUPS) {
  const groupElement = element('div', 'example-group');
  groupElement.append(element('p', 'example-group-label', group.label));

  for (const example of group.examples) {
    const button = element('button', 'example-button');
    button.type = 'button';
    button.append(
      element('span', 'example-title', example.title),
      element('code', 'example-code', firstLine(example.code)),
    );
    button.addEventListener('click', () => {
      if (busy) {
        return;
      }
      input.value = '';
      resizeInput();
      void runSourceLine(example.code);
      input.focus();
    });
    groupElement.append(button);
  }

  exampleList.append(groupElement);
}

// --------------------------------------------------------------- guide runs

document.querySelectorAll<HTMLPreElement>('pre[data-run]').forEach((pre) => {
  const wrapper = element('div', 'snippet');
  pre.replaceWith(wrapper);
  wrapper.append(pre);

  const button = element('button', 'run-snippet', 'Run ▸');
  button.type = 'button';
  button.addEventListener('click', () => {
    if (busy) {
      return;
    }
    void runSourceLine(pre.textContent ?? '');
    document.querySelector('#playground')?.scrollIntoView({
      behavior: 'smooth',
      block: 'start',
    });
  });
  wrapper.append(button);
});

// ---------------------------------------------------------------- scrollspy

const tocLinks = [...document.querySelectorAll<HTMLAnchorElement>('.toc a')];
const tocSections = tocLinks
  .map((link) => {
    const id = link.getAttribute('href')?.slice(1);
    return { link, section: id ? document.getElementById(id) : null };
  })
  .filter((entry): entry is { link: HTMLAnchorElement; section: HTMLElement } =>
    entry.section !== null,
  );

function updateToc(): void {
  if (tocSections.length === 0) {
    return;
  }
  let active = tocSections[0];
  for (const entry of tocSections) {
    if (entry.section.getBoundingClientRect().top <= 130) {
      active = entry;
    }
  }
  for (const entry of tocSections) {
    entry.link.classList.toggle('active', entry === active);
  }
}

let tocQueued = false;
window.addEventListener(
  'scroll',
  () => {
    if (tocQueued) {
      return;
    }
    tocQueued = true;
    requestAnimationFrame(() => {
      tocQueued = false;
      updateToc();
    });
  },
  { passive: true },
);

// --------------------------------------------------------------------- boot

/** Replays a restored session line by line, rendering as results arrive. */
async function replaySession(
  lines: readonly string[],
  notice: string,
): Promise<void> {
  const myEpoch = epoch + 1;
  epoch = myEpoch;
  runner.reset();
  transcript.length = 0;
  historyCursor = 0;
  output.replaceChildren();
  appendBanner();
  appendNotice(notice);
  setBusy(true);

  const started = Date.now();
  for (const source of lines) {
    if (myEpoch !== epoch) {
      return;
    }
    if (source.trim() === '') {
      continue;
    }
    if (Date.now() - started > RESTORE_BUDGET_MS) {
      appendNotice('Restore stopped: this session takes too long to replay.');
      break;
    }

    let outcome: RunOutcome;
    try {
      outcome = await runner.run(source);
    } catch {
      if (myEpoch !== epoch) {
        return;
      }
      appendNotice('Restore stopped: the interpreter failed.');
      break;
    }

    if (myEpoch !== epoch) {
      return;
    }

    appendEntry(source, outcome.lines);
    if (outcome.truncated) {
      appendNotice('Output shortened while restoring this session.');
    }
    if (outcome.tripped) {
      appendNotice('Restore stopped: a line exceeded the time limit.');
      await rebuild();
      return;
    }
    transcript.push(source);
  }

  historyCursor = transcript.length;
  saveAutosave();
  if (myEpoch === epoch) {
    setBusy(false);
  }
}

async function boot(): Promise<void> {
  const initialHash = location.hash;
  const hadShareHash = initialHash.startsWith(SHARE_HASH_PREFIX);
  const shared = transcriptFromHash(initialHash);
  const saved =
    shared === null || shared.length === 0 ? readAutosave() : null;

  if (shared !== null && shared.length > 0) {
    await replaySession(shared, 'Restored a shared session.');
  } else if (saved !== null && saved.length > 0) {
    await replaySession(
      saved,
      hadShareHash
        ? 'That share link did not contain a session; restored your last session.'
        : 'Restored your last session.',
    );
  } else {
    appendBanner();
    if (hadShareHash) {
      appendNotice('That share link did not contain a session.');
    }
    await runSourceLine(DEFAULT_SOURCE);
  }

  clearShareHash();
}

void boot();

resizeInput();
updateToc();
