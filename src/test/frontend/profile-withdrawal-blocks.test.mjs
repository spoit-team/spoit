// Run from the repository root: bun src/test/frontend/profile-withdrawal-blocks.test.mjs
// 회원 탈퇴 확인창의 차단 상태만 확인합니다. 서버는 withdrawalBlocks(blocked·leaderClubNames·
// teamMatchClubNames·individualMatchNames)를 항상 내려주고, 화면은 그 값으로 비밀번호 입력과
// 확인 버튼을 잠급니다. 스크립트가 그 잠금을 풀지 않는지, 막히지 않았을 때는 기존 동작 그대로인지 봅니다.
import assert from 'node:assert/strict';
import { readFile } from 'node:fs/promises';

globalThis.Element ??= class Element {};

class Control extends Element {
  constructor(properties = {}) {
    super();
    Object.assign(this, {
      hidden: false, disabled: false, value: '', dataset: {}, textContent: '',
      id: '', open: false, className: ''
    }, properties);
    this.listeners = {};
    this.attributes = {};
  }
  addEventListener(type, handler) { (this.listeners[type] ??= []).push(handler); }
  setAttribute(name, value) { this.attributes[name] = String(value); }
  getAttribute(name) { return this.attributes[name] ?? null; }
  focus() { documentObject.activeElement = this; }
  matches(selector) { return selector === '[data-close-withdrawal]' && this.attributes['data-close-withdrawal'] !== undefined; }
  closest(selector) { return this.matches(selector) ? this : null; }
  querySelectorAll() { return documentObject.focusables; }
  showModal() { this.open = true; }
  close() { this.open = false; }
  dispatch(type, event = {}) {
    let prevented = false;
    const payload = { target: this, ...event, preventDefault() { prevented = true; } };
    for (const handler of this.listeners[type] ?? []) handler(payload);
    return { ...payload, prevented, defaultPrevented: prevented };
  }
}

const ids = new Map();
const control = (id, properties = {}) => {
  const element = new Control({ id, ...properties });
  ids.set(id, element);
  return element;
};

const openButton = control('open-withdrawal-dialog');
const dialog = control('profile-withdrawal-dialog');
const form = control('profile-withdrawal-form');
const password = control('withdrawal-password');
const error = control('withdrawal-error', { hidden: true });
const confirmButton = control('confirm-withdrawal', { disabled: true });
const blocks = control('withdrawal-blocks');
const closeButton = control('withdrawal-close');
const teamsLink = control('withdrawal-teams-link');
const historyLink = control('withdrawal-history-link');
closeButton.setAttribute('data-close-withdrawal', '');

const documentObject = {
  activeElement: openButton,
  focusables: [closeButton, teamsLink, historyLink, password, confirmButton],
  getElementById: id => ids.get(id),
  querySelector: () => null,
  querySelectorAll: () => [],
  listeners: {},
  addEventListener(type, handler) { (this.listeners[type] ??= []).push(handler); },
  dispatch(type, target, extra = {}) {
    let prevented = false;
    const event = { target, ...extra, preventDefault() { prevented = true; } };
    for (const handler of this.listeners[type] ?? []) handler(event);
    return { prevented, event };
  }
};

globalThis.document = documentObject;
globalThis.window = { addEventListener() {} };

await import('../../main/resources/static/js/profile-edit.js');

const open = () => openButton.dispatch('click');

/* 1) 막히지 않은 상태: 지금과 같은 동작이어야 한다. */
open();
assert.equal(dialog.open, true, '열기 버튼은 대화상자를 연다');
assert.equal(documentObject.activeElement, password, '막히지 않았으면 비밀번호 입력으로 초점이 간다');
assert.equal(confirmButton.disabled, true, '확인 버튼은 비어 있는 동안 잠겨 있다');

password.value = 'secret';
password.dispatch('input');
assert.equal(confirmButton.disabled, false, '비밀번호를 입력하면 확인 버튼이 활성화된다');

const filledSubmit = form.dispatch('submit');
assert.equal(filledSubmit.prevented, false, '값이 있으면 서버 제출을 막지 않는다');

password.value = '   ';
password.dispatch('input');
const blankSubmit = form.dispatch('submit');
assert.equal(blankSubmit.prevented, true, '공백만 입력하면 제출하지 않는다');
assert.equal(error.hidden, false, '공백 안내 문구를 보여 준다');

/* 닫으면 열었던 버튼으로 초점이 돌아온다. */
documentObject.dispatch('click', closeButton);
assert.equal(dialog.open, false, '닫기 버튼은 대화상자를 닫는다');
assert.equal(documentObject.activeElement, openButton, '닫으면 열었던 버튼으로 초점이 돌아온다');

/* 2) 막힌 상태: 서버 렌더링 값이 잠금을 결정한다. */
dialog.dataset.withdrawalBlocked = 'true';
password.disabled = true;
confirmButton.disabled = true;

open();
assert.equal(dialog.open, true);
assert.equal(documentObject.activeElement, blocks, '막혔으면 사유 목록으로 초점이 간다');
assert.equal(confirmButton.disabled, true, '막혔으면 확인 버튼은 잠겨 있다');
assert.equal(error.hidden, true, '열 때 이전 안내 문구는 숨긴다');

/* 스크립트가 잠금을 풀지 않는다: 입력이 들어와도 확인 버튼이 되살아나지 않는다. */
password.value = 'secret';
password.dispatch('input');
assert.equal(confirmButton.disabled, true, '막힌 상태에서는 입력해도 확인 버튼이 되살아나지 않는다');

const blockedSubmit = form.dispatch('submit');
assert.equal(blockedSubmit.prevented, true, '막힌 상태에서는 제출하지 않는다');
assert.equal(error.hidden, true, '제출을 막아도 오류 문구를 새로 띄우지 않는다');

/* 비활성 입력이 아니더라도 blocked 값이 서면 제출을 막는다. */
password.disabled = false;
const blockedSubmitWithoutDisabled = form.dispatch('submit');
assert.equal(blockedSubmitWithoutDisabled.prevented, true, 'blocked 값만으로도 제출을 막는다');

/* 3) 탭 이동은 새 링크까지 포함해 대화상자 안에서만 돈다. */
documentObject.activeElement = historyLink;
const middle = dialog.dispatch('keydown', { key: 'Tab', shiftKey: false });
assert.equal(middle.prevented, false, '중간 요소에서는 기본 탭 이동을 막지 않는다');
documentObject.activeElement = confirmButton;
const wrapped = dialog.dispatch('keydown', { key: 'Tab', shiftKey: false });
assert.equal(wrapped.prevented, true, '마지막 요소에서 Tab은 첫 요소로 되돌린다');
assert.equal(documentObject.activeElement, closeButton, '첫 요소는 닫기 버튼이다');
documentObject.activeElement = closeButton;
const wrappedBack = dialog.dispatch('keydown', { key: 'Tab', shiftKey: true });
assert.equal(wrappedBack.prevented, true, '첫 요소에서 Shift+Tab은 마지막 요소로 되돌린다');
assert.equal(documentObject.activeElement, confirmButton, '마지막 요소는 확인 버튼이다');

/* 4) Escape는 막힌 상태에서도 대화상자를 닫는다. */
documentObject.dispatch('keydown', null, { key: 'Escape' });
assert.equal(dialog.open, false, 'Escape는 대화상자를 닫는다');

/* ── 템플릿 계약: 서버 값만으로 잠그고, 세 사유를 모두 보여 준다. ─────��─── */
const template = await readFile('src/main/resources/templates/mypage/profile.html', 'utf8');
const slice = (open, close) => {
  const start = template.indexOf(open);
  assert.ok(start >= 0, `${open} 마크업이 있어야 한다`);
  const end = template.indexOf(close, start);
  return template.slice(start, end < 0 ? template.length : end);
};

const dialogMarkup = slice('id="profile-withdrawal-dialog"', '<script');
assert.match(dialogMarkup, /th:attr="data-withdrawal-blocked=\$\{withdrawalBlocks != null and withdrawalBlocks\.blocked\} \? 'true' : null"/,
  '잠금은 서버의 withdrawalBlocks.blocked로만 정해진다');
assert.match(dialogMarkup, /id="withdrawal-blocks"[\s\S]*?role="group"/,
  '사유 목록은 이름 있는 그룹이어야 한다');
assert.match(dialogMarkup, /aria-labelledby="withdrawal-blocks-title"/,
  '사유 목록은 제목과 연결돼야 한다');
assert.match(dialogMarkup, /id="withdrawal-blocks"[\s\S]*?tabindex="-1"/,
  '사유 목록은 초점을 받을 수 있어야 한다');
assert.match(dialogMarkup, /th:if="\$\{withdrawalBlocks != null and withdrawalBlocks\.blocked\}"/,
  '사유 목록은 막혔을 때만 그린다');

for (const [field, label] of [
  ['leaderClubNames', '동호회장인 팀은 위임하거나 해체해 주세요'],
  ['teamMatchClubNames', '모집 중인 팀 매치에 신청되어 있어요'],
  ['individualMatchNames', '종료 상태가 아닌 매치에 신청되어 있어요']
]) {
  assert.ok(dialogMarkup.includes(`withdrawalBlocks.${field}`), `${field} 사유를 그려야 한다`);
  assert.ok(dialogMarkup.includes(label), `${field} 사유의 안내 문구가 있어야 한다`);
  assert.ok(dialogMarkup.includes(`#lists.size(withdrawalBlocks.${field})`), `${field} 개수를 보여 줘야 한다`);
  assert.ok(dialogMarkup.includes(`#strings.listJoin(withdrawalBlocks.${field}, ' · ')`),
    `${field} 이름을 이어서 보여 줘야 한다`);
}

/* 개인 매치 안내 문구는 사실과 맞아야 한다.
   실제 경기는 끝났어도 결과·평가 처리 전에는 저장 상태가 '매칭 완료'로 남는다. */
assert.match(dialogMarkup, /경기가 끝났더라도 결과 처리 전에는 <strong>매칭 완료<\/strong> 상태일 수 있습니다/,
  '끝난 경기도 결과 처리 전에는 매칭 완료로 남을 수 있음을 알려야 한다');
assert.doesNotMatch(dialogMarkup, /아직 끝나지 않은 상태/,
  '매칭 완료를 미종료로 단정하지 않는다');
assert.doesNotMatch(dialogMarkup, /아직 종료되지 않은 매치/,
  '행 라벨도 미종료로 단정하지 않는다');
// 종료 전환은 결과 제출·평가 처리에 달려 있어 시간을 보장할 수 없다. 기간·자동 처리 약속을 넣지 않는다.
assert.doesNotMatch(dialogMarkup, /종료 시각이 지나면|자동으로 종료|스케줄|24시간|며칠|이내에|즉시 종료/,
  '시간·자동 처리에 대한 보장을 화면에 쓰지 않는다');

/* 종료 상태 안내는 개인 매치 사유에만 해당한다. 팀 사유만 있으면 위임·취소로 풀 수 있으므로 붙이지 않는다. */
const noteTag = dialogMarkup.match(/<p class="profile-withdrawal-block-note"[^>]*>/)?.[0] ?? '';
assert.ok(noteTag.length > 0, '개인 매치 안내 문구가 있어야 한다');
assert.match(noteTag, /th:if="\$\{!#lists\.isEmpty\(withdrawalBlocks\.individualMatchNames\)\}"/,
  '종료 상태 안내는 individualMatchNames가 있을 때만 그린다');
assert.match(dialogMarkup, /해당 매치가 종료 상태가 되거나 신청이 취소되면 이 제한이 해제됩니다/,
  '안내가 개인 매치 신청에 대한 것임을 밝힌다');

/* 링크: 팀 사유는 팀 관리, 개인 매치 사유는 신청 내역으로 보낸다. */
assert.match(dialogMarkup, /th:href="@\{\/teams\/manage\}"[\s\S]*?팀 관리로 가기/,
  '팀 사유는 팀 관리로 이어져야 한다');
assert.match(dialogMarkup, /th:href="@\{\/mypage#reservations\}"[\s\S]*?신청 내역 보기/,
  '개인 매치 사유는 신청 내역으로 이어져야 한다');
assert.doesNotMatch(dialogMarkup, /<button[^>]*>\s*팀 관리로 가기/,
  '사유 해결은 이동 링크여야 하고 제출 버튼이면 안 된다');

/* 잠금 대상과 기존 폼 계약은 그대로다. */
assert.match(dialogMarkup, /id="withdrawal-password"[^>]*th:disabled="\$\{withdrawalBlocks != null and withdrawalBlocks\.blocked\}"/,
  '비밀번호 입력은 막혔을 때 비활성이다');
assert.match(dialogMarkup, /id="withdrawal-password"[^>]*name="password"/, '비밀번호 필드는 유지된다');
assert.match(dialogMarkup, /th:action="@\{\/mypage\/withdrawal-requests\}"/, '탈퇴 경로는 유지된다');
assert.match(dialogMarkup, /th:name="\$\{_csrf\.parameterName\}"/, 'CSRF 숨김 필드는 유지된다');
assert.match(dialogMarkup, /id="confirm-withdrawal"[^>]*type="submit"[^>]*disabled/, '확인 버튼은 제출 버튼으로 남는다');
assert.match(dialogMarkup, /id="withdrawal-error"[^>]*role="alert"/, '오류 안내는 role=alert를 유지한다');

/* 안내 문구도 상태에 맞게 바뀐다. */
assert.match(dialogMarkup, /탈퇴를 막는 항목을 정리한 뒤 비밀번호를 입력할 수 있습니다/,
  '막혔을 때는 입력할 수 없다고 안내한다');
assert.match(dialogMarkup, /비밀번호를 입력하면 확인 버튼이 활성화됩니다/,
  '막히지 않았을 때의 기존 안내는 유지한다');

console.log('PASS: profile withdrawal blocks — locked input/submit, three blocker groups, preserved form contract');
