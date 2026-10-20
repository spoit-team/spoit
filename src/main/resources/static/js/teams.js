/* 팀 목록·상세 화면 UI: 공유, 가입 신청 대화상자, 글자 수 표시만 담당합니다. 팀 데이터와 신청 처리는 서버가 제공합니다. */
(function () {
  'use strict';

  const dialog = document.getElementById('team-application-dialog');
  const toast = document.getElementById('team-toast');
  const messageInput = document.getElementById('team-application-message');
  const messageCount = document.getElementById('team-application-count');
  const applicationError = document.getElementById('team-application-error');
  let toastTimer = 0;
  let dialogOpener = null;

  function announce(text) {
    if (!toast) return;
    toast.textContent = text;
    toast.hidden = false;
    window.clearTimeout(toastTimer);
    toastTimer = window.setTimeout(function () { toast.hidden = true; }, 2800);
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

  document.addEventListener('click', function (event) {
    const target = event.target instanceof Element ? event.target : null;
    if (!target) return;

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
        announce('현재 브라우저에서 링크 복사를 사용할 수 없습니다.');
        return;
      }
      navigator.clipboard.writeText(window.location.href)
        .then(function () { announce('팀 링크를 복사했습니다.'); })
        .catch(function () { announce('현재 브라우저에서 링크 복사를 사용할 수 없습니다.'); });
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
    if (applicationError) applicationError.hidden = true;
  });

  if (applicationError) openDialog();
})();
