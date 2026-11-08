// Dependency-free execution check: bun src/test/frontend/match-rating-exclusion.test.mjs
// 레이팅 제외는 확인창을 거친 뒤에만 보내고, 결과 토스트가 묻히지 않도록 새로고침 없이 섹션만 바꾸는지 확인합니다.
import assert from 'node:assert/strict';
import { readFile } from 'node:fs/promises';

const source = await readFile(new URL('../../main/resources/static/js/match-rating-exclusion.js', import.meta.url), 'utf8');

globalThis.Element ??= class Element {};

class Node extends Element {
  constructor(className = '', attributes = {}) {
    super();
    this.classes = new Set(className.split(' ').filter(Boolean));
    this.attributes = attributes;
    this.children = [];
    this.parent = null;
    this.listeners = {};
    this.textContent = '';
    this.disabled = false;
    this.hidden = false;
    this.dataset = {};
    this.classList = {
      add: (...names) => names.forEach(name => this.classes.add(name)),
      remove: (...names) => names.forEach(name => this.classes.delete(name)),
      contains: name => this.classes.has(name)
    };
  }
  get className() { return [...this.classes].join(' '); }
  set className(value) { this.classes = new Set(value.split(' ').filter(Boolean)); }
  append(child) { child.parent = this; this.children.push(child); return child; }
  prepend(child) { child.parent = this; this.children.unshift(child); }
  remove() { if (this.parent) this.parent.children = this.parent.children.filter(child => child !== this); }
  matches(selector) {
    return selector.trim().split(/\s+/).at(-1).match(/\.[\w-]+|\[[\w-]+\]/g).every(token => token.startsWith('.')
      ? this.classes.has(token.slice(1)) : this.attributes[token.slice(1, -1)] !== undefined);
  }
  descendants() { return this.children.flatMap(child => [child, ...child.descendants()]); }
  querySelector(selector) { return this.descendants().find(node => node.matches(selector)) ?? null; }
  closest(selector) {
    for (let node = this; node; node = node.parent) if (node.matches(selector)) return node;
    return null;
  }
  addEventListener(type, handler) { (this.listeners[type] ??= []).push(handler); }
  click() { for (let node = this; node; node = node.parent) (node.listeners.click ?? []).forEach(handler => handler({ target: this })); }
}

function setup(fetchImpl) {
  const root = new Node('ops-section ops-rating');
  root.dataset = { exclusionUrl: '/admin/operations/matches/9101/rating-exclusion', csrfHeader: 'X-CSRF-TOKEN', csrfToken: 'token' };
  const heading = root.append(new Node('section-heading ops-section-heading'));
  const badge = heading.append(new Node('status-badge warn'));
  badge.textContent = '반영 대기';
  root.append(new Node('notice-strip notice-policy'));
  const actions = root.append(new Node('ops-rating-actions'));
  const button = actions.append(new Node('admin-button', { 'data-rating-exclude': '' }));

  const modal = new Node('modal-backdrop');
  modal.hidden = true;
  const submit = modal.append(new Node('admin-button', { 'data-rating-submit': '' }));
  const cancel = modal.append(new Node('admin-button', { 'data-rating-cancel': '' }));

  const calls = { fetch: [], toast: [], reload: 0 };
  globalThis.document = {
    getElementById: id => ({ 'ops-rating': root, 'rating-exclusion-modal': modal })[id] ?? null,
    createElement: () => new Node(),
    addEventListener() {}
  };
  const window = {
    PlayOnAdmin: {
      openModal: target => { target.hidden = false; },
      closeModal: target => { target.hidden = true; },
      toast: (message, isError) => calls.toast.push({ message, isError }),
      trapFocus() {}
    },
    location: { reload: () => { calls.reload++; } },
    setTimeout: handler => handler()
  };
  globalThis.fetch = async (url, options) => { calls.fetch.push({ url, options }); return fetchImpl(); };
  new Function('window', source)(window);
  return { root, badge, button, modal, submit, cancel, calls };
}

const respond = (status, body) => () => ({ ok: status < 400, status, redirected: false, json: async () => body });
const settle = () => new Promise(resolve => setTimeout(resolve, 0));

{ // 닫기를 누르면 아무 요청도 보내지 않는다.
  const page = setup(respond(200, {}));
  page.button.click();
  assert.equal(page.modal.hidden, false);
  page.cancel.click();
  assert.equal(page.modal.hidden, true);
  assert.equal(page.calls.fetch.length, 0);
}

{ // 성공하면 새로고침 없이 섹션을 제외 상태로 바꾸고 성공 토스트를 띄운다.
  const page = setup(respond(200, { outcome: 'EXCLUDED', message: '레이팅 반영에서 제외했습니다.' }));
  page.button.click();
  page.submit.click();
  await settle();
  assert.equal(page.calls.fetch[0].url, '/admin/operations/matches/9101/rating-exclusion');
  assert.equal(page.calls.fetch[0].options.method, 'POST');
  assert.equal(page.calls.fetch[0].options.headers['X-CSRF-TOKEN'], 'token');
  assert.equal(page.calls.reload, 0);
  assert.equal(page.modal.hidden, true);
  assert.deepEqual(page.calls.toast, [{ message: '레이팅 반영에서 제외했습니다.', isError: false }]);
  assert.equal(page.badge.textContent, '레이팅 제외됨');
  assert.ok(page.badge.classes.has('neutral') && !page.badge.classes.has('warn'));
  assert.equal(page.root.querySelector('.notice-strip'), null);
  assert.equal(page.root.querySelector('[data-rating-exclude]'), null);
  assert.equal(page.root.querySelector('.ops-rating-message').textContent, '레이팅 반영에서 제외된 경기입니다.');
}

{ // 그 사이 반영됐으면 반영 완료 상태로 맞추고 오류 토스트를 띄운다.
  const page = setup(respond(409, { outcome: 'ALREADY_APPLIED', message: '이미 레이팅에 반영된 경기입니다.' }));
  page.button.click();
  page.submit.click();
  await settle();
  assert.deepEqual(page.calls.toast, [{ message: '이미 레이팅에 반영된 경기입니다.', isError: true }]);
  assert.equal(page.badge.textContent, '반영 완료');
  assert.equal(page.root.querySelector('[data-rating-exclude]'), null);
}

{ // 네트워크 오류면 섹션은 그대로 두고 다시 시도할 수 있게 한다.
  const page = setup(() => { throw new Error('offline'); });
  page.button.click();
  page.submit.click();
  await settle();
  assert.equal(page.calls.toast[0].isError, true);
  assert.equal(page.badge.textContent, '반영 대기');
  assert.notEqual(page.root.querySelector('[data-rating-exclude]'), null);
  assert.equal(page.modal.hidden, true);
}

console.log('PASS: match-rating-exclusion');
