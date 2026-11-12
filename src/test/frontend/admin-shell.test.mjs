// Dependency-free execution check: bun src/test/frontend/admin-shell.test.mjs
// 관리자 공통 셸과 시설 화면 스크립트가 window를 받아 초기화되고, 시설 메뉴 클릭이 화면을 전환하는지 확인합니다.
import assert from 'node:assert/strict';

// 스크립트의 이벤트 핸들러가 instanceof로 참조하는 브라우저 생성자입니다.
globalThis.Element ??= class Element {};
globalThis.HTMLFormElement ??= class HTMLFormElement extends Element {};

class Control extends Element {
  constructor(properties = {}) {
    super();
    Object.assign(this, { hidden: false, disabled: false, value: '', dataset: {}, textContent: '', id: '' }, properties);
    this.listeners = {};
    this.attributes = {};
    this.classes = new Set();
    this.classList = {
      add: name => this.classes.add(name),
      remove: name => this.classes.delete(name),
      contains: name => this.classes.has(name),
      toggle: (name, active) => active ? this.classes.add(name) : this.classes.delete(name)
    };
  }
  addEventListener(type, handler) { (this.listeners[type] ??= []).push(handler); }
  dispatch(type, target = this) {
    const event = { target, defaultPrevented: false, preventDefault() { this.defaultPrevented = true; } };
    for (const handler of this.listeners[type] ?? []) handler(event);
    return event;
  }
  setAttribute(name, value) { this.attributes[name] = value; }
  removeAttribute(name) { delete this.attributes[name]; }
  getAttribute(name) { return this.attributes[name] ?? null; }
  focus() {}
  reset() {}
  closest() { return null; }
  querySelector() { return null; }
  querySelectorAll() { return []; }
}

function createPage(hash = '') {
  const ids = new Map();
  const control = (id, properties) => {
    const element = new Control({ id, ...properties });
    ids.set(id, element);
    return element;
  };
  const body = new Control();
  body.dataset = { adminRole: 'facility', view: 'dashboard' };
  const views = [
    control('facility-dashboard', { dataset: { viewTitle: '대시보드' } }),
    control('facility-assets', { dataset: { viewTitle: '시설·공간 관리' }, hidden: true })
  ];
  const nav = control('nav-assets', { dataset: { viewTarget: 'facility-assets' } });
  nav.closest = selector => selector === 'a' ? { href: 'http://localhost/admin/facility' } : null;
  const layout = new Control();
  const sidebarToggle = new Control();
  const breadcrumb = control('breadcrumb-current');
  for (const element of ['admin-notification-modal', 'room-editor-modal', 'room-editor-form',
    'facility-modal', 'facility-form', 'room-editor-capacity-hint', 'room-editor-hour-grid']) {
    control(element);
  }
  const opener = new Control({ dataset: { openFacility: '' } });
  opener.closest = selector => selector === '[data-open-facility]' ? opener : null;
  const document = {
    body,
    title: '',
    getElementById: id => ids.get(id),
    querySelector: selector => selector === '.admin-layout' ? layout
      : selector === '[data-sidebar-toggle]' ? sidebarToggle : null,
    querySelectorAll: selector => selector === '.admin-view' ? views
      : selector === '[data-view-target]' ? [nav] : [],
    addEventListener(type, handler) { (this.listeners[type] ??= []).push(handler); },
    dispatch(type, target) {
      const event = { target, defaultPrevented: false, preventDefault() { this.defaultPrevented = true; } };
      for (const handler of this.listeners[type] ?? []) handler(event);
      return event;
    },
    listeners: {}
  };
  const window = {
    location: { hash, pathname: '/admin/facility', href: 'http://localhost/admin/facility' },
    history: { lastUrl: null, replaceState(state, title, url) { this.lastUrl = url; } },
    matchMedia: () => ({ matches: false, addEventListener() {} }),
    addEventListener(type, handler) { (this.listeners[type] ??= []).push(handler); },
    listeners: {},
    confirm: () => true,
    requestAnimationFrame: handler => handler(),
    setTimeout,
    clearTimeout
  };
  return { document, window, nav, views, breadcrumb, opener, facilityModal: ids.get('facility-modal') };
}

const page = createPage();
globalThis.window = page.window;
globalThis.document = page.document;

await import('../../main/resources/static/js/admin-shell.js');
assert.equal(typeof page.window.PlayOnAdmin?.openModal, 'function', 'common shell must initialize and expose PlayOnAdmin');

await import('../../main/resources/static/js/admin-facility.js');

page.document.dispatch('click', page.opener);
assert.equal(page.facilityModal.hidden, false, 'facility screen must open its modal through the common shell');
page.window.PlayOnAdmin.closeModal(page.facilityModal);
assert.equal(page.facilityModal.hidden, true);

page.nav.dispatch('click');
assert.equal(page.views[0].hidden, true, 'previously visible view must be hidden');
assert.equal(page.views[1].hidden, false, 'facility nav click must reveal its view');
assert.equal(page.breadcrumb.textContent, '시설·공간 관리');
assert.equal(page.nav.getAttribute('aria-current'), 'page');
assert.equal(page.window.history.lastUrl, '#facility-assets');
assert.equal(page.document.title, '시설·공간 관리 · SPO:IT 관리자');

/* 민원·요청/점검·유지보수 화면과 해시가 사라졌으므로, 예전 해시로 들어와도 빈 화면이 아니라
   첫 화면(대시보드)으로 돌아가야 한다. 링크를 타고 들어온 사용자가 화면을 잃지 않게 한다. */
const fallbackPage = createPage('#facility-requests');
globalThis.window = fallbackPage.window;
globalThis.document = fallbackPage.document;
await import('../../main/resources/static/js/admin-shell.js?removed-facility-hash');
assert.equal(fallbackPage.views[0].hidden, false, 'a removed hash must fall back to the first view');
assert.equal(fallbackPage.views[1].hidden, true, 'only the fallback view may stay visible');
assert.equal(fallbackPage.window.history.lastUrl, '#facility-dashboard',
  'the removed hash must be replaced with the dashboard hash');
assert.equal(fallbackPage.document.title, '대시보드 · SPO:IT 관리자');

/* 결과 안내는 공용 ui-feedback.js가 그린다. 관리자 셸은 기존 화면 스크립트가 쓰던 PlayOnAdmin API를 그쪽으로 넘긴다.
   (새로고침 뒤 한 번만 보여 주는 토스트 자체는 ui-feedback.test.mjs가 검증한다.) */
const delegated = createPage();
const feedbackCalls = [];
delegated.window.SpoitFeedback = {
  toast: message => feedbackCalls.push(['toast', message]),
  alert: (title, reason, options) => feedbackCalls.push(['alert', title, reason, options?.returnFocus ?? null]),
  reloadWithToast: message => feedbackCalls.push(['reloadWithToast', message])
};
let directReloads = 0;
delegated.window.location.reload = () => { directReloads++; };
globalThis.window = delegated.window;
globalThis.document = delegated.document;
await import('../../main/resources/static/js/admin-shell.js?delegation');
const shell = delegated.window.PlayOnAdmin;
const focusTarget = { focus() {} };

shell.reloadWithToast('참가 신청을 승인했습니다.', false);
shell.toast('레이팅 반영에서 제외했습니다.', false);
shell.toast('이미 레이팅에 반영된 경기입니다.', true);
shell.showErrorModal('팀 배정을 처리할 수 없습니다', '정원이 모두 찼습니다.', focusTarget);
shell.reloadWithToast('저장하지 못했습니다.', true);
assert.deepEqual(feedbackCalls, [
  ['reloadWithToast', '참가 신청을 승인했습니다.'],
  ['toast', '레이팅 반영에서 제외했습니다.'],
  ['alert', null, '이미 레이팅에 반영된 경기입니다.', null],
  ['alert', '팀 배정을 처리할 수 없습니다', '정원이 모두 찼습니다.', focusTarget],
  ['alert', null, '저장하지 못했습니다.', null]
], 'success goes to the toast, every failure goes to the failure modal with its reason');
assert.equal(directReloads, 0, 'a failure must not reload the page; the success reload is left to ui-feedback.js');

console.log('PASS: admin shell and facility scripts initialize with window, open modals, switch views, fall back from a removed hash, and hand results to the shared toast and failure modal.');
