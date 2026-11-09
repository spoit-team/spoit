// Dependency-free execution check: bun src/test/frontend/match-evaluation.test.mjs
// 선수 평가 영역이 진행률을 제출 검사와 같은 빈 칸 기준으로 세고, 슬라이더를 움직일 때 표를 다시 그리지 않는지 확인합니다.
import assert from 'node:assert/strict';

class FakeClassList {
  constructor() { this.names = new Set(); }
  add(...names) { names.forEach(name => this.names.add(name)); }
  remove(...names) { names.forEach(name => this.names.delete(name)); }
  toggle(name, force) { (force ?? !this.names.has(name)) ? this.names.add(name) : this.names.delete(name); }
  contains(name) { return this.names.has(name); }
}

class FakeNode {
  constructor(properties = {}) {
    Object.assign(this, { hidden: false, textContent: '', value: '' }, properties);
    this.attributes = {};
    this.style = {};
    this.listeners = {};
    this.dispatched = [];
    this.bySelector = {};
    this.classList = new FakeClassList();
    this.focused = 0;
  }
  setAttribute(name, value) { this.attributes[name] = String(value); }
  getAttribute(name) { return this.attributes[name] ?? null; }
  removeAttribute(name) { delete this.attributes[name]; }
  querySelector(selector) { return this.bySelector[selector] ?? null; }
  closest() { return null; }
  addEventListener(type, handler) { (this.listeners[type] ??= []).push(handler); }
  dispatchEvent(event) {
    this.dispatched.push(event);
    for (const handler of this.listeners[event.type] ?? []) handler(event);
    return true;
  }
  focus() { this.focused += 1; }
}

const VIEW = {
  matchId: 42, mySide: 'A',
  targets: [
    { participantId: 11, nickname: '에이', matchSide: 'A' },
    { participantId: 21, nickname: '비', matchSide: 'B' }
  ],
  items: [{ itemId: 1, itemName: '패스' }, { itemId: 2, itemName: '수비' }],
  submitted: false, canSubmit: true, deadlineAt: null
};

function evaluationPage({ view, status = 200 }) {
  const progressFilled = new FakeNode();
  const progressBarRole = new FakeNode();
  const progressFill = new FakeNode();
  const progress = new FakeNode();
  progress.bySelector = {
    '[data-progress-filled]': progressFilled,
    '[role="progressbar"]': progressBarRole,
    '.match-evaluation-progress-bar span': progressFill
  };
  const missingBox = new FakeNode();
  const firstMissingSlider = new FakeNode();

  // 표 전체를 다시 그린 횟수는 innerHTML 대입 횟수로 센다.
  const renders = [];
  const body = new FakeNode();
  Object.defineProperty(body, 'innerHTML', {
    get() { return renders.at(-1) ?? ''; },
    set(html) { renders.push(html); }
  });
  body.bySelector = {
    '[data-evaluation-progress]': progress,
    '[data-evaluation-missing]': missingBox,
    '.match-evaluation-score.is-missing input[data-key]': firstMissingSlider
  };

  const root = new FakeNode();
  root.dataset = { evaluationUrl: '/mypage/matches/42/evaluation', csrfHeader: 'X-CSRF-TOKEN', csrfToken: 'csrf-value' };
  root.bySelector = { '[data-evaluation-body]': body, '[data-evaluation-message]': new FakeNode({ hidden: true }) };

  globalThis.document = {
    getElementById: id => (id === 'match-evaluation' ? root : null),
    dispatchEvent() { return true; }
  };
  globalThis.window = {
    PlayOnUI: { escapeHTML: value => String(value ?? '').replace(/[&<>"']/g, character => `&#${character.charCodeAt(0)};`) }
  };
  const requests = [];
  globalThis.fetch = (url, options) => {
    requests.push({ url, options });
    return Promise.resolve({
      ok: status === 200, redirected: false, status,
      json: () => Promise.resolve(options.method === 'POST' ? { message: '평가를 제출했습니다.' } : view)
    });
  };

  return { root, renders, requests, progressFilled, progressBarRole, progressFill, missingBox, firstMissingSlider };
}

function flush() {
  return new Promise(resolve => setTimeout(resolve, 0));
}

// 화면이 그린 슬라이더 칸 하나를 흉내 낸다. 슬라이더·숫자·못 봄 버튼이 한 칸(data-score-cell) 안에 있다.
function scoreCell(key, itemName) {
  const cell = new FakeNode();
  cell.classList.add('match-evaluation-score', 'is-empty');
  const slider = new FakeNode({ value: '5', disabled: false });
  slider.dataset = { key, itemName };
  const number = new FakeNode({ textContent: '–' });
  const skip = new FakeNode();
  skip.setAttribute('aria-pressed', 'false');
  slider.closest = selector => ({ 'input[data-key]': slider, '[data-score-cell]': cell })[selector] ?? null;
  skip.closest = selector => ({ '[data-skip]': skip, '[data-score-cell]': cell })[selector] ?? null;
  cell.bySelector = { 'input[data-key]': slider, '[data-score-value]': number, '[data-skip]': skip };
  return { cell, slider, number, skip };
}

function move(page, cell, value) {
  cell.slider.value = value;
  page.root.dispatchEvent({ type: 'input', target: cell.slider });
}

function tap(page, node) {
  page.root.dispatchEvent({ type: 'click', target: node });
}

function actionButton(action) {
  const node = new FakeNode();
  node.dataset = { action };
  node.closest = selector => (selector === '[data-action]' ? node : null);
  return node;
}

function fillButton(participantId) {
  const node = new FakeNode();
  node.dataset = { fill: String(participantId) };
  node.closest = selector => (selector === '[data-fill]' ? node : null);
  return node;
}

function loadedDetails(page) {
  return page.root.dispatched.filter(event => event.type === 'match-evaluation:loaded').map(event => event.detail);
}

async function open(page, name) {
  await import(`../../main/resources/static/js/match-evaluation.js?${name}`);
  page.root.dispatchEvent({ type: 'match-result:captain' });
  await flush();
  await flush();
}

/* ── 처음에는 모든 칸이 빈 칸이고, 슬라이더를 움직이면 표를 다시 그리지 않고 그 칸과 진행률만 바꾼다 ── */
{
  const page = evaluationPage({ view: VIEW });
  await open(page, 'progress');

  const first = page.renders[0];
  assert.equal(page.renders.length, 1);
  assert.match(first, /항목 <b data-progress-filled>0<\/b> \/ 4/, '가운데에 놓인 슬라이더는 고른 칸이 아닙니다.');
  assert.match(first, /data-action="review"/, '제출 버튼은 진행률 바에 있어야 합니다.');
  assert.equal(first.match(/data-action="review"/g).length, 1, '제출 버튼은 하나여야 합니다.');
  assert.equal(first.match(/type="range"/g).length, 4, '칸마다 슬라이더가 하나씩 있어야 합니다.');
  assert.doesNotMatch(first, /<select/, '선택 칸은 슬라이더로 바뀌었습니다.');
  assert.match(first, /class="match-evaluation-score is-empty" data-score-cell/);
  assert.match(first, /min="0" max="10" step="1"\s+value="5"/);
  assert.match(first, /aria-label="에이 패스" aria-valuetext="패스 점수 없음"/);
  assert.match(first, /data-score-value aria-hidden="true">–<\/span>/, '고르기 전 점수는 회색 "–"입니다.');
  assert.match(first, /aria-pressed="false"[^>]*>못 봄<\/button>/);
  assert.match(first, /모두 관찰 불가/, '선수 줄마다 한 번에 관찰 불가로 두는 버튼은 그대로입니다.');
  assert.match(first, /<p class="match-evaluation-guide">5 = 보통 · 7 = 눈에 띄게 잘함 · 3 = 눈에 띄게 아쉬움<\/p>\s*<section/,
    '점수 기준은 표 바로 위에 한 줄로 둡니다.');

  const pass = scoreCell('11:1', '패스');
  move(page, pass, '7');

  assert.equal(page.renders.length, 1, '슬라이더를 움직일 때 표를 다시 그리면 슬라이더 포커스를 잃습니다.');
  assert.equal(String(page.progressFilled.textContent), '1');
  assert.equal(page.progressBarRole.getAttribute('aria-valuenow'), '1');
  assert.equal(page.progressFill.style.width, '25%');
  assert.equal(pass.number.textContent, '7');
  assert.equal(pass.slider.getAttribute('aria-valuetext'), '패스 7점', '스크린 리더가 "패스 7점"으로 읽어야 합니다.');
  assert.ok(pass.cell.classList.contains('is-scored') && !pass.cell.classList.contains('is-empty'));
}

/* ── 처음 위치(5)를 움직이지 않고 탭·클릭만 해도 5점으로 채운다 ─────────── */
{
  const page = evaluationPage({ view: VIEW });
  await open(page, 'tap');

  const pass = scoreCell('11:1', '패스');
  tap(page, pass.slider);

  assert.equal(String(page.progressFilled.textContent), '1');
  assert.equal(pass.number.textContent, '5');
  assert.equal(pass.slider.getAttribute('aria-valuetext'), '패스 5점');
}

/* ── 진행률과 제출 검사는 같은 빈 칸 판정을 쓴다(0점·못 봄도 고른 칸, 못 봄을 끄면 다시 빈 칸) ── */
{
  const page = evaluationPage({ view: VIEW });
  await open(page, 'same-rule');

  move(page, scoreCell('11:1', '패스'), '0');
  const defense = scoreCell('11:2', '수비');
  tap(page, defense.skip);
  assert.equal(String(page.progressFilled.textContent), '2', '0점과 못 봄도 고른 칸으로 셉니다.');
  assert.equal(defense.skip.getAttribute('aria-pressed'), 'true');
  assert.equal(defense.slider.disabled, true, '못 봄인 칸의 슬라이더는 움직이지 않습니다.');
  assert.equal(defense.slider.getAttribute('aria-valuetext'), '수비 관찰 불가');
  assert.ok(defense.cell.classList.contains('is-skipped'));

  tap(page, actionButton('review'));
  const reviewed = page.renders.at(-1);
  assert.match(reviewed, /빈 칸이 2개 있습니다/, '제출 검사도 같은 두 칸을 비었다고 봐야 합니다.');
  assert.match(reviewed, /항목 <b data-progress-filled>2<\/b> \/ 4/);
  assert.match(reviewed, /class="match-evaluation-score is-empty is-missing" data-score-cell/);
  assert.equal(page.firstMissingSlider.focused, 1, '첫 빈 칸의 슬라이더로 포커스를 옮겨야 합니다.');

  move(page, scoreCell('21:1', '패스'), '5');
  assert.equal(String(page.progressFilled.textContent), '3');
  assert.match(page.missingBox.innerHTML, /빈 칸이 1개 있습니다/);

  tap(page, defense.skip);
  assert.equal(String(page.progressFilled.textContent), '2', '못 봄을 끈 칸은 점수를 되살리지 않고 빈 칸이 됩니다.');
  assert.equal(defense.skip.getAttribute('aria-pressed'), 'false');
  assert.equal(defense.slider.disabled, false);
  assert.equal(defense.slider.value, '5');
  assert.equal(defense.number.textContent, '–');
  assert.equal(defense.slider.getAttribute('aria-valuetext'), '수비 점수 없음');
}

/* ── "모두 관찰 불가"는 그 선수의 모든 칸을 못 봄으로 채운다 ────────────── */
{
  const page = evaluationPage({ view: VIEW });
  await open(page, 'fill');

  tap(page, fillButton(21));
  const filled = page.renders.at(-1);
  assert.match(filled, /항목 <b data-progress-filled>2<\/b> \/ 4/);
  assert.equal(filled.match(/aria-pressed="true"/g).length, 2);
  assert.match(filled, /aria-label="비 패스" aria-valuetext="패스 관찰 불가" disabled/);
}

/* ── 제출 형식은 그대로다: 점수는 숫자, 못 봄은 NOT_OBSERVED와 null ───────── */
{
  const page = evaluationPage({ view: VIEW });
  await open(page, 'payload');

  move(page, scoreCell('11:1', '패스'), '10');
  tap(page, scoreCell('11:2', '수비').skip);
  move(page, scoreCell('21:1', '패스'), '0');
  tap(page, scoreCell('21:2', '수비').slider);
  tap(page, actionButton('review'));
  tap(page, actionButton('submit'));
  await flush();
  await flush();

  const post = page.requests.find(request => request.options.method === 'POST');
  assert.ok(post, '확인 단계 뒤 제출해야 합니다.');
  assert.deepEqual(JSON.parse(post.options.body), {
    evaluations: [
      { targetParticipantId: 11, scores: [
        { itemId: 1, observationStatus: 'OBSERVED', score: 10 },
        { itemId: 2, observationStatus: 'NOT_OBSERVED', score: null }
      ] },
      { targetParticipantId: 21, scores: [
        { itemId: 1, observationStatus: 'OBSERVED', score: 0 },
        { itemId: 2, observationStatus: 'OBSERVED', score: 5 }
      ] }
    ]
  });
}

/* ── 제출 뒤에는 고정되지 않는 "평가 제출 완료" 바를 그린다 ───────────── */
{
  const page = evaluationPage({ view: { ...VIEW, submitted: true, canSubmit: false } });
  await open(page, 'submitted');

  assert.match(page.renders[0], /class="match-evaluation-progress is-done"/);
  assert.match(page.renders[0], /평가 제출 완료/);
  assert.match(page.renders[0], /제출한 평가는 고칠 수 없습니다/);
  assert.doesNotMatch(page.renders[0], /data-action="review"/, '제출한 뒤에는 제출 버튼이 없어야 합니다.');
  assert.deepEqual(loadedDetails(page), [{ submitted: true, canSubmit: false }]);
}

/* ── 조회할 때마다 결과 화면 단계 바에 평가 상태를 알리고, 실패하면 null을 보낸다 ── */
{
  const page = evaluationPage({ view: VIEW });
  await open(page, 'loaded');
  assert.deepEqual(loadedDetails(page), [{ submitted: false, canSubmit: true }]);

  const failed = evaluationPage({ view: { message: '평가 정보를 불러오지 못했습니다.' }, status: 500 });
  await open(failed, 'loaded-failed');
  assert.deepEqual(loadedDetails(failed), [null]);
}

console.log('PASS: evaluation sliders share the submit check, update in place, keep the payload, and report their state.');
