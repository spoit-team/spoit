// Dependency-free interaction checks: bun src/test/frontend/match-selection.test.mjs
import assert from 'node:assert/strict';
import { readFile } from 'node:fs/promises';

class Control {
  constructor(properties = {}) {
    Object.assign(this, { hidden: false, disabled: false, checked: false, value: '', dataset: {}, textContent: '' }, properties);
    this.listeners = {};
    this.attributes = {};
    this.classes = new Set();
    this.classList = {
      add: name => this.classes.add(name),
      remove: name => this.classes.delete(name),
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
  focus() {}
  reportValidity() { return true; }
  querySelector() { return null; }
  querySelectorAll() { return []; }
}

function exercise(source, legacy = false) {
  const clubField = legacy ? 'teamId' : 'clubId';
  const memberField = legacy ? 'memberIds' : 'clubMemberIds';
  const applicationField = legacy ? 'kind' : 'applicationType';
  const ids = new Map();
  for (const id of [
    'selection-dialog', 'selection-form', 'applicant-phone', 'selection-kind', 'individual-panel',
    'team-panel', 'team-step-select', 'team-step-roster', 'team-step-indicator-1', 'team-step-indicator-2',
    'team-options', 'team-selected-name', 'team-selected-count', 'selection-capacity-notice',
    'selection-error', 'selection-back', 'selection-submit', 'selection-dialog-heading', 'selection-dialog-lede'
  ]) ids.set(id, new Control());
  const get = id => ids.get(id);
  const dialog = get('selection-dialog');
  dialog.open = false;
  dialog.showModal = () => { dialog.open = true; };
  dialog.close = () => { dialog.open = false; };
  const applicationType = get('selection-kind');
  applicationType.name = applicationField;
  const radio = new Control({ name: clubField, value: '10' });
  const option = new Control({ dataset: { teamIndex: '10' } });
  // RES-04: 101은 이미 이 매치에 신청한 회원이라 체크된 채 잠겨 있다. 동호회장 자동 포함은 없다.
  const members = [
    new Control({ name: memberField, value: '101', checked: true, dataset: { applied: 'true' } }),
    new Control({ name: memberField, value: '102' }),
    new Control({ name: memberField, value: '103' })
  ];
  option.querySelector = selector => selector === 'b' ? new Control({ textContent: '테스트 팀' }) : radio;
  radio.closest = () => option;
  radio.matches = selector => selector === `input[name="${clubField}"]`;
  const roster = new Control({ dataset: { teamRoster: '10' } });
  roster.querySelectorAll = selector => members.filter(member =>
    !selector.includes(':checked') || (member.checked && (!selector.includes(':disabled') || !member.disabled)));
  roster.querySelector = () => members.find(member => !member.disabled) ?? null;
  const options = get('team-options');
  options.querySelector = selector => selector.includes(':checked') && !radio.checked ? null : radio;
  options.querySelectorAll = selector => selector === '.md-team-option' ? [option] : [radio];
  const openers = ['individual', 'team'].map(kind => new Control({ dataset: { openSelection: kind } }));
  const document = {
    currentScript: { dataset: { capacity: '4', availableSeats: '2' } },
    getElementById: get,
    querySelector: selector => selector === '[data-team-roster="10"]' ? roster : null,
    querySelectorAll: selector => ({
      '[data-team-roster]': [roster], '[data-open-selection]': openers, '[data-close-dialog]': []
    })[selector] ?? []
  };
  new Function('document', source)(document);
  const form = get('selection-form');
  const snapshot = () => ({
    type: applicationType.value,
    buttonDisabled: get('selection-submit').disabled,
    count: get('team-selected-count').textContent,
    notice: get('selection-capacity-notice').textContent,
    fields: [applicationType, radio, ...members]
      .filter(input => !input.disabled && (input === applicationType || input.checked))
      .map(input => [input.name === clubField ? 'clubId' : input.name === memberField ? 'clubMemberIds' : 'applicationType', input.value])
  });
  const trace = [];

  openers[0].dispatch('click');
  assert.equal(dialog.open, true);
  assert.equal(radio.disabled, true);
  assert.equal(get('selection-submit').disabled, false);
  trace.push(snapshot());

  openers[1].dispatch('click');
  assert.equal(get('selection-submit').disabled, true);
  radio.checked = true;
  options.dispatch('change', radio);
  assert.equal(get('selection-submit').disabled, false);
  assert.equal(form.dispatch('submit').defaultPrevented, true); // First team step only advances.
  assert.deepEqual(members.map(member => member.disabled), [true, false, false]);
  assert.equal(members[0].checked, true); // Already applied: stays checked and locked.
  assert.equal(get('selection-submit').disabled, true); // Applicant alone is not a team.
  assert.equal(get('team-selected-count').textContent, '총 1명 · 신청자 포함');
  trace.push(snapshot());

  members[1].checked = true;
  roster.dispatch('change', members[1]);
  assert.equal(get('selection-submit').disabled, false); // Applicant + one member fit two seats.
  assert.equal(get('team-selected-count').textContent, '총 2명 · 신청자 포함');

  members[2].checked = true;
  roster.dispatch('change', members[2]);
  assert.equal(get('selection-submit').disabled, true);
  assert.equal(form.dispatch('submit').defaultPrevented, true); // Three people exceed two seats.
  trace.push(snapshot());

  members[2].checked = false;
  roster.dispatch('change', members[2]);
  assert.equal(form.dispatch('submit').defaultPrevented, false);
  // The locked, already-applied member is not submitted.
  assert.deepEqual(snapshot().fields, [['applicationType', 'team'], ['clubId', '10'], ['clubMemberIds', '102']]);
  trace.push(snapshot());

  options.dispatch('change', radio); // Re-selecting the club clears picks but keeps the applied lock.
  assert.deepEqual(members.map(member => member.checked), [true, false, false]);
  members[1].checked = true;
  roster.dispatch('change', members[1]);

  openers[0].dispatch('click');
  assert.deepEqual(snapshot().fields, [['applicationType', 'individual']]);
  trace.push(snapshot());
  get('selection-back').dispatch('click');
  assert.equal(dialog.open, false);
  return trace;
}

const filename = 'src/main/resources/static/js/match-detail-page.js';
const currentTrace = exercise(await readFile(filename, 'utf8'));
const baseline = process.argv.find(argument => argument.startsWith('--baseline='))?.split('=')[1];
if (baseline) {
  const previous = Bun.spawnSync(['git', 'show', `${baseline}:${filename}`]);
  assert.equal(previous.exitCode, 0, 'Baseline source must exist');
  assert.deepEqual(currentTrace, exercise(previous.stdout.toString(), true));
}
console.log('PASS: individual/team selection, capacity limits, already-applied members locked, submit fields and close behavior.');
