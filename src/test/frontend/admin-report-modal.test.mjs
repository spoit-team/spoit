// Run from the repository root: bun src/test/frontend/admin-report-modal.test.mjs
// 신고 접수함 상세 모달(admin-report.js)의 동작만 확인합니다:
// 서버가 selectedReport와 함께 렌더하면 모달을 열고, 닫기·Escape는 상태 필터와
// 페이지를 유지해 목록 URL로 되돌립니다. 원문은 스크립트로 전달되지 않습니다.
import assert from 'node:assert/strict';
import { runInNewContext } from 'node:vm';

class El {
  constructor(attributes = {}) {
    this.attributes = attributes;
    this.dataset = {};
    this.hidden = true;
    this.classList = new Set();
    this.classes = { add: n => this.classList.add(n) };
    this.listeners = {};
    this.focused = null;
  }
  getAttribute(name) { return this.attributes[name] ?? null; }
  setAttribute(name, value) { this.attributes[name] = String(value); }
  removeAttribute(name) { delete this.attributes[name]; }
  focus() { this.focused = true; }
  addEventListener(type, handler) { (this.listeners[type] ??= []).push(handler); }
  dispatch(type, event = {}) {
    for (const handler of this.listeners[type] ?? []) handler(event);
  }
}

function makePage({ withModal = true, status = '검토 중', page = '2', listUrl = '/admin/system/reports' } = {}) {
  const ids = new Map();
  const body = new El();
  const modal = new El({
    'data-report-list-url': listUrl,
    'data-report-status': status,
    'data-report-page': page
  });
  modal.querySelector = () => (withModal ? {} : null);
  modal.addEventListener = function (type, handler) { (this.listeners[type] ??= []).push(handler); };
  if (withModal) ids.set('report-case', modal);

  const resolution = new El();
  resolution.value = '';
  ids.set('report-decision-resolution', resolution);
  ids.set('report-decision-error', new El());

  const decisionForm = new El();
  decisionForm.addEventListener = function (type, handler) { (this.listeners[type] ??= []).push(handler); };
  ids.set('report-decision-form', decisionForm);

  const navigations = [];
  const documentListeners = {};
  const document = {
    body,
    getElementById: id => ids.get(id),
    querySelector: () => null,
    querySelectorAll: () => [],
    addEventListener(type, handler) { (documentListeners[type] ??= []).push(handler); }
  };
  const window = {
    PlayOnAdmin: {
      openModalCalls: [],
      openModal(modalArg, focus) { this.openModalCalls.push({ modal: modalArg, focus }); modalArg.hidden = false; },
      closeModal() {},
      trapFocus() {}
    },
    location: { href: '' },
    setTimeout
  };
  // location.href setter를 잡아 닫기 이동을 기록한다.
  Object.defineProperty(window.location, 'href', {
    set(value) { navigations.push(value); },
    get() { return this._href || ''; }
  });
  return { ids, body, modal, resolution, decisionForm, document, window, navigations, documentListeners };
}

async function load(page) {
  const source = await Bun.file('src/main/resources/static/js/admin-report.js').text();
  runInNewContext(source, { window: page.window, document: page.document, Element: El, URLSearchParams });
}

/* ── 1. selectedReport가 있으면 모달을 열고 닫기 초점을 지정한다 ─────────── */
const open = makePage();
await load(open);
assert.equal(open.modal.hidden, false, 'the server-rendered modal must open on load');
assert.deepEqual(open.window.PlayOnAdmin.openModalCalls.map(call => call.focus),
  ['.modal-close'], 'the modal must take focus on the close control first');

/* ── 2. 닫기는 상태 필터와 페이지를 유지해 목록으로 돌아간다 ─────────────── */
const closeTarget = new El();
closeTarget.closest = sel => (sel === '[data-close-report]' ? closeTarget : null);
open.modal.dispatch('click', { target: closeTarget });
assert.equal(open.navigations.length, 1, 'closing must navigate back to the list');
assert.equal(open.navigations[0],
  '/admin/system/reports?' + new URLSearchParams({ status: '검토 중', page: '2' }) + '#report-ledger',
  'the return URL must keep the status filter and page');

/* ── 2-1. context path가 있으면 서버가 싣은 목록 주소를 그대로 쓴다 ──────── */
const contextPathPage = makePage({ status: '접수', page: '3', listUrl: '/spoit/admin/system/reports' });
await load(contextPathPage);
contextPathPage.modal.dispatch('click', { target: closeTarget });
assert.equal(contextPathPage.navigations.length, 1, 'closing must navigate back to the list');
assert.equal(contextPathPage.navigations[0],
  '/spoit/admin/system/reports?' + new URLSearchParams({ status: '접수', page: '3' }) + '#report-ledger',
  'the return URL must reuse the context-prefixed list url from the server');

/* ── 2-2. 속성이 없으면 root-relative 기본 주소로 되돌아간다 ────────────── */
const fallbackPage = makePage({ status: '기각', page: '0', listUrl: '' });
await load(fallbackPage);
fallbackPage.modal.dispatch('click', { target: closeTarget });
assert.equal(fallbackPage.navigations.length, 1, 'closing must navigate back to the list');
assert.equal(fallbackPage.navigations[0],
  '/admin/system/reports?' + new URLSearchParams({ status: '기각' }) + '#report-ledger',
  'a missing list url must fall back to the root-relative route and drop page 0');

/* ── 3. Escape도 같은 경로로 목록에 돌려놓는다 ─────────────────────────── */
const escapePage = makePage({ status: '접수', page: '0' });
await load(escapePage);
for (const handler of escapePage.documentListeners.keydown ?? []) handler({ key: 'Escape' });
assert.equal(escapePage.navigations[0],
  '/admin/system/reports?' + new URLSearchParams({ status: '접수' }) + '#report-ledger',
  'page 0 must not add a page param on close');

/* ── 4. selectedReport가 없으면 모달 없이 폼 보강만 초기화된다 ──────────── */
const noModal = makePage({ withModal: false });
await load(noModal);
assert.equal(noModal.modal.hidden, true, 'no modal element means nothing opens');
assert.deepEqual(noModal.window.PlayOnAdmin.openModalCalls, []);

/* ── 5. 처리 완료·기각에는 사유가 필요하다(기존 계약 유지) ─────────────── */
const guarded = makePage();
guarded.decisionForm.addEventListener('submit', event => { event.captured = true; });
await load(guarded);
// listeners.submit = [테스트 캡처, 스크립트 검증 핸들러]
const scriptSubmitHandler = guarded.decisionForm.listeners.submit[1];
assert.equal(typeof scriptSubmitHandler, 'function', 'the script must attach a submit guard');
guarded.resolution.value = '   ';
const blockedEvent = { submitter: { value: 'complete' }, preventDefault() { this.prevented = true; } };
scriptSubmitHandler(blockedEvent);
assert.equal(blockedEvent.prevented, true, 'empty resolution must block complete');
assert.equal(guarded.ids.get('report-decision-error').hidden, false, 'the error must surface');

guarded.resolution.value = '노쇼 확인';
const acceptedEvent = { submitter: { value: 'complete' }, preventDefault() { this.prevented = true; } };
scriptSubmitHandler(acceptedEvent);
assert.notEqual(acceptedEvent.prevented, true, 'a filled resolution must submit');

console.log('PASS: report modal opens on selected report, closes back to the filtered list, and keeps decision guards.');
