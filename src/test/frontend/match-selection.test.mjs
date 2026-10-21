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
  const option = new Control({ dataset: { teamIndex: '10', leaderAuto: 'true', leaderIsApplicant: 'false' } });
  const members = [
    new Control({ name: memberField, value: '101', dataset: { leaderOnly: 'true' } }),
    new Control({ name: memberField, value: '102', dataset: { applicant: 'true' } }),
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
  assert.deepEqual(members.map(member => member.disabled), [true, true, false]);
  assert.equal(get('selection-submit').disabled, false); // Applicant + auto-included leader fit.
  trace.push(snapshot());

  members[2].checked = true;
  roster.dispatch('change', members[2]);
  assert.equal(get('selection-submit').disabled, true);
  assert.equal(form.dispatch('submit').defaultPrevented, true); // Three people exceed two seats.
  trace.push(snapshot());

  members[2].checked = false;
  roster.dispatch('change', members[2]);
  assert.equal(form.dispatch('submit').defaultPrevented, false);
  trace.push(snapshot());

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
console.log('PASS: individual/team selection, capacity limits, excluded roster members, submit fields and close behavior.');
