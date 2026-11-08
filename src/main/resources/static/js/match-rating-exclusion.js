/* 매치 운영 화면의 노쇼 경기 레이팅 제외: 레이팅 반영 섹션의 버튼을 누르면 확인창을 띄우고,
   확인하면 폼 없이 fetch + CSRF 헤더로 보낸다. 결과 토스트가 새로고침에 묻히지 않도록 페이지를 다시
   불러오지 않고, 응답 outcome에 맞춰 섹션(배지·안내·버튼)만 바꾼다. 이미 제외·반영된 경기로 거절되면
   그 상태로 맞추고, 그 밖의 실패는 서버 message만 알린 뒤 확인창을 닫는다. */
(function (global) {
  'use strict';

  const root = document.getElementById('ops-rating');
  const modal = document.getElementById('rating-exclusion-modal');
  if (!root || !modal) return;

  const admin = global.PlayOnAdmin;
  const url = root.dataset.exclusionUrl;
  const csrfHeader = root.dataset.csrfHeader;
  const csrfToken = root.dataset.csrfToken;
  const submit = modal.querySelector('[data-rating-submit]');

  // 서버(RoomManagementService.ratingOf)가 그리는 처리 완료 상태와 같은 문구·톤이다.
  const PROCESSED = {
    EXCLUDED: { label: '레이팅 제외됨', tone: 'neutral', message: '레이팅 반영에서 제외된 경기입니다.' },
    ALREADY_EXCLUDED: { label: '레이팅 제외됨', tone: 'neutral', message: '레이팅 반영에서 제외된 경기입니다.' },
    ALREADY_APPLIED: { label: '반영 완료', tone: 'good', message: '레이팅에 반영된 경기라 제외할 수 없습니다.' }
  };
  const TONES = ['good', 'warn', 'bad', 'neutral'];

  let busy = false;

  function open() {
    submit.disabled = false;
    admin?.openModal(modal, '[data-rating-cancel]');
  }

  function close() {
    if (busy || modal.hidden) return;
    admin?.closeModal(modal);
  }

  /** 처리가 끝난 상태로 섹션을 바꾼다. 안내와 버튼은 반영 전에만 두므로 지운다. */
  function renderProcessed(state) {
    const badge = root.querySelector('.ops-section-heading .status-badge');
    if (badge) {
      badge.classList.remove(...TONES);
      badge.classList.add(state.tone);
      badge.textContent = state.label;
    }
    root.querySelector('.notice-strip')?.remove();
    root.querySelector('[data-rating-exclude]')?.remove();
    const actions = root.querySelector('.ops-rating-actions');
    if (!actions) return;
    let message = actions.querySelector('.ops-rating-message');
    if (!message) {
      message = document.createElement('p');
      message.className = 'ops-rating-message';
      actions.prepend(message);
    }
    message.textContent = state.message;
  }

  async function send() {
    if (busy) return;
    busy = true;
    submit.disabled = true;
    const options = { method: 'POST', headers: { Accept: 'application/json' }, credentials: 'same-origin' };
    if (csrfHeader && csrfToken) options.headers[csrfHeader] = csrfToken;
    let ok = false;
    let outcome = null;
    let text = '요청을 처리하지 못했습니다. 잠시 후 다시 시도해 주세요.';
    try {
      const response = await fetch(url, options);
      const data = await response.json().catch(() => ({}));
      ok = response.ok && !response.redirected;
      outcome = response.redirected ? null : data.outcome;
      text = data.message || text;
    } catch (error) {
      text = '서버에 연결하지 못했습니다. 네트워크 상태를 확인해 주세요.';
    }
    busy = false;
    close();
    const state = PROCESSED[outcome];
    if (state) renderProcessed(state);
    admin?.toast(text, !ok);
  }

  root.addEventListener('click', function (event) {
    const button = event.target instanceof Element ? event.target.closest('[data-rating-exclude]') : null;
    if (button && !button.disabled && !busy) open();
  });

  modal.addEventListener('click', function (event) {
    const target = event.target instanceof Element ? event.target : null;
    if (!target) return;
    if (target.closest('[data-rating-submit]')) {
      send();
      return;
    }
    if (target.closest('[data-rating-cancel]') || target === modal) close();
  });

  document.addEventListener('keydown', function (event) {
    if (modal.hidden) return;
    if (event.key === 'Escape') {
      close();
      return;
    }
    admin?.trapFocus(modal, event);
  });
})(window);
