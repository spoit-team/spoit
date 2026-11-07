/* 동호회 이력 창: 이름 버튼을 누르면 data-history-url 주소로 이력을 불러와 보여 준다.
   participant-history.js(시설 관리자 참가자 이력)와 같은 흐름, 창 열고 닫기만 <dialog>로 한다. */
(function (global) {
  'use strict';

  const dialog = document.getElementById('team-history-dialog');
  if (!dialog) return;

  const escapeHTML = global.PlayOnUI.escapeHTML;
  const title = dialog.querySelector('[data-history-title]');
  const body = dialog.querySelector('[data-history-body]');
  const kicker = dialog.querySelector('[data-history-kicker]');

  const ERROR_MESSAGES = {
    403: '동호회장만 가입 신청자 이력을 볼 수 있습니다.',
    404: '이미 처리된 가입 신청이거나 이 동호회의 동호회원이 아닙니다.',
    login: '로그인이 만료되었습니다. 다시 로그인한 뒤 열어 주세요.',
    other: '이력을 불러오지 못했습니다. 잠시 후 다시 시도해 주세요.'
  };

  let requestSeq = 0;   // 늦게 도착한 이전 요청의 응답을 버리기 위한 번호
  let current = null;   // { url, name } 다시 시도할 대상
  let opener = null;    // 닫은 뒤 초점을 돌려줄 버튼

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
    body.innerHTML = `<div class="team-history-state"><b>${escapeHTML(heading)}</b><p>${escapeHTML(message)}</p>`
      + (retry ? '<p><button class="team-history-button" type="button" data-history-retry>다시 시도</button></p>' : '')
      + '</div>';
  }

  function kv(label, value) {
    return `<div class="team-history-kv"><small>${escapeHTML(label)}</small><b>${escapeHTML(value)}</b></div>`;
  }

  function renderHistory(history) {
    const changes = history.recentChanges || [];
    const penalties = history.activePenalties || [];
    const changeList = changes.length
      ? '<ol class="team-history-changes">' + changes.map(change => {
        const delta = deltaOf(change.finalDelta);
        return `<li class="team-history-change"><time datetime="${escapeHTML(String(change.calculatedAt ?? ''))}">`
          + `${escapeHTML(formatDate(change.calculatedAt))}</time>`
          + `<span class="team-history-delta ${delta.tone}">${escapeHTML(delta.text)}</span></li>`;
      }).join('') + '</ol>'
      : '<p class="team-history-empty">최근 레이팅 변동이 없습니다.</p>';
    const penaltyList = penalties.length
      ? '<div class="team-history-penalties">' + penalties.map(penalty => {
        const until = penalty.endAt ? formatDate(penalty.endAt) + '까지' : '기한 없음';
        return `<span class="team-history-badge">${escapeHTML(penalty.penaltyType)} · ${escapeHTML(until)}</span>`;
      }).join('') + '</div>'
      : '<p class="team-history-empty">적용 중인 제재가 없습니다.</p>';

    // 동호회원 이력(②)은 lastClubMatchAt 칸이 있음: 신고·제재 대신 마지막 동호회 경기일
    const isClub = 'lastClubMatchAt' in history;
    body.innerHTML = '<div class="team-history-grid">'
      + kv('티어', history.tierLabel)
      + kv(isClub ? '동호회 반영 경기' : '반영 경기', `${history.ratedMatchCount}경기`)
      + kv('레이팅 제외 경기', `${history.excludedMatchCount}건`)
      + (isClub
        ? kv('마지막 동호회 경기일', history.lastClubMatchAt ? formatDate(history.lastClubMatchAt) : '기록 없음')
        : kv('처리 완료 신고', `${history.confirmedReportCount}건`))
      + '</div>'
      + `<section class="team-history-section"><h3>${isClub ? '최근 동호회 경기 레이팅 변동' : '최근 레이팅 변동'}</h3>${changeList}</section>`
      + (isClub ? '' : `<section class="team-history-section"><h3>적용 중인 제재</h3>${penaltyList}</section>`);
  }

  async function fetchHistory(url) {
    try {
      const response = await fetch(url, { headers: { Accept: 'application/json' }, credentials: 'same-origin' });
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
    title.textContent = target.name || '이력';
    dialog.setAttribute('aria-busy', 'true');
    renderState('불러오는 중', '이력을 불러오는 중입니다.', false);

    const result = await fetchHistory(target.url);
    if (seq !== requestSeq || !dialog.open) return;
    dialog.removeAttribute('aria-busy');
    if (result.error) {
      renderState('이력을 볼 수 없습니다', ERROR_MESSAGES[result.error], result.error === 'other');
      return;
    }
    title.textContent = result.history.nickname || title.textContent;
    renderHistory(result.history);
  }

  function open(trigger) {
    opener = trigger;
    // 제목 위 작은 글씨: 가입 신청자 이력 / 동호회 이력 (이름 버튼의 data-history-label)
    kicker.textContent = trigger.dataset.historyLabel || '이력';
    if (!dialog.open) dialog.showModal();
    load({ url: trigger.dataset.historyUrl, name: trigger.textContent.trim() });
  }

  document.addEventListener('click', function (event) {
    const target = event.target instanceof Element ? event.target : null;
    if (!target) return;
    const trigger = target.closest('[data-history-url]');
    if (trigger) {
      open(trigger);
      return;
    }
    if (!dialog.open) return;
    if (target.closest('[data-history-retry]') && current) {
      load(current);
      return;
    }
    if (target.closest('[data-history-close]') || target === dialog) dialog.close();
  });

  // ESC·닫기 버튼·배경 클릭 모두 여기로 옴: 늦게 온 응답 버리고 초점 돌려줌
  dialog.addEventListener('close', function () {
    requestSeq++;
    dialog.removeAttribute('aria-busy');
    opener?.focus?.();
    opener = null;
  });
})(window);
