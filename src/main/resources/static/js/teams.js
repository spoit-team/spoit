/* 팀 목록·상세 화면 UI: 공유, 가입 신청 대화상자, 글자 수 표시만 담당합니다. 팀 데이터와 신청 처리는 서버가 제공합니다.
   처리 결과는 공용 안내(ui-feedback.js)로 보여 줍니다: 성공은 토스트, 실패는 원인을 담은 안내 모달. */
(function () {
  'use strict';

  const dialog = document.getElementById('team-application-dialog');
  const messageInput = document.getElementById('team-application-message');
  const messageCount = document.getElementById('team-application-count');
  let dialogOpener = null;

  const feedback = window.SpoitFeedback;
  const SHARE_ERROR_TITLE = '링크를 복사할 수 없습니다';
  const SHARE_UNAVAILABLE = '현재 브라우저에서 링크 복사를 사용할 수 없습니다.';

  function shareFailed() {
    if (feedback) feedback.alert(SHARE_ERROR_TITLE, SHARE_UNAVAILABLE);
    else window.alert(SHARE_UNAVAILABLE);
  }

  function openDialog() {
    if (!dialog || dialog.open) return;
    dialogOpener = document.activeElement;
    dialog.showModal();
    messageInput?.focus();
  }

  function closeDialog() {
    if (!dialog?.open) return;
    dialog.close();
    dialogOpener?.focus?.();
    dialogOpener = null;
  }

  // 뒤로 가기를 써도 되는지: 보통 왼쪽 클릭 + 같은 탭에 이전 기록 + 바로 전 화면이 이 링크와 같은 목록
  function canGoBackToList(link, event) {
    // Ctrl·Shift·Cmd 클릭(새 탭·새 창)은 브라우저에 맡김
    if (event.button !== 0 || event.ctrlKey || event.shiftKey || event.metaKey || event.altKey) return false;
    // 새 탭으로 연 상세는 돌아갈 기록이 없음
    if (window.history.length <= 1) return false;
    if (!document.referrer) return false;
    try {
      const from = new URL(document.referrer);
      const to = new URL(link.href);
      // 알림·마이페이지·관리 화면이나 가입 신청 뒤(상세가 다시 열림)면 뒤로 가기 대신 링크 주소로
      return from.origin === window.location.origin && from.pathname === to.pathname;
    } catch (error) {
      return false;
    }
  }

  document.addEventListener('click', function (event) {
    const target = event.target instanceof Element ? event.target : null;
    if (!target) return;
   
    // 목록으로 돌아가기: 바로 전이 목록이면 뒤로 가기(보던 자리·불러온 장 그대로), 아니면 링크대로
    const backLink = target.closest('[data-back-to-list]');
    if (backLink && canGoBackToList(backLink, event)) {
      event.preventDefault();
      window.history.back();
      return;
    }

    if (target.closest('[data-team-apply]')) {
      openDialog();
      return;
    }

    if (target.closest('[data-dialog-close]')) {
      closeDialog();
      return;
    }

    if (target.closest('[data-team-share]')) {
      if (!navigator.clipboard?.writeText) {
        shareFailed();
        return;
      }
      navigator.clipboard.writeText(window.location.href)
        .then(function () { feedback?.toast('동호회 링크를 복사했습니다.'); })
        .catch(shareFailed);
    }
  });

  dialog?.addEventListener('click', function (event) {
    if (event.target === dialog) closeDialog();
  });

  document.addEventListener('keydown', function (event) {
    if (event.key === 'Escape' && dialog?.open) closeDialog();
  });

  messageInput?.addEventListener('input', function () {
    if (messageCount) messageCount.textContent = String(messageInput.value.length);
  });

    // 제출 버튼 두 번 누름 방지(POST만, 검사에 걸려 막힌 제출은 그대로 둠)
  document.addEventListener('submit', function (event) {
    const form = event.target;
    if (event.defaultPrevented || form.method !== 'post') return;
    form.querySelectorAll('button[type="submit"]').forEach(function (button) {
      button.disabled = true;
    });
  });

  // 뒤로 가기로 돌아오면 버튼 다시 켬
  window.addEventListener('pageshow', function () {
    document.querySelectorAll('form[method="post"] button[type="submit"]').forEach(function (button) {
      button.disabled = false;
    });
  });
})();
