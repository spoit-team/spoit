/* 신고 접수함(시스템 관리자)의 상세 모달과 처리 양식 보강을 담당합니다.
   신고 원문은 서버가 렌더링한 텍스트를 그대로 두고, 이 스크립트는 모달 열기·닫기와
   검증·중복 제출 방지만 합니다. 처리 결과와 상태 판정은 서버가 하며, 사유가 필요한
   동작(처리 완료·기각)은 전송 전에 한 번 더 확인합니다. */
(function (global) {
  'use strict';

  const admin = global.PlayOnAdmin;
  const modal = document.getElementById('report-case');

  /* 상세 모달을 닫으면 목록으로 돌아갑니다. 이동 주소는 서버가 모달에 싣은
     data-report-list-url(context path 포함)로 정하며, 속성이 없을 때만 root-relative
     기본 주소를 씁니다. 상태 필터와 페이지는 함께 복원하고, 신고 원문은 전송 대상에
     포함하지 않습니다. */
  function closeToList() {
    const params = new URLSearchParams();
    const status = modal?.getAttribute('data-report-status') || '';
    const page = modal?.getAttribute('data-report-page') || '';
    if (status) params.set('status', status);
    if (page && page !== '0') params.set('page', page);
    const query = params.toString();
    const listUrl = modal?.getAttribute('data-report-list-url') || '/admin/system/reports';
    global.location.href = listUrl + (query ? '?' + query : '') + '#report-ledger';
  }

  if (modal && modal.querySelector('section')) {
    admin?.openModal(modal, '.modal-close');

    modal.addEventListener('click', function (event) {
      const target = event.target instanceof Element ? event.target : null;
      if (!target) return;
      if (target.closest('[data-close-report]') || target === modal) closeToList();
    });

    // 알림 모달(관리자 공통)이 열려 있으면 그쪽이 먼저 닫혀야 하므로, 이 모달의
    // Escape·Tab 처리를 건너뜁니다. admin-shell.js는 전파를 막지 않습니다.
    function otherModalOpen() {
      const notification = document.getElementById('admin-notification-modal');
      return Boolean(notification && !notification.hidden);
    }

    document.addEventListener('keydown', function (event) {
      if (modal.hidden || otherModalOpen()) return;
      if (event.key === 'Escape') closeToList();
      else if (event.key === 'Tab') admin?.trapFocus(modal, event);
    });
    // 처리·페널티 전송 결과는 공용 안내(ui-feedback.js)가 성공 토스트·실패 모달로 상세 모달 위에 보여 줍니다.
  }

  const form = document.getElementById('report-decision-form');
  if (!form) return;

  const resolution = document.getElementById('report-decision-resolution');
  const error = document.getElementById('report-decision-error');
  let submitting = false;

  function showError(message) {
    if (!error) return;
    error.textContent = message;
    error.hidden = false;
    resolution?.setAttribute('aria-invalid', 'true');
    resolution?.focus();
  }

  function clearError() {
    if (!error) return;
    error.hidden = true;
    resolution?.removeAttribute('aria-invalid');
  }

  resolution?.addEventListener('input', function () {
    if (resolution.value.trim()) clearError();
  });

  form.addEventListener('submit', function (event) {
    // 두 번 눌러 같은 요청이 중복 전송되는 것만 막습니다. 페이지가 이동하면 상태는 사라집니다.
    if (submitting) {
      event.preventDefault();
      return;
    }
    // 어떤 버튼으로 제출했는지는 submitter로 읽습니다. 이 버튼을 비활성화하면 브라우저가
    // 그 name=value를 전송에서 빼 서버가 처리 방향을 알 수 없으므로 버튼은 잠그지 않습니다.
    const action = event.submitter?.value || '';
    const needsReason = action === 'complete' || action === 'dismiss';
    if (needsReason && !(resolution?.value || '').trim()) {
      event.preventDefault();
      showError('처리 완료 또는 기각에는 처리 사유를 입력해 주세요.');
      return;
    }
    clearError();
    submitting = true;
    // 진행 중임을 보조기술에 알립니다. 잠금은 버튼이 아니라 이 플래그로 처리합니다.
    form.setAttribute('aria-busy', 'true');
    global.setTimeout(function () {
      submitting = false;
      form.removeAttribute('aria-busy');
    }, 5000);
  });

  // 다른 관리자가 먼저 처리해 상태가 바뀌면 서버가 거절합니다. 그 안내는 공통 flash(role="alert")가 맡습니다.
})(window);
