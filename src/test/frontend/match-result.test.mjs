// Dependency-free execution check: bun src/test/frontend/match-result.test.mjs
// 경기 결과 화면이 확정 참가자에게는 요약 API만, 팀장에게는 제출 영역을 열어 주는지 확인합니다.
import assert from 'node:assert/strict';

globalThis.Element ??= class Element {};

class SyNode extends Element {
  constructor(properties = {}) {
    super();
    Object.assign(this, {
      hidden: false, disabled: false, textContent: '', type: '', className: '', id: ''
    }, properties);
    this.attributes = {};
    this.children = [];
    this.classes = new Set();
    this.listeners = {};
    this.classList = {
      add: name => this.classes.add(name),
      remove: name => this.classes.delete(name),
      contains: name => this.classes.has(name),
      toggle: (name, active) => active ? this.classes.add(name) : this.classes.delete(name)
    };
  }
  addEventListener(type, handler) { (this.listeners[type] ??= []).push(handler); }
  setAttribute(name, value) { this.attributes[name] = String(value); }
  getAttribute(name) { return this.attributes[name] ?? null; }
  removeAttribute(name) { delete this.attributes[name]; }
  focus() {}
  closest(selector) {
    if (selector === '[data-result]') return this.result ? this : null;
    if (selector === '[data-action]') return this.action ? this : null;
    return null;
  }
  querySelector(selector) { return (this.bySelector?.[selector]) ?? null; }
  querySelectorAll(selector) { return (this.bySelectorAll?.[selector]) ?? []; }
  replaceChildren(...nodes) { this.children = nodes; }
  append(...nodes) { this.children.push(...nodes); }
  appendChild(node) { this.children.push(node); return node; }
  dispatch(type, event = {}) {
    const payload = { target: this, defaultPrevented: false, preventDefault() { this.defaultPrevented = true; }, ...event };
    for (const handler of this.listeners[type] ?? []) handler(payload);
    return payload;
  }
}

function hasClass(node, name) {
  return (node.className || '').split(/\s+/).includes(name) || node.classes.has(name);
}

function textOf(node) {
  if (!node) return '';
  return String(node.textContent || '') + node.children.map(textOf).join(' ');
}

function resultPage(options = {}) {
  const { summary, summaryStatus = () => 200, captain, captainStatus = 200 } = options;
  const calls = [];
  const fields = {};
  const field = name => {
    const node = new SyNode();
    fields[name] = node;
    return node;
  };
  const summarySection = new SyNode({ hidden: true });
  const stateBody = new SyNode();
  const stateMessage = new SyNode({ hidden: true });
  const submitSection = new SyNode({ hidden: true });
  const submitBody = new SyNode();
  const submitMessage = new SyNode({ hidden: true });
  const teamsContainer = new SyNode();

  ['matchId', 'side', 'resultStatus', 'ratingStatus', 'title', 'sport', 'schedule', 'facility', 'deadline'].forEach(field);

  const root = new SyNode();
  root.setAttribute('data-api-base', '/mypage/matches');
  root.setAttribute('data-match-id', '42');
  root.setAttribute('data-csrf-header', 'X-CSRF-TOKEN');
  root.setAttribute('data-csrf-token', 'csrf-value');
  root.bySelector = {
    '[data-result-summary]': summarySection,
    '[data-result-body]': stateBody,
    '[data-result-message]': stateMessage,
    '[data-result-submit]': submitSection,
    '[data-result-submit-body]': submitBody,
    '[data-result-submit-message]': submitMessage,
    '[data-result-teams]': teamsContainer
  };
  root.bySelectorAll = {
    '[data-field="matchId"]': [fields.matchId],
    '[data-field="side"]': [fields.side],
    '[data-field="resultStatus"]': [fields.resultStatus],
    '[data-field="ratingStatus"]': [fields.ratingStatus],
    '[data-field="title"]': [fields.title],
    '[data-field="sport"]': [fields.sport],
    '[data-field="schedule"]': [fields.schedule],
    '[data-field="facility"]': [fields.facility],
    '[data-field="deadline"]': [fields.deadline]
  };

  const document = {
    hidden: false,
    activeElement: null,
    addEventListener() {},
    createElement(tagName) {
      const node = new SyNode();
      node.tagName = String(tagName).toUpperCase();
      return node;
    },
    querySelector(selector) { return selector === '[data-result-root]' ? root : null; }
  };

  const window = {
    location: { pathname: '/mypage/matches/42/result-page', href: 'http://localhost/mypage/matches/42/result-page' },
    fetch(url, init) {
      calls.push({ url, method: (init && init.method) || 'GET', init });
      if (url.endsWith('/result/summary')) {
        const status = summaryStatus();
        return Promise.resolve({
          ok: status === 200, redirected: false, status,
          json: () => Promise.resolve(summary)
        });
      }
      const status = typeof captainStatus === 'function' ? captainStatus() : captainStatus;
      return Promise.resolve({
        ok: status === 200, redirected: false, status,
        json: () => Promise.resolve(captain)
      });
    }
  };

  return {
    root, fields, summarySection, stateBody, stateMessage,
    submitSection, submitBody, submitMessage, teamsContainer, document, window, calls
  };
}

function flush() {
  return new Promise(resolve => setTimeout(resolve, 0));
}

/* ── 1) 일반 참가자: 요약만 읽고 팀장 제출 영역은 403으로 감춘다 ───────── */
{
  const page = resultPage({
    summary: {
      matchId: 42, status: 'CLOSED', resultStatus: 'CONFIRMED', ratingStatus: 'APPLIED',
      finalResult: 'A_WIN', mySide: 'A', myResult: null,
      title: '토요 풋살', sportName: '풋살',
      scheduleLabel: '2026년 10월 30일 (금) 18:00 ~ 20:00',
      facilityName: 'MAT 테스트 시설',
      deadlineLabel: '2026년 10월 31일 (토) 20:00',
      teams: [
        { side: 'A', mine: true, members: [{ nickname: '에이스', captain: true }, { nickname: '가나', captain: false }] },
        { side: 'B', mine: false, members: [{ nickname: '베스트', captain: true }] }
      ]
    },
    captain: { message: '이 경기의 팀장만 결과를 제출할 수 있습니다.' },
    captainStatus: 403
  });
  globalThis.document = page.document;
  globalThis.window = page.window;

  await import('../../main/resources/static/js/match-result.js?participant');
  await flush();
  await flush();

  assert.deepEqual(page.calls.map(call => call.url), [
    '/mypage/matches/42/result/summary',
    '/mypage/matches/42/result'
  ], '확정 참가자는 요약 API를 먼저, 팀장 전용 조회는 그다음에 시도해야 합니다.');

  assert.equal(page.summarySection.hidden, false, '요약 카드가 보여야 합니다.');
  assert.equal(page.fields.ratingStatus.textContent, '레이팅 반영 완료');
  assert.equal(page.fields.side.textContent, 'A팀');
  assert.equal(page.fields.title.textContent, '토요 풋살');
  assert.equal(page.fields.sport.textContent, '풋살');
  assert.equal(page.fields.schedule.textContent, '2026년 10월 30일 (금) 18:00 ~ 20:00');
  assert.equal(page.fields.facility.textContent, 'MAT 테스트 시설');
  assert.equal(page.fields.deadline.textContent, '2026년 10월 31일 (토) 20:00');
  assert.equal(page.submitSection.hidden, true, '팀장이 아니면 제출 영역을 감춰야 합니다.');
  assert.equal(page.stateMessage.hidden, true, '팀장이 아닌 것은 오류가 아니므로 안내를 띄우지 않습니다.');
  assert.match(textOf(page.stateBody), /최종 결과/);

  // A/B팀 로스터는 요약 API의 확정 참가자 닉네임으로만 그린다.
  assert.equal(page.teamsContainer.children.length, 2, 'A/B 두 팀 로스터를 그려야 합니다.');
  const [teamA, teamB] = page.teamsContainer.children;
  assert.ok(hasClass(teamA, 'is-mine'), '내 진영 팀에 is-mine을 붙여야 합니다.');
  assert.ok(!hasClass(teamB, 'is-mine'));
  assert.match(textOf(teamA), /A팀/);
  assert.match(textOf(teamA), /에이스/);
  assert.match(textOf(teamA), /팀장/);
  assert.match(textOf(teamA), /가나/);
  assert.match(textOf(teamB), /베스트/);
}

/* ── 2) 팀장: 제출 선택지를 보여 주고 CSRF 헤더로 제출한다 ───────────── */
{
  let canViewResult = true;
  const viewStatus = () => canViewResult ? 200 : 403;
  const page = resultPage({
    summary: {
      matchId: 42, status: 'MATCHED', resultStatus: 'SUBMITTED', ratingStatus: 'WAITING',
      finalResult: null, mySide: 'B', myResult: 'B_WIN'
    },
    captain: {
      matchId: 42, mySide: 'B', myResult: null, opponentSubmitted: true, conflict: false,
      opponentResult: null, finalResult: null, ratingStatus: 'WAITING', deadlineAt: null, canSubmit: true
    },
    summaryStatus: viewStatus,
    captainStatus: viewStatus
  });
  globalThis.document = page.document;
  globalThis.window = page.window;

  await import('../../main/resources/static/js/match-result.js?captain');
  await flush();
  await flush();

  assert.equal(page.submitSection.hidden, false, '팀장에게는 제출 영역을 열어야 합니다.');

  const choices = page.submitBody.children.find(child => hasClass(child, 'match-result-choices'));
  assert.ok(choices, '제출 가능한 팀장에게 결과 선택지를 보여 줘야 합니다.');
  assert.equal(choices.children.length, 3);

  const win = choices.children.find(child => child.getAttribute('data-result') === 'B_WIN');
  win.result = true;
  page.root.dispatch('click', { target: win });

  const confirm = page.submitBody.children.find(child => hasClass(child, 'match-result-confirm'));
  assert.ok(confirm, '고르면 제출 확인 단계를 보여 줘야 합니다.');

  const actions = confirm.children.find(child => hasClass(child, 'match-result-confirm-actions'));
  const submit = actions.children.find(child => child.getAttribute('data-action') === 'submit');
  submit.action = true;
  page.root.dispatch('click', { target: submit });
  // 제출 요청과 상태 재조회 사이에 열람 권한이 바뀌었다.
  canViewResult = false;
  await flush();
  await flush();

  const post = page.calls.find(call => call.method === 'POST');
  assert.ok(post, '확인 뒤에는 팀장 전용 결과 API로 제출해야 합니다.');
  assert.equal(post.url, '/mypage/matches/42/result');
  assert.deepEqual(JSON.parse(post.init.body), { result: 'B_WIN' });
  assert.equal(post.init.headers['X-CSRF-TOKEN'], 'csrf-value');
  assert.equal(page.summarySection.hidden, true, '403 재조회 뒤 이전 요약이 보이면 안 됩니다.');
  assert.equal(page.submitSection.hidden, true, '403 재조회 뒤 제출 영역을 숨겨야 합니다.');
}

/* ── 3) 결과가 없으면 빈 상태를 꾸며 내지 않고 서버 문구를 그대로 쓴다 ── */
{
  const page = resultPage({
    summary: { message: '경기 정보를 찾을 수 없습니다.' },
    captain: { message: '경기 정보를 찾을 수 없습니다.' },
    captainStatus: 404
  });
  globalThis.document = page.document;
  globalThis.window = page.window;

  await import('../../main/resources/static/js/match-result.js?missing');
  await flush();
  await flush();

  assert.equal(page.summarySection.hidden, true, '요약이 없으면 카드를 펼치지 않습니다.');
  assert.equal(page.stateMessage.hidden, false);
  assert.equal(page.stateMessage.textContent, '경기 정보를 찾을 수 없습니다.');
  assert.equal(page.stateMessage.classes.has('is-error'), true);
  assert.equal(page.submitSection.hidden, true);
}

/* 팀장 조회가 실패하면 권한을 추측하지 않고 재시도할 수 있게 한다. */
{
  let status = 500;
  const page = resultPage({
    summary: { matchId: 42, resultStatus: 'SUBMITTED', ratingStatus: 'WAITING', mySide: 'B' },
    captain: { matchId: 42, mySide: 'B', ratingStatus: 'WAITING', canSubmit: true },
    captainStatus: () => status
  });
  globalThis.document = page.document;
  globalThis.window = page.window;

  await import('../../main/resources/static/js/match-result.js?retry-captain');
  await flush();

  assert.equal(page.submitSection.hidden, false);
  assert.match(textOf(page.submitBody), /제출 상태를 불러오지 못했습니다/);
  assert.ok(!page.submitBody.children.some(child => hasClass(child, 'match-result-choices')));
  const retry = page.submitBody.children.find(child => child.textContent === '다시 시도');
  assert.ok(retry);

  status = 200;
  retry.dispatch('click');
  await flush();

  assert.ok(page.submitBody.children.some(child => hasClass(child, 'match-result-choices')));
  assert.doesNotMatch(textOf(page.submitBody), /제출 상태를 불러오지 못했습니다/);
}

/* 결과가 없는 레이팅 제외 경기에서 제외 원인을 추측하지 않는다. */
{
  const page = resultPage({
    summary: { matchId: 42, resultStatus: 'CONFLICT', ratingStatus: 'EXCLUDED', mySide: 'A' },
    captainStatus: 403
  });
  globalThis.document = page.document;
  globalThis.window = page.window;

  await import('../../main/resources/static/js/match-result.js?excluded');
  await flush();

  assert.match(textOf(page.stateBody), /레이팅 반영에서 제외된 경기/);
  assert.doesNotMatch(textOf(page.stateBody), /마감/);
}

console.log('PASS: result screen keeps the summary read-only for participants and gates submission to captains.');
