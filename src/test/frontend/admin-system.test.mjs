// Run from the repository root: bun src/test/frontend/admin-system.test.mjs
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
const reportModal = { hidden: true, addEventListener() {} };
const reportBody = { replaceChildren(...children) { this.children = children; } };
const settingsHandlers = {};
const elements = {
  'role-modal': roleModal,
  'report-modal': reportModal,
  'report-modal-body': reportBody,
  'report-modal-title': {},
  'report-modal-subtitle': {},
  'role-user-id': {}, 'role-user-label': {}, 'role-select': {}, 'role-reason': {},
  'report-detail-3': {
    dataset: { reportTitle: '신고 제목', reportSubtitle: '신고 내용' },
    content: { cloneNode: () => '신고 상세' }
  },
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

dispatch('click', { target: new Element('[data-open-report]', { reportId: '3' }) });
assert.equal(reportModal.hidden, false);
assert.equal(reportModal.focusSelector, '.report-decision-card select');
assert.deepEqual(reportBody.children, ['신고 상세']);
assert.equal(elements['report-modal-title'].textContent, '신고 제목');
dispatch('keydown', { key: 'Escape' });
assert.equal(reportModal.hidden, true);
assert.equal(reportBody.children.length, 0);

settingsHandlers.input();
assert.equal(elements['settings-state'].textContent, '저장하지 않은 변경 사항이 있습니다.');
console.log('PASS: system admin script initializes, handles modals and marks settings dirty.');
