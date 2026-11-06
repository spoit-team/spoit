// Run from the repository root: bun src/test/frontend/check-contracts.mjs
// Optional: --baseline=HEAD also checks that markup and literal routes stay intact.
import assert from 'node:assert/strict';
import { readFile } from 'node:fs/promises';
import path from 'node:path';

const resources = path.resolve('src/main/resources');
const baseline = process.argv.find(argument => argument.startsWith('--baseline='))?.split('=')[1];
const failures = [];
const formNames = new Set();
const namedForms = new Map();
let assertions = 0;

function check(condition, message) {
  assertions++;
  if (!condition) failures.push(message);
}

async function files(pattern) {
  return Array.fromAsync(new Bun.Glob(pattern).scan({ cwd: resources }));
}

async function inspectHtml(html) {
  const elements = [];
  const forms = [];
  let currentForm = null;
  const response = new HTMLRewriter()
    .on('*', {
      element(element) {
        const attributes = Object.fromEntries(element.attributes);
        elements.push({ tag: element.tagName, attributes });
        if (element.tagName === 'form') {
          currentForm = { attributes, inputs: [] };
          forms.push(currentForm);
          element.onEndTag(() => { currentForm = null; });
        }
        if (currentForm && ['input', 'select', 'textarea'].includes(element.tagName)) {
          currentForm.inputs.push(attributes);
        }
      }
    })
    .transform(new Response(html));
  await response.text();
  return { elements, forms };
}

const invariantAttributes = [
  'id', 'class', 'type', 'method', 'role', 'hidden', 'required', 'disabled',
  'min', 'max', 'minlength', 'maxlength', 'pattern', 'multiple', 'autocomplete'
];

function markupSignature(elements) {
  return elements.map(({ tag, attributes }) => [
    tag,
    invariantAttributes.map(name => attributes[name] ?? null),
    Object.keys(attributes).filter(name => name.startsWith('data-')).sort()
  ]);
}

function literalRoutes(elements) {
  return elements.flatMap(({ attributes }) => Object.entries(attributes)
    .filter(([name]) => ['th:action', 'th:href', 'th:src'].includes(name))
    .flatMap(([, value]) => {
      const route = value.match(/@\{(\/[^\s(]*)/);
      if (!route) return [];
      return [route[1].replace(/\{[A-Za-z]\w*\}/g, '{}').replace(/\}$/, '')];
    }));
}

const scripts = await files('static/js/*.js');
for (const file of scripts) {
  const source = await readFile(path.join(resources, file), 'utf8');
  try {
    new Bun.Transpiler({ loader: 'js' }).transformSync(source);
    check(true, file);
  } catch (error) {
    check(false, `${file}: ${error.message}`);
  }
}

const styles = await files('static/css/*.css');
const css = await Bun.build({
  entrypoints: styles.map(file => path.join(resources, file)),
  target: 'browser',
  write: false
});
check(css.success, `CSS syntax: ${css.logs.join('\n')}`);

// 관리자 화면 전환 시 admin-shell.js가 .admin-view로 초점을 옮긴다.
// 뷰 전체를 감싸는 기본 초점 테두리는 끄고, 키보드일 때 제목에 표시를 남긴다.
const adminCss = await readFile(path.join(resources, 'static/css/admin.css'), 'utf8');
check(/\.admin-view:focus,\s*\.admin-view:focus-visible\s*\{[^}]*outline:\s*none/.test(adminCss),
  'admin.css: .admin-view must not draw the full-viewport focus ring');
check(/\.admin-view:focus-visible\s+\.page-heading-copy\s*\{[^}]*outline:\s*(?!none\b|0)\S/.test(adminCss),
  'admin.css: keyboard focus on .admin-view must mark the page title instead');
// sticky topbar가 앵커 점프 대상의 첫 부분을 가리지 않게 한다(관리자 페이지 진입 시 화면이 살짝 내려가 보이던 문제).
check(/html\s*\{[^}]*scroll-padding-top:\s*\d+px/.test(adminCss),
  'admin.css: anchor jumps must account for the sticky topbar via scroll-padding-top');

const templates = await files('templates/**/*.html');
for (const file of templates) {
  const source = await readFile(path.join(resources, file), 'utf8');
  const { elements, forms } = await inspectHtml(source);
  for (const { tag, attributes } of elements) {
    if (['input', 'select', 'textarea'].includes(tag) && attributes.name) formNames.add(attributes.name);
  }
  for (const form of forms) {
    if (form.attributes.id) namedForms.set(form.attributes.id, form);
  }
  const ids = new Set();
  for (const { attributes } of elements) {
    if (attributes.id) {
      // Fragment variants can deliberately reuse IDs in mutually exclusive branches.
      if (!file.replaceAll('\\', '/').startsWith('templates/fragments/')) {
        check(!ids.has(attributes.id), `${file}: duplicate static ID ${attributes.id}`);
      }
      ids.add(attributes.id);
    }
    for (const name of ['th:href', 'th:src']) {
      // 캐시 버스팅 파라미터(v=...)가 붙은 참조도 자산 존재 검사 대상에 포함한다.
      // 괄호 안에 ${...} 같은 표현식이 와도 URL 경로 부분만 뽑아낸다.
      const reference = attributes[name]?.match(/^@\{\/(css|js|images)\/([^{}()]+?)(?:\(.+\))?\}$/);
      if (reference) {
        const asset = path.join(resources, 'static', reference[1], reference[2].split('(')[0]);
        check(await Bun.file(asset).exists(), `${file}: missing asset ${asset}`);
      }
    }
  }
  for (const form of forms) {
    check(Boolean(form.attributes['th:action']), `${file}: form missing th:action`);
    if (form.attributes.method?.toLowerCase() !== 'post') continue;
    check(form.inputs.some(input => input['th:name'] === '${_csrf.parameterName}'
      && input['th:value'] === '${_csrf.token}'), `${file}: POST form missing CSRF binding`);
  }
  if (baseline) {
    const result = Bun.spawnSync(['git', 'show', `${baseline}:src/main/resources/${file.replaceAll('\\', '/')}`]);
    check(result.exitCode === 0, `${file}: baseline unavailable`);
    if (result.exitCode !== 0) continue;
    const previous = await inspectHtml(result.stdout.toString());
    try {
      assert.deepEqual(markupSignature(elements), markupSignature(previous.elements));
      assert.deepEqual(literalRoutes(elements), literalRoutes(previous.elements));
      check(true, file);
    } catch {
      check(false, `${file}: structural markup, validation attributes, DOM hooks or literal routes changed`);
    }
  }
}

const requiredBindings = {
  'selection-form': ['applicationType', 'clubId', 'clubMemberIds'],
  'team-create-form': ['clubName', 'sportId', 'regionId'],
  'team-transfer-form': ['clubMemberId'],
  'team-remove-form': ['clubMemberId'],
  'team-disband-form': ['clubNameConfirmation'],
  'facility-form': ['concurrentMatchLimit'],
  'room-editor-form': ['facilityId', 'sportId', 'teamCapacity', 'feePerPerson'],
  'profile-form': ['nickname', 'regionId', 'representativeSportId'],
  'signup-form': ['nickname', 'regionId'],
  // 예약 운영 일정: 주간·날짜별·모집 조건·휴관 저장 폼의 필수 바인딩입니다.
  'weekly-hours-form': ['facilityId'],
  'date-schedule-form': ['facilityId', 'date', 'hours', 'note'],
  'date-schedule-reset-form': ['facilityId', 'date'],
  'condition-sport-form': ['facilityId', 'sportId'],
  'conditions-form': ['facilityId', 'sportId', 'capacity', 'note'],
  'closure-form': ['facilityId', 'date', 'start', 'end', 'reason']
};
if (namedForms.has('team-edit-form')) {
  requiredBindings['team-edit-form'] = ['clubName', 'regionId'];
}
for (const [id, names] of Object.entries(requiredBindings)) {
  const form = namedForms.get(id);
  check(Boolean(form), `Missing form ${id}`);
  for (const name of names) {
    check(Boolean(form?.inputs.some(input => input.name === name)), `${id}: missing binding ${name}`);
  }
}
for (const file of scripts) {
  const source = await readFile(path.join(resources, file), 'utf8');
  for (const selector of source.matchAll(/(?:input|select|textarea)\[name=["']([A-Za-z]\w*)["']\]/g)) {
    check(formNames.has(selector[1]), `${file}: selector references missing form field ${selector[1]}`);
  }
}

/* ── 회원 탈퇴 확인창 계약 ────────────────────────────────────────────────
   서버는 withdrawalBlocks(blocked·leaderClubNames·individualMatchNames)를
   항상 내려주고, 화면은 그 값으로만 비밀번호 입력과 확인 버튼을 잠근다. 사유가 늘거나
   잠금 조건이 화면에서 임의로 풀리면 사용자가 막힌 줄 모르고 탈퇴를 시도하므로 여기서 고정한다. */
const profileSource = await readFile(path.join(resources, 'templates/mypage/profile.html'), 'utf8');
const withdrawalDialog = profileSource.slice(
  profileSource.indexOf('id="profile-withdrawal-dialog"'),
  profileSource.indexOf('<script', profileSource.indexOf('id="profile-withdrawal-dialog"')));
check(withdrawalDialog.length > 0, 'profile.html: withdrawal dialog missing');
// 잠금은 서버 값 하나로만 정해진다. 클라이언트 임의 판단이나 사유별 분기를 두지 않는다.
check(/th:attr="data-withdrawal-blocked=\$\{withdrawalBlocks != null and withdrawalBlocks\.blocked\} \? 'true' : null"/.test(withdrawalDialog),
  'withdrawal dialog: lock state must come from withdrawalBlocks.blocked');
check(/id="withdrawal-blocks"[\s\S]*?role="group"[\s\S]*?aria-labelledby="withdrawal-blocks-title"/.test(withdrawalDialog),
  'withdrawal blocks: the blocker list must be a labelled group');
check(/id="withdrawal-blocks"[\s\S]*?tabindex="-1"/.test(withdrawalDialog),
  'withdrawal blocks: the blocker list must be able to take focus when the input is disabled');
for (const [field, phrase] of [
  ['leaderClubNames', '동호회에 가입되어 있어요'],
  ['individualMatchNames', '종료 상태가 아닌 매치에 신청되어 있어요']
]) {
  check(withdrawalDialog.includes(`withdrawalBlocks.${field}`),
    `withdrawal blocks: ${field} must be rendered`);
  check(withdrawalDialog.includes(`#lists.size(withdrawalBlocks.${field})`),
    `withdrawal blocks: ${field} must show its count`);
  check(withdrawalDialog.includes(`#strings.listJoin(withdrawalBlocks.${field}, ' · ')`),
    `withdrawal blocks: ${field} must list the names`);
  check(withdrawalDialog.includes(phrase), `withdrawal blocks: ${field} needs its guidance phrase`);
}
// 사실 관계: 실제 경기가 끝났어도 결과·평가 처리 전에는 저장 상태가 '매칭 완료'로 남는다.
// 저장 상태는 '종료'만 허용 기준이고, 종료 시각·자동 처리·기간 보장은 화면에서 약속하지 않는다.
check(/경기가 끝났더라도 결과 처리 전에는 <strong>매칭 완료<\/strong> 상태일 수 있습니다/.test(withdrawalDialog),
  'withdrawal blocks: 매칭 완료 must be described as possibly-already-played');
check(!/아직 끝나지 않은 상태|아직 종료되지 않은 매치/.test(withdrawalDialog),
  'withdrawal blocks: must not claim the match has not finished');
check(!/종료 시각이 지나면|자동으로 종료|스케줄|24시간|이내에|즉시 종료/.test(withdrawalDialog),
  'withdrawal blocks: no scheduler/end-time/duration promise in the guidance');
// 종료 상태 안내는 매치 신청(개인·동호회) 사유에만 참이다. 동호회 가입 사유만 있으면 동호회 탈퇴로 풀 수 있다.
const withdrawalNoteTag = withdrawalDialog.match(/<p class="profile-withdrawal-block-note"[^>]*>/)?.[0] ?? '';
check(withdrawalNoteTag.includes('th:if="${!#lists.isEmpty(withdrawalBlocks.individualMatchNames)}"'),
  'withdrawal blocks: the 종료 guidance must render only for the individual blocker');
check(/해당 매치가 종료 상태가 되거나 신청이 취소되면 이 제한이 해제됩니다/.test(withdrawalDialog),
  'withdrawal blocks: the note must scope itself to the individual applications');
check(/id="withdrawal-password"[^>]*th:disabled="\$\{withdrawalBlocks != null and withdrawalBlocks\.blocked\}"/.test(withdrawalDialog),
  'withdrawal dialog: the password input must be disabled while blocked');
check(/id="withdrawal-error"[^>]*role="alert"/.test(withdrawalDialog),
  'withdrawal dialog: the password error must stay a live region');
check(/th:href="@\{\/teams\/manage\}"/.test(withdrawalDialog)
  && /th:href="@\{\/mypage#reservations\}"/.test(withdrawalDialog),
  'withdrawal blocks: team and individual blockers must link to their own fix screens');
// 필드를 숫자로 되돌리거나 임의로 늘리지 않는다.
check(namedForms.get('profile-withdrawal-form')?.inputs.some(input => input.name === 'password'),
  'profile-withdrawal-form: the password field must stay the only payload');
const withdrawalScript = await readFile(path.join(resources, 'static/js/profile-edit.js'), 'utf8');
check(withdrawalScript.includes('withdrawalIsBlocked'),
  'profile-edit.js: the blocked state must gate the dialog');
check(!/withdrawalConfirm\.disabled\s*=\s*false/.test(withdrawalScript),
  'profile-edit.js: the confirm button must never be force-enabled');
check(withdrawalScript.includes('withdrawalBlocks?.focus'),
  'profile-edit.js: a blocked dialog must move focus to the blocker list');

// 레이아웃: 비밀번호 입력이 대화상자 안쪽 여백을 넘지 않고, 사유가 길어져도 버튼이 보여야 한다.
const mypageCss = await readFile(path.join(resources, 'static/css/mypage.css'), 'utf8');
check(/\.profile-withdrawal-dialog-content \.auth-input\s*\{[^}]*box-sizing:\s*border-box/.test(mypageCss),
  'mypage.css: the withdrawal password input must not overflow the dialog padding');
check(/\.profile-withdrawal-actions\s*\{[^}]*position:\s*sticky/.test(mypageCss),
  'mypage.css: the withdrawal actions must stay reachable while the dialog scrolls');
check(/\.profile-withdrawal-block-label\s*\{[^}]*word-break:\s*keep-all/.test(mypageCss),
  'mypage.css: Korean blocker labels must not break mid-word');

/* ── 예약 운영 일정 화면 계약 ──────────────────────────────────────────────
   시설 관리 일정 화면은 백엔드 lane과 모델 키/폼 필드/경로를 공유한다.
   마크업이 조용히 어긋나면 저장 요청이 빈 값으로 나가므로 여기서 명시적으로 고정한다. */
const facilitySource = await readFile(path.join(resources, 'templates/admin/facility.html'), 'utf8');
// 일정 카드 마크업은 조각으로 분리되어 있고, 시설 선택 폼만 facility.html에 남는다.
const scheduleCardSource = await readFile(path.join(resources, 'templates/fragments/schedule-card.html'), 'utf8');
const scheduleMarkup = facilitySource + scheduleCardSource;
const facilityScheduleForm = namedForms.get('weekly-hours-form');
const facilityDateForm = namedForms.get('date-schedule-form');
const facilityClosureForm = namedForms.get('closure-form');
const facilityConditionsForm = namedForms.get('conditions-form');

// 1) 경로: 시설·날짜·종목·탭을 함께 넘기고, 저장 엔드포인트는 기존 경로를 유지한다.
check(facilitySource.includes('name="view" value="schedule"'),
  'facility.html: schedule view must be reachable with view=schedule');
for (const route of [
  '@{/admin/facility/schedule/weekly}',
  '@{/admin/facility/schedule/date}',
  '@{/admin/facility/schedule/date/reset}',
  '@{/admin/facility/schedule/conditions}',
  '@{/admin/facility/schedule/closures}',
  '@{/admin/facility/schedule/closures/{scheduleChangeId}/cancel(scheduleChangeId=${closure.scheduleChangeId})}'
]) {
  check(scheduleMarkup.includes(route), `facility.html: missing schedule route ${route}`);
}

// 2) 주간 폼: 7일 체크박스와 동적 start-<day>/end-<day> 이름, 24:00 종료 후보.
check(facilityScheduleForm?.inputs.some(input => input.name === 'openDays'
  && input['th:value'] === '${row.day}'), 'weekly-hours-form: openDays must post the weekday number');
check(facilityScheduleForm?.inputs.some(input => input['th:name'] === "${'start-' + row.day}"),
  'weekly-hours-form: start-<day> select must keep its dynamic name');
check(facilityScheduleForm?.inputs.some(input => input['th:name'] === "${'end-' + row.day}"),
  'weekly-hours-form: end-<day> select must keep its dynamic name');
check(scheduleMarkup.includes('<option value="24:00"'),
  'facility.html: weekly/closure end must offer an explicit 24:00 option');
check(scheduleMarkup.includes('#numbers.sequence(0, 23)'),
  'facility.html: hour options must cover 00:00–23:00 without depending on the server model');
check(facilityScheduleForm?.inputs.some(input => input['th:disabled'] === '${!row.open}'),
  'weekly-hours-form: closed weekday selects must be disabled so they are not submitted');
check(scheduleMarkup.includes('data-weekly-hours-note'),
  'facility.html: weekly rows must show the end-of-day / next-day note');

// 3) 날짜 폼: ISO date 히든, hours 반복, note. 전체 해제는 휴무라는 안내를 유지한다.
check(facilityDateForm?.inputs.some(input => input.name === 'date' && input.type !== 'date'),
  'date-schedule-form: date must be posted as a hidden ISO value');
check(facilityDateForm?.inputs.some(input => input.name === 'hours' && input.type === 'checkbox'),
  'date-schedule-form: hours must stay hour checkboxes');
check(scheduleMarkup.includes('id="date-schedule-night-help"')
  && scheduleMarkup.includes('id="date-schedule-blocked-help"'),
  'facility.html: date editor must explain cross-day editing and locked closure slots');
check(scheduleMarkup.includes('date-schedule-blocked-help'),
  'facility.html: locked slots must explain that the stored value is restored after cancellation');
check(facilityDateForm !== undefined && scheduleMarkup.includes('id="date-schedule-save-state"'),
  'facility.html: date form must surface the empty-selection (closed day) state');

// 4) 모집 조건: 종목 조회 폼과 저장 폼이 분리되고, 저장 폼은 sportId를 히든으로 보낸다.
check(facilityConditionsForm?.inputs.some(input => input.name === 'sportId' && input.type === 'hidden'),
  'conditions-form: sportId must be posted as a hidden value chosen by the server model');
check(namedForms.get('condition-sport-form')?.attributes.method?.toLowerCase() === 'get',
  'condition-sport-form: switching sports must re-read stored conditions with GET');
check(facilityConditionsForm?.inputs.some(input => input.name === 'capacity'
  && input.min === '2' && input.max === '200'),
  'conditions-form: capacity must stay limited to 2–200');

// 5) 휴관: 시각·사유 입력과 취소 확인을 유지하고 24:00 종료를 제공한다.
check(facilityClosureForm?.inputs.some(input => input.name === 'end' && input.required !== undefined),
  'closure-form: end time must stay required');
check(/data-confirm="[^"]*휴관/.test(scheduleMarkup),
  'facility.html: cancelling a closure must ask for confirmation');

// 5-1) 새 구조의 POST 폼도 모두 CSRF 숨김 필드를 유지한다(공통 검사와 별도로 명시).
for (const id of ['weekly-hours-form', 'date-schedule-form', 'date-schedule-reset-form',
  'conditions-form', 'closure-form']) {
  const form = namedForms.get(id);
  check(form?.attributes.method?.toLowerCase() === 'post', `${id}: must stay a POST form`);
  check(form?.inputs.some(input => input['th:name'] === '${_csrf.parameterName}'
    && input['th:value'] === '${_csrf.token}'), `${id}: missing CSRF binding`);
}
// 날짜 초기화·휴관 취소는 폼 하나에 대상 ID만 다른 독립 요청이어야 한다.
check(facilityDateForm?.attributes.method?.toLowerCase() === 'post'
  && namedForms.get('date-schedule-reset-form') !== facilityDateForm,
  'facility.html: resetting a date must be a separate POST from saving it');

// 6) 충돌 경고: 안전한 alertdialog, 전체 충돌 목록, 저장 우회/자동 취소 선택지 없음.
const conflictModal = facilitySource.slice(
  facilitySource.indexOf('id="schedule-conflict-modal"'),
  facilitySource.indexOf('id="facility-delete-blocked-modal"'));
check(conflictModal.length > 0, 'facility.html: schedule conflict modal missing');
check(conflictModal.includes('role="alertdialog"'),
  'schedule-conflict-modal: must stay an alertdialog');
check(conflictModal.includes('th:attr="data-auto-open=${scheduleConflicts != null and !#lists.isEmpty(scheduleConflicts)'),
  'schedule-conflict-modal: auto-open must depend on a non-empty scheduleConflicts list');
check(conflictModal.includes('th:each="conflict : ${scheduleConflicts}"'),
  'schedule-conflict-modal: every conflicting match must be rendered');
check(conflictModal.includes('${conflict.matchId}'),
  'schedule-conflict-modal: each room ID must stay visible even when a title exists');
check(conflictModal.includes('conflict.title'),
  'schedule-conflict-modal: each room title must be rendered');
check(conflictModal.includes('conflict.startAt') && conflictModal.includes('conflict.endAt'),
  'schedule-conflict-modal: each room start/end must be rendered');
check(conflictModal.includes('${scheduleConflictRange}'),
  'schedule-conflict-modal: the requested range must be rendered with th:text');
check(!/type="submit"/.test(conflictModal) && !/data-force/.test(conflictModal),
  'schedule-conflict-modal: no bypass or auto-cancel action is allowed');

// 6-1) 시설 삭제 차단 경고: 일정 충돌 경고와 같은 alertdialog로 막는 매칭방 전체를 보여 준다.
const deleteBlockModal = facilitySource.slice(
  facilitySource.indexOf('id="facility-delete-blocked-modal"'),
  facilitySource.indexOf('th:replace="~{fragments/admin :: toast}"'));
check(deleteBlockModal.length > 0, 'facility.html: facility delete blocked modal missing');
check(deleteBlockModal.includes('role="alertdialog"'),
  'facility-delete-blocked-modal: must stay an alertdialog');
check(deleteBlockModal.includes(
  'th:attr="data-auto-open=${deleteBlockConflicts != null and !#lists.isEmpty(deleteBlockConflicts)'),
  'facility-delete-blocked-modal: auto-open must depend on a non-empty deleteBlockConflicts list');
check(deleteBlockModal.includes('th:each="conflict : ${deleteBlockConflicts}"'),
  'facility-delete-blocked-modal: every blocking match must be rendered');
check(deleteBlockModal.includes('${conflict.matchId}') && deleteBlockModal.includes('conflict.title'),
  'facility-delete-blocked-modal: each room ID and title must be rendered');
check(deleteBlockModal.includes('conflict.startAt') && deleteBlockModal.includes('conflict.endAt'),
  'facility-delete-blocked-modal: each room start/end must be rendered');
check(deleteBlockModal.includes('deleteBlockSummary'),
  'facility-delete-blocked-modal: the blocking room summary must be rendered with th:text');
check(deleteBlockModal.includes('deleteBlockMessage'),
  'facility-delete-blocked-modal: the server guidance message must be rendered inside the modal');
// 삭제 차단 안내는 모달 안에만 표시한다: 컨트롤러가 error가 아니라 deleteBlockMessage로 전달하는지 검사한다.
const adminControllerSource = await readFile(path.resolve('src/main/java/co/kr/spoit/facility/controller/FacilityAdminController.java'), 'utf8');
const deleteBlockCatch = adminControllerSource.slice(
  adminControllerSource.indexOf('catch (FacilityDeleteBlockedException'),
  adminControllerSource.indexOf('catch (IllegalArgumentException | FacilityAccessDeniedException | FacilityNotFoundException', adminControllerSource.indexOf('catch (FacilityDeleteBlockedException')));
check(deleteBlockCatch.length > 0 && deleteBlockCatch.includes('deleteBlockMessage') && !deleteBlockCatch.includes('"error"'),
  'facility delete blocked: guidance must be flash-only via deleteBlockMessage, not the shared error flash');
check(deleteBlockModal.includes('data-close-facility-delete-blocked'),
  'facility-delete-blocked-modal: the confirm button must close the dialog');
check(!/type="submit"/.test(deleteBlockModal) && !/data-force/.test(deleteBlockModal),
  'facility-delete-blocked-modal: no bypass or auto-cancel action is allowed');

// 7) 탭 복귀: 시설을 바꿔도 보던 탭을 유지한다.
check(facilitySource.includes('data-schedule-tab-input'),
  'facility.html: facility switch form must carry the current schedule tab');

// 8) 스크립트: 전체 요일 휴무를 막지 않고, 같은 시각만 막는다.
const facilityScript = await readFile(path.join(resources, 'static/js/admin-facility.js'), 'utf8');
check(!facilityScript.includes('운영할 요일을 하나 이상 선택'),
  'admin-facility.js: all-week closure must not be blocked on the client');
check(facilityScript.includes('findInvalidWeeklyRow'),
  'admin-facility.js: identical start/end must be rejected before POST');
check(facilityScript.includes("id === 'schedule-conflict-modal'")
  || facilityScript.includes("getElementById('schedule-conflict-modal')"),
  'admin-facility.js: conflict modal must be wired');
check(facilityScript.includes('scheduleReturnTarget'),
  'admin-facility.js: closing the auto-opened alert must restore focus inside the screen');
check(facilityScript.includes("getElementById('facility-delete-blocked-modal')")
  && facilityScript.includes('deleteBlockedReturnTarget'),
  'admin-facility.js: facility delete blocked modal must be wired and restore focus');
// 달력(날짜·월 이동)은 GET 링크지만 JS가 있으면 조각 교체로 처리해 전체 새로고침을 막는다.
// 시설이 선택되지 않았을 때는 서버가 렌더링한 빈 상태가 유일한 화면이므로 기본 이동에 맡긴다.
check(facilityScript.includes("closest('a.date-calendar-day, .calendar-month-nav a')")
  && facilityScript.includes('url.searchParams.get(\'date\')'),
  'admin-facility.js: calendar day/month links must swap the schedule card without a full reload');
check(facilityScript.includes("target?.closest('#date-schedule-hour-grid')"),
  'admin-facility.js: date slot summary must be delegated so fragment swaps keep it working');

/* ── 대시보드 시설 현황 계약 ──────────────────────────────────────────────
   대시보드에서 실제로 연결된 것은 담당 시설 전체의 매칭방 현황과 참가 신청 피드뿐이다.
   민원·요청/점검·유지보수 화면은 제거되었으므로 그 흔적이 다시 생기면 여기서 막는다. */
const dashboardSource = fragmentSlice(facilitySource, 'id="facility-dashboard"', 'id="facility-assets"');
check(Boolean(dashboardSource), 'facility.html: dashboard view missing');
// 섹션 표시 여부는 스크립트가 아니라 서버가 view로 결정한다. 하드코딩 hidden이면
// POST/Redirect/Get 진입 시 JS 실행 전에 대시보드가 먼저 그려졌다가 바뀌는 깜빡임이 생긴다.
for (const [id, key] of [['facility-dashboard', 'dashboard'], ['facility-assets', 'assets'],
  ['facility-schedule', 'schedule'], ['facility-bookings', 'bookings'],
  ['facility-stats', 'stats'], ['facility-content', 'content']]) {
  check(new RegExp(`<section class="admin-view" id="${id}"[^>]*th:hidden="\\$\\{view != '${key}'\\}"`)
    .test(facilitySource),
    `facility.html: ${id} must be hidden by the server view model, not a hardcoded hidden`);
}
for (const field of ['dashboard?.matchCount', 'dashboard?.recruitingCount',
  'dashboard?.hourlyBars', 'dashboard?.recentMatches', 'todayLabel', 'scopeName']) {
  check(dashboardSource?.includes(field), `facility.html: dashboard must render ${field}`);
}
// 24칸 차트는 시각별 실제 방 수를 title·aria-label로 알린다(상대 높이만으로는 값을 알 수 없다).
check(dashboardSource?.includes('bar.matchCount') && dashboardSource?.includes('bar.completedCount'),
  'facility.html: each hourly bar must expose its real match/completed counts');
check(/th:aria-label="\$\{bar\.label[^"]*bar\.matchCount/.test(dashboardSource ?? ''),
  'facility.html: hourly bars need an aria-label with the hour and counts');
// 지표는 실제 집계되는 두 값만 남는다. 없는 값을 0건이나 '연동 전'으로 그리지 않는다.
for (const removed of ['openRequestCount', 'maintenanceCount', '미처리 요청', '점검 예정',
  '연동 전', 'is-unlinked', 'metric-tag']) {
  check(!dashboardSource?.includes(removed),
    `facility.html: removed dashboard metric must not come back (${removed})`);
}
check(dashboardSource?.includes('<div class="metric-grid two-metrics">'),
  'facility.html: the dashboard must lay out exactly two metrics in one row');
check((dashboardSource?.match(/class="card metric-card"/g) ?? []).length === 2,
  'facility.html: the dashboard must render exactly two metric cards');
// 두 칸짜리 지표 줄은 좁은 화면에서 한 칸씩 쌓이므로, 세로 구분선 대신 가로 구분선을 쓴다.
const twoMetricRule = adminCss.match(/\.metric-grid\.two-metrics\s*\{([^}]*)\}/)?.[1] ?? '';
check(/grid-template-columns:\s*repeat\(2,minmax\(0,1fr\)\)/.test(twoMetricRule),
  'admin.css: two metrics must fill one row on wide screens');
check(/\.metric-grid\.two-metrics \.metric-card:nth-child\(-n\+2\)\s*\{\s*border-bottom:\s*0/.test(adminCss),
  'admin.css: a two-cell metric row must not draw a bottom divider under its only row');
check(/@media \(max-width: 600px\)[\s\S]*\.metric-grid\.two-metrics\s*\{\s*grid-template-columns:\s*1fr/.test(adminCss),
  'admin.css: two metrics must stack into one column on narrow screens');
check(!/@\{[^}]*view='requests'/.test(dashboardSource ?? ''),
  'facility.html: the dashboard must not link to the removed requests view');
// 최근 요청 카드는 담당 시설 매칭방의 참가 신청(모든 상태, 최신 5건)으로 채운다.
// 서버 계약은 dashboard.recentRequests의 여섯 필드이며, 이 카드는 조회 전용 상세로만 이어진다.
const recentRequestFields = ['matchId', 'matchTitle', 'facilityName', 'applicantName',
  'createdAtLabel', 'statusLabel', 'statusTone'];
check(dashboardSource?.includes('th:each="recentRequest : ${dashboard?.recentRequests}"'),
  'facility.html: recent requests must be listed from dashboard.recentRequests');
for (const field of recentRequestFields) {
  check(dashboardSource?.includes(`recentRequest.${field}`),
    `facility.html: recent request item must render recentRequest.${field}`);
}
check(dashboardSource?.includes('#lists.isEmpty(dashboard?.recentRequests)'),
  'facility.html: the recent request feed needs an honest empty state');
check(/최신 5건/.test(dashboardSource ?? ''),
  'facility.html: the recent request card must state that it shows at most 5 items');
check(/모든 상태/.test(dashboardSource ?? ''),
  'facility.html: the recent request card must state that every application status is included');
check(/팀 신청[^<]*1건/.test(dashboardSource ?? ''),
  'facility.html: the recent request card must state that a team application counts as one');
check(/담당 시설[^<]{0,20}한정|담당 시설 매칭방/.test(dashboardSource ?? ''),
  'facility.html: the recent request card must state the manager-scoped range');
// 승인·처리가 구현된 것처럼 보이지 않게 한다: 이 카드에는 폼·제출 버튼이 없다.
const recentRequestCard = dashboardSource?.slice(
  dashboardSource.indexOf('<h2>최근 요청</h2>'),
  dashboardSource.indexOf('</article>', dashboardSource.indexOf('<h2>최근 요청</h2>'))) ?? '';
check(recentRequestCard.length > 0, 'facility.html: recent request card missing');
check(!/<form\b/.test(recentRequestCard) && !/type="submit"/.test(recentRequestCard),
  'facility.html: the recent request card must not offer approve/process actions');
check((recentRequestCard.match(/@{/g) ?? []).length === 1
  && recentRequestCard.includes('@{/admin/operations(matchId=${recentRequest.matchId})}'),
  'facility.html: each recent request must deep-link to its read-only room detail only');
check(dashboardSource?.includes('@{/admin/operations(matchId=${match.matchId})}'),
  'facility.html: recent matches must keep their detail link');
check(dashboardSource?.includes('data-open-room-editor'),
  'facility.html: the dashboard must keep the room editor button');
// 두 목록의 첫 열은 방 제목이 먼저·크게(cell-primary), 방 번호는 아래 보조 정보(cell-secondary)다.
const roomIdentityCell = '<span class="cell-primary" th:text="${match.title}">방 이름</span>'
  + '<span class="cell-secondary" th:text="${match.matchId}">방 번호</span>';
check(dashboardSource?.includes(roomIdentityCell),
  'facility.html: recent matches must list the room title first and the room number as secondary text');
check(!dashboardSource?.includes('<span class="cell-primary" th:text="${match.matchId}">'),
  'facility.html: recent matches must not lead with the room number');

/* ── 매칭방 조회·상세 화면 계약 ────────────────────────────────────────────
   시설 관리자는 담당 시설의 매칭방을 조회하고, 상세 화면에서 신청·참가자만 읽습니다.
   필터 값이 어긋나면 Spring Long 바인딩이 실패하고, 상세 화면에 없는 변경 조작이
   다시 생기면 서버에 없는 동작을 사용자에게 약속하게 되므로 여기서 고정한다. */
const bookingsSource = fragmentSlice(facilitySource, 'id="facility-bookings"', 'id="facility-stats"');
check(Boolean(bookingsSource), 'facility.html: bookings view missing');
check(bookingsSource?.includes('name="view" value="bookings"'),
  'facility.html: bookings filter must post view=bookings to stay on the matching list');
for (const field of ['name="q"', 'name="status"', 'name="sportId"']) {
  check(Boolean(bookingsSource?.includes(field)),
    `facility.html: bookings filter must keep the ${field} input`);
}
check(bookingsSource?.includes('${bookingFilters?.q}'),
  'facility.html: the search term must be restored from bookingFilters');
check(bookingsSource?.includes('${bookingFilters?.status =='),
  'facility.html: the status filter must restore the selected option');
// 'matched'는 DB 값이 '매칭 완료'일 뿐 A/B 팀 배정을 뜻하지 않는다. 표시 문구가 그보다 앞서면 안 된다.
check(bookingsSource?.includes('>매칭 완료</option>'),
  'facility.html: the matched status must be labelled 매칭 완료');
check(!/팀 배정 완료/.test(bookingsSource ?? ''),
  'facility.html: the matched status must not claim team assignment');
// 종목 목록은 담당 시설의 예약 종목(bookingSports)에서 온다. 'all' 문자열은 Long 바인딩을 깨뜨린다.
check(bookingsSource?.includes('th:each="sport : ${bookingSports}"'),
  'facility.html: the sport filter must list bookingSports');
check(bookingsSource?.includes('<option value=""'),
  'facility.html: "all sports" must submit an empty value so Spring can bind sportId as Long');
const sportFilterMarkup = bookingsSource?.slice(
  bookingsSource.indexOf('id="booking-sport-filter"'),
  bookingsSource.indexOf('</select>', bookingsSource.indexOf('id="booking-sport-filter"')) + '</select>'.length) ?? '';
check(sportFilterMarkup.length > 0 && !sportFilterMarkup.includes('value="all"'),
  'facility.html: the sport filter must not send a non-numeric value');
check(bookingsSource?.includes('${bookingFilters?.sportId == sport.sportId}'),
  'facility.html: the sport filter must restore the selected sport');
// 시설 필터는 종목 필터 바로 뒤에 오고, 담당 시설(${facilities})만 나열한다.
// '전체 시설'도 빈 값이어야 Spring이 facilityId를 Long으로 바인딩할 수 있다.
const facilityFilterStart = bookingsSource?.indexOf('id="booking-facility-filter"') ?? -1;
const facilityFilterMarkup = facilityFilterStart < 0 ? '' : bookingsSource.slice(
  facilityFilterStart,
  bookingsSource.indexOf('</select>', facilityFilterStart) + '</select>'.length);
check(facilityFilterMarkup.length > 0,
  'facility.html: the bookings toolbar needs a facility filter');
check(facilityFilterStart > (bookingsSource?.indexOf('id="booking-sport-filter"') ?? -1),
  'facility.html: the facility filter must sit after the sport filter');
check(facilityFilterMarkup.includes('name="facilityId"'),
  'facility.html: the facility filter must submit facilityId');
check(/<option value=""[^>]*>전체 시설<\/option>/.test(facilityFilterMarkup),
  'facility.html: the facility default option must be 전체 시설 with an empty value');
check(!facilityFilterMarkup.includes('value="all"'),
  'facility.html: the facility filter must not send a non-numeric value');
check(facilityFilterMarkup.includes('th:each="facility : ${facilities}"'),
  'facility.html: the facility filter must list only the manager\'s facilities');
check(facilityFilterMarkup.includes('th:value="${facility.facilityId}"')
  && facilityFilterMarkup.includes('th:text="${facility.name}"'),
  'facility.html: the facility option must bind facilityId and name');
check(facilityFilterMarkup.includes('${bookingFilters?.facilityId == null}'),
  'facility.html: the facility filter must mark 전체 시설 selected when unset');
check(facilityFilterMarkup.includes('${bookingFilters?.facilityId == facility.facilityId}'),
  'facility.html: the facility filter must restore the selected facility');
check(/aria-label="[^"]+"/.test(facilityFilterMarkup),
  'facility.html: the facility filter needs an accessible label');
check(bookingsSource?.includes('view=\'bookings\''),
  'facility.html: the reset link must keep view=bookings');
check(bookingsSource?.includes('#facility-bookings'),
  'facility.html: the reset link must restore the bookings anchor');
// 검색·초기화는 옆 드롭다운(.field-control 40px)과 높이·글자 크기를 맞춘다. 높이와 좌우 여백은
// 전용 클래스로 키우되 글자는 드롭다운과 같은 크기를 쓰고, 다른 .admin-button.small은 32px로 둔다.
const bookingsToolbar = fragmentSlice(bookingsSource ?? '', '<form class="toolbar"', '</form>') ?? '';
check(bookingsToolbar.length > 0, 'facility.html: the bookings filter toolbar is missing');
const filterActionUses = Array.from(bookingsToolbar.matchAll(/class="([^"]*)"/g))
  .filter(match => match[1].split(/\s+/).includes('booking-filter-action')).length;
check(filterActionUses === 2,
  `facility.html: the taller filter sizing must land on exactly the 검색·초기화 controls (found ${filterActionUses})`);
for (const [label, pattern] of [
  ['검색', /<button class="([^"]*)" type="submit">검색<\/button>/],
  ['초기화', /<a class="([^"]*)"[^>]*>초기화<\/a>/]
]) {
  const matched = bookingsToolbar.match(pattern);
  check(Boolean(matched) && matched[1].split(/\s+/).includes('booking-filter-action'),
    `facility.html: the bookings ${label} control must opt into the taller filter sizing`);
}
const filterActionRule = adminCss.match(/\.toolbar\s+\.admin-button\.small\.booking-filter-action\s*\{([^}]*)\}/);
const filterActionBody = filterActionRule?.[1] ?? '';
check(Boolean(filterActionRule),
  'admin.css: the bookings filter buttons need their own sizing rule');
// 값은 CSS에서 직접 읽는다. 드롭다운 높이가 바뀌면 버튼 높이·좌우 여백도 같은 비율로 다시 키워야 한다.
// 글자 크기는 비율이 아니라 드롭다운 값을 그대로 따라가므로 아래에서 별도로 비교한다.
const baseSmall = adminCss.match(/\.admin-button\.small\s*\{([^}]*)\}/)?.[1] ?? '';
const dropdownHeight = Number(adminCss.match(/\.field-control\s*\{[^}]*height:\s*([\d.]+)px/)?.[1]);
const baseHeight = Number(baseSmall.match(/min-height:\s*([\d.]+)px/)?.[1]);
const basePadding = Number(baseSmall.match(/padding:\s*0\s+([\d.]+)px/)?.[1]);
const filterHeight = Number(filterActionBody.match(/min-height:\s*([\d.]+)px/)?.[1]);
const filterPadding = Number(filterActionBody.match(/padding:\s*0\s+([\d.]+)px/)?.[1]);
const filterFont = Number(filterActionBody.match(/font-size:\s*([\d.]+)px/)?.[1]);
check(baseHeight === 32 && dropdownHeight === 40,
  'admin.css: the filter buttons grow from the 32px small button to the 40px dropdown height');
check(filterHeight === dropdownHeight,
  `admin.css: the bookings filter buttons must be ${dropdownHeight}px tall to line up with the dropdowns (got ${filterHeight})`);
check(Math.abs(filterPadding - basePadding * (dropdownHeight / baseHeight)) < 0.01,
  `admin.css: horizontal padding must scale ${dropdownHeight / baseHeight}× with the height to keep the button ratio`);
// 글자는 확대하지 않는다. 옆 드롭다운(.field-control)과 같은 크기여야 나란히 놓았을 때 어긋나지 않는다.
const dropdownFont = Number(adminCss.match(/\.field-control\s*\{[^}]*font-size:\s*([\d.]+)px/)?.[1]);
check(dropdownFont > 0,
  'admin.css: the dropdown font size must be readable from the .field-control rule');
check(Math.abs(filterFont - dropdownFont) < 0.01,
  `admin.css: the bookings filter label must use the same ${dropdownFont}px as the adjacent dropdown (got ${filterFont})`);
check(/\.admin-button\.small\s*\{[^}]*min-height:\s*32px/.test(adminCss),
  'admin.css: every other small admin button must stay 32px tall');
for (const field of ['match.matchId', 'match.title', 'match.facilityName', 'match.sportName',
  'match.participation', 'match.dateLabel', 'match.statusLabel', 'match.statusTone']) {
  check(bookingsSource?.includes(field), `facility.html: bookings table must render ${field}`);
}
check(bookingsSource?.includes('@{/admin/operations(matchId=${match.matchId})}'),
  'facility.html: each booking must deep-link to its read-only detail');
check(bookingsSource?.includes(roomIdentityCell),
  'facility.html: each booking must list the room title first and the room number as secondary text');
check(!bookingsSource?.includes('<span class="cell-primary" th:text="${match.matchId}">'),
  'facility.html: each booking must not lead with the room number');
// 행 관리 열: 상세·수정·취소가 같은 버튼 모양을 쓰되, 상세는 화면 이동이라 링크(<a>)로 남긴다.
// 취소는 확인 모달을 거치는 POST 폼이어야 하고, 되돌릴 수 없는 동작이라 위험 색을 유지한다.
const roomRowActions = bookingsSource?.slice(
  bookingsSource.indexOf('<td class="room-row-actions">'),
  bookingsSource.indexOf('</td>', bookingsSource.indexOf('<td class="room-row-actions">'))) ?? '';
check(roomRowActions.length > 0, 'facility.html: the booking row action cell is missing');
check(/<a class="admin-button small" th:href="@\{\/admin\/operations\(matchId=\$\{match\.matchId\}\)\}"/.test(roomRowActions),
  'facility.html: the room detail action must stay a link but look like the row buttons');
check(/<button class="admin-button small" type="button" data-edit-room/.test(roomRowActions),
  'facility.html: the room edit action must use the same button style');
check(/<form class="room-cancel-form" method="post"/.test(roomRowActions),
  'facility.html: the room cancel action must stay a POST form');
check(/data-cancel-room/.test(roomRowActions)
  && /class="admin-button small danger-ghost"[^>]*type="submit"[^>]*data-cancel-room/.test(roomRowActions),
  'facility.html: the destructive room cancel must keep its danger styling');
check(!/class="table-link/.test(roomRowActions),
  'facility.html: row actions must not fall back to plain text links');

/* ── 예약 통계 화면 계약 ────────────────────────────────────────────────────
   통계 화면은 POST 폼으로 조회 조건을 서버에 넘기고(다른 관리자 변경 요청과 같은 관례),
   서버가 계산한 값만 그립니다. 기간 직접 입력(periodFrom/periodTo)과
   시설 미선택(-시설 선택-) 상태가 계약입니다. */
const statsSource = fragmentSlice(facilitySource, 'id="facility-stats"', 'id="facility-content"');
check(Boolean(statsSource), 'facility.html: stats view missing');
const statsForm = fragmentSlice(statsSource ?? '', '<form class="heading-actions"', '</form>') ?? '';
check(statsForm.length > 0, 'facility.html: the stats filter form is missing');
check(/method="post"/.test(statsForm),
  'facility.html: the stats filter must submit as a POST form');
check(statsForm.includes('@{/admin/facility/stats}'),
  'facility.html: the stats filter must post to the dedicated stats route');
for (const field of ['name="facilityId"', 'name="period"', 'name="periodFrom"', 'name="periodTo"']) {
  check(statsForm.includes(field), `facility.html: the stats filter must keep the ${field} input`);
}
check(/<option value=""[^>]*>-시설 선택-<\/option>/.test(statsForm),
  'facility.html: the stats facility select must start from an unselected -시설 선택- option');
check(/<option value="custom"[^>]*>직접 입력<\/option>/.test(statsForm),
  'facility.html: the stats period select must offer 직접 입력');
check(/name="periodFrom"[^>]*type="date"/.test(statsForm)
  && /name="periodTo"[^>]*type="date"/.test(statsForm),
  'facility.html: custom stats periods must use native date inputs');
/* custom은 일수가 7/30/90과 같아도 "직접 입력"을 유지해야 한다. 일수 비교로 되돌아가지 않게 고정한다. */
check(statsForm.includes("${stats?.requestedPeriod == 'custom'}"),
  'facility.html: the stats period select must restore 직접 입력 from requestedPeriod');
check(!/stats\.period != 7 and stats\.period != 30/.test(statsForm),
  'facility.html: the stats period select must not infer 직접 입력 from the day count');
check(statsSource?.includes('${stats?.applicationTotal ?: 0}')
  && statsSource?.includes('${stats?.individualCount ?: 0}')
  && statsSource?.includes('${stats?.clubCount ?: 0}')
  && statsSource?.includes('${stats?.approvalRate ?: 0}')
  && statsSource?.includes('${stats?.approvedCount ?: 0}'),
  'facility.html: the stats view must render the application metrics from stats');
check(!/시설 관리자 제외/.test(statsSource ?? ''),
  'facility.html: participant count no longer excludes the facility manager');
const statsNotice = fragmentSlice(statsSource ?? '', 'class="notice-strip"', '</div>') ?? '';
check(statsNotice.length > 0 && !/생성된 매칭방/.test(statsNotice),
  'facility.html: the stats basis notice must describe the start_at range, not creation time');

const operationsSource = await readFile(path.join(resources, 'templates/admin/operations.html'), 'utf8');
check(operationsSource.includes('th:if="${match == null}"'),
  'operations.html: a missing match must render an honest empty state');
check(operationsSource.includes('id="ops-content" th:if="${match != null}"'),
  'operations.html: the detail body must render only for a real match');
for (const field of ['match.matchId', 'match.title', 'match.facilityName', 'match.sportName',
  'match.dateLabel', 'match.participation', 'match.statusLabel', 'match.statusTone']) {
  check(operationsSource.includes(field), `operations.html: room summary must render ${field}`);
}
check(operationsSource.includes('th:each="roomApplication : ${match.applications}"')
  && operationsSource.includes('th:each="participant : ${match.participants}"'),
  'operations.html: applications and participants must be listed from the model');
// 'application'은 Thymeleaf 예약어라 th:each 지역 변수 이름으로 쓸 수 없다. 예약어로 되돌아가면
// 렌더링이 실패하므로 여기서 이름을 고정한다.
check(!/th:each="application\s*:/.test(operationsSource),
  'operations.html: the application loop variable must not use the reserved name "application"');
for (const field of ['roomApplication.applicantName', 'roomApplication.kindLabel',
  'roomApplication.statusLabel', 'roomApplication.createdAtLabel',
  'participant.name', 'participant.teamLabel', 'participant.statusLabel']) {
  check(operationsSource.includes(field), `operations.html: ${field} must be rendered`);
}
check(operationsSource.includes('#lists.isEmpty(match.applications)')
  && operationsSource.includes('#lists.isEmpty(match.participants)'),
  'operations.html: both lists need an empty state');
// 승인·반려와 팀 배정은 폼 없이 API로 처리한다.
check(!/<form\b/.test(operationsSource) && !/th:action=/.test(operationsSource),
  'operations.html: write actions must not use forms');
check(!/type="submit"/.test(operationsSource),
  'operations.html: API actions must use regular buttons');
check(!/opsApplication|match\.pendingGroups|match\.teamA\b|match\.canAssign|match\.status ==/.test(operationsSource)
  && !/data-(?:action|decision)="complete"/.test(operationsSource),
  'operations.html: unsupported model bindings and completion controls must stay out of the page');
check(operationsSource.includes('th:data-decision-url="@{/admin/operations/applications}"')
  && operationsSource.includes('th:if="${roomApplication.decidable}"'),
  'operations.html: application decisions must use the operations API and be gated per row');
for (const decision of ['confirm', 'reject']) {
  const action = new RegExp(String.raw`data-decision="${decision}"\s+th:data-application-id="\$\{roomApplication\.applicationId\}"`);
  check(action.test(operationsSource), `operations.html: ${decision} must target the application row ID`);
}
check(operationsSource.includes('@{/admin/facility(view=\'bookings\')}'),
  'operations.html: the detail must link back to the booking list');

// 관리자 진입 버튼은 역할별로만 노출하고, 관리자 화면끼리는 서로 이동 링크를 두지 않는다.
function fragmentSlice(source, openMarker, closeMarker) {
  const start = source.indexOf(openMarker);
  if (start < 0) return null;
  const end = source.indexOf(closeMarker, start);
  return end < 0 ? null : source.slice(start, end);
}

function anchorsOf(source) {
  return Array.from(source.matchAll(/<a\b[^>]*>/g), match => match[0]);
}

const layoutSource = await readFile(path.join(resources, 'templates/fragments/layout.html'), 'utf8');
const adminSource = await readFile(path.join(resources, 'templates/fragments/admin.html'), 'utf8');

const siteHeader = fragmentSlice(layoutSource, '<header th:fragment="siteHeader(active)"', '</header>');
check(Boolean(siteHeader), 'layout.html: siteHeader fragment missing');
const headerAdminLinks = anchorsOf(siteHeader ?? '').filter(tag => tag.includes('/admin/'));
check(headerAdminLinks.length === 2, `layout.html: expected 2 admin entry links, found ${headerAdminLinks.length}`);
const facilityEntry = headerAdminLinks.find(tag => tag.includes('@{/admin/facility}'));
const systemEntry = headerAdminLinks.find(tag => tag.includes('@{/admin/system}'));
check(Boolean(facilityEntry), 'layout.html: missing facility admin entry link');
check(Boolean(systemEntry), 'layout.html: missing system admin entry link');
check(/th:if="\$\{[^"]*currentMember != null[^"]*roleLabel == '시설 관리자'[^"]*\}"/.test(facilityEntry ?? ''),
  'layout.html: facility admin entry must be gated to 시설 관리자');
check(/th:if="\$\{[^"]*currentMember != null[^"]*roleLabel == '시스템 관리자'[^"]*\}"/.test(systemEntry ?? ''),
  'layout.html: system admin entry must be gated to 시스템 관리자');
check(headerAdminLinks.every(tag => tag.includes('th:if=')),
  'layout.html: admin entry links must not render for general members');

// 템플릿의 역할 라벨은 MemberRole이 만드는 라벨과 같아야 한다(라벨이 바뀌면 버튼이 사라진다).
const memberRoleSource = await readFile(
  path.resolve('src/main/java/co/kr/spoit/member/domain/MemberRole.java'), 'utf8');
const currentMemberSource = await readFile(
  path.resolve('src/main/java/co/kr/spoit/member/dto/CurrentMemberDto.java'), 'utf8');
check(currentMemberSource.includes('private final String roleLabel;'),
  'CurrentMemberDto: roleLabel property used by the header gating must exist');
for (const [role, expected] of [
  ['FACILITY_MANAGER', '시설 관리자'],
  ['ADMIN', '시스템 관리자']
]) {
  check(memberRoleSource.includes(`${role}("ROLE_${role}", "${expected}")`),
    `MemberRole.${role} label must stay "${expected}"`);
  check(layoutSource.includes(`roleLabel == '${expected}'`),
    `layout.html: admin entry gating must use MemberRole label "${expected}"`);
}

const facilitySidebar = fragmentSlice(adminSource, '<aside th:fragment="facilitySidebar(active)"', '</aside>');
const systemSidebar = fragmentSlice(adminSource, '<aside th:fragment="systemSidebar(active)"', '</aside>');
check(Boolean(facilitySidebar), 'admin.html: facilitySidebar fragment missing');
check(Boolean(systemSidebar), 'admin.html: systemSidebar fragment missing');
check(!/th:href="@\{\/admin\/system/.test(facilitySidebar ?? ''),
  'admin.html: facility sidebar must not link to the system admin page');
check(!/th:href="@\{\/admin\/facility/.test(systemSidebar ?? ''),
  'admin.html: system sidebar must not link to the facility admin page');
check(!(facilitySidebar ?? '').includes('role-switch') && !(systemSidebar ?? '').includes('role-switch'),
  'admin.html: cross-admin role switch must stay removed');
check(/th:href="@\{\/admin\/facility/.test(facilitySidebar ?? ''),
  'admin.html: facility sidebar must keep its own menu links');
check(/th:href="@\{\/admin\/system/.test(systemSidebar ?? ''),
  'admin.html: system sidebar must keep its own menu links');
// 민원·요청, 점검·유지보수 화면은 백엔드 처리 없이 껍데기만 남아 있었으므로 메뉴에서 제거했다.
// 되살아나면 화면에 없는 동작을 약속하게 되므로, 남은 메뉴와 함께 부재를 고정한다.
for (const removed of ['facility-requests', 'facility-maintenance', '민원·요청', '점검·유지보수']) {
  check(!(facilitySidebar ?? '').includes(removed),
    `admin.html: removed facility menu entry must stay gone (${removed})`);
}
for (const kept of ['facility-dashboard', 'facility-assets', 'facility-schedule',
  'facility-bookings', 'facility-stats', 'facility-content']) {
  check((facilitySidebar ?? '').includes(kept),
    `admin.html: facility sidebar must keep ${kept}`);
}

/* ── 제거된 시설 관리자 화면 계약 ──────────────────────────────────────────
   민원·요청과 점검·유지보수는 서버 모델·핸들러가 없어 화면만 존재했다. 화면과 진입점,
   모달·스크립트 연결이 다시 생기면 없는 동작을 약속하게 되므로 부재를 고정한다. */
for (const removed of ['id="facility-requests"', 'id="facility-maintenance"',
  'id="maintenance-modal"', 'id="maintenance-form"', 'data-open-maintenance',
  'data-close-maintenance', '@{/admin/facility/requests', '@{/admin/facility/maintenance',
  'requestMetrics', 'maintenanceMetrics', 'maintenanceFilters', '요청 목록', '점검 일지']) {
  check(!facilitySource.includes(removed),
    `facility.html: removed requests/maintenance UI must stay gone (${removed})`);
}
check(!facilityScript.includes('maintenanceModal') && !facilityScript.includes('maintenance-modal')
  && !facilityScript.includes('data-open-maintenance') && !facilityScript.includes('data-close-maintenance'),
  'admin-facility.js: maintenance modal handlers must be removed with the screen');

// 실제 시설·시스템 템플릿의 모든 .admin-view가 대체 포커스 표시 대상(제목)을 가져야 한다.
for (const file of ['templates/admin/facility.html', 'templates/admin/system.html']) {
  const source = await readFile(path.join(resources, file), 'utf8');
  const views = source.split(/<section class="admin-view"/).slice(1);
  const missing = views.filter(view => !/class="page-heading-copy"[\s\S]*<h1\b/.test(view));
  check(views.length > 0 && missing.length === 0,
    `${file}: every .admin-view needs .page-heading-copy with an h1 for the keyboard focus indicator`);
}

/* ── 통합 알림 계약 ────────────────────────────────────────────────────
   헤더 배지와 마이페이지·관리자 알림은 GET /api/notifications를 단일 원본으로 쓴다.
   실시간 연결은 로그인 사용자만 하고, 수신 데이터는 목록 재조회 신호로만 쓴다.
   경로·마커가 어긋나면 배지가 조용히 멈추므로 여기서 고정한다. */
const notificationScript = await readFile(path.join(resources, 'static/js/notification-center.js'), 'utf8');
const notificationFragment = await readFile(path.join(resources, 'templates/fragments/notification.html'), 'utf8');
const myPageSource = await readFile(path.join(resources, 'templates/mypage/index.html'), 'utf8');
const matchResultSource = await readFile(path.join(resources, 'templates/mypage/match-result.html'), 'utf8');
const matchResultScript = await readFile(path.join(resources, 'static/js/match-result.js'), 'utf8');

// 1) API·소켓 경로와 STOMP 구독 주소는 백엔드 계약과 같아야 한다.
for (const [needle, message] of [
  ['@{/api/notifications}', 'layout.html: notification config must point at GET /api/notifications'],
  ['@{/ws/notifications}', 'layout.html: notification config must point at the native STOMP endpoint'],
]) {
  check(layoutSource.includes(needle), message);
}
check(notificationScript.includes("'/user/queue/notifications'"),
  'notification-center.js: STOMP subscription destination must stay /user/queue/notifications');
check(notificationScript.includes("frame('CONNECT'"),
  'notification-center.js: the client must send a CONNECT frame before subscribing');
check(notificationScript.includes('CONNECTED') && notificationScript.includes("frame('SUBSCRIBE'"),
  'notification-center.js: the client must subscribe only after the broker answers CONNECTED');
// CSRF는 CONNECT 헤더로 보낸다(같은 출처·인증 세션 유지).
check(/headers\[csrfHeader\] = csrfToken/.test(notificationScript),
  'notification-center.js: the CONNECT frame must carry the CSRF header');

// 2) 실시간 수신 데이터를 화면에 직접 그리지 않고 목록을 다시 읽는다(텍스트 안전 렌더링).
check(/item\.command === 'MESSAGE'[\s\S]{0,120}refresh\(\)/.test(notificationScript),
  'notification-center.js: a MESSAGE must trigger a fresh API read instead of rendering the payload');
check(notificationScript.includes('textContent') && !/innerHTML\s*=\s*[^;]*item\./.test(notificationScript),
  'notification-center.js: API values must be rendered as text, never injected as HTML');
// 이동 주소는 같은 출처의 상대 경로만 허용한다.
check(/!trimmed\.startsWith\('\/'\)\s*\|\|\s*trimmed\.startsWith\('\/\/'\)/.test(notificationScript),
  'notification-center.js: href values must be limited to same-origin relative paths');

// 3) 비로그인은 접속·조회하지 않고, 화면을 떠나면 소켓·타이머를 정리한다.
check(/if \(!authenticated\) return;/.test(notificationScript),
  'notification-center.js: anonymous visitors must not open the socket or poll the API');
check(notificationScript.includes("addEventListener('visibilitychange'")
  && notificationScript.includes('stop();'),
  'notification-center.js: hidden documents must release the socket and timers');
check(notificationScript.includes("addEventListener('pagehide'"),
  'notification-center.js: leaving the page must release the socket and timers');
check(notificationScript.includes('scheduleReconnect') && notificationScript.includes('RECONNECT_MAX'),
  'notification-center.js: a dropped socket must reconnect with a bounded backoff');

// 4) 마이페이지·관리자 화면은 같은 API를 쓰는 공통 조각을 사용한다.
check(myPageSource.includes('fragments/notification :: center'),
  'mypage/index.html: the notification modal must come from the shared fragment');
check(myPageSource.includes('data-notification-surface') && myPageSource.includes('fragments/notification :: mypageItem'),
  'mypage/index.html: the notification section must be a live surface with the shared item template');
check(adminSource.includes('fragments/notification :: adminItem'),
  'admin.html: the admin notification modal must use the shared item template');
check(adminSource.includes('@{/api/notifications}') && adminSource.includes('@{/ws/notifications}'),
  'admin.html: the admin notification modal must carry the same API/socket config');
// 서버 폼은 JS가 없을 때의 대체 경로로 남긴다.
check(myPageSource.includes('@{/mypage/notifications/read-all}')
  && myPageSource.includes('@{/mypage/notifications/{notificationId}/read(notificationId=${item.notificationId})}'),
  'mypage/index.html: the server notification forms must stay as the no-JS path');
check(adminSource.includes('@{/admin/notifications/read-all}')
  && adminSource.includes('@{/admin/notifications/{id}/read(id=${item.notificationId})}'),
  'admin.html: the server admin notification forms must stay as the no-JS path');

// 5) 배지는 헤더 한 곳에서 갱신되고, 숨김 상태로 시작해 0건을 그리지 않는다.
check(layoutSource.includes('data-notification-badge') && layoutSource.includes('th:hidden='),
  'layout.html: the header badge must start hidden and be owned by the notification script');
check(notificationFragment.includes('data-notification-badge') === false,
  'notification.html: the modal fragment must not add a second badge');

/* ── 경기 결과 화면 계약 ──────────────────────────────────────────────
   확정 참가자는 요약 API만, 팀장은 팀장 전용 조회·제출 API를 쓴다.
   화면이 다른 사용자의 결과를 그리지 않도록 경로와 노출 조건을 고정한다. */
check(matchResultSource.includes('@{/mypage} + \'#reservations\''),
  'match-result.html: the result screen must offer a way back to the application list');
check(matchResultScript.includes("apiUrl('result/summary')"),
  'match-result.js: confirmed participants must read GET /mypage/matches/{matchId}/result/summary');
check(matchResultScript.includes("apiUrl('result')"),
  'match-result.js: captains must use the captain-only /result API for submission');
check(/status === 403[\s\S]{0,120}submitSection\.hidden = true/.test(matchResultScript),
  'match-result.js: a non-captain 403 must hide the submission area instead of showing an error');
check(matchResultScript.includes('textContent') && !/innerHTML\s*=/.test(matchResultScript),
  'match-result.js: API values must be rendered as text, never injected as HTML');
check(matchResultScript.includes('JSON.stringify({ result: pending })'),
  'match-result.js: submission must post {result} to the captain-only endpoint');
check(!/myResult/.test(matchResultSource),
  'match-result.html: the other captain\'s submission values must not be rendered by the server');
// 화면이 되살린 표시 정보(일시·시설·제출 마감·A/B팀 로스터)는 서버가 훅만 두고 스크립트가 요약 API 값으로 채운다.
for (const field of ['schedule', 'facility', 'deadline']) {
  check(matchResultSource.includes(`data-field="${field}"`),
    `match-result.html: the summary must keep the data-field="${field}" hook for the result API`);
}
check(matchResultSource.includes('data-result-teams') && matchResultScript.includes('data-result-teams'),
  'match-result.html: the A/B roster must be rendered by the script from the summary API');
check(matchResultScript.includes('renderTeams') && matchResultScript.includes('member.nickname'),
  'match-result.js: the roster must be drawn from the confirmed members\' nicknames');
check(!/innerHTML\s*=/.test(matchResultScript),
  'match-result.js: roster values must be rendered as text, never injected as HTML');
// 서버는 팀장 여부를 모르므로 단계 바는 숨긴 채 시작하고, 평가 진행률은 제출 검사와 같은 빈 칸 판정을 쓴다.
const matchEvaluationScript = await readFile(path.join(resources, 'static/js/match-evaluation.js'), 'utf8');
check(/<ol\b(?=[^>]*\sdata-result-steps(?=[\s>=]))(?=[^>]*\shidden(?=[\s>=]))[^>]*>/.test(matchResultSource),
  'match-result.html: the captain step bar must start hidden so participants never see it');
check(/missing = missingKeys\(\)/.test(matchEvaluationScript) && /missingKeys\(\)\.size/.test(matchEvaluationScript),
  'match-evaluation.js: the progress count must use the same empty-cell check as submission review');
// 점수는 슬라이더로 고르고, 스크린 리더가 "항목 n점"을 읽을 수 있게 값이 바뀔 때마다 aria-valuetext를 고친다.
check(/type="range"/.test(matchEvaluationScript) && !/<select\b/.test(matchEvaluationScript),
  'match-evaluation.js: scores must be chosen with sliders, not select boxes');
check(/aria-valuetext="\$\{/.test(matchEvaluationScript)
    && /setAttribute\('aria-valuetext'/.test(matchEvaluationScript),
  'match-evaluation.js: each score slider must keep aria-valuetext in step with its value');
// Chrome은 밝은 accent-color에 대비를 맞추려고 남은 트랙을 검게 칠하므로, 평가 슬라이더는 두 엔진의 트랙을 직접 그린다.
const matchResultCss = await readFile(path.join(resources, 'static/css/match-result.css'), 'utf8');
check(!/accent-color\s*:/.test(matchResultCss)
    && ['::-webkit-slider-runnable-track', '::-webkit-slider-thumb', '::-moz-range-track', '::-moz-range-progress', '::-moz-range-thumb']
      .every(part => matchResultCss.includes(`.match-evaluation-slider${part}`)),
  'match-result.css: score sliders must draw their own track and thumb in Chrome and Firefox instead of using accent-color');
check(/setProperty\('--match-evaluation-slider-ratio'/.test(matchEvaluationScript)
    && /var\(--match-evaluation-slider-ratio\b/.test(matchResultCss),
  'match-evaluation.js: the Chrome track fill must follow the slider position');

if (failures.length) {
  console.error(failures.join('\n'));
  process.exitCode = 1;
} else {
  console.log(`PASS: ${scripts.length} JS, ${styles.length} CSS, ${templates.length} templates; ${assertions} contract checks.`);
}
