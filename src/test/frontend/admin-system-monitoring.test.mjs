// Run from the repository root: bun src/test/frontend/admin-system-monitoring.test.mjs
// 시스템 모니터링 자동 갱신만 확인합니다. 백엔드 계약은 POST /admin/system/monitoring +
// SystemDashboard JSON(camelCase getter)이며, 여기서는 그 계약을 흉내 낸 응답으로
// 15초 주기 요청, CSRF 헤더, DOM 갱신, 중복 요청 방지, 실패 시 기존 값 보존을 검증합니다.
import assert from 'node:assert/strict';
import { runInNewContext } from 'node:vm';

const SOURCE_URL = 'src/main/resources/static/js/admin-system.js';
const TEMPLATE_URL = 'src/main/resources/templates/admin/system.html';
const MONITORING_URL = '/admin/system/monitoring';
const CSRF_HEADER = 'X-CSRF-TOKEN';
const CSRF_TOKEN = 'csrf-value';

class El {
  constructor(tag = 'div', dataset = {}) {
    this.tagName = tag.toUpperCase();
    this.dataset = dataset;
    this.hidden = false;
    this.textContent = '';
    this.className = '';
    this.style = {};
    this.children = [];
    this.attributes = {};
    this.listeners = {};
    const classes = new Set();
    this.classList = {
      add: (...names) => names.forEach(name => classes.add(name)),
      remove: (...names) => names.forEach(name => classes.delete(name)),
      contains: name => classes.has(name),
      toggle: (name, active) => (active ? classes.add(name) : classes.delete(name))
    };
    this.classes = classes;
  }
  appendChild(child) { child.parentElement = this; this.children.push(child); return child; }
  replaceChildren(...nodes) { this.children = nodes; nodes.forEach(node => { node.parentElement = this; }); }
  addEventListener(type, handler) { (this.listeners[type] ??= []).push(handler); }
  setAttribute(name, value) { this.attributes[name] = String(value); }
  getAttribute(name) { return this.attributes[name] ?? null; }
  hasAttribute(name) { return Object.hasOwn(this.attributes, name); }
  dispatch(type, event = {}) {
    let prevented = false;
    const payload = { type, target: this, ...event, preventDefault() { prevented = true; } };
    for (const handler of this.listeners[type] ?? []) handler(payload);
    return { prevented };
  }
}

const roles = {};
const roleOf = name => (roles[name] ??= new El('span', { monitoring: name }));
// 차트 설명·서비스 목록은 페이지에 이미 있는 요소이고, JS는 그 내용만 바꿉니다.
const bars = roleOf('bars');
const services = roleOf('services');
// 수동 새로고침은 템플릿에서 disabled로 시작하는 <button type="button">이며, 스크립트가 초기화되면 활성화됩니다.
const refreshButton = new El('button');
refreshButton.disabled = true;
refreshButton.setAttribute('type', 'button');

const root = new El('section', {
  monitoringUrl: MONITORING_URL,
  csrfHeader: CSRF_HEADER,
  csrfToken: CSRF_TOKEN
});
root.querySelector = selector => {
  if (selector === '[data-monitoring-refresh]') return refreshButton;
  const match = /^\[data-monitoring="([a-z-]+)"\]$/.exec(selector);
  return match ? roleOf(match[1]) : null;
};
roleOf('bars').className = 'bar-chart';
roleOf('services').className = 'system-status-list';

// 페이지에 이미 렌더링된 값: 실패 시 이 값이 지워지면 안 됩니다.
roleOf('cpu-percent').textContent = '11';
roleOf('api-status').textContent = '정상';
// 템플릿은 갱신 안내를 hidden 상태로 렌더링하고, JS는 실패할 때만 보여 줍니다.
roleOf('status').hidden = true;
const intervals = [];
const cleared = [];

const document = {
  hidden: false,
  getElementById: id => (id === 'system-dashboard' ? root : null),
  querySelector: () => null,
  querySelectorAll: () => [],
  addEventListener() {},
  createElement: tag => new El(tag)
};

const requests = [];
let responder = () => Promise.reject(new Error('no responder'));

const window = {
  PlayOnAdmin: { openModal() {}, closeModal() {}, trapFocus() {}, toast() {} },
  setInterval(handler, delay) {
    const timer = { handler, delay };
    intervals.push(timer);
    return timer;
  },
  clearInterval(timer) { cleared.push(timer); }
};

async function load() {
  const source = await Bun.file(SOURCE_URL).text();
  runInNewContext(source, {
    window,
    document,
    Element: El,
    fetch: (url, options) => {
      const pending = responder(url, options);
      const entry = { url, options, settled: Promise.resolve(pending).catch(() => {}) };
      requests.push(entry);
      return pending;
    }
  });
}

function jsonResponse(body, { status = 200, redirected = false, contentType = 'application/json;charset=UTF-8' } = {}) {
  return {
    ok: status >= 200 && status < 300,
    status,
    redirected,
    headers: { get: name => (name.toLowerCase() === 'content-type' ? contentType : null) },
    json: () => Promise.resolve(body)
  };
}

// 클릭 핸들러는 비동기 갱신을 기다리지 않고 반환하므로, 응답 처리까지 microtask를 비웁니다.
const flush = () => new Promise(resolve => setTimeout(resolve, 0));

const dashboard = {
  collectedAt: '2026-11-02 14:35:10',
  apiStatus: '정상',
  apiUptime: '2일 3시간',
  cpuPercent: 42,
  cpuPeak: 87,
  memoryPercent: 61,
  memoryCapacity: '2GB',
  diskPercent: 33,
  allHealthy: true,
  bars: [
    { cpuHeight: 42, memoryHeight: 61, label: '14:35' },
    { cpuHeight: null, memoryHeight: 61, label: '14:40' }
  ],
  services: [
    { name: 'API 서버', detail: '정상', tone: 'good' },
    { name: '데이터베이스', detail: '연결 정상', tone: 'good' }
  ]
};

/* ── 1. 15초 주기 등록 + POST·CSRF 계약 ───────────────────────────────── */
await load();
assert.equal(intervals.length, 1, '자동 갱신 타이머를 하나만 등록해야 합니다.');
assert.equal(intervals[0].delay, 15000, '자동 갱신 주기는 15초여야 합니다.');
assert.equal(typeof intervals[0].handler, 'function', 'interval handler must be callable');

responder = () => Promise.resolve(jsonResponse(dashboard));
await intervals[0].handler();

assert.equal(requests.length, 1);
assert.equal(requests[0].url, MONITORING_URL);
assert.equal(requests[0].options.method, 'POST', '자동 갱신은 POST로 보내야 합니다.');
assert.equal(requests[0].options.credentials, 'same-origin');
assert.equal(requests[0].options.headers[CSRF_HEADER], CSRF_TOKEN, 'CSRF 토큰을 템플릿 값으로 전송해야 합니다.');
assert.equal(requests[0].options.headers.Accept, 'application/json');
assert.equal(requests[0].options.headers['Content-Type'], undefined, '본문 없는 POST에 JSON 본문을 붙이지 않습니다.');

/* ── 2. 정상 응답 반영 ─────────────────────────────────────────────────── */
assert.equal(roleOf('collected-at').textContent, '마지막 수집 2026-11-02 14:35:10');
assert.equal(roleOf('chart-collected-at').textContent, '마지막 수집 2026-11-02 14:35:10');
assert.equal(roleOf('api-status').textContent, '정상');
assert.equal(roleOf('api-uptime').textContent, '2일 3시간');
assert.equal(roleOf('api-uptime-label').textContent, '현재 프로세스 가동시간');
assert.equal(roleOf('cpu-percent').textContent, '42');
assert.equal(roleOf('cpu-unit').hidden, false, '측정된 CPU에는 % 단위를 보여야 합니다.');
assert.equal(roleOf('cpu-label').textContent, 'JVM 프로세스 CPU');
assert.equal(roleOf('cpu-peak').textContent, '· 피크 87%');
assert.equal(roleOf('memory-percent').textContent, '61');
assert.equal(roleOf('memory-capacity').textContent, '최대 힙 2GB');
assert.equal(roleOf('cpu-progress').textContent, '42%');
assert.equal(roleOf('cpu-track').style.width, '42%');
assert.equal(roleOf('memory-progress').textContent, '61%');
assert.equal(roleOf('disk-progress').textContent, '33%');
assert.equal(roleOf('disk-track').style.width, '33%');
assert.equal(roleOf('service-badge').textContent, 'API·DB 정상');
assert.equal(roleOf('service-badge').classList.contains('good'), true);
assert.equal(roleOf('service-badge').classList.contains('warn'), false);
assert.equal(roleOf('chart-note').textContent.includes('15초 수집'), true);

// 차트: 측정된 막대만 만들고 값이 없는 막대는 0으로 그리지 않습니다.
assert.equal(bars.children.length, 2);
const [firstGroup, secondGroup] = bars.children;
assert.equal(firstGroup.className, 'bar-group');
assert.equal(firstGroup.children[0].className, 'bar-pair');
assert.equal(firstGroup.children[0].children[0].style.height, '42%');
assert.equal(firstGroup.children[0].children[1].className, 'bar green');
assert.equal(firstGroup.children[1].className, 'bar-label');
assert.equal(firstGroup.children[1].textContent, '14:35');
assert.equal(secondGroup.children[0].children.length, 1, 'CPU가 없는 구간에는 CPU 막대를 만들지 않습니다.');
assert.equal(secondGroup.children[0].children[0].className, 'bar green');
assert.equal(secondGroup.children[1].textContent, '14:40');

// 서비스 목록: 이름·상세·tone을 그대로 옮깁니다.
assert.equal(services.children.length, 2);
assert.equal(services.children[0].className, 'system-status-row');
assert.equal(services.children[0].children[0].className, 'live-dot good');
assert.equal(services.children[0].children[1].textContent, 'API 서버');
assert.equal(services.children[0].children[2].textContent, '정상');
assert.equal(roleOf('status').hidden, true, '성공 시에는 갱신 실패 배지를 남기지 않습니다.');
assert.equal(roleOf('status-detail').textContent, '');

/* ── 3. 측정 불가(null)는 0이 아니라 미수집으로 표시 ──────────────────── */
responder = () => Promise.resolve(jsonResponse({
  ...dashboard,
  collectedAt: '2026-11-02 14:40:10',
  cpuPercent: null,
  cpuPeak: null,
  memoryPercent: null,
  memoryCapacity: null,
  diskPercent: null,
  bars: [{ cpuHeight: null, memoryHeight: null, label: '14:40' }],
  services: []
}));
await intervals[0].handler();

assert.equal(roleOf('cpu-percent').textContent, '—');
assert.equal(roleOf('cpu-unit').hidden, true, '측정 불가 값에는 % 단위를 숨겨야 합니다.');
assert.equal(roleOf('cpu-label').textContent, '측정값 없음');
assert.equal(roleOf('cpu-peak').textContent, '', '피크가 없으면 빈 값이어야 합니다.');
assert.equal(roleOf('memory-percent').textContent, '—');
assert.equal(roleOf('memory-unit').hidden, true);
assert.equal(roleOf('memory-capacity').textContent, '');
assert.equal(roleOf('cpu-progress').textContent, '—', '진행 막대도 0%가 아니라 미수집으로 보여야 합니다.');
assert.equal(roleOf('disk-progress').textContent, '—');
assert.equal(roleOf('cpu-track').style.width, '0%');
assert.equal(bars.children.length, 1);
assert.equal(bars.children[0].children[0].children.length, 0, '측정 불가 구간에는 막대를 그리지 않습니다.');
assert.equal(bars.children[0].children[1].textContent, '14:40');
assert.equal(services.children.length, 1);
assert.equal(services.children[0].className, 'table-empty');
assert.equal(services.children[0].textContent, '수집된 서비스 상태가 없습니다.');

/* ── 4. 단일 in-flight: 느린 응답 중에는 다시 보내지 않음 ─────────────── */
let release = null;
const beforePending = requests.length;
responder = () => new Promise(resolve => { release = () => resolve(jsonResponse(dashboard)); });
const pending = intervals[0].handler();
const duplicate = intervals[0].handler();
assert.equal(requests.length, beforePending + 1, '응답이 오기 전에는 요청을 겹쳐 보내지 않습니다.');
release();
await Promise.all([pending, duplicate]);

/* ── 5. 통신 실패: 기존 수치 유지 + 실패 안내 ─────────────────────────── */
responder = () => Promise.reject(new Error('network down'));
const beforeFailure = { cpu: roleOf('cpu-percent').textContent, api: roleOf('api-status').textContent };
await intervals[0].handler();
assert.equal(roleOf('cpu-percent').textContent, beforeFailure.cpu, '실패해도 기존 수치를 지우지 않습니다.');
assert.equal(roleOf('api-status').textContent, beforeFailure.api);
assert.equal(roleOf('status').textContent, '자동 갱신 실패 · 마지막 수집 값');
assert.equal(roleOf('status').hidden, false);
assert.equal(roleOf('status').classList.contains('warn'), true);
assert.equal(roleOf('status-detail').textContent, '자동 갱신에 실패했습니다. 표시 중인 값은 마지막 수집 결과입니다.');
assert.equal(requests.length, beforePending + 2);

/* ── 6. 5xx도 같은 실패 처리 ─────────────────────────────────────────── */
responder = () => Promise.resolve(jsonResponse(null, { status: 500 }));
await intervals[0].handler();
assert.equal(roleOf('cpu-percent').textContent, beforeFailure.cpu);
assert.equal(roleOf('status').hidden, false);

/* ── 7. 세션 만료(403): 무한 재시도 중단 + 명확한 안내 ────────────────── */
responder = () => Promise.resolve(jsonResponse(null, { status: 403 }));
await intervals[0].handler();
assert.equal(cleared.length, 1, '권한 오류 뒤에는 타이머를 정리해야 합니다.');
assert.equal(intervals[0].handler, cleared[0].handler);
assert.equal(roleOf('status').textContent.includes('세션'), true);
assert.equal(roleOf('status-detail').textContent.includes('로그인 세션'), true);
assert.equal(roleOf('cpu-percent').textContent, beforeFailure.cpu, '세션 만료에도 기존 수치를 유지합니다.');
const afterStop = requests.length;
await intervals[0].handler();
assert.equal(requests.length, afterStop, '중단된 뒤에는 더 요청하지 않습니다.');

/* ── 8. 로그인으로 리다이렉트된 응답도 같은 중단 ─────────────────────── */
function makeHarness() {
  const local = {};
  const localRoles = {};
  const localRole = name => (localRoles[name] ??= new El('span', { monitoring: name }));
  const localRefresh = new El('button');
  localRefresh.disabled = true;
  localRefresh.setAttribute('type', 'button');
  const localRoot = new El('section', {
    monitoringUrl: MONITORING_URL,
    csrfHeader: CSRF_HEADER,
    csrfToken: CSRF_TOKEN
  });
  localRoot.querySelector = selector => {
    if (selector === '[data-monitoring-refresh]') return localRefresh;
    const match = /^\[data-monitoring="([a-z-]+)"\]$/.exec(selector);
    return match ? localRole(match[1]) : null;
  };
  localRole('cpu-percent').textContent = '11';
  localRole('status').hidden = true;
  local.document = {
    hidden: false,
    getElementById: id => (id === 'system-dashboard' ? localRoot : null),
    querySelector: () => null,
    querySelectorAll: () => [],
    addEventListener() {},
    createElement: tag => new El(tag)
  };
  local.intervals = [];
  local.cleared = [];
  local.requests = [];
  local.responder = () => Promise.reject(new Error('no responder'));
  local.window = {
    PlayOnAdmin: { openModal() {}, closeModal() {}, trapFocus() {}, toast() {} },
    setInterval(handler, delay) { const timer = { handler, delay }; local.intervals.push(timer); return timer; },
    clearInterval(timer) { local.cleared.push(timer); }
  };
  local.load = async () => runInNewContext(await Bun.file(SOURCE_URL).text(), {
    window: local.window,
    document: local.document,
    Element: El,
    fetch: (url, options) => {
      const pending = local.responder(url, options);
      local.requests.push({ url, options, settled: Promise.resolve(pending).catch(() => {}) });
      return pending;
    }
  });
  local.role = localRole;
  local.refresh = localRefresh;
  return local;
}

const redirectHarness = makeHarness();
await redirectHarness.load();
redirectHarness.responder = () => Promise.resolve(jsonResponse(null, { status: 200, redirected: true, contentType: 'text/html' }));
await redirectHarness.intervals[0].handler();
assert.equal(redirectHarness.cleared.length, 1, '로그인 페이지로 튕기면 자동 갱신을 멈춰야 합니다.');
assert.equal(redirectHarness.role('status').textContent.includes('세션'), true);
assert.equal(redirectHarness.role('cpu-percent').textContent, '11', '리다이렉트에도 기존 수치를 유지합니다.');
const redirectRequests = redirectHarness.requests.length;
await redirectHarness.intervals[0].handler();
assert.equal(redirectHarness.requests.length, redirectRequests);

/* ── 9. 숨겨진 탭에서는 요청하지 않음 ────────────────────────────────── */
const hiddenHarness = makeHarness();
await hiddenHarness.load();
hiddenHarness.responder = () => Promise.resolve(jsonResponse(dashboard));
hiddenHarness.document.hidden = true;
await hiddenHarness.intervals[0].handler();
assert.equal(hiddenHarness.requests.length, 0, '보이지 않는 탭에서 주기 요청을 낭비하지 않습니다.');
hiddenHarness.document.hidden = false;
await hiddenHarness.intervals[0].handler();
assert.equal(hiddenHarness.requests.length, 1);

/* ── 10. 수동 새로고침: 같은 POST 경로 + CSRF만 사용(disabled → 활성화) ──── */
const manual = makeHarness();
assert.equal(manual.refresh.disabled, true, '템플릿은 JS 초기화 전까지 버튼을 비활성으로 둡니다.');
await manual.load();
assert.equal(typeof manual.refresh.listeners.click?.[0], 'function', '수동 새로고침 버튼에 클릭 핸들러를 연결해야 합니다.');
assert.equal(manual.refresh.disabled, false, '스크립트가 정상 초기화되면 버튼을 활성화해야 합니다.');
assert.equal(manual.refresh.getAttribute('aria-busy'), 'false');
assert.equal(manual.refresh.getAttribute('aria-disabled'), 'false');

manual.responder = () => Promise.resolve(jsonResponse({ ...dashboard, cpuPercent: 77, cpuPeak: 90 }));
const manualBefore = manual.requests.length;
const click = manual.refresh.dispatch('click');
assert.equal(click.prevented, false, '버튼이므로 기본 이동(정적 GET)이 없어야 합니다.');
assert.equal(manual.requests.length, manualBefore + 1);
assert.equal(manual.requests[manualBefore].url, MONITORING_URL);
assert.equal(manual.requests[manualBefore].options.method, 'POST', '수동 조작은 POST만 보내야 합니다.');
assert.equal(manual.requests[manualBefore].options.credentials, 'same-origin');
assert.equal(manual.requests[manualBefore].options.headers[CSRF_HEADER], CSRF_TOKEN,
  '수동 갱신도 같은 CSRF 헤더를 실어야 합니다.');
assert.equal(manual.refresh.disabled, true, '요청 중에는 다시 누를 수 없어야 합니다.');
assert.equal(manual.refresh.getAttribute('aria-busy'), 'true', '요청 중에는 진행 상태를 노출해야 합니다.');

await manual.requests[manualBefore].settled;
await flush();
assert.equal(manual.role('cpu-percent').textContent, '77', '수동 갱신 성공 시 DOM을 갱신해야 합니다.');
assert.equal(manual.role('cpu-peak').textContent, '· 피크 90%');
assert.equal(manual.refresh.getAttribute('aria-busy'), 'false');
assert.equal(manual.role('status').hidden, true);
assert.equal(manual.role('status-detail').textContent, '모니터링 값을 새로 고쳤습니다.',
  '수동 갱신 성공은 화면을 바꾸지 않고 보조기술에만 알립니다.');
assert.equal(manual.intervals.length, 1, '수동 갱신이 자동 polling 타이머를 새로 만들지 않습니다.');

// 같은 성공 문구를 두 번 받아도 live region이 비워졌다가 다시 채워져 재낭독됩니다.
manual.responder = () => Promise.resolve(jsonResponse({ ...dashboard, cpuPercent: 78 }));
const repeatStart = manual.requests.length;
manual.refresh.dispatch('click');
assert.equal(manual.role('status-detail').textContent, '', '요청 시작 시 이전 안내를 비워 반복 낭독을 보장합니다.');
await manual.requests[repeatStart].settled;
await flush();
assert.equal(manual.role('status-detail').textContent, '모니터링 값을 새로 고쳤습니다.');

/* ── 11. 연속 클릭은 중복 송신하지 않음 ──────────────────────────────── */
let releaseManual = null;
manual.responder = () => new Promise(resolve => { releaseManual = () => resolve(jsonResponse({ ...dashboard, cpuPercent: 55 })); });
const burstStart = manual.requests.length;
manual.refresh.dispatch('click');
manual.refresh.dispatch('click');
manual.refresh.dispatch('click');
assert.equal(manual.requests.length, burstStart + 1, '요청이 진행 중일 때 연속 클릭은 새 요청을 보내지 않습니다.');
assert.equal(manual.refresh.disabled, true, '요청 중에는 버튼이 비활성이어야 합니다.');
assert.equal(manual.refresh.getAttribute('aria-disabled'), 'true', '요청 중에는 다시 누를 수 없음을 알려야 합니다.');
releaseManual();
await manual.requests[burstStart].settled;
await flush();
assert.equal(manual.requests.length, burstStart + 1, '진행 중 클릭을 모아 추가 요청을 보내지 않습니다.');
assert.equal(manual.role('cpu-percent').textContent, '55');
assert.equal(manual.refresh.disabled, false, '응답이 끝나면 다시 누를 수 있어야 합니다.');
assert.equal(manual.refresh.getAttribute('aria-disabled'), 'false');
assert.equal(manual.refresh.getAttribute('aria-busy'), 'false');

/* ── 12. 수동 갱신 실패: 기존 수치 보존 + 실패 안내 ───────────────────── */
const keptCpu = manual.role('cpu-percent').textContent;
manual.responder = () => Promise.reject(new Error('network down'));
const failStart = manual.requests.length;
manual.refresh.dispatch('click');
await manual.requests[failStart].settled;
await flush();
assert.equal(manual.role('cpu-percent').textContent, keptCpu, '수동 갱신이 실패해도 기존 값을 지우지 않습니다.');
assert.equal(manual.role('status').hidden, false);
assert.equal(manual.role('status-detail').textContent, '자동 갱신에 실패했습니다. 표시 중인 값은 마지막 수집 결과입니다.');
assert.equal(manual.refresh.disabled, false, '실패해도 버튼은 다시 사용할 수 있어야 합니다.');
assert.equal(manual.refresh.getAttribute('aria-busy'), 'false');

/* ── 13. 버튼은 GET으로 이동하지 않음(수정자 클릭 포함) ───────────────── */
const modifier = makeHarness();
await modifier.load();
modifier.responder = () => Promise.resolve(jsonResponse(dashboard));
// 버튼에는 href가 없으므로 새 탭 열기 경로도 없다. 수정자 클릭도 그대로 POST 갱신을 호출한다.
const modifierStart = modifier.requests.length;
const modifierClick = modifier.refresh.dispatch('click', { ctrlKey: true });
assert.equal(modifierClick.prevented, false);
assert.equal(modifier.requests.length, modifierStart + 1, '수정자 클릭에도 같은 POST 경로로 갱신합니다.');
assert.equal(modifier.requests[modifierStart].options.method, 'POST');
await modifier.requests[modifierStart].settled;
await flush();
assert.equal(modifier.refresh.hasAttribute('href'), false, '버튼에는 이동할 GET 경로가 없어야 합니다.');

/* ── 14. 세션 만료: GET 이동 없이 안내 유지 + 버튼 비활성 ─────────────── */
const expired = makeHarness();
await expired.load();
expired.responder = () => Promise.resolve(jsonResponse(null, { status: 403 }));
expired.refresh.dispatch('click');
await expired.requests[0].settled;
await flush();
assert.equal(expired.role('status').textContent.includes('세션'), true);
assert.equal(expired.role('status-detail').textContent.includes('로그인 세션'), true);
assert.equal(expired.refresh.disabled, true, '세션이 끝나면 버튼을 안전하게 비활성화해야 합니다.');
assert.equal(expired.role('cpu-percent').textContent, '11', '세션 만료에도 기존 수치를 유지합니다.');
expired.refresh.dispatch('click'); // disabled 상태의 클릭은 아무 것도 하지 않는다.
await flush();
assert.equal(expired.requests.length, 1, '세션 만료 뒤에는 수동 클릭으로도 요청하지 않습니다.');
assert.equal(expired.intervals.length > 0 && expired.cleared.length, 1, '자동 갱신 타이머는 그대로 정리됩니다.');

/* ── 15. 안전한 DOM 생성: 차트/서비스 목록에 innerHTML을 쓰지 않음 ─────── */
const source = await Bun.file(SOURCE_URL).text();
assert.equal(source.includes('innerHTML'), false, '응답 문자열은 textContent/createElement로만 반영합니다.');
assert.equal(source.includes('insertAdjacentHTML'), false);

/* ── 16. 템플릿 계약: POST 전용 버튼 + JS 초기화 전 disabled ───────────── */
const template = await Bun.file(TEMPLATE_URL).text();
assert.equal(template.includes('th:data-monitoring-url="@{/admin/system/monitoring}"'), true);
assert.equal(template.includes('th:data-csrf-header="${_csrf?.headerName}"'), true);
assert.equal(template.includes('th:data-csrf-token="${_csrf?.token}"'), true);
assert.equal(template.includes('data-monitoring-refresh'), true);
assert.equal(
  /<button class="admin-button" type="button" data-monitoring-refresh disabled>↻ 새로고침<\/button>/.test(template),
  true,
  '수동 새로고침은 disabled로 시작하는 type=button이어야 합니다.');
assert.equal(/data-monitoring-refresh[^>]*th:href/.test(template), false,
  '수동 새로고침에 GET 링크를 다시 붙이면 안 됩니다.');
assert.equal(template.includes("@{/admin/system(view='dashboard')}"), false,
  '모니터링 화면에는 수동 새로고침용 GET 경로가 남아 있으면 안 됩니다.');
assert.equal(/<noscript>[\s\S]*?JavaScript가 필요합니다[\s\S]*?<\/noscript>/.test(template), true,
  'JS가 없을 때 버튼이 비활성인 이유를 안내해야 합니다.');

console.log('PASS: monitoring refresh posts every 15s and on demand with CSRF, updates the DOM, and keeps values on failure.');
