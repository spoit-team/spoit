// Dependency-free execution check: bun src/test/frontend/signup-consent-focus.test.mjs
// signup.js의 필수 약관 판정(동의/다음 단계/제출)과 admin-shell.js의 focus trap 동작을 확인합니다.
import assert from 'node:assert/strict';
// DOM 스텁 공통부는 support/dom-stub.mjs가 준비합니다(Element/HTMLFormElement 포함).
import { DomElement } from './support/dom-stub.mjs';

/* 이 테스트만의 능력: focus()가 document.activeElement를 옮기고,
   이 화면의 요소는 closest 탐색을 하지 않습니다(각 요소가 필요할 때 직접 덮어씁니다). */
class Control extends DomElement {
  focus() { globalThis.document.activeElement = this; }
  closest() { return null; }
}

/* ---------- signup.js ---------- */
function signupPage() {
  const ids = new Map();
  const control = (id, properties = {}) => {
    const element = new Control({ id, ...properties });
    ids.set(id, element);
    return element;
  };
  const form = control('signup-form');
  form.dataset = { startStep: '1' };
  const page = {
    form,
    stepOne: control('signup-step-1'),
    stepTwo: control('signup-step-2', { hidden: true }),
    stepOneDot: control('step-1-dot'),
    stepTwoDot: control('step-2-dot'),
    stepOneLabel: control('step-1-label'),
    stepTwoLabel: control('step-2-label'),
    role: control('signup-role', { value: 'member' }),
    roleHint: control('signup-role-hint'),
    facilityFields: control('facility-signup-fields', { hidden: true }),
    systemFields: control('system-signup-fields', { hidden: true }),
    facilityName: control('signup-facility-name'),
    adminInvite: control('signup-admin-invite'),
    roleError: control('signup-role-error', { hidden: true }),
    message: control('signup-message'),
    termsError: control('terms-error', { hidden: true }),
    agreeAll: control('agree-all'),
    service: control('agree-service'),
    privacy: control('agree-privacy'),
    marketing: control('agree-marketing', { disabled: true }),
    nextStep: control('next-step'),
    previousStep: control('previous-step'),
    region: control('signup-preferred-region'),
    sport: control('signup-preferred-sport')
  };
  for (const id of ['signup-email', 'signup-phone', 'signup-password', 'signup-password-confirm', 'signup-nickname']) {
    control(id, { value: id === 'signup-email' ? 'user@example.com' : 'valid-value' });
  }
  globalThis.document = {
    activeElement: null,
    getElementById: id => ids.get(id),
    addEventListener() {}
  };
  return page;
}

let page = signupPage();
await import('../../main/resources/static/js/signup.js');

assert.equal(page.stepOne.hidden, false);
assert.equal(page.stepTwo.hidden, true);
assert.equal(page.roleHint.textContent, '현재는 일반 사용자로만 가입할 수 있습니다.');

// 전체 동의는 필수 약관만 채우고, 비활성 마케팅 항목은 건드리지 않는다.
page.agreeAll.checked = true;
page.agreeAll.dispatch('change');
assert.equal(page.service.checked, true);
assert.equal(page.privacy.checked, true);
assert.equal(page.marketing.checked, false);
assert.equal(page.termsError.hidden, true);

// 필수 약관이 하나라도 빠지면 오류를 다시 노출한다.
page.termsError.hidden = false;
page.privacy.checked = false;
page.privacy.dispatch('change');
assert.equal(page.termsError.hidden, false, '필수 약관 미동의 시 오류가 유지되어야 합니다.');

// 다음 단계: 첫 미동의 항목에 포커스하고 단계 1에 머문다.
page.nextStep.dispatch('click');
assert.equal(page.termsError.hidden, false);
assert.equal(globalThis.document.activeElement, page.privacy);
assert.equal(page.stepOne.hidden, false);

// 모두 동의하면 단계 2로 이동하고 이메일로 포커스가 간다.
page.privacy.checked = true;
page.nextStep.dispatch('click');
assert.equal(page.stepOne.hidden, true);
assert.equal(page.stepTwo.hidden, false);
assert.equal(globalThis.document.activeElement.id, 'signup-email');

// 제출 시 필수 약관 미동의면 제출을 막고 단계 1로 되돌린다.
page.form.dataset.submitted = 'true';
page.service.checked = false;
const submitEvent = page.form.dispatch('submit');
assert.equal(submitEvent.defaultPrevented, true);
assert.equal(page.stepOne.hidden, false);
assert.equal(page.termsError.hidden, false);
assert.equal(globalThis.document.activeElement, page.service);

/* ---------- admin-shell.js ---------- */
function adminPage() {
  const ids = new Map();
  const control = (id, properties = {}) => {
    const element = new Control({ id, ...properties });
    ids.set(id, element);
    return element;
  };
  const body = control('body');
  body.dataset = {};
  const modal = control('admin-notification-modal', { hidden: true });
  const closeButton = control('modal-close');
  const firstButton = control('first');
  const lastInput = control('last');
  closeButton.classList.add('modal-close');
  closeButton.querySelector = selector => selector === '.modal-close' ? closeButton : null;
  closeButton.closest = () => null;
  modal.querySelectorAll = () => [closeButton, firstButton, lastInput];
  modal.querySelector = selector => selector === '.modal-close' ? closeButton : null;
  const opener = control('opener');
  opener.closest = selector => selector === '[data-open-admin-notifications]' ? opener : null;
  globalThis.document = {
    activeElement: null,
    body,
    title: '',
    getElementById: id => ids.get(id) ?? (id === 'admin-notification-modal' ? modal : undefined),
    querySelector: () => null,
    querySelectorAll: () => [],
    addEventListener(type, handler) { (this.listeners[type] ??= []).push(handler); },
    dispatch(type, properties = {}) {
      const event = { target: this, defaultPrevented: false, preventDefault() { this.defaultPrevented = true; }, ...properties };
      for (const handler of this.listeners[type] ?? []) handler(event);
      return event;
    },
    listeners: {}
  };
  const window = {
    location: { hash: '', pathname: '/admin/facility', href: 'http://localhost/admin/facility' },
    history: { replaceState() {} },
    matchMedia: () => ({ matches: false, addEventListener() {} }),
    addEventListener(type, handler) { (this.listeners[type] ??= []).push(handler); },
    confirm: () => true,
    requestAnimationFrame: handler => handler(),
    setTimeout,
    clearTimeout,
    listeners: {}
  };
  return { window, modal, closeButton, firstButton, lastInput, opener, body };
}

const admin = adminPage();
globalThis.window = admin.window;
// 다른 테스트와 모듈 캐시를 공유하지 않고 새 window로 초기화한다.
await import('../../main/resources/static/js/admin-shell.js?signup-consent-focus');
assert.equal(typeof window.PlayOnAdmin?.trapFocus, 'function');

document.activeElement = admin.opener;
const openEvent = document.dispatch('click', { target: admin.opener });
assert.equal(openEvent.defaultPrevented, true);
assert.equal(admin.modal.hidden, false);
assert.equal(admin.body.classes.has('has-admin-modal'), true);
assert.equal(document.activeElement, admin.closeButton);

// Escape는 trap보다 먼저 모달을 닫고 이전 포커스를 복원한다.
document.dispatch('keydown', { key: 'Escape', shiftKey: false, preventDefault() {} });
assert.equal(admin.modal.hidden, true);
assert.equal(admin.body.classes.has('has-admin-modal'), false);
assert.equal(document.activeElement, admin.opener);

document.dispatch('click', { target: admin.opener });
// Tab: 마지막에서 첫 항목으로 순환한다.
document.activeElement = admin.lastInput;
const tab = document.dispatch('keydown', { key: 'Tab', shiftKey: false });
assert.equal(tab.defaultPrevented, true);
assert.equal(document.activeElement, admin.closeButton);

// Shift+Tab: 첫 항목에서 마지막으로 순환한다.
document.activeElement = admin.closeButton;
const shiftTab = document.dispatch('keydown', { key: 'Tab', shiftKey: true });
assert.equal(shiftTab.defaultPrevented, true);
assert.equal(document.activeElement, admin.lastInput);

// 공개 API: hidden 모달과 포커스 가능 요소가 없는 모달은 아무 것도 하지 않는다.
admin.modal.hidden = true;
const untouched = { key: 'Tab', shiftKey: false, defaultPrevented: false, preventDefault() { this.defaultPrevented = true; } };
window.PlayOnAdmin.trapFocus(admin.modal, untouched);
assert.equal(untouched.defaultPrevented, false, '닫힌 모달은 Tab을 가로채지 않아야 합니다.');
admin.modal.hidden = false;
const emptyModal = { hidden: false, querySelectorAll: () => [] };
window.PlayOnAdmin.trapFocus(emptyModal, untouched);
assert.equal(untouched.defaultPrevented, false, '포커스 가능 요소가 없으면 Tab을 가로채지 않아야 합니다.');

console.log('PASS: signup consent gating and admin focus trap behave as before.');
