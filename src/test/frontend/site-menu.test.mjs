// Run from the repository root: bun src/test/frontend/site-menu.test.mjs
// site-menu.js의 토글 클릭을 실제 브라우저처럼 버블링으로 재현합니다. setMenuOpen이 toggle.innerHTML을
// 다시 그리면 클릭 대상이던 SVG/path가 문서에서 떨어져 나가므로, document 클릭 핸들러는 event.target이
// 아니라 이벤트가 지나온 경로(composedPath)로 "바깥 클릭"을 판단해야 합니다.
import assert from 'node:assert/strict';

class FakeElement {
  constructor(tagName) {
    this.tagName = String(tagName).toUpperCase();
    this.parentNode = null;
    this.children = [];
    this.attributes = {};
    this.classes = new Set();
    this.listeners = {};
    this.hidden = false;
    this.id = '';
    this.type = '';
    this.title = '';
    this.textContent = '';
    this._innerHTML = '';
    const self = this;
    this.classList = {
      add: (...names) => names.forEach(name => self.classes.add(name)),
      remove: (...names) => names.forEach(name => self.classes.delete(name)),
      contains: name => self.classes.has(name)
    };
  }

  get className() { return [...this.classes].join(' '); }
  set className(value) { this.classes = new Set(String(value).split(/\s+/).filter(Boolean)); }

  append(...nodes) {
    for (const node of nodes) {
      node.parentNode = this;
      this.children.push(node);
    }
  }

  replaceChildren(...nodes) {
    for (const child of this.children) child.parentNode = null;
    this.children = [];
    this.append(...nodes);
  }

  setAttribute(name, value) { this.attributes[name] = String(value); }
  removeAttribute(name) { delete this.attributes[name]; }
  getAttribute(name) { return Object.prototype.hasOwnProperty.call(this.attributes, name) ? this.attributes[name] : null; }

  // toggle.innerHTML 교체는 기존 SVG/path를 문서에서 떼어 낸다.
  get innerHTML() { return this._innerHTML; }
  set innerHTML(markup) {
    for (const child of this.children) child.parentNode = null;
    this.children = [];
    this._innerHTML = String(markup);
    if (this._innerHTML.includes('<svg')) {
      const svg = new FakeElement('svg');
      svg.append(new FakeElement('path'));
      this.append(svg);
    }
  }

  matches(selector) {
    if (selector.includes(',')) return selector.split(',').some(part => this.matches(part.trim()));
    const parsed = selector.match(/^([a-zA-Z0-9-]*)((?:[.#][\w-]+|\[[^\]]+\])*)$/);
    if (!parsed) return false;
    const [, tag, rest] = parsed;
    if (tag && this.tagName !== tag.toUpperCase()) return false;
    for (const token of rest.match(/[.#][\w-]+|\[[^\]]+\]/g) ?? []) {
      if (token.startsWith('.')) {
        if (!this.classes.has(token.slice(1))) return false;
      } else if (token.startsWith('#')) {
        if (this.id !== token.slice(1)) return false;
      } else if (!Object.prototype.hasOwnProperty.call(this.attributes, token.slice(1, -1).split('=')[0])) {
        return false;
      }
    }
    return true;
  }

  closest(selector) {
    let node = this;
    while (node) {
      if (typeof node.matches === 'function' && node.matches(selector)) return node;
      node = node.parentNode;
    }
    return null;
  }

  contains(node) {
    let current = node;
    while (current) {
      if (current === this) return true;
      current = current.parentNode;
    }
    return false;
  }

  querySelector(selector) { return this.querySelectorAll(selector)[0] ?? null; }

  querySelectorAll(selector) {
    const found = [];
    const walk = node => {
      for (const child of node.children) {
        if (child.matches(selector)) found.push(child);
        walk(child);
      }
    };
    walk(this);
    return found;
  }

  cloneNode() {
    const copy = new FakeElement(this.tagName);
    copy.className = this.className;
    copy.id = this.id;
    copy.textContent = this.textContent;
    for (const [name, value] of Object.entries(this.attributes)) copy.setAttribute(name, value);
    for (const child of this.children) copy.append(child.cloneNode());
    return copy;
  }

  focus() { documentObject.activeElement = this; }

  addEventListener(type, handler) { (this.listeners[type] ??= []).push(handler); }

  dispatchEvent(type, properties = {}) {
    // 경로는 디스패치 시점에 고정한다. 핸들러가 DOM을 바꿔도 이후 버블링 단계는 같은 경로를 쓴다.
    const path = [];
    for (let node = this; node; node = node.parentNode) path.push(node);
    const event = {
      type,
      target: this,
      currentTarget: null,
      defaultPrevented: false,
      preventDefault() { this.defaultPrevented = true; },
      composedPath: () => path.slice(),
      ...properties
    };
    for (const node of path) {
      for (const handler of [...(node.listeners?.[type] ?? [])]) {
        event.currentTarget = node;
        handler(event);
      }
    }
    return event;
  }
}

const create = (tagName, className, properties = {}) => {
  const element = new FakeElement(tagName);
  if (className) element.className = className;
  return Object.assign(element, properties);
};

const body = create('body');
const header = create('header', 'sticky');
const headerInner = create('div', 'mobile-nav-header');
const sourceNav = create('nav', 'primary-nav');
const desktopLink = create('a');
desktopLink.setAttribute('href', '/matches');
desktopLink.textContent = '매치';
sourceNav.append(desktopLink);
const actions = create('div', 'ml-auto');
const memberArea = create('div');
memberArea.setAttribute('data-auth-member', '');
const accountLink = create('a');
accountLink.setAttribute('href', '/mypage');
accountLink.textContent = '테스트회원';
const logoutForm = create('form');
memberArea.append(accountLink, logoutForm);

header.append(headerInner);
headerInner.append(sourceNav, actions, memberArea);
body.append(header);

const documentObject = {
  body,
  activeElement: null,
  listeners: {},
  createElement: tagName => new FakeElement(tagName),
  // 스크립트는 body 바로 아래의 sticky 헤더만 찾는다.
  querySelector: selector => selector === 'body > header.sticky' ? header : null,
  querySelectorAll: () => [],
  addEventListener(type, handler) { (this.listeners[type] ??= []).push(handler); },
  dispatchEvent(type, properties = {}) { return FakeElement.prototype.dispatchEvent.call(this, type, properties); }
};
const windowObject = {
  listeners: {},
  matchMedia: () => ({ matches: false, addEventListener() {} }),
  addEventListener(type, handler) { (this.listeners[type] ??= []).push(handler); }
};
documentObject.parentNode = windowObject;
body.parentNode = documentObject;

globalThis.document = documentObject;
globalThis.window = windowObject;

await import('../../main/resources/static/js/site-menu.js');

const toggle = actions.querySelector('button.mobile-menu-toggle');
const menu = headerInner.querySelector('#mobile-primary-menu');
assert.ok(toggle, '토글 버튼이 액션 영역에 생성되어야 합니다');
assert.ok(menu, '모바일 메뉴 패널이 헤더 안에 생성되어야 합니다');
assert.equal(menu.hidden, true, '처음에는 메뉴가 닫혀 있어야 합니다');
assert.equal(toggle.getAttribute('aria-expanded'), 'false');

/* 1) 실제 브라우저처럼 안쪽 path에서 클릭이 버블링된다. 열리면서 innerHTML이 바뀌어 대상이 떨어져 나간다. */
const firstPath = toggle.querySelector('path');
assert.ok(firstPath, '토글 아이콘의 path가 있어야 합니다');
firstPath.dispatchEvent('click');

assert.equal(firstPath.closest('button'), null, '클릭 대상 path는 다시 그리면서 문서에서 떨어져 나간다');
assert.equal(headerInner.contains(firstPath), false, '떨어져 나간 대상은 헤더에도 속하지 않는다');
assert.equal(menu.hidden, false, '아이콘 첫 클릭으로 메뉴가 열린 채 유지되어야 합니다');
assert.equal(toggle.getAttribute('aria-expanded'), 'true');
assert.equal(toggle.getAttribute('aria-label'), '메뉴 닫기');

/* 2) 다시 그려진 닫기 아이콘을 누르면 닫힌다. */
const secondPath = toggle.querySelector('path');
assert.ok(secondPath && secondPath !== firstPath, '두 번째 클릭은 새로 그려진 아이콘을 대상으로 합니다');
secondPath.dispatchEvent('click');
assert.equal(menu.hidden, true, '아이콘 두 번째 클릭으로 메뉴가 닫혀야 합니다');
assert.equal(toggle.getAttribute('aria-expanded'), 'false');

/* 3) 패널 안쪽 클릭은 바깥 클릭이 아니므로 열린 상태를 유지한다. */
toggle.dispatchEvent('click');
assert.equal(menu.hidden, false);
menu.dispatchEvent('click');
assert.equal(menu.hidden, false, '패널 안쪽 클릭은 메뉴를 닫지 않아야 합니다');

/* 4) 헤더 바깥 클릭은 닫는다. */
const outside = create('div');
body.append(outside);
outside.dispatchEvent('click');
assert.equal(menu.hidden, true, '바깥 클릭은 메뉴를 닫아야 합니다');

/* 5) Escape는 메뉴를 닫고 토글에 초점을 돌려준다. */
toggle.dispatchEvent('click');
assert.equal(menu.hidden, false);
documentObject.dispatchEvent('keydown', { key: 'Escape' });
assert.equal(menu.hidden, true, 'Escape는 메뉴를 닫아야 합니다');
assert.equal(documentObject.activeElement, toggle, 'Escape는 토글로 초점을 되돌려야 합니다');
assert.equal(toggle.getAttribute('aria-label'), '메뉴 열기');

console.log('PASS: site menu stays open on detached-icon clicks, closes on second toggle click, outside click and Escape.');
