// Dependency-free execution check: bun src/test/frontend/match-join-payment.test.mjs
// 신청 화면: 무료 매치는 1단계에서 바로 제출하고, 유료 매치는 2단계에서 결제 준비를 보낸 뒤 카카오페이 결제 페이지로 이동하는지 확인합니다.
import assert from 'node:assert/strict';
import { readFile } from 'node:fs/promises';

const source = await readFile(new URL('../../main/resources/static/js/match-join-page.js', import.meta.url), 'utf8');

class Node {
  constructor(attributes = {}) {
    this.attributes = attributes;
    this.dataset = {};
    this.listeners = {};
    this.hidden = false;
    this.disabled = false;
    this.checked = false;
    this.value = attributes.value ?? '';
    this.textContent = '';
    this.classes = new Set();
    this.classList = {
      toggle: (name, on) => (on ? this.classes.add(name) : this.classes.delete(name)),
      add: name => this.classes.add(name),
      contains: name => this.classes.has(name)
    };
  }
  addEventListener(type, handler) { (this.listeners[type] ??= []).push(handler); }
  dispatch(type, event = {}) {
    let prevented = false;
    const payload = { preventDefault: () => { prevented = true; }, target: this, ...event };
    (this.listeners[type] ?? []).forEach(handler => handler(payload));
    return prevented;
  }
  setAttribute(name, value) { this.attributes[name] = value; }
  removeAttribute(name) { delete this.attributes[name]; }
  focus() {}
}

async function flush() {
  for (let i = 0; i < 10; i++) await Promise.resolve();
}

function setup({ paid, team = false, responses }) {
  const form = new Node();
  form.dataset = paid
    ? { paid: 'true', applicationType: team ? 'team' : 'individual', paymentPrepareUrl: '/matches/1/apply/payment/prepare',
        csrfHeader: 'X-CSRF-TOKEN', csrfToken: 'token' }
    : { paid: 'false', applicationType: 'individual' };
  const rulesAgree = new Node();
  const paymentAgree = new Node();
  const continueButton = new Node();
  const payButton = new Node();
  const backButton = new Node();
  const joinError = new Node();
  const paymentError = new Node();
  const stageIndex = new Node();
  const stageHeading = new Node();
  const panels = [new Node({ 'data-step-panel': '1' }), new Node({ 'data-step-panel': '2' })];
  panels[0].dataset.stepPanel = '1';
  panels[1].dataset.stepPanel = '2';
  panels[1].hidden = true;
  const indicators = ['1', '2', '3'].map(step => {
    const node = new Node();
    node.dataset.stepIndicator = step;
    node.querySelector = () => null;
    return node;
  });
  const clubId = new Node({ value: '5' });
  const members = [new Node({ value: '21' }), new Node({ value: '22' })];

  form.querySelector = selector => {
    if (selector === 'input[name="clubId"]') return team ? clubId : null;
    return null;
  };
  form.querySelectorAll = selector => (selector === 'input[name="clubMemberIds"]' && team ? members : []);

  const ids = {
    'join-form': form, 'stage-index': stageIndex, 'stage-heading': stageHeading, 'continue-button': continueButton,
    'back-button': backButton, 'rules-agree': rulesAgree, 'payment-agree': paymentAgree, 'join-error': joinError,
    'payment-error': paymentError, 'submit-payment': paid ? payButton : null
  };
  globalThis.document = {
    getElementById: id => ids[id] ?? null,
    querySelectorAll: selector => {
      if (selector === '[data-step-panel]') return panels;
      if (selector === '[data-step-indicator]') return indicators;
      return [];
    }
  };
  const calls = { fetch: [], assigned: null, alerts: [] };
  globalThis.window = {
    scrollTo() {},
    location: { assign: url => { calls.assigned = url; } },
    // 공용 실패 안내(ui-feedback.js): 서버가 거부한 결제는 원인을 모달로 보여 준다.
    SpoitFeedback: { alert: (title, reason, options) => calls.alerts.push({ title, reason, options }) }
  };
  let index = 0;
  globalThis.fetch = async (url, options) => {
    calls.fetch.push({ url, options, body: JSON.parse(options.body) });
    const response = responses[index++];
    return { ok: response.status === 200, redirected: false, json: async () => response.body };
  };
  new Function(source)();
  return { form, rulesAgree, paymentAgree, continueButton, payButton, backButton, joinError, paymentError,
    stageIndex, panels, calls };
}

const prepared = { status: 200, body: { ok: true, redirectUrl: 'https://online-pay.kakao.com/mockup/v1/abc/info' } };

// 1. 무료 매치: 동의하면 폼을 그대로 제출하고 버튼을 막는다.
{
  const page = setup({ paid: false, responses: [] });
  assert.equal(page.form.dispatch('submit'), true, '동의 전에는 제출을 막는다');
  assert.match(page.joinError.textContent, /동의해 주세요/);
  page.rulesAgree.checked = true;
  assert.equal(page.form.dispatch('submit'), false, '무료 매치는 폼을 제출한다');
  assert.equal(page.continueButton.disabled, true);
  assert.equal(page.calls.fetch.length, 0);
}

// 2. 유료 팀 매치: 1단계 제출은 막고 2단계로, 결제 준비 → 카카오페이 결제 페이지로 이동.
{
  const page = setup({ paid: true, team: true, responses: [prepared] });
  page.rulesAgree.checked = true;
  assert.equal(page.form.dispatch('submit'), true, '유료 매치는 폼을 제출하지 않는다');
  assert.equal(page.panels[1].hidden, false, '결제 단계를 연다');
  assert.equal(page.stageIndex.textContent, 'STEP 02 / 03');

  page.payButton.dispatch('click');
  await flush();
  assert.equal(page.calls.fetch.length, 0, '결제 동의 전에는 주문을 만들지 않는다');
  assert.match(page.paymentError.textContent, /결제 및 환불 안내에 동의/);

  page.paymentAgree.checked = true;
  page.payButton.dispatch('click');
  await flush();
  const prepare = page.calls.fetch[0];
  assert.equal(prepare.url, '/matches/1/apply/payment/prepare');
  assert.equal(prepare.options.headers['X-CSRF-TOKEN'], 'token');
  assert.deepEqual(prepare.body, { applicationType: 'team', clubId: 5, clubMemberIds: [21, 22],
    agreeRules: true, agreePayment: true }, '금액은 보내지 않는다(서버가 계산)');
  assert.equal(page.calls.assigned, 'https://online-pay.kakao.com/mockup/v1/abc/info');
  assert.equal(page.payButton.disabled, true, '이동하는 동안 다시 누를 수 없다');
}

// 3. 서버가 결제 준비를 거절하면 이동하지 않고 사유를 실패 안내 모달로 보여 주며 다시 누를 수 있다.
{
  const page = setup({ paid: true, responses: [{ status: 400, body: { ok: false, message: '정원이 모두 찼습니다.' } }] });
  page.rulesAgree.checked = true;
  page.paymentAgree.checked = true;
  page.payButton.dispatch('click');
  await flush();
  assert.equal(page.calls.assigned, null);
  assert.deepEqual(page.calls.alerts.map(({ title, reason }) => ({ title, reason })),
    [{ title: '결제를 시작할 수 없습니다', reason: '정원이 모두 찼습니다.' }]);
  assert.equal(page.calls.alerts[0].options.returnFocus, page.payButton, '모달을 닫으면 결제 버튼으로 돌아간다');
  assert.equal(page.paymentError.textContent, '', '서버 거부 사유는 입력 옆 문구로 남기지 않는다');
  assert.equal(page.payButton.disabled, false);
}

console.log('PASS: free matches submit directly; paid matches go to step 2, prepare on the server without sending amounts, then move to the KakaoPay page.');
