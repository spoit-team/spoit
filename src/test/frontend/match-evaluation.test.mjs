// Dependency-free execution check: bun src/test/frontend/match-evaluation.test.mjs
// 선수 평가 영역이 진행률을 제출 검사와 같은 빈 칸 기준으로 세고, 칸을 고를 때 표를 다시 그리지 않는지 확인합니다.
import assert from 'node:assert/strict';

class FakeNode {
  constructor(properties = {}) {
    Object.assign(this, { hidden: false, textContent: '', value: '' }, properties);
    this.attributes = {};
    this.style = {};
    this.listeners = {};
    this.dispatched = [];
    this.bySelector = {};
    this.classList = { add() {}, remove() {}, toggle() {} };
  }
  setAttribute(name, value) { this.attributes[name] = String(value); }
  getAttribute(name) { return this.attributes[name] ?? null; }
  removeAttribute(name) { delete this.attributes[name]; }
  querySelector(selector) { return this.bySelector[selector] ?? null; }
  addEventListener(type, handler) { (this.listeners[type] ??= []).push(handler); }
  dispatchEvent(event) {
    this.dispatched.push(event);
    for (const handler of this.listeners[event.type] ?? []) handler(event);
    return true;
  }
  focus() {}
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

  // 표 전체를 다시 그린 횟수는 innerHTML 대입 횟수로 센다.
  const renders = [];
  const body = new FakeNode();
  Object.defineProperty(body, 'innerHTML', {
    get() { return renders.at(-1) ?? ''; },
    set(html) { renders.push(html); }
  });
  body.bySelector = { '[data-evaluation-progress]': progress, '[data-evaluation-missing]': missingBox };

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
  globalThis.fetch = () => Promise.resolve({
    ok: status === 200, redirected: false, status,
    json: () => Promise.resolve(view)
  });

  return { root, renders, progressFilled, progressBarRole, progressFill, missingBox };
}

function flush() {
  return new Promise(resolve => setTimeout(resolve, 0));
}

function select(key, value) {
  const node = new FakeNode({ value });
  node.dataset = { key };
  node.closest = selector => (selector === 'select[data-key]' ? node : null);
  return node;
}

function actionButton(action) {
  const node = new FakeNode();
  node.dataset = { action };
  node.closest = selector => (selector === '[data-action]' ? node : null);
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

/* ── 칸을 고르면 표를 다시 그리지 않고 숫자와 막대만 바꾼다 ───────────────── */
{
  const page = evaluationPage({ view: VIEW });
  await open(page, 'progress');

  assert.equal(page.renders.length, 1);
  assert.match(page.renders[0], /항목 <b data-progress-filled>0<\/b> \/ 4/);
  assert.match(page.renders[0], /data-action="review"/, '제출 버튼은 진행률 바에 있어야 합니다.');
  assert.equal(page.renders[0].match(/data-action="review"/g).length, 1, '제출 버튼은 하나여야 합니다.');

  page.root.dispatchEvent({ type: 'change', target: select('11:1', '7') });

  assert.equal(page.renders.length, 1, '칸을 고를 때 표를 다시 그리면 선택 칸 포커스를 잃습니다.');
  assert.equal(String(page.progressFilled.textContent), '1');
  assert.equal(page.progressBarRole.getAttribute('aria-valuenow'), '1');
  assert.equal(page.progressFill.style.width, '25%');
}

/* ── 진행률과 제출 검사는 같은 빈 칸 판정을 쓴다(0점·관찰 불가도 고른 칸) ── */
{
  const page = evaluationPage({ view: VIEW });
  await open(page, 'same-rule');

  page.root.dispatchEvent({ type: 'change', target: select('11:1', '0') });
  page.root.dispatchEvent({ type: 'change', target: select('11:2', 'NOT_OBSERVED') });
  assert.equal(String(page.progressFilled.textContent), '2', '0점과 관찰 불가도 고른 칸으로 셉니다.');

  page.root.dispatchEvent({ type: 'click', target: actionButton('review') });
  const reviewed = page.renders.at(-1);
  assert.match(reviewed, /빈 칸이 2개 있습니다/, '제출 검사도 같은 두 칸을 비었다고 봐야 합니다.');
  assert.match(reviewed, /항목 <b data-progress-filled>2<\/b> \/ 4/);

  page.root.dispatchEvent({ type: 'change', target: select('21:1', '5') });
  assert.equal(String(page.progressFilled.textContent), '3');
  assert.match(page.missingBox.innerHTML, /빈 칸이 1개 있습니다/);

  page.root.dispatchEvent({ type: 'change', target: select('21:1', '') });
  assert.equal(String(page.progressFilled.textContent), '2', '"선택"으로 되돌린 칸은 다시 빈 칸입니다.');
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

console.log('PASS: evaluation progress shares the submit check, updates in place, and reports its state.');
