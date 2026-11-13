// Run from the repository root: bun src/test/frontend/admin-system.test.mjs
// 역할 변경 모달 열기·닫기와 설정 변경 표시만 확인합니다. 신고 상세 모달은 신고 접수함
// (admin-report.js)이 담당하므로 이 스크립트에 없는 것이 계약입니다.
import assert from 'node:assert/strict';
import { runInNewContext } from 'node:vm';

class Element {
  constructor(selector, dataset = {}) { this.selector = selector; this.dataset = dataset; }
  closest(selector) { return selector === this.selector ? this : null; }
}

const handlers = {};
const listen = (type, handler) => (handlers[type] ??= []).push(handler);
const dispatch = (type, event) => (handlers[type] ?? []).forEach(handler => handler(event));
const roleModal = { hidden: true, addEventListener() {} };
const settingsHandlers = {};
const elements = {
  'role-modal': roleModal,
  'role-user-id': {}, 'role-user-label': {}, 'role-select': {}, 'role-reason': {},
  'system-settings-form': { addEventListener(type, handler) { settingsHandlers[type] = handler; } },
  'settings-state': {}
};
const admin = {
  openModal(modal, focus) { modal.hidden = false; modal.focusSelector = focus; },
  closeModal(modal) { modal.hidden = true; },
  trapFocus() {}, toast() {}
};
const document = { getElementById: id => elements[id], addEventListener: listen };
const source = await Bun.file('src/main/resources/static/js/admin-system.js').text();
runInNewContext(source, { window: { PlayOnAdmin: admin }, document, Element });

// 옛 신고 모달 훅(data-open-report, report-modal)은 접수함 모달로 대체되어 제거됐다.
assert.equal(elements['report-modal'], undefined, 'report-modal hook must stay removed from admin-system.js');
assert.ok(!source.includes('[data-open-report]'), 'admin-system.js must not reference data-open-report');

dispatch('click', { target: new Element('[data-user-role]', {
  userId: '7', userLabel: '관리자', userRoleValue: 'admin'
}) });
assert.equal(roleModal.hidden, false);
assert.equal(roleModal.focusSelector, '#role-select');
assert.equal(elements['role-user-id'].value, '7');
assert.equal(elements['role-user-label'].value, '관리자');
assert.equal(elements['role-select'].value, 'admin');
dispatch('keydown', { key: 'Escape' });
assert.equal(roleModal.hidden, true);

settingsHandlers.input();
assert.equal(elements['settings-state'].textContent, '저장하지 않은 변경 사항이 있습니다.');
console.log('PASS: system admin script initializes, handles the role modal and marks settings dirty.');
