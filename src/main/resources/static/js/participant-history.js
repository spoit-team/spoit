/* 매치 운영 화면의 참가자 이력 모달: 신청자·참가자 이름 버튼을 누르면 이력 조회 API의 요약을 보여 준다.
   조회만 하므로 CSRF가 필요 없고, 팀 배정 등 화면의 다른 동작에는 손대지 않는다.
   열고 닫기·초점 이동은 admin-shell.js의 PlayOnAdmin을 쓰고, ESC·닫기 버튼·배경 클릭으로 닫는다. */
(function (global) {
  'use strict';

  const modal = document.getElementById('participant-history-modal');
  if (!modal) return;

  const admin = global.PlayOnAdmin;
  const escapeHTML = global.PlayOnUI.escapeHTML;
  const baseUrl = modal.dataset.historyUrl;
  const title = modal.querySelector('[data-history-title]');
  const body = modal.querySelector('[data-history-body]');

  const ERROR_MESSAGES = {
    403: '담당 시설의 매칭방이 아니어서 참가자 이력을 볼 수 없습니다.',
    404: '이 매칭방에 신청·참가 기록이 없는 회원이거나 매칭방을 찾을 수 없습니다.',
    login: '로그인이 만료되었습니다. 다시 로그인한 뒤 열어 주세요.',
    other: '참가자 이력을 불러오지 못했습니다. 잠시 후 다시 시도해 주세요.'
  };

  let requestSeq = 0;     // 늦게 도착한 이전 요청의 응답을 버리기 위한 번호
  let current = null;     // { userId, name } 다시 시도할 대상

  function formatDate(value) {
    return typeof value === 'string' && value.length >= 10 ? value.slice(0, 10).replaceAll('-', '.') : '—';
  }

  // 부호를 함께 보여 색을 구분하지 못해도 오르내림을 알 수 있게 한다.
  function deltaOf(value) {
    const number = Number(value);
    if (!Number.isFinite(number) || number === 0) return { text: '0.00', tone: 'is-flat' };
    const size = Math.abs(number).toFixed(2);
    return number > 0 ? { text: '+' + size, tone: 'is-up' } : { text: '−' + size, tone: 'is-down' };
  }

  function renderState(heading, message, retry) {
    body.innerHTML = `<div class="empty-state"><b>${escapeHTML(heading)}</b><p>${escapeHTML(message)}</p>`
      + (retry ? '<p><button class="admin-button small" type="button" data-history-retry>다시 시도</button></p>' : '')
      + '</div>';
  }

  function kv(label, value) {
    return `<div class="report-kv"><small>${escapeHTML(label)}</small><b>${escapeHTML(value)}</b></div>`;
  }

  function renderHistory(history) {
    const changes = history.recentChanges || [];
    const penalties = history.activePenalties || [];
    const changeList = changes.length
      ? '<ol class="history-changes">' + changes.map(change => {
        const delta = deltaOf(change.finalDelta);
        return `<li class="history-change"><time datetime="${escapeHTML(String(change.calculatedAt ?? ''))}">`
          + `${escapeHTML(formatDate(change.calculatedAt))}</time>`
          + `<span class="history-delta ${delta.tone}">${escapeHTML(delta.text)}</span></li>`;
      }).join('') + '</ol>'
      : '<p class="history-empty">최근 레이팅 변동이 없습니다.</p>';
    const penaltyList = penalties.length
      ? '<div class="history-penalties">' + penalties.map(penalty => {
        const until = penalty.endAt ? formatDate(penalty.endAt) + '까지' : '기한 없음';
        return `<span class="status-badge warn">${escapeHTML(penalty.penaltyType)} · ${escapeHTML(until)}</span>`;
      }).join('') + '</div>'
      : '<p class="history-empty">적용 중인 제재가 없습니다.</p>';

    body.innerHTML = '<div class="report-kv-grid">'
      + kv('티어', history.tierLabel)
      + kv('반영 경기', `${history.ratedMatchCount}경기`)
      + kv('레이팅 제외 경기', `${history.excludedMatchCount}건`)
      + kv('처리 완료 신고', `${history.confirmedReportCount}건`)
      + '</div>'
      + `<section class="history-section"><h3>최근 레이팅 변동</h3>${changeList}</section>`
      + `<section class="history-section"><h3>적용 중인 제재</h3>${penaltyList}</section>`;
  }

  async function fetchHistory(userId) {
    try {
      const response = await fetch(`${baseUrl}/${encodeURIComponent(userId)}/history`, {
        headers: { Accept: 'application/json' },
        credentials: 'same-origin'
      });
      // 세션이 끝나면 로그인 화면으로 리다이렉트되어 200 HTML이 온다.
      if (response.redirected) return { error: 'login' };
      if (!response.ok) return { error: ERROR_MESSAGES[response.status] ? response.status : 'other' };
      return { history: await response.json() };
    } catch (error) {
      return { error: 'other' };
    }
  }

  async function load(target) {
    const seq = ++requestSeq;
    current = target;
    title.textContent = target.name || '참가자';
    modal.setAttribute('aria-busy', 'true');
    renderState('불러오는 중', '참가자 이력을 불러오는 중입니다.', false);

    const result = await fetchHistory(target.userId);
    if (seq !== requestSeq || modal.hidden) return;
    modal.removeAttribute('aria-busy');
    if (result.error) {
      renderState('이력을 볼 수 없습니다', ERROR_MESSAGES[result.error], result.error === 'other');
      return;
    }
    title.textContent = result.history.nickname || title.textContent;
    renderHistory(result.history);
  }

  function open(trigger) {
    admin.openModal(modal, '.modal-close');
    load({ userId: trigger.dataset.historyUserId, name: trigger.textContent.trim() });
  }

  function close() {
    requestSeq++;
    modal.removeAttribute('aria-busy');
    admin.closeModal(modal);
  }

  document.addEventListener('click', function (event) {
    const target = event.target instanceof Element ? event.target : null;
    if (!target) return;
    const trigger = target.closest('[data-history-user-id]');
    if (trigger) {
      open(trigger);
      return;
    }
    if (modal.hidden) return;
    if (target.closest('[data-history-retry]') && current) {
      load(current);
      return;
    }
    if (target.closest('[data-history-close]') || target === modal) close();
  });

  document.addEventListener('keydown', function (event) {
    if (modal.hidden) return;
    if (event.key === 'Escape') {
      event.preventDefault();
      close();
      return;
    }
    admin.trapFocus(modal, event);
  });
})(window);
