// Dependency-free execution check: bun src/test/frontend/ui-feedback.test.mjs
// 공용 결과 안내(ui-feedback.js): 성공은 오른쪽 아래 토스트, 실패는 제목 + 원인을 담은 안내 모달.
// 서버 flash 읽기, 새로고침 뒤 한 번만 보여 주는 토스트, 자동 사라짐, Esc·초점 복귀를 확인합니다.
import assert from 'node:assert/strict';

globalThis.Element ??= class Element {};

class Node extends Element {
  constructor(tag, document) {
    super();
    this.tagName = tag.toUpperCase();
    this.ownerDocument = document;
    this.attributes = new Map();
    this.children = [];
    this.parent = null;
    this.listeners = {};
    this.classes = new Set();
    this.style = { values: {}, setProperty(name, value) { this.values[name] = value; } };
    this.textContent = '';
    this.hidden = false;
    this.open = false;
    this.classList = {
      add: name => this.classes.add(name),
      remove: name => this.classes.delete(name),
      contains: name => this.classes.has(name)
    };
  }
  set className(value) { value.split(/\s+/).filter(Boolean).forEach(name => this.classes.add(name)); }
  get className() { return [...this.classes].join(' '); }
  set id(value) { this.setAttribute('id', value); }
  get id() { return this.getAttribute('id'); }
  setAttribute(name, value) { this.attributes.set(name, String(value)); }
  getAttribute(name) { return this.attributes.has(name) ? this.attributes.get(name) : null; }
  removeAttribute(name) { this.attributes.delete(name); }
  hasAttribute(name) { return this.attributes.has(name); }
  appendChild(child) { child.remove(); child.parent = this; this.children.push(child); return child; }
  append(...nodes) { nodes.forEach(node => this.appendChild(node)); }
  remove() {
    if (!this.parent) return;
    this.parent.children = this.parent.children.filter(node => node !== this);
    this.parent = null;
  }
  get isConnected() {
    let node = this;
    while (node.parent) node = node.parent;
    return node === this.ownerDocument.body;
  }
  addEventListener(type, handler) { (this.listeners[type] ??= []).push(handler); }
  dispatch(type, extra = {}) {
    const event = { type, target: this, ...extra };
    for (const handler of this.listeners[type] ?? []) handler(event);
    return event;
  }
  focus() { this.ownerDocument.activeElement = this; }
  closest(selector) {
    let node = this;
    while (node) {
      if (node.matchesSelector(selector)) return node;
      node = node.parent;
    }
    return null;
  }
  matchesSelector(selector) {
    if (selector.startsWith('#')) return this.id === selector.slice(1);
    const attribute = selector.match(/^\[([\w-]+)\]$/);
    if (attribute) return this.hasAttribute(attribute[1]);
    if (selector === '[hidden]') return this.hidden;
    return false;
  }
  querySelector(selector) {
    for (const child of this.children) {
      if (child.matchesSelector(selector)) return child;
      const found = child.querySelector(selector);
      if (found) return found;
    }
    return null;
  }
  /* 안내 모달 마크업에서 쓰는 요소(id, 닫기 버튼)만 만든다. */
  set innerHTML(html) {
    this.children = [];
    for (const [, tag, attributes] of html.matchAll(/<(\w+)([^>]*)>/g)) {
      const id = attributes.match(/\bid="([^"]+)"/)?.[1];
      const closes = /\bdata-ui-alert-close\b/.test(attributes);
      if (!id && !closes) continue;
      const node = this.ownerDocument.createElement(tag);
      if (id) node.id = id;
      if (closes) node.setAttribute('data-ui-alert-close', '');
      this.appendChild(node);
    }
  }
  /* 브라우저의 <dialog> 동작: showModal로 열고, close는 'close' 이벤트를 보낸다. */
  showModal() { this.open = true; }
  close() {
    if (!this.open) return;
    this.open = false;
    this.dispatch('close');
  }
}

function createPage({ flash = null, storage = memoryStorage(), timers = manualTimers() } = {}) {
  const document = {
    readyState: 'loading',
    activeElement: null,
    listeners: {},
    createElement(tag) { return new Node(tag, document); },
    getElementById(id) { return document.body.querySelector('#' + id); },
    addEventListener(type, handler, options) {
      (document.listeners[type] ??= []).push({ handler, capture: options === true || options?.capture === true });
    },
    dispatch(type, extra = {}) {
      const event = {
        type, propagationStopped: false, defaultPrevented: false, ...extra,
        preventDefault() { this.defaultPrevented = true; },
        stopImmediatePropagation() { this.propagationStopped = true; }
      };
      const ordered = [...(document.listeners[type] ?? [])].sort((a, b) => Number(b.capture) - Number(a.capture));
      for (const { handler } of ordered) {
        if (event.propagationStopped) break;
        handler(event);
      }
      return event;
    }
  };
  document.body = new Node('body', document);
  if (flash) {
    const element = new Node('div', document);
    element.id = 'ui-feedback-flash';
    Object.entries(flash).forEach(([name, value]) => element.setAttribute(name, value));
    document.body.appendChild(element);
  }
  let reloads = 0;
  const window = {
    document,
    sessionStorage: storage,
    location: { reload() { reloads++; } },
    requestAnimationFrame: handler => handler(),
    setTimeout: timers.setTimeout,
    clearTimeout: timers.clearTimeout
  };
  return { window, document, timers, reloads: () => reloads };
}

function memoryStorage() {
  const values = new Map();
  return {
    getItem: key => values.has(key) ? values.get(key) : null,
    setItem: (key, value) => values.set(key, String(value)),
    removeItem: key => values.delete(key),
    size: () => values.size
  };
}

function manualTimers() {
  let next = 1;
  const pending = new Map();
  return {
    setTimeout(handler, delay) { const id = next++; pending.set(id, { handler, delay }); return id; },
    clearTimeout(id) { pending.delete(id); },
    /* delay가 가장 긴 것까지 차례로 실행해 '시간이 지났다'를 흉내 낸다. */
    runAll() {
      while (pending.size) {
        const [id, { handler }] = pending.entries().next().value;
        pending.delete(id);
        handler();
      }
    },
    delays: () => [...pending.values()].map(timer => timer.delay)
  };
}

let loads = 0;
async function load(page) {
  globalThis.window = page.window;
  globalThis.document = page.document;
  await import(`../../main/resources/static/js/ui-feedback.js?page=${++loads}`);
  page.document.readyState = 'interactive';
  page.document.dispatch('DOMContentLoaded');
  return page.window.SpoitFeedback;
}

const toastRegion = page => page.document.getElementById('ui-toast-region');
const toastTexts = page => (toastRegion(page)?.children ?? [])
  .map(item => item.children.find(child => child.classes.has('ui-toast-text'))?.textContent);
const alertDialog = page => page.document.getElementById('ui-alert');

/* 1) 서버 flash: 성공 문구는 토스트로, 실패는 제목 + 원인 모달로 한 번만 보여 주고 요소를 지운다. */
{
  const page = createPage({ flash: {
    'data-toast': '12번 매칭방을 만들었습니다.'
  } });
  await load(page);
  assert.deepEqual(toastTexts(page), ['12번 매칭방을 만들었습니다.']);
  assert.equal(toastRegion(page).children[0].classes.has('is-shown'), true, 'the toast must slide in');
  assert.equal(alertDialog(page), null, 'a success must not open the failure modal');
  assert.equal(page.document.getElementById('ui-feedback-flash'), null, 'the flash element is consumed');
}
{
  const page = createPage({ flash: {
    'data-error': '선택한 시간이 시설 운영 시간을 벗어났습니다.',
    'data-error-title': '매칭방을 만들 수 없습니다'
  } });
  await load(page);
  const dialog = alertDialog(page);
  assert.equal(dialog.open, true, 'a server failure must open the failure modal');
  assert.equal(dialog.getAttribute('role'), 'alertdialog');
  assert.equal(dialog.querySelector('#ui-alert-title').textContent, '매칭방을 만들 수 없습니다');
  assert.equal(dialog.querySelector('#ui-alert-reason').textContent, '선택한 시간이 시설 운영 시간을 벗어났습니다.');
  assert.equal(page.document.activeElement, dialog.querySelector('[data-ui-alert-close]'), '[확인] gets focus');
  assert.equal(toastRegion(page), null, 'a failure must not use the toast');
}
{
  const page = createPage({ flash: { 'data-error': '요청을 처리하지 못했습니다.' } });
  await load(page);
  assert.equal(alertDialog(page).querySelector('#ui-alert-title').textContent, '요청을 처리할 수 없습니다',
    'a failure without a title falls back to the default title');
}

/* 2) 토스트는 4초 뒤 사라지고, 닫기 버튼으로도 바로 닫힌다. */
{
  const page = createPage();
  const feedback = await load(page);
  feedback.toast('프로필을 저장했습니다.');
  assert.ok(page.timers.delays().includes(4000), 'the toast stays for 4 seconds');
  page.timers.runAll();
  assert.equal(toastRegion(page).children.length, 0, 'the toast is removed after it fades out');

  feedback.toast('동호회 링크를 복사했습니다.');
  const item = toastRegion(page).children[0];
  item.querySelector = selector => item.children.find(child => child.matchesSelector(selector)) ?? null;
  item.children.find(child => child.classes.has('ui-toast-close')).dispatch('click');
  assert.equal(item.classes.has('is-shown'), false, 'the close button hides the toast at once');
}

/* 3) 실패 모달: Esc는 아래 화면 스크립트보다 먼저 모달만 닫고, 초점을 문제가 된 칸으로 돌려준다. */
{
  const page = createPage();
  const feedback = await load(page);
  const field = page.document.createElement('input');
  page.document.body.appendChild(field);
  let pageEscapes = 0;
  page.document.addEventListener('keydown', event => { if (event.key === 'Escape') pageEscapes++; });
  let closed = 0;
  feedback.alert('운영 시간을 저장할 수 없습니다', '시작과 종료 시간이 같은 요일이 있습니다.',
    { returnFocus: field, onClose: () => { closed++; } });
  assert.equal(feedback.isAlertOpen(), true);

  const escape = page.document.dispatch('keydown', { key: 'Escape' });
  assert.equal(escape.defaultPrevented, true);
  assert.equal(pageEscapes, 0, 'Escape must not also close a screen modal underneath');
  assert.equal(feedback.isAlertOpen(), false);
  assert.equal(page.document.activeElement, field, 'focus returns to the field that needs fixing');
  assert.equal(closed, 1);

  feedback.alert('신청을 취소할 수 없습니다', '이미 처리된 신청입니다.');
  // 버튼 클릭은 dialog까지 올라가 처리된다(이벤트 위임).
  alertDialog(page).dispatch('click', { target: alertDialog(page).querySelector('[data-ui-alert-close]') });
  assert.equal(feedback.isAlertOpen(), false, '[확인] closes the modal');

  page.document.dispatch('keydown', { key: 'Escape' });
  assert.equal(pageEscapes, 1, 'once the modal is closed, Escape belongs to the page again');
}

/* 4) 새로고침 뒤 토스트: 맡긴 문구를 다음 화면에서 한 번만 보여 준다. */
{
  const storage = memoryStorage();
  const before = createPage({ storage });
  const feedback = await load(before);
  feedback.reloadWithToast('참가 신청을 승인했습니다.');
  assert.equal(before.reloads(), 1, 'reloads immediately');
  assert.equal(toastRegion(before), null, 'the toast must not flash on the page that is about to reload');

  const after = createPage({ storage });
  await load(after);
  assert.deepEqual(toastTexts(after), ['참가 신청을 승인했습니다.']);
  assert.equal(storage.size(), 0, 'the pending toast must be shown only once');

  const again = createPage({ storage });
  await load(again);
  assert.equal(toastRegion(again), null, 'a second reload shows nothing');
}

/* 5) 저장소를 쓸 수 없으면 토스트를 먼저 띄우고 잠시 뒤 새로고침한다. */
{
  const blocked = createPage({ storage: { setItem() { throw new Error('blocked'); }, getItem: () => null, removeItem() {} } });
  const feedback = await load(blocked);
  feedback.reloadWithToast('팀 배정을 저장했습니다.');
  assert.deepEqual(toastTexts(blocked), ['팀 배정을 저장했습니다.']);
  assert.equal(blocked.reloads(), 0);
  blocked.timers.runAll();
  assert.equal(blocked.reloads(), 1, 'the page still reloads after the toast');
}

console.log('PASS: ui-feedback shows server success as a toast and failures as a titled modal once, auto-hides toasts, closes the modal on Escape before page handlers, returns focus, and carries a toast across a reload.');
