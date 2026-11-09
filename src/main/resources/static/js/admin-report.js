/* 신고 접수함(시스템 관리자)의 처리 양식 보강만 담당합니다.
   신고 원문은 서버가 렌더링한 텍스트를 그대로 두고, 이 스크립트는 검증과 중복 제출 방지만 합니다.
   처리 결과와 상태 판정은 서버가 하며, 사유가 필요한 동작(처리 완료·기각)은 전송 전에 한 번 더 확인합니다. */
(function (global) {
  'use strict';

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
