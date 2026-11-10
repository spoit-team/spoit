/* 매치 운영 화면의 참가 신청 승인·반려: 대기 신청 행의 버튼을 누르면 확인창을 띄우고,
   확인하면 폼 없이 fetch + CSRF 헤더로 보낸다. 성공하면 참가 현황·팀 배정 버튼 상태까지 맞추기 위해
   페이지를 새로 불러오되, 결과 토스트는 새로고침된 화면에서 보여 준다. 실패하면 서버 message를 알린 뒤 확인창을 닫는다.
   팀 신청도 팀원 한 명(신청 행 하나)씩 처리한다. */
(function (global) {
  'use strict';

  const root = document.getElementById('ops-applications');
  const modal = document.getElementById('application-decision-modal');
  if (!root || !modal) return;

  const admin = global.PlayOnAdmin;

  /* 실패 안내는 사라지지 않는 모달로 보여 준다. 공통 셸이 없으면 예전처럼 토스트로 알린다. */
  function showError(title, message) {
    if (admin?.showErrorModal) admin.showErrorModal(title, message);
    else admin?.toast(message, true);
  }
  const baseUrl = root.dataset.decisionUrl;
  const csrfHeader = root.dataset.csrfHeader;
  const csrfToken = root.dataset.csrfToken;

  const title = modal.querySelector('[data-decision-title]');
  const message = modal.querySelector('[data-decision-message]');
  const submit = modal.querySelector('[data-decision-submit]');

  const LABELS = {
    confirm: { title: '참가 신청 승인', action: '승인', result: '참가 확정으로 바뀝니다.' },
    reject: { title: '참가 신청 반려', action: '반려', result: '반려되고 정원 자리가 다시 열립니다. 되돌릴 수 없습니다.' }
  };

  // 공통 셸이 없으면(스크립트 순서 오류) 예전처럼 새로고침만 한다.
  function reloadWithToast(text) {
    if (admin?.reloadWithToast) admin.reloadWithToast(text, false);
    else global.location.reload();
  }

  let pending = null;   // { applicationId, decision }
  let busy = false;

  function targetName(button) {
    const club = button.dataset.clubName;
    const name = button.dataset.applicantName || '신청자';
    return club ? `${club} ${name}` : name;
  }

  function open(button) {
    const decision = button.dataset.decision;
    const label = LABELS[decision];
    if (!label) return;
    pending = { applicationId: button.dataset.applicationId, decision };
    title.textContent = label.title;
    // 결제한 신청을 반려하면 1인 참가비가 결제 수단으로 환불된다(팀 신청은 대표 결제에서 부분 환불).
    const refund = decision === 'reject' ? Number(button.dataset.refundAmount || 0) : 0;
    const refundText = refund > 0 ? ` ${refund.toLocaleString('ko-KR')}원이 결제 수단으로 환불됩니다.` : '';
    message.textContent = `${targetName(button)} 님의 참가 신청을 ${label.action}할까요? ${label.result}${refundText}`;
    submit.textContent = label.action;
    submit.classList.toggle('primary', decision === 'confirm');
    submit.classList.toggle('danger-solid', decision === 'reject');
    submit.disabled = false;
    admin?.openModal(modal, '[data-decision-submit]');
  }

  function close() {
    if (busy || modal.hidden) return;
    pending = null;
    admin?.closeModal(modal);
  }

  async function send() {
    if (!pending || busy) return;
    busy = true;
    submit.disabled = true;
    const options = { method: 'POST', headers: { Accept: 'application/json' }, credentials: 'same-origin' };
    if (csrfHeader && csrfToken) options.headers[csrfHeader] = csrfToken;
    let ok = false;
    let text = '요청을 처리하지 못했습니다. 잠시 후 다시 시도해 주세요.';
    try {
      const response = await fetch(`${baseUrl}/${encodeURIComponent(pending.applicationId)}/${pending.decision}`, options);
      const data = await response.json().catch(() => ({}));
      ok = response.ok && !response.redirected;
      text = data.message || text;
    } catch (error) {
      text = '서버에 연결하지 못했습니다. 네트워크 상태를 확인해 주세요.';
    }
    if (ok) {
      reloadWithToast(text);
      return;
    }
    busy = false;
    close();
    showError('신청을 처리할 수 없습니다', text);
  }

  root.addEventListener('click', function (event) {
    const button = event.target instanceof Element ? event.target.closest('[data-decision]') : null;
    if (button && !busy) open(button);
  });

  modal.addEventListener('click', function (event) {
    const target = event.target instanceof Element ? event.target : null;
    if (!target) return;
    if (target.closest('[data-decision-submit]')) {
      send();
      return;
    }
    if (target.closest('[data-decision-cancel]') || target === modal) close();
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
