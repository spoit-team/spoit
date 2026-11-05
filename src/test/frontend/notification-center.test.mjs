// Dependency-free execution check: bun src/test/frontend/notification-center.test.mjs
// 통합 알림 스크립트가 API 응답으로 배지를 갱신하고, 실시간 수신에는 목록 재조회로 반응하며,
// 화면을 떠날 때 소켓·타이머를 정리하는지 확인합니다.
import assert from 'node:assert/strict';

globalThis.Element ??= class Element {};

class Node extends Element {
  constructor(properties = {}) {
    super();
    Object.assign(this, { hidden: false, disabled: false, textContent: '', id: '', dataset: {}, children: [] }, properties);
    this.attributes = {};
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
  removeAttribute(name) { delete this.attributes[name]; }
  getAttribute(name) { return this.attributes[name] ?? null; }
  hasAttribute(name) { return name in this.attributes; }
  dispatch(type, event = {}) {
    const payload = { target: this, defaultPrevented: false, preventDefault() { this.defaultPrevented = true; }, ...event };
    for (const handler of this.listeners[type] ?? []) handler(payload);
    return payload;
  }
  focus() { globalThis.document.activeElement = this; }
  closest(selector) { return this.matches(selector) ? this : null; }
  matches(selector) { return (this.selectors ?? []).includes(selector); }
  querySelector(selector) { return (this.bySelector?.[selector]) ?? null; }
  querySelectorAll(selector) { return (this.bySelectorAll?.[selector]) ?? []; }
  replaceChildren(...nodes) { this.children = nodes; }
  append(...nodes) { this.children.push(...nodes); }
  appendChild(node) { this.children.push(node); return node; }
}

// 스크립트가 instanceof HTMLFormElement로 폼을 구분하므로, 테스트 폼은 isForm 표식으로 판별한다.
globalThis.HTMLFormElement ??= class HTMLFormElement extends Element {
  static [Symbol.hasInstance](instance) { return Boolean(instance && instance.isForm); }
};

function buildItem() {
  const fields = {};
  const field = name => {
    const node = new Node();
    node.setAttribute('data-field', name);
    fields[name] = node;
    return node;
  };
  const link = field('link');
  const copy = field('copy');
  const type = field('type');
  const state = field('state');
  const title = field('title');
  const body = field('body');
  const time = field('time');
  const readButton = new Node();
  readButton.setAttribute('data-notification-action', 'read');
  const deleteButton = new Node();
  deleteButton.setAttribute('data-notification-action', 'delete');
  const remote = new Node();
  return {
    item: new Node(), fields, link, copy, type, state, title, body, time, readButton, deleteButton, remote
  };
}

function notificationPage(options = {}) {
  const calls = [];
  const sockets = [];
  const { apiResponse = { items: [], unreadCount: 0 }, authenticated = 'true' } = options;

  const config = new Node();
  config.setAttribute('data-notification-api', '/api/notifications');
  config.setAttribute('data-notification-stream', '/ws/notifications');
  config.setAttribute('data-notification-csrf-header', 'X-CSRF-TOKEN');
  config.setAttribute('data-notification-csrf-token', 'csrf-value');
  config.setAttribute('data-notification-authenticated', authenticated);

  const surface = new Node();
  const list = new Node();
  const template = new Node();
  const empty = new Node();
  const summary = new Node();
  summary.setAttribute('data-notification-summary', 'short');
  const readAllButton = new Node();

  const badge = new Node();
  badge.hidden = true;
  const opener = new Node();
  opener.setAttribute('data-notification-open', '');

  const dialog = new Node();
  dialog.hidden = true;
  const closeButton = new Node();
  closeButton.classes.add('playon-notification-close');
  const statusRegion = new Node();

  surface.bySelector = {
    '[data-notification-list]': list,
    'template[data-notification-item-template]': template,
    '[data-notification-empty]': empty
  };
  surface.bySelectorAll = {
    '[data-notification-summary]': [summary],
    '[data-notification-read-all]': [readAllButton]
  };
  dialog.bySelector = { '.playon-notification-close': closeButton };
  dialog.bySelectorAll = { 'button:not(:disabled):not([hidden]), [href], input:not(:disabled), [tabindex]:not([tabindex="-1"])': [closeButton] };

  const documentListeners = {};
  const document = {
    hidden: false,
    activeElement: null,
    body: new Node(),
    listeners: documentListeners,
    addEventListener(type, handler) { (documentListeners[type] ??= []).push(handler); },
    dispatch(type, event = {}) {
      const payload = { target: options.target ?? dialog, defaultPrevented: false, preventDefault() { this.defaultPrevented = true; }, ...event };
      for (const handler of documentListeners[type] ?? []) handler(payload);
      return payload;
    },
    querySelector(selector) {
      if (selector === '[data-notification-config]') return config;
      if (selector === '.playon-notification-backdrop[data-notification-dialog]') return dialog;
      if (selector === '[data-notification-status]') return statusRegion;
      return null;
    },
    querySelectorAll(selector) {
      if (selector === '[data-notification-surface]') return [surface];
      if (selector === '[data-notification-badge]') return [badge];
      if (selector === '[data-notification-open]') return [opener];
      return [];
    }
  };

  const windowListeners = {};
  const timers = { intervals: [], timeouts: [], cleared: [] };
  const window = {
    location: { href: 'http://localhost/mypage', host: 'localhost', pathname: '/mypage' },
    listeners: windowListeners,
    addEventListener(type, handler) { (windowListeners[type] ??= []).push(handler); },
    dispatch(type, event = {}) {
      for (const handler of windowListeners[type] ?? []) handler(event);
    },
    setTimeout(handler, delay) { timers.timeouts.push({ handler, delay }); return timers.timeouts.length; },
    clearTimeout(id) { timers.cleared.push(id); },
    setInterval(handler, delay) { timers.intervals.push({ handler, delay }); return timers.intervals.length; },
    clearInterval(id) { timers.cleared.push('interval:' + id); },
    fetch(url, init) {
      const call = { url, init, method: (init && init.method) || 'GET' };
      calls.push(call);
      const intercepted = options.onFetch?.(call);
      if (intercepted) return intercepted;
      return Promise.resolve({
        ok: true,
        redirected: false,
        status: 200,
        json: () => Promise.resolve(typeof apiResponse === 'function' ? apiResponse() : apiResponse)
      });
    },
    WebSocket: class FakeWebSocket {
      constructor(url) {
        this.url = url;
        this.listeners = {};
        this.sent = [];
        this.readyState = 0;
        sockets.push(this);
      }
      addEventListener(type, handler) { (this.listeners[type] ??= []).push(handler); }
      send(data) { this.sent.push(data); }
      close() { this.readyState = 3; this.dispatch('close'); }
      open() { this.readyState = 1; this.dispatch('open'); }
      message(data) { this.dispatch('message', { data }); }
      error() { this.dispatch('error'); }
      dispatch(type, event = {}) {
        for (const handler of this.listeners[type] ?? []) handler({ target: this, ...event });
      }
    }
  };

  return { config, surface, list, template, empty, summary, readAllButton, badge, opener, dialog, closeButton, statusRegion, document, window, calls, sockets, timers };
}

function flush() {
  return new Promise(resolve => setTimeout(resolve, 0));
}

/* ── 1) 비로그인: 조회도 소켓도 열지 않는다 ─────────────────────────── */
{
  const page = notificationPage({ authenticated: 'false' });
  globalThis.document = page.document;
  globalThis.window = page.window;
  await import('../../main/resources/static/js/notification-center.js?guest');
  await flush();
  assert.equal(page.calls.length, 0, '비로그인 사용자는 알림 API를 호출하지 않아야 합니다.');
  assert.equal(page.sockets.length, 0, '비로그인 사용자는 웹소켓을 열지 않아야 합니다.');
}

/* ── 2) 로그인: API로 배지를 갱신하고 CONNECT → SUBSCRIBE 순서로 구독한다 ── */
{
  const page = notificationPage({
    apiResponse: {
      unreadCount: 2,
      items: [
        { notificationId: 11, read: false, type: '매칭', title: '신청이 접수되었습니다', body: '본문', time: '방금', href: '/mypage#reservations' },
        { notificationId: 10, read: true, type: '팀', title: '가입 승인', body: '본문', time: '1시간 전', href: 'https://evil.example/x' }
      ]
    }
  });
  globalThis.document = page.document;
  globalThis.window = page.window;

  const clones = [];
  const fixture = buildItem();
  page.template.content = { firstElementChild: fixture.item };
  const fieldsFor = fixtureRef => selector => {
    const match = /^\[data-field="(.+)"\]$/.exec(selector);
    if (match) return fixtureRef.fields[match[1]] || null;
    if (selector === '[data-notification-action="read"]') return fixtureRef.readButton;
    if (selector === '[data-notification-action="delete"]') return fixtureRef.deleteButton;
    return null;
  };
  fixture.item.querySelector = fieldsFor(fixture);
  fixture.link.querySelector = selector => selector === '[data-field="copy"]' ? fixture.copy : null;
  fixture.item.cloneNode = () => {
    const clone = buildItem();
    clone.item.querySelector = fieldsFor(clone);
    clone.link.querySelector = selector => selector === '[data-field="copy"]' ? clone.copy : null;
    clones.push(clone);
    return clone.item;
  };

  await import('../../main/resources/static/js/notification-center.js?member');
  await flush();
  await flush();

  const apiCalls = page.calls.filter(call => call.url === '/api/notifications');
  assert.ok(apiCalls.length >= 1, '로그인 사용자는 알림 목록을 조회해야 합니다.');
  assert.equal(apiCalls[0].init.credentials, 'same-origin');
  assert.equal(page.badge.hidden, false);
  assert.equal(page.badge.textContent, '2', '배지는 읽지 않은 수를 보여야 합니다.');
  assert.match(page.opener.getAttribute('aria-label'), /새 알림 2건/);

  assert.equal(page.sockets.length, 1, '로그인 사용자는 웹소켓을 한 번 엽니다.');
  const socket = page.sockets[0];
  assert.equal(socket.url, 'ws://localhost/ws/notifications', 'https가 아니면 ws로 접속해야 합니다.');

  socket.open();
  const connectFrame = socket.sent[0];
  assert.match(connectFrame, /^CONNECT\n/);
  assert.match(connectFrame, /X-CSRF-TOKEN:csrf-value/, 'CONNECT에 CSRF 헤더를 실어야 합니다.');
  assert.ok(!socket.sent.some(frame => frame.startsWith('SUBSCRIBE')), 'CONNECTED 전에는 구독하지 않습니다.');

  socket.message('CONNECTED\nversion:1.2\n\n\u0000');
  const subscribeFrame = socket.sent.find(frame => frame.startsWith('SUBSCRIBE'));
  assert.ok(subscribeFrame, 'CONNECTED 뒤에 구독해야 합니다.');
  assert.match(subscribeFrame, /destination:\/user\/queue\/notifications/);

  const before = page.calls.filter(call => call.url === '/api/notifications').length;
  socket.message('MESSAGE\ndestination:\/user\/queue\/notifications\n\n{"title":"<img src=x onerror=alert(1)>"}\u0000');
  await flush();
  await flush();
  const after = page.calls.filter(call => call.url === '/api/notifications').length;
  assert.ok(after > before, 'MESSAGE를 받으면 목록을 다시 조회해야 합니다.');
  // 수신 payload는 화면에 그대로 그리지 않는다.
  assert.ok(!page.list.children.some(child => JSON.stringify(child).includes('onerror')));

  // 이동 주소가 상대 경로가 아니면 링크를 만들지 않는다.
  assert.equal(clones[1]?.link.getAttribute('href') ?? null, null,
    '외부 출처 알림 주소는 링크로 만들지 않아야 합니다.');
  assert.equal(clones[0]?.link.getAttribute('href'), '/mypage#reservations');

  // 화면을 숨기면 소켓을 닫고 주기 조회를 멈춘다.
  page.document.hidden = true;
  page.document.dispatch('visibilitychange');
  assert.equal(socket.readyState, 3, '문서가 숨겨지면 소켓을 닫아야 합니다.');
  assert.ok(!page.sockets.some(item => item.readyState === 1), '숨김 상태에서 새 연결을 열지 않아야 합니다.');

  // 다시 보이면 재조회하고 재연결한다.
  page.document.hidden = false;
  page.document.dispatch('visibilitychange');
  await flush();
  assert.ok(page.sockets.length >= 2, '화면으로 돌아오면 다시 연결해야 합니다.');
  assert.ok(page.calls.filter(call => call.url === '/api/notifications').length > after,
    '화면으로 돌아오면 목록을 다시 조회해야 합니다.');

  // 페이지를 떠나면 정리한다.
  page.window.dispatch('pagehide');
  assert.ok(page.sockets.every(item => item.readyState === 3), '페이지를 떠나면 모든 소켓을 닫아야 합니다.');

  const beforeRestore = page.calls.length;
  page.window.dispatch('pageshow', { persisted: true });
  await flush();
  assert.equal(page.sockets.length, 3, '캐시에서 복원하면 소켓을 다시 연결해야 합니다.');
  assert.ok(page.calls.length > beforeRestore, '캐시에서 복원하면 서버 상태를 다시 조회해야 합니다.');
}

/* 명령이 끝난 뒤 이전 조회가 완료되어도 최종 화면은 명령 이후 서버 상태를 따른다. */
{
  let snapshot = { items: [{ notificationId: 7, read: false }], unreadCount: 1 };
  let resolveOld;
  let holdNextGet = false;
  const ok = { ok: true, redirected: false };
  const page = notificationPage({
    apiResponse: () => snapshot,
    onFetch(call) {
      if (call.method === 'GET' && holdNextGet) {
        holdNextGet = false;
        const oldSnapshot = snapshot;
        return new Promise(resolve => {
          resolveOld = () => resolve({ ...ok, json: () => Promise.resolve(oldSnapshot) });
        });
      }
      if (call.url === '/api/notifications/7/delete') {
        snapshot = { items: [], unreadCount: 0 };
      }
      return null;
    }
  });
  globalThis.document = page.document;
  globalThis.window = page.window;
  await import('../../main/resources/static/js/notification-center.js?delete-race');
  await flush();
  holdNextGet = true;
  page.window.dispatch('focus');
  const button = new Node();
  button.setAttribute('data-notification-action', 'delete');
  button.setAttribute('data-notification-id', '7');
  button.selectors = ['button[data-notification-action]'];
  page.document.dispatch('click', { target: button });
  await flush();
  resolveOld();
  await flush();
  await flush();
  assert.equal(page.badge.hidden, true, '마지막 미확인 알림을 삭제하면 배지가 사라져야 합니다.');
  assert.equal(page.readAllButton.disabled, true, '마지막 미확인 알림을 삭제하면 모두 읽음이 비활성화되어야 합니다.');
}

/* 캐시에 들어갈 때 진행 중이던 조회는 복원 후 새 조회를 막지 않는다. */
{
  let snapshot = { items: [], unreadCount: 1 };
  let resolveOld;
  let holdNextGet = false;
  const page = notificationPage({
    apiResponse: () => snapshot,
    onFetch(call) {
      if (call.method === 'GET' && holdNextGet) {
        holdNextGet = false;
        const oldSnapshot = snapshot;
        return new Promise(resolve => {
          resolveOld = () => resolve({ ok: true, redirected: false, json: () => Promise.resolve(oldSnapshot) });
        });
      }
      return null;
    }
  });
  globalThis.document = page.document;
  globalThis.window = page.window;
  await import('../../main/resources/static/js/notification-center.js?cache-race');
  await flush();
  holdNextGet = true;
  page.window.dispatch('focus');
  page.window.dispatch('pagehide', { persisted: true });
  snapshot = { items: [], unreadCount: 2 };
  page.window.dispatch('pageshow', { persisted: true });
  await flush();
  assert.equal(page.badge.textContent, '2', '이전 조회가 대기 중이어도 복원 후 새 상태를 불러와야 합니다.');
  resolveOld();
  await flush();
  assert.equal(page.badge.textContent, '2', '복원 전에 시작한 조회가 새 상태를 덮으면 안 됩니다.');
}

/* 모두 읽음 요청 중 도착한 알림은 서버가 반환한 미확인 상태를 유지한다. */
{
  let snapshot = { items: [{ notificationId: 7, read: false }], unreadCount: 1 };
  let finishReadAll;
  const page = notificationPage({
    apiResponse: () => snapshot,
    onFetch(call) {
      if (call.url === '/api/notifications/read-all') {
        return new Promise(resolve => { finishReadAll = resolve; });
      }
      return null;
    }
  });
  globalThis.document = page.document;
  globalThis.window = page.window;
  await import('../../main/resources/static/js/notification-center.js?read-all-race');
  await flush();
  const button = new Node();
  button.setAttribute('data-notification-read-all', '');
  button.selectors = ['[data-notification-read-all]'];
  page.document.dispatch('click', { target: button });
  snapshot = { items: [{ notificationId: 8, read: false }, { notificationId: 7, read: true }], unreadCount: 1 };
  page.window.dispatch('focus');
  await flush();
  finishReadAll({ ok: true, redirected: false });
  await flush();
  await flush();
  assert.equal(page.badge.textContent, '1', '요청 중 도착한 미확인 알림은 배지에 남아야 합니다.');
  assert.equal(page.readAllButton.disabled, false);
}

/* ── 3) 읽음·삭제·모두 읽음은 API로 나가고 실패하면 재조회한다 ───────── */
{
  const page = notificationPage({
    apiResponse: {
      unreadCount: 1,
      items: [{ notificationId: 7, read: false, type: '매칭', title: '제목', body: '본문', time: '방금', href: '/mypage' }]
    }
  });
  globalThis.document = page.document;
  globalThis.window = page.window;

  const readButton = new Node();
  readButton.setAttribute('data-notification-action', 'read');
  readButton.setAttribute('data-notification-id', '7');
  readButton.selectors = ['button[data-notification-action]'];
  const deleteButton = new Node();
  deleteButton.setAttribute('data-notification-action', 'delete');
  deleteButton.setAttribute('data-notification-id', '7');
  deleteButton.selectors = ['button[data-notification-action]'];

  await import('../../main/resources/static/js/notification-center.js?actions');
  await flush();
  await flush();

  const readAll = new Node();
  readAll.setAttribute('data-notification-read-all', '');
  readAll.selectors = ['[data-notification-read-all]'];
  page.document.dispatch('click', { target: readAll });
  await flush();
  assert.ok(page.calls.some(call => call.url === '/api/notifications/read-all'),
    '모두 읽음은 POST /api/notifications/read-all로 보내야 합니다.');

  let event = page.document.dispatch('click', { target: readButton });
  assert.equal(event.defaultPrevented, true);
  assert.equal(readButton.closest('form'), null);
  await flush();
  const readCall = page.calls.find(call => call.url === '/api/notifications/7/read');
  assert.ok(readCall, '읽음은 POST /api/notifications/{id}/read로 보내야 합니다.');
  assert.equal(readCall.init.headers['X-CSRF-TOKEN'], 'csrf-value');

  page.document.dispatch('click', { target: deleteButton });
  await flush();
  assert.ok(page.calls.some(call => call.url === '/api/notifications/7/delete'),
    '삭제는 POST /api/notifications/{id}/delete로 보내야 합니다.');
}

/* ── 4) 서버 폼은 JS 없이도 남고, JS가 있으면 같은 일을 API로 처리한다 ── */
{
  const page = notificationPage({
    apiResponse: { items: [{ notificationId: 3, read: false, type: '알림', title: '제목', body: '본문', time: '방금', href: '/mypage' }], unreadCount: 1 }
  });
  globalThis.document = page.document;
  globalThis.window = page.window;
  await import('../../main/resources/static/js/notification-center.js?forms');
  await flush();

  const form = new Node({ isForm: true });
  form.attributes.action = '/mypage/notifications/read-all';
  const button = new Node();
  button.setAttribute('data-notification-read-all', '');
  form.querySelector = selector => selector === '[data-notification-read-all]' ? button : null;

  const event = page.document.dispatch('submit', { target: form });
  assert.equal(event.defaultPrevented, true, '모두 읽음 폼은 API 경로로 가로채야 합니다.');
  await flush();
  assert.ok(page.calls.some(call => call.url === '/api/notifications/read-all'));

  const other = new Node({ isForm: true });
  other.attributes.action = '/mypage/applications/1/cancel';
  other.querySelector = () => null;
  const untouched = page.document.dispatch('submit', { target: other });
  assert.equal(untouched.defaultPrevented, false, '알림과 무관한 폼은 건드리지 않아야 합니다.');
}

console.log('PASS: notification center updates badges, re-reads on MESSAGE, and releases the socket.');
