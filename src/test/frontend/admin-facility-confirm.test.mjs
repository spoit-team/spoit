// Dependency-free execution check: bun src/test/frontend/admin-facility-confirm.test.mjs
// 시설 수정·삭제는 확인 창을 거친 뒤에만 서버로 전송되고, 취소하면 아무 요청도 보내지 않는지 확인합니다.
// 등록은 확인 없이 바로 저장되고, 필수 검증 실패는 확인 창보다 먼저 막히는지도 함께 확인합니다.
import assert from 'node:assert/strict';

globalThis.Element ??= class Element {};
globalThis.HTMLFormElement ??= class HTMLFormElement extends Element {};

function matchesSelector(element, selector) {
  return selector.split(',').some(part => {
    const token = part.trim();
    if (!token) return false;
    if (token.startsWith('#')) return element.id === token.slice(1);
    if (token.startsWith('.')) return element.classes.has(token.slice(1));
    if (token.startsWith('[')) {
      const attribute = token.slice(1, -1).split('=')[0].trim();
      return element.attributes[attribute] !== undefined;
    }
    return false;
  });
}

class Control extends Element {
  constructor(properties = {}) {
    super();
    Object.assign(this, { hidden: false, disabled: false, value: '', checked: false, dataset: {}, textContent: '', id: '' }, properties);
    this.listeners = {};
    this.attributes = {};
    this.classes = new Set(String(properties.className ?? '').split(' ').filter(Boolean));
    this.parentElement = null;
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
  matches(selector) { return matchesSelector(this, selector); }
  closest(selector) {
    let node = this;
    while (node) {
      if (matchesSelector(node, selector)) return node;
      node = node.parentElement;
    }
    return null;
  }
  append(...children) {
    const [child] = children;
    child.parentElement = this;
    return child;
  }
  focus() { this.focused = true; }
  reset() { this.resetCount = (this.resetCount ?? 0) + 1; }
  querySelector() { return null; }
  querySelectorAll() { return []; }
}

class Form extends HTMLFormElement {
  constructor(properties = {}) {
    super();
    Object.assign(this, { hidden: false, disabled: false, value: '', dataset: {}, textContent: '', id: '' }, properties);
    this.listeners = {};
    this.attributes = {};
    this.classes = new Set(String(properties.className ?? '').split(' ').filter(Boolean));
    this.parentElement = null;
    this.requestSubmitCount = 0;
    this.classList = {
      add: name => this.classes.add(name),
      remove: name => this.classes.delete(name),
      contains: name => this.classes.has(name),
      toggle: (name, active) => active ? this.classes.add(name) : this.classes.delete(name)
    };
  }
  addEventListener(type, handler) { (this.listeners[type] ??= []).push(handler); }
  setAttribute(name, value) { this.attributes[name] = value; }
  removeAttribute(name) { delete this.attributes[name]; }
  getAttribute(name) { return this.attributes[name] ?? null; }
  matches(selector) { return matchesSelector(this, selector); }
  closest() { return null; }
  focus() { this.focused = true; }
  append(...children) {
    const [child] = children;
    child.parentElement = this;
    return child;
  }
  reset() { this.resetCount = (this.resetCount ?? 0) + 1; }
  querySelector() { return null; }
  querySelectorAll() { return []; }
  /* 실제 브라우저처럼 submit 이벤트를 다시 발생시킵니다. */
  requestSubmit() {
    this.requestSubmitCount += 1;
    page.document.dispatch('submit', this);
  }
}

const ids = new Map();
const control = (id, properties = {}) => {
  const element = new Control({ id, ...properties });
  ids.set(id, element);
  return element;
};

const editButton = control('edit-facility-1', {
  dataset: {
    facilityId: '1',
    facilityName: '올림픽 체육관',
    facilityAddress: '서울 송파구 올림픽로 25',
    facilitySports: '농구',
    facilityZones: '3',
    facilityStatus: '운영 중'
  }
});
editButton.setAttribute('data-edit-facility', '');

const deleteForm = new Form({ id: 'delete-facility-1', className: 'facility-delete-form' });
ids.set('delete-facility-1', deleteForm);
const deleteButton = control('delete-facility-1-button', { dataset: { facilityName: '올림픽 체육관' } });
deleteButton.setAttribute('data-delete-facility', '');
deleteForm.append(deleteButton);
deleteForm.querySelector = selector => selector === '[data-delete-facility]' ? deleteButton : null;

const facilityForm = new Form({
  id: 'facility-form',
  dataset: { facilityBase: '/admin/facilities' }
});
ids.set('facility-form', facilityForm);
facilityForm.action = '/admin/facilities';
const submitFacility = control('facility-save', {});
const facilityNameInput = control('facility-name');
const facilityAddressInput = control('facility-address');
const facilityZonesInput = control('facility-zone-count');
const facilityStatusSelect = control('facility-operating-status');
const facilitySportsValue = control('facility-sports-value');
[submitFacility, facilityNameInput, facilityAddressInput, facilityZonesInput, facilityStatusSelect, facilitySportsValue]
  .forEach(element => facilityForm.append(element));

const facilityModal = control('facility-modal', { hidden: true });
const facilityModalTitle = control('facility-modal-title');
facilityModal.querySelector = selector => selector === '#facility-modal-title' ? facilityModalTitle : null;

const confirmModal = control('facility-confirm-modal', { hidden: true });
const confirmTitle = control('facility-confirm-title');
const confirmMessage = control('facility-confirm-message');
const confirmName = control('facility-confirm-name');
confirmName.setAttribute('data-facility-confirm-name', '');
const confirmCancel = control('facility-confirm-cancel', { className: 'admin-button' });
confirmCancel.setAttribute('data-close-facility-confirm', '');
const confirmSubmit = control('facility-confirm-submit', { className: 'admin-button primary' });
confirmSubmit.setAttribute('data-confirm-facility-action', '');
[confirmTitle, confirmMessage, confirmName, confirmCancel, confirmSubmit]
  .forEach(element => confirmModal.append(element));

['room-editor-modal', 'room-editor-form', 'maintenance-modal', 'admin-toast',
  'breadcrumb-current', 'facility-map-address', 'room-editor-hour-grid', 'room-editor-time-summary',
  'room-editor-capacity-hint', 'room-editor-team-a', 'room-editor-team-b', 'date-schedule-hour-grid',
  'date-schedule-count', 'date-schedule-ranges'].forEach(id => control(id));
control('admin-notification-modal', { hidden: true });

const sportOptions = [
  control('sport-tennis', { value: '테니스' }),
  control('sport-soccer', { value: '축구+풋살' }),
  control('sport-basketball', { value: '농구' })
];
sportOptions.forEach(input => input.setAttribute('data-facility-sport', ''));

const body = new Control();
body.dataset = { adminRole: 'facility', view: 'assets' };
const views = [control('facility-assets', { dataset: { viewTitle: '시설·공간 관리' } })];
const layout = control('admin-layout');
const sidebarToggle = control('sidebar-toggle');

const page = {};
const documentObject = {
  body,
  title: '',
  activeElement: submitFacility,
  getElementById: id => ids.get(id),
  querySelector: selector => selector === '.admin-layout' ? layout
    : selector === '[data-sidebar-toggle]' ? sidebarToggle
    : selector === '[data-facility-confirm-name]' ? confirmName : null,
  querySelectorAll: selector => selector === '[data-facility-sport]' ? sportOptions
    : selector === '.admin-view' ? views
    : selector === '[data-view-target]' ? [] : [],
  addEventListener(type, handler) { (this.listeners[type] ??= []).push(handler); },
  dispatch(type, target, extra = {}) {
    const event = { target, defaultPrevented: false, preventDefault() { this.defaultPrevented = true; }, ...extra };
    for (const handler of this.listeners[type] ?? []) handler(event);
    return event;
  },
  listeners: {}
};

const windowObject = {
  location: { hash: '#facility-assets', pathname: '/admin/facility', href: 'http://localhost/admin/facility' },
  history: { replaceState() {} },
  matchMedia: () => ({ matches: false, addEventListener() {} }),
  addEventListener(type, handler) { (this.listeners[type] ??= []).push(handler); },
  listeners: {},
  confirm: () => { throw new Error('브라우저 기본 확인은 쓰지 않습니다'); },
  requestAnimationFrame: handler => handler(),
  setTimeout,
  clearTimeout
};

page.document = documentObject;
globalThis.document = documentObject;
globalThis.window = windowObject;

await import('../../main/resources/static/js/admin-shell.js');
await import('../../main/resources/static/js/admin-facility.js');

/* 1) 수정: 검증을 통과하면 확인 창이 열리고, 취소하면 아무 요청도 가지 않는다. */
documentObject.dispatch('click', editButton);
assert.equal(facilityModal.hidden, false, 'edit button must open the facility modal');
assert.equal(facilityForm.dataset.facilityMode, 'edit', 'edit mode must be remembered');
assert.equal(facilityForm.action, '/admin/facilities/1', 'edit must target the row facility ID');
assert.equal(facilityNameInput.value, '올림픽 체육관');

sportOptions[2].checked = true;
const editSubmit = documentObject.dispatch('submit', facilityForm);
assert.equal(editSubmit.defaultPrevented, true, 'update must wait for confirmation');
assert.equal(confirmModal.hidden, false, 'update confirmation must be visible');
assert.equal(confirmTitle.textContent, '시설 정보를 수정할까요?');
assert.equal(confirmName.textContent, '올림픽 체육관', 'confirmation must name the edited facility');
assert.equal(facilitySportsValue.value, '농구', 'sports value must be prepared before confirming');
assert.equal(facilityForm.requestSubmitCount, 0);

documentObject.dispatch('click', confirmCancel);
assert.equal(confirmModal.hidden, true, 'cancel must close the confirmation');
assert.equal(facilityForm.requestSubmitCount, 0, 'cancel must not submit the update');
assert.equal(facilityModal.hidden, false, 'cancel must keep the edit form open');

/* Escape는 확인 창만 닫고 수정 모달은 남긴다. */
documentObject.dispatch('submit', facilityForm);
assert.equal(confirmModal.hidden, false);
documentObject.dispatch('keydown', null, { key: 'Escape' });
assert.equal(confirmModal.hidden, true, 'Escape must close only the confirmation');
assert.equal(facilityModal.hidden, false, 'Escape must keep the underlying edit form open');
assert.equal(facilityForm.requestSubmitCount, 0, 'Escape must not submit the update');

/* 2) 수정: 확인하면 서버 폼이 정확히 한 번만 제출된다. */
documentObject.dispatch('submit', facilityForm);
documentObject.dispatch('click', confirmSubmit);
assert.equal(facilityForm.requestSubmitCount, 1, 'confirm must submit the update exactly once');
documentObject.dispatch('click', confirmSubmit);
assert.equal(facilityForm.requestSubmitCount, 1, 'a second confirm click must not submit again');
assert.equal(confirmModal.hidden, true);

/* 3) 삭제: 확인 창에 행의 시설 이름이 표시되고, 취소하면 POST가 발생하지 않는다. */
const deleteSubmit = documentObject.dispatch('submit', deleteForm);
assert.equal(deleteSubmit.defaultPrevented, true, 'delete must wait for confirmation');
assert.equal(confirmModal.hidden, false);
assert.equal(confirmTitle.textContent, '이 시설을 삭제할까요?');
assert.equal(confirmName.textContent, '올림픽 체육관');
assert.equal(confirmSubmit.textContent, '삭제');
assert.equal(deleteForm.requestSubmitCount, 0);

documentObject.dispatch('click', confirmCancel);
assert.equal(deleteForm.requestSubmitCount, 0, 'cancel must not delete the facility');

documentObject.dispatch('submit', deleteForm);
documentObject.dispatch('click', confirmSubmit);
assert.equal(deleteForm.requestSubmitCount, 1, 'confirm must delete the facility exactly once');

/* 4) 등록: 확인 없이 바로 저장한다. */
const openFacility = control('open-facility');
openFacility.setAttribute('data-open-facility', '');
documentObject.dispatch('click', openFacility);
assert.equal(facilityForm.dataset.facilityMode, 'create');
assert.equal(facilityForm.action, '/admin/facilities');
/* 실제 브라우저의 form.reset()은 체크박스를 해제하므로 등록 시에는 아무 종목도 선택되지 않은 상태다. */
sportOptions.forEach(input => { input.checked = false; });
sportOptions[0].checked = true;
const createSubmit = documentObject.dispatch('submit', facilityForm);
assert.equal(createSubmit.defaultPrevented, false, 'registration must submit without extra confirmation');
assert.equal(confirmModal.hidden, true, 'registration must not open the confirmation');
assert.equal(facilitySportsValue.value, '테니스');

/* 5) 검증 실패: 종목을 고르지 않으면 확인 창보다 먼저 막는다. */
documentObject.dispatch('click', editButton);
sportOptions.forEach(input => { input.checked = false; });
const invalidSubmit = documentObject.dispatch('submit', facilityForm);
assert.equal(invalidSubmit.defaultPrevented, true);
assert.equal(confirmModal.hidden, true, 'validation failure must not open the confirmation');
assert.equal(ids.get('admin-toast').dataset.kind, 'error');
assert.equal(facilityForm.requestSubmitCount, 1, 'validation failure must not submit the edit');

console.log('PASS: facility update/delete need confirmation, cancel sends nothing, confirm submits once, create and validation stay as before.');
