// Dependency-free execution check: bun src/test/frontend/admin-facility-schedule.test.mjs
// 예약 운영 일정 화면의 사전 안내 동작만 확인합니다. 실제 시간·충돌 검증은 서버가 합니다.
// - 전체 요일 휴무는 클라이언트에서 막지 않는다.
// - 시작과 종료가 같은 시각은 보내지 않고, 종료가 이르면 다음 날 운영으로 허용한다.
// - 서버가 돌려준 충돌 경고는 전체 목록을 그대로 열고, 닫으면 화면 안 조작 요소로 초점을 되돌린다.
// - 시설을 바꿔도 보고 있던 탭이 유지된다.
// - 날짜 슬롯을 모두 해제한 제출은 휴무 저장이므로 막지 않고 확인만 받는다.
import assert from 'node:assert/strict';
// DOM 스텁 공통부는 support/dom-stub.mjs가 준비합니다(Element/HTMLFormElement 포함).
import { DomElement, DomFormElement } from './support/dom-stub.mjs';

const Control = DomElement;

/* 이 테스트만의 능력: 값 선택/체크 시 change 이벤트를 함께 발생시킵니다. */
class Select extends Control {
  constructor(properties = {}) { super({ type: 'select', ...properties }); }
  select(value) {
    this.value = value;
    this.dispatch('change');
  }
}

class Checkbox extends Control {
  constructor(properties = {}) { super({ type: 'checkbox', ...properties }); }
  toggle(checked) {
    this.checked = checked;
    this.dispatch('change');
  }
}

/* 이 테스트만의 능력: 폼을 id 문자열로 만들고, 스크립트의 instanceof 판정을 위해
   HTMLFormElement 체인을 유지합니다. */
class Form extends DomFormElement {
  constructor(id) {
    super({ id });
    /* 이 테스트의 폼은 클래스 토글을 쓰지 않습니다. */
    this.classList = { add() {}, remove() {}, contains: () => false, toggle() {} };
  }
  /* 원래 이 테스트의 폼은 closest 탐색을 하지 않습니다. */
  closest() { return null; }
}

const ids = new Map();
function control(id, properties = {}, Ctor = Control) {
  const element = new Ctor({ id, ...properties });
  ids.set(id, element);
  return element;
}

/* ── 주간 폼 ─────────────────────────────────────────────── */
const weeklyForm = new Form('weekly-hours-form');

const weekdays = [];
for (let day = 1; day <= 7; day += 1) {
  const openDay = new Checkbox({ name: 'openDays', value: String(day), checked: false });
  const start = new Select({ name: 'start-' + day, value: '00:00', disabled: true });
  const end = new Select({ name: 'end-' + day, value: '00:00', disabled: true });
  const status = new Control({ textContent: '휴무' });
  const note = new Control({ textContent: '휴무' });
  const row = new Control({ dataset: { weeklyHoursRow: String(day) } });
  row.querySelector = selector => selector === 'input[name="openDays"]' ? openDay
    : selector === 'select[name^="start-"]' ? start
      : selector === 'select[name^="end-"]' ? end
        : selector === '[data-weekly-hours-status]' ? status
          : selector === '[data-weekly-hours-note]' ? note : null;
  weekdays.push({ row, openDay, start, end, status, note });
}

/* ── 날짜 폼 ─────────────────────────────────────────────── */
const hourSlots = ['08:00', '09:00', '10:00'].map(time => new Checkbox({ name: 'hours', value: time }));
const dateGrid = new Control({ id: 'date-schedule-hour-grid' });
dateGrid.append(...hourSlots);
dateGrid.querySelectorAll = selector => selector === 'input[name="hours"]:checked'
  ? hourSlots.filter(slot => slot.checked) : [];
dateGrid.querySelector = selector => selector === 'input[name="hours"]:checked'
  ? hourSlots.find(slot => slot.checked) ?? null
  : selector === 'input[name="hours"]:not(:disabled)' ? hourSlots.find(slot => !slot.disabled) ?? null : null;
ids.set('date-schedule-hour-grid', dateGrid);

control('date-schedule-count', { textContent: '0시간 선택' });
control('date-schedule-ranges', { textContent: '선택된 시간이 없습니다.' });
const dateSaveState = control('date-schedule-save-state', { textContent: '변경은 서버에 저장됩니다.' });
control('date-schedule-selected-label', { textContent: '2026년 11월 3일 (화)' });
control('date-schedule-note', { name: 'note', value: '' });

const dateFormElement = new (class extends Form {
  constructor() { super('date-schedule-form'); }
})();
const dateHidden = new Control({ name: 'date', value: '' });
dateHidden.parentElement = dateFormElement;
dateFormElement.querySelector = selector => selector === 'input[name="date"]' ? dateHidden
  : selector === 'input[name="hours"]:not(:disabled)' ? hourSlots[0] : null;
ids.set('date-schedule-form', dateFormElement);

/* ── 휴관 폼 ─────────────────────────────────────────────── */
const closureStart = control('closure-start', { name: 'start', value: '' });
const closureEnd = control('closure-end', { name: 'end', value: '' });
control('closure-date', { name: 'date', value: '' });
control('closure-reason', { name: 'reason', value: '' });
const closureHint = control('closure-range-hint', { innerHTML: '<b>24:00</b> 안내', textContent: '' });
const closureForm = new Form('closure-form');

/* ── 모집 조건 ───────────────────────────────────────────── */
const sportTrigger = control('condition-sport', { name: 'sportId', value: '1' }, Select);
const sportForm = control('condition-sport-form', { submitCount: 0, submit() { this.submitCount += 1; } });
control('condition-capacity', { name: 'capacity', value: '10' });
control('condition-note', { name: 'note', value: '' });
const conditionSportValue = control('condition-sport-value', { name: 'sportId', value: '1' }, Control);
control('condition-save-state', { textContent: '저장하면 이 종목의 새 예약에 적용됩니다.' });
control('condition-sport-state', { textContent: '' });
const conditionsForm = new Form('conditions-form');
ids.set('conditions-form', conditionsForm);

/* ── 탭 ──────────────────────────────────────────────────── */
const tabInput = control('schedule-tab-input', { value: '' });
tabInput.setAttribute('data-schedule-tab-input', '');
const hoursTab = new Control({ dataset: { scheduleTab: 'schedule-hours' } });
hoursTab.setAttribute('aria-selected', 'true');
const conditionsTab = new Control({ dataset: { scheduleTab: 'schedule-conditions' } });
conditionsTab.setAttribute('aria-selected', 'false');
const changesTab = new Control({ dataset: { scheduleTab: 'schedule-changes' } });
changesTab.setAttribute('aria-selected', 'false');
const scheduleTabs = [hoursTab, conditionsTab, changesTab];

/* ── 화면(탭 패널) ───────────────────────────────────────── */
const hoursPanel = new Control({ id: 'schedule-hours' });
const conditionsPanel = new Control({ id: 'schedule-conditions', hidden: true });
const changesPanel = new Control({ id: 'schedule-changes', hidden: true });
hoursPanel.querySelector = selector => selector === '[data-weekly-hours-row]' ? weekdays[0].row : null;
const scheduleView = new Control({ id: 'facility-schedule' });
scheduleView.querySelectorAll = selector => selector === '[data-schedule-panel]'
  ? [hoursPanel, conditionsPanel, changesPanel] : [];
ids.set('facility-schedule', scheduleView);

/* ── 충돌 경고 ───────────────────────────────────────────── */
const conflictModal = control('schedule-conflict-modal', { hidden: true, dataset: { autoOpen: 'true' } });
const conflictCloseButton = control('schedule-conflict-close');
conflictCloseButton.setAttribute('data-close-schedule-conflict', '');
conflictCloseButton.setAttribute('type', 'button');

const body = new Control({ id: 'body' });

const documentObject = {
  body,
  activeElement: body,
  listeners: {},
  getElementById: id => ids.get(id),
  querySelector: selector => selector === '[data-schedule-tab-input]' ? tabInput
    : selector === '[data-schedule-tab][aria-selected="true"]'
      ? scheduleTabs.find(tab => tab.getAttribute('aria-selected') === 'true') ?? null
      : null,
  querySelectorAll: selector => selector === '#weekly-hours-form tbody tr' ? weekdays.map(day => day.row)
    : selector === '[data-schedule-tab]' ? scheduleTabs
      : [],
  addEventListener(type, handler) { (this.listeners[type] ??= []).push(handler); },
  dispatch(type, target, extra = {}) {
    const event = { target, defaultPrevented: false, preventDefault() { this.defaultPrevented = true; }, ...extra };
    for (const handler of this.listeners[type] ?? []) handler(event);
    return event;
  }
};

const toasts = [];
const trapCalls = [];
const windowObject = {
  confirm: () => true,
  PlayOnAdmin: {
    toast(message, isError) { toasts.push({ message, isError }); },
    openModal(modal, focusSelector) {
      modal.hidden = false;
      modal.focusSelector = focusSelector;
      documentObject.activeElement = conflictCloseButton;
    },
    closeModal(modal) { modal.hidden = true; },
    trapFocus(modal, event) { trapCalls.push({ modal, key: event.key }); }
  }
};

globalThis.document = documentObject;
globalThis.window = windowObject;

await import('../../main/resources/static/js/admin-facility.js');

/* 1) 전체 요일 휴무: 어떤 요일도 선택하지 않아도 제출을 막지 않는다. */
weekdays.forEach(day => { day.openDay.checked = false; });
const allClosedSubmit = documentObject.dispatch('submit', weeklyForm);
assert.equal(allClosedSubmit.defaultPrevented, false,
  'all-week closure must be submitted: it is a valid setting');
assert.equal(toasts.length, 0, 'all-week closure must not raise an error toast');

/* 2) 같은 시각은 거부한다. */
weekdays[0].openDay.checked = true;
weekdays[0].start.value = '10:00';
weekdays[0].end.value = '10:00';
const sameTimeSubmit = documentObject.dispatch('submit', weeklyForm);
assert.equal(sameTimeSubmit.defaultPrevented, true, 'identical start/end must be blocked before POST');
assert.equal(toasts.at(-1).isError, true);
assert.equal(weekdays[0].end.focused, true, 'the invalid end select must receive focus');

/* 3) 종료가 이르면 다음 날 운영으로 허용하고, 행에 그 사실을 표시한다. */
weekdays[0].start.select('22:00');
weekdays[0].end.select('06:00');
assert.equal(weekdays[0].note.textContent, '다음 날 06:00까지');
assert.equal(weekdays[0].note.classes.has('is-next-day'), true);
const overnightSubmit = documentObject.dispatch('submit', weeklyForm);
assert.equal(overnightSubmit.defaultPrevented, false, 'overnight hours must be allowed');

/* 4) 24:00 종료는 자정 경계로 안내하고 오류로 보지 않는다. */
weekdays[1].openDay.toggle(true);
weekdays[1].start.value = '22:00';
weekdays[1].end.select('24:00');
assert.equal(weekdays[1].note.classes.has('is-error'), false, '24:00 must not be treated as identical times');
assert.match(weekdays[1].note.textContent, /24:00/);

/* 5) 자동으로 열린 충돌 경고: 열린 상태로 시작하고, 닫으면 화면 안 요소로 초점을 되돌린다. */
assert.equal(conflictModal.hidden, false, 'conflicts from the server must open the alert automatically');

/* Tab은 모달 안에서만 순환한다. */
const tabEvent = documentObject.dispatch('keydown', conflictCloseButton, { key: 'Tab' });
assert.equal(tabEvent.defaultPrevented, false, 'Tab must be left to the focus trap, not swallowed');
assert.equal(trapCalls.length, 1, 'Tab inside the alert must be trapped in the dialog');
assert.equal(trapCalls[0].modal, conflictModal);

/* Enter로 확인 버튼이 눌리면(브라우저 기본 동작) 닫히고 초점은 화면 조작 요소로 돌아간다. */
documentObject.dispatch('click', conflictCloseButton);
assert.equal(conflictModal.hidden, true, 'confirm must close the alert');
assert.equal(body.focused, false, 'focus must not fall back to body');
assert.equal(weekdays[0].openDay.focused, true,
  'focus must return to the logical control of the current schedule screen');

/* Escape도 같은 방식으로 닫는다. */
conflictModal.hidden = false;
weekdays[0].openDay.focused = false;
const escapeEvent = documentObject.dispatch('keydown', conflictCloseButton, { key: 'Escape' });
assert.equal(escapeEvent.defaultPrevented, true);
assert.equal(conflictModal.hidden, true, 'Escape must close the alert');
assert.equal(weekdays[0].openDay.focused, true, 'Escape must also restore focus inside the screen');

/* 다시 열리지 않도록 자동 열림 표시는 한 번만 쓴다. */
assert.equal(conflictModal.dataset.autoOpen, undefined, 'auto-open flag must be consumed once');

/* 6) 탭 유지: 기본은 예약 시간이고, 탭을 바꾸면 시설 전환 폼의 값도 바뀐다. */
assert.equal(tabInput.value, 'schedule-hours', 'the default tab must be preserved on facility switch');
hoursTab.setAttribute('aria-selected', 'false');
conditionsTab.setAttribute('aria-selected', 'true');
documentObject.dispatch('click', conditionsTab);
assert.equal(tabInput.value, 'schedule-conditions', 'the selected tab must be carried into the facility switch form');

/* 7) 날짜 슬롯 0개 제출: 휴무 저장이므로 막지 않고 확인만 받는다. */
hourSlots.forEach(slot => { slot.checked = false; });
const emptySubmit = documentObject.dispatch('submit', dateFormElement);
assert.equal(emptySubmit.defaultPrevented, false, 'empty selection must still be submittable as a closed day');
assert.equal(dateSaveState.textContent.includes('휴무'), true,
  'empty selection must be announced as a closed day');

/* 확인에서 취소하면 전송하지 않는다. */
windowObject.confirm = () => false;
const emptyDenied = documentObject.dispatch('submit', dateFormElement);
assert.equal(emptyDenied.defaultPrevented, true, 'declining the closed-day confirmation must stop the POST');
windowObject.confirm = () => true;

/* 8) 휴관 같은 시각 거부, 24:00 종료는 허용. */
closureStart.value = '10:00';
closureEnd.value = '10:00';
const closureSame = documentObject.dispatch('submit', closureForm);
assert.equal(closureSame.defaultPrevented, true, 'closure with identical start/end must be blocked');

closureStart.value = '22:00';
closureEnd.value = '24:00';
closureEnd.dispatch('change');
assert.equal(closureHint.textContent.includes('24:00'), true, 'the closure hint must name the 24:00 boundary');
const closureOk = documentObject.dispatch('submit', closureForm);
assert.equal(closureOk.defaultPrevented, false, 'closure ending at 24:00 must be allowed');

/* 9) 모집 조건 종목 전환: 편집이 없으면 서버에 저장된 그 종목 조건을 다시 조회한다. */
sportTrigger.select('2');
assert.equal(sportForm.submitCount, 1, 'switching sport must re-read stored conditions from the server');

/* 저장 폼은 화면에서 고른 종목을 히든 값으로 보낸다. */
conditionsForm.dispatch('submit');
assert.equal(conditionSportValue.value, '2', 'the save form must post the selected sport');

console.log('PASS: all-week closure stays allowed, identical times are blocked, overnight and 24:00 are allowed, the conflict alert traps Tab and restores focus inside the screen, tabs carry over, and a closed-day submission is confirmed.');
