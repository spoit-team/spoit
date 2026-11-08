/* 시스템 관리자 UI: 역할 변경 모달과 서버가 렌더링한 신고 상세 모달만 담당합니다.
   역할 변경·신고 조치·계정 상태 변경은 모두 서버 폼으로 전송됩니다. */
(function (global) {
  'use strict';

  const admin = global.PlayOnAdmin;
  const roleModal = document.getElementById('role-modal');
  const reportModal = document.getElementById('report-modal');
  const reportBody = document.getElementById('report-modal-body');
  const reportTitle = document.getElementById('report-modal-title');
  const reportSubtitle = document.getElementById('report-modal-subtitle');

  function openModal(modal, focusSelector) {
    admin?.openModal(modal, focusSelector);
  }

  function closeModal(modal) {
    admin?.closeModal(modal);
  }

  function closeReport() {
    closeModal(reportModal);
    if (reportBody) reportBody.replaceChildren();
    if (reportTitle) reportTitle.textContent = '신고 상세';
    if (reportSubtitle) reportSubtitle.textContent = '관련 활동 내역을 검토해 주세요.';
  }

  document.addEventListener('click', function (event) {
    const target = event.target instanceof Element ? event.target : null;
    if (!target) return;

    const roleButton = target.closest('[data-user-role]');
    if (roleButton) {
      document.getElementById('role-user-id').value = roleButton.dataset.userId || '';
      document.getElementById('role-user-label').value = roleButton.dataset.userLabel || '';
      document.getElementById('role-select').value = roleButton.dataset.userRoleValue || 'member';
      document.getElementById('role-reason').value = '';
      openModal(roleModal, '#role-select');
      return;
    }

    if (target.closest('[data-close-role]')) {
      closeModal(roleModal);
      return;
    }

    if (target.closest('[data-close-report]')) {
      closeReport();
      return;
    }

    const reportButton = target.closest('[data-open-report]');
    if (reportButton) {
      const id = reportButton.dataset.reportId || '';
      const source = document.getElementById('report-detail-' + id);
      if (!source || !reportBody) {
        admin?.toast('신고 상세 정보를 찾을 수 없습니다.', true);
        return;
      }
      reportBody.replaceChildren(source.content.cloneNode(true));
      if (reportTitle) reportTitle.textContent = source.dataset.reportTitle || '신고 상세';
      if (reportSubtitle) reportSubtitle.textContent = source.dataset.reportSubtitle || '관련 활동 내역을 검토해 주세요.';
      openModal(reportModal, '.report-decision-card select');
    }
  });

  [roleModal, reportModal].forEach(function (modal) {
    modal?.addEventListener('click', function (event) {
      if (event.target !== modal) return;
      if (modal === reportModal) closeReport();
      else closeModal(modal);
    });
  });

  document.addEventListener('keydown', function (event) {
    if (event.key !== 'Escape') return;
    if (reportModal && !reportModal.hidden) {
      closeReport();
      return;
    }
    if (roleModal && !roleModal.hidden) closeModal(roleModal);
  });

  document.addEventListener('keydown', function (event) {
    [roleModal, reportModal].forEach(function (modal) {
      if (modal && !modal.hidden) admin?.trapFocus(modal, event);
    });
  });

  const settingsForm = document.getElementById('system-settings-form');
  function markSettingsDirty() {
    const state = document.getElementById('settings-state');
    if (state) state.textContent = '저장하지 않은 변경 사항이 있습니다.';
  }

  settingsForm?.addEventListener('input', markSettingsDirty);
  settingsForm?.addEventListener('change', markSettingsDirty);
  settingsForm?.addEventListener('click', function (event) {
    if (event.target instanceof Element && event.target.closest('.toggle[role="switch"]')) markSettingsDirty();
  });

  /* ── 모니터링 자동 갱신 ─────────────────────────────────────────────────
     모니터링 영역만 15초마다 POST로 다시 받아 제자리에서 갱신합니다. 전체 페이지를 다시
     그리지 않으므로 다른 탭에서 입력 중인 폼 내용이 사라지지 않습니다. 응답이 없거나 실패하면
     기존 수치를 지우지 않고 마지막 수집 결과임을 알립니다. */
  const MONITORING_INTERVAL_MS = 15000;
  const SESSION_STOP_MESSAGE = '세션 만료 · 자동 갱신 중지';
  const SESSION_STOP_ANNOUNCEMENT = '로그인 세션이 만료되어 자동 갱신을 멈췄습니다. 페이지를 새로고침해 주세요.';
  const FAILURE_MESSAGE = '자동 갱신 실패 · 마지막 수집 값';
  const FAILURE_ANNOUNCEMENT = '자동 갱신에 실패했습니다. 표시 중인 값은 마지막 수집 결과입니다.';
  const EMPTY_CHART_NOTE = '앱 재시작 후 15초 간격으로 수집해 5분 평균 차트를 다시 쌓습니다 (최대 최근 12시간)';
  const FILLED_CHART_NOTE = '최근 12시간 · 15초 수집 · 차트는 5분 평균 · 앱 재시작 시 이력 초기화';

  const monitoringRoot = document.getElementById('system-dashboard');
  const monitoringUrl = monitoringRoot?.dataset.monitoringUrl || '';
  const monitoringFields = {};
  let refreshButton = null;
  let monitoringInFlight = false;
  let monitoringStopped = !monitoringRoot || !monitoringUrl;
  let monitoringFailed = false;
  let monitoringTimer = 0;

  function field(role) {
    if (!(role in monitoringFields)) {
      monitoringFields[role] = monitoringRoot?.querySelector('[data-monitoring="' + role + '"]') ?? null;
    }
    return monitoringFields[role];
  }

  function setText(role, value) {
    const element = field(role);
    if (element) element.textContent = value;
  }

  function setPercent(role, value) {
    setText(role, value === null || value === undefined ? '—' : String(value));
    const unit = field(role === 'cpu-percent' ? 'cpu-unit' : 'memory-unit');
    if (unit) unit.hidden = value === null || value === undefined;
  }

  function setProgress(role, value) {
    setText(role, value === null || value === undefined ? '—' : value + '%');
    const track = field(role.replace('-progress', '-track'));
    if (track) track.style.width = (value === null || value === undefined ? 0 : value) + '%';
  }

  function setStatus(message, tone) {
    const badge = field('status');
    if (!badge) return;
    badge.textContent = message || '';
    // .status-badge는 author 규칙이라 [hidden]을 덮으므로, 숨길 때는 배지 클래스를 먼저 뺀다.
    badge.classList.remove('status-badge', 'bad', 'warn');
    badge.hidden = !message;
    if (message) {
      badge.classList.add('status-badge');
      badge.classList.add(tone === 'warn' ? 'warn' : 'bad');
    }
  }

  /* 화면 문구와 별개로 보조기술에만 알린다(수동 새로고침 성공처럼 조용한 피드백용). */
  function announce(text) {
    const detail = field('status-detail');
    if (detail) detail.textContent = text || '';
  }

  function stopMonitoring(message, announcement) {
    monitoringStopped = true;
    if (monitoringTimer) {
      global.clearInterval(monitoringTimer);
      monitoringTimer = 0;
    }
    setStatus(message, 'warn');
    announce(announcement);
    syncRefreshButton(); // 세션 만료 뒤에는 버튼을 다시 눌러도 요청이 나가지 않게 비활성화한다.
  }

  function renderBars(bars) {
    const container = field('bars');
    if (!container) return;
    const groups = [];
    (Array.isArray(bars) ? bars : []).forEach(function (bar) {
      if (!bar) return;
      const group = document.createElement('div');
      group.className = 'bar-group';
      const pair = document.createElement('div');
      pair.className = 'bar-pair';
      if (bar.cpuHeight !== null && bar.cpuHeight !== undefined) {
        const cpuBar = document.createElement('i');
        cpuBar.className = 'bar';
        cpuBar.style.height = bar.cpuHeight + '%';
        pair.appendChild(cpuBar);
      }
      if (bar.memoryHeight !== null && bar.memoryHeight !== undefined) {
        const memoryBar = document.createElement('i');
        memoryBar.className = 'bar green';
        memoryBar.style.height = bar.memoryHeight + '%';
        pair.appendChild(memoryBar);
      }
      const label = document.createElement('span');
      label.className = 'bar-label';
      label.textContent = bar.label ?? '';
      group.appendChild(pair);
      group.appendChild(label);
      groups.push(group);
    });
    container.replaceChildren(...groups);    const isEmpty = groups.length === 0;
    setText('chart-note', isEmpty ? EMPTY_CHART_NOTE : FILLED_CHART_NOTE);
    setText('chart-empty', isEmpty ? '아직 표본이 없습니다 (재시작 직후)' : 'JVM CPU · 힙 메모리 (%)');
  }

  function renderServices(services) {
    const container = field('services');
    if (!container) return;
    const rows = [];
    (Array.isArray(services) ? services : []).forEach(function (service) {
      if (!service) return;
      const row = document.createElement('div');
      row.className = 'system-status-row';
      const dot = document.createElement('i');
      dot.className = service.tone ? 'live-dot ' + service.tone : 'live-dot';
      const name = document.createElement('span');
      name.className = 'service-name';
      name.textContent = service.name ?? '';
      const detail = document.createElement('small');
      detail.textContent = service.detail ?? '';
      row.appendChild(dot);
      row.appendChild(name);
      row.appendChild(detail);
      rows.push(row);
    });
    if (rows.length === 0) {
      const empty = document.createElement('p');
      empty.className = 'table-empty';
      empty.textContent = '수집된 서비스 상태가 없습니다.';
      rows.push(empty);
    }
    container.replaceChildren(...rows);
  }

  function renderMonitoring(data) {
    if (!data || typeof data !== 'object') throw new TypeError('모니터링 응답 형식이 올바르지 않습니다.');
    const collectedAt = data.collectedAt != null ? String(data.collectedAt) : null;
    setText('collected-at', collectedAt !== null ? '마지막 수집 ' + collectedAt : '수집 시각 없음');
    setText('chart-collected-at', collectedAt !== null ? '마지막 수집 ' + collectedAt : '수집 전');

    setText('api-status', data.apiStatus != null ? String(data.apiStatus) : '—');
    setText('api-uptime', data.apiUptime != null ? String(data.apiUptime) : '—');
    setText('api-uptime-label', data.apiUptime != null ? '현재 프로세스 가동시간' : '가동시간 정보 없음');

    setPercent('cpu-percent', data.cpuPercent);
    setText('cpu-label', data.cpuPercent != null ? 'JVM 프로세스 CPU' : '측정값 없음');
    setText('cpu-peak', data.cpuPeak != null ? '· 피크 ' + data.cpuPeak + '%' : '');

    setPercent('memory-percent', data.memoryPercent);
    setText('memory-label', data.memoryPercent != null ? 'JVM 힙 사용률' : '측정값 없음');
    setText('memory-capacity', data.memoryCapacity != null ? '최대 힙 ' + data.memoryCapacity : '');

    const badge = field('service-badge');
    if (badge) {
      badge.classList.remove('good', 'warn', 'bad');
      badge.classList.add(collectedAt === null ? 'neutral' : (data.allHealthy ? 'good' : 'warn'));
      badge.textContent = collectedAt === null ? '수집 전' : (data.allHealthy ? 'API·DB 정상' : 'API·DB 확인 필요');
    }

    setProgress('cpu-progress', data.cpuPercent);
    setProgress('memory-progress', data.memoryPercent);
    setProgress('disk-progress', data.diskPercent);

    renderBars(data.bars);
    renderServices(data.services);
  }

  /* 단일 in-flight: 이전 요청이 끝나기 전에는 다음 요청을 보내지 않습니다.
     수동 새로고침(manual)은 자동 갱신과 같은 POST 경로를 쓰되, 숨은 탭 검사를 건너뜁니다. */
  async function refreshMonitoring(manual) {
    if (monitoringStopped || monitoringInFlight || !monitoringRoot) return;
    if (manual ? monitoringRoot.hidden : (document.hidden || monitoringRoot.hidden)) return;
    monitoringInFlight = true;
    if (manual) announce(''); // 같은 성공 문구가 반복되어도 다시 읽히도록 이전 안내를 먼저 비운다.
    syncRefreshButton(); // 진행 중임을 버튼에도 알린다(자동·수동 공통).
    try {
      const headers = { Accept: 'application/json', 'X-Requested-With': 'fetch' };
      if (monitoringRoot.dataset.csrfHeader && monitoringRoot.dataset.csrfToken) {
        headers[monitoringRoot.dataset.csrfHeader] = monitoringRoot.dataset.csrfToken;
      }
      const response = await fetch(monitoringUrl, {
        method: 'POST',
        credentials: 'same-origin',
        headers: headers,
        redirect: 'follow'
      });
      if (response.status === 401 || response.status === 403) {
        stopMonitoring(SESSION_STOP_MESSAGE, SESSION_STOP_ANNOUNCEMENT);
        return;
      }
      if (!response.ok) throw new Error('HTTP ' + response.status);
      const contentType = response.headers.get('content-type') || '';
      if (response.redirected || !contentType.includes('application/json')) {
        stopMonitoring(SESSION_STOP_MESSAGE, SESSION_STOP_ANNOUNCEMENT);
        return;
      }
      renderMonitoring(await response.json());
      if (monitoringFailed) {
        monitoringFailed = false;
        setStatus('');
        announce('');
      } else if (manual) {
        // 사용자가 직접 누른 갱신이므로 반영됐음을 조용히 알린다(자동 갱신은 알리지 않는다).
        setStatus('');
        announce('모니터링 값을 새로 고쳤습니다.');
      }
    } catch (error) {
      // 기존 수치는 그대로 두고 실패만 알린다. 같은 실패가 반복되면 안내를 다시 읽지 않는다.
      if (!monitoringFailed) {
        monitoringFailed = true;
        setStatus(FAILURE_MESSAGE, 'warn');
        announce(FAILURE_ANNOUNCEMENT);
      }
    } finally {
      monitoringInFlight = false;
      syncRefreshButton();
    }
  }

  /* 수동 새로고침: 화면 전체를 다시 그리지 않고 자동 갱신과 같은 POST JSON 경로만 사용합니다.
     버튼은 템플릿에서 disabled로 시작하고, 이 스크립트가 정상 초기화될 때만 활성화합니다.
     JS가 로드되지 않으면 눌러도 아무 요청도 나가지 않습니다(GET 이동 경로 없음). */
  function syncRefreshButton() {
    refreshButton ??= monitoringRoot?.querySelector('[data-monitoring-refresh]') ?? null;
    if (!refreshButton) return;
    const busy = monitoringInFlight;
    // 세션이 끝났으면 다시 눌러도 소용없으므로 비활성으로 남긴다.
    refreshButton.disabled = busy || monitoringStopped;
    refreshButton.setAttribute('aria-busy', busy ? 'true' : 'false');
    refreshButton.setAttribute('aria-disabled', refreshButton.disabled ? 'true' : 'false');
  }

  function requestManualRefresh() {
    if (!refreshButton || refreshButton.disabled) return;
    refreshMonitoring(true); // 진행 중이면 단일 in-flight 규칙에 따라 아무 것도 보내지 않는다.
  }

  if (!monitoringStopped) {
    monitoringTimer = global.setInterval(refreshMonitoring, MONITORING_INTERVAL_MS);
  }

  refreshButton ??= monitoringRoot?.querySelector('[data-monitoring-refresh]') ?? null;
  refreshButton?.addEventListener('click', requestManualRefresh);
  syncRefreshButton();
})(window);
