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
  'team-edit-form': ['clubName', 'sportId', 'regionId'],
  'team-transfer-form': ['clubMemberId'],
  'team-remove-form': ['clubMemberId'],
  'team-disband-form': ['clubNameConfirmation'],
  'facility-form': ['concurrentMatchLimit'],
  'room-editor-form': ['facilityId', 'sportId', 'teamACapacity', 'teamBCapacity', 'feePerPerson'],
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
  facilitySource.indexOf('id="maintenance-modal"'));
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
  facilitySource.indexOf('id="maintenance-modal"'));
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
for (const field of ['match.matchId', 'match.title', 'match.facilityName', 'match.sportName',
  'match.participation', 'match.dateLabel', 'match.statusLabel', 'match.statusTone']) {
  check(bookingsSource?.includes(field), `facility.html: bookings table must render ${field}`);
}
check(bookingsSource?.includes('@{/admin/operations(matchId=${match.matchId})}'),
  'facility.html: each booking must deep-link to its read-only detail');

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
  'roomApplication.statusLabel', 'roomApplication.receiptNo', 'roomApplication.createdAtLabel',
  'participant.name', 'participant.teamLabel', 'participant.statusLabel']) {
  check(operationsSource.includes(field), `operations.html: ${field} must be rendered`);
}
check(operationsSource.includes('#lists.isEmpty(match.applications)')
  && operationsSource.includes('#lists.isEmpty(match.participants)'),
  'operations.html: both lists need an empty state');
// 조회 전용: 쓰기 폼·제출 버튼·없는 getter(seats/members 등)를 참조하지 않는다.
check(!/<form\b/.test(operationsSource) && !/th:action=/.test(operationsSource),
  'operations.html: the read-only detail must not contain write forms');
check(!/type="submit"/.test(operationsSource),
  'operations.html: the read-only detail must not offer submit actions');
check(!/opsApplication|match\.pendingGroups|match\.teamA|match\.canAssign|match\.status ==/.test(operationsSource),
  'operations.html: unimplemented confirm/reject/assign/complete controls must stay out of the page');
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

// 실제 시설·시스템 템플릿의 모든 .admin-view가 대체 포커스 표시 대상(제목)을 가져야 한다.
for (const file of ['templates/admin/facility.html', 'templates/admin/system.html']) {
  const source = await readFile(path.join(resources, file), 'utf8');
  const views = source.split(/<section class="admin-view"/).slice(1);
  const missing = views.filter(view => !/class="page-heading-copy"[\s\S]*<h1\b/.test(view));
  check(views.length > 0 && missing.length === 0,
    `${file}: every .admin-view needs .page-heading-copy with an h1 for the keyboard focus indicator`);
}

if (failures.length) {
  console.error(failures.join('\n'));
  process.exitCode = 1;
} else {
  console.log(`PASS: ${scripts.length} JS, ${styles.length} CSS, ${templates.length} templates; ${assertions} contract checks.`);
}
