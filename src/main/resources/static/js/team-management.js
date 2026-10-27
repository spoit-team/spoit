/* 팀 관리 화면: 탭 키보드 이동, 대화상자 열기·닫기, 해체 확인 검증만 담당합니다.
   팀 정보·팀원·가입 신청의 변경은 모두 서버 폼으로 전송됩니다. */
(function () {
  'use strict';

  const tabs = Array.from(document.querySelectorAll('.team-management-tab'));
  let activeDialog = null;
  let dialogOpener = null;

  function openDialog(dialog, focusSelector) {
    if (!dialog || dialog.open) return;
    dialogOpener = document.activeElement;
    activeDialog = dialog;
    dialog.showModal();
    const focusTarget = focusSelector ? dialog.querySelector(focusSelector) : null;
    focusTarget?.focus();
  }

  function closeActiveDialog() {
    if (!activeDialog?.open) return;
    activeDialog.close();
    activeDialog = null;
    dialogOpener?.focus?.();
    dialogOpener = null;
  }

  document.addEventListener('click', function (event) {
    const target = event.target instanceof Element ? event.target : null;
    if (!target) return;

    if (target.closest('[data-dialog-close]')) {
      closeActiveDialog();
      return;
    }

    if (target.closest('[data-team-create]')) {
      const dialog = document.getElementById('team-create-dialog');
      openDialog(dialog, 'input[name="clubName"]');
      return;
    }

    if (target.closest('[data-team-edit]')) {
      const dialog = document.getElementById('team-edit-dialog');
      openDialog(dialog, 'input[name="clubName"]');
      return;
    }

    const transfer = target.closest('[data-team-transfer]');
    if (transfer) {
      document.getElementById('team-transfer-member-id').value = transfer.dataset.memberId || '';
      document.getElementById('team-transfer-member-name').textContent = transfer.dataset.memberName || '팀원';
      openDialog(document.getElementById('team-transfer-dialog'), '.team-dialog-close');
      return;
    }

    const remove = target.closest('[data-team-remove-member]');
    if (remove) {
      document.getElementById('team-remove-member-id').value = remove.dataset.memberId || '';
      document.getElementById('team-remove-member-name').textContent = remove.dataset.memberName || '팀원';
      openDialog(document.getElementById('team-remove-dialog'), '.team-dialog-close');
      return;
    }

    if (target.closest('[data-team-leave]')) {
      openDialog(document.getElementById('team-leave-dialog'), '.team-dialog-close');
      return;
    }

    if (target.closest('[data-team-disband]')) {
      openDialog(document.getElementById('team-disband-dialog'), 'input[name="clubNameConfirmation"]');
    }
  });

  document.querySelectorAll('dialog.team-dialog').forEach(function (dialog) {
    dialog.addEventListener('click', function (event) {
      if (event.target === dialog) closeActiveDialog();
    });
    dialog.addEventListener('close', function () {
      if (activeDialog === dialog) activeDialog = null;
    });
  });

  document.addEventListener('keydown', function (event) {
    if (event.key === 'Escape' && activeDialog?.open) {
      closeActiveDialog();
      return;
    }
    if (event.key !== 'Tab' || !activeDialog?.open) return;
    const focusable = Array.from(activeDialog.querySelectorAll('button:not(:disabled), input:not(:disabled), select:not(:disabled), textarea:not(:disabled)'));
    if (!focusable.length) return;
    const first = focusable[0];
    const last = focusable[focusable.length - 1];
    if (event.shiftKey && document.activeElement === first) {
      event.preventDefault();
      last.focus();
    } else if (!event.shiftKey && document.activeElement === last) {
      event.preventDefault();
      first.focus();
    }
  });

  tabs.forEach(function (tab, index) {
    tab.addEventListener('keydown', function (event) {
      let next = null;
      if (event.key === 'ArrowRight') next = tabs[(index + 1) % tabs.length];
      else if (event.key === 'ArrowLeft') next = tabs[(index - 1 + tabs.length) % tabs.length];
      else if (event.key === 'Home') next = tabs[0];
      else if (event.key === 'End') next = tabs[tabs.length - 1];
      if (!next) return;
      event.preventDefault();
      next.focus();
    });
  });

  document.querySelectorAll('#team-create-form, #team-edit-form').forEach(function (form) {
    const dayInputs = Array.from(form.querySelectorAll('input[name="activityDays"]'));
    const startInput = form.querySelector('input[name="activityStartTime"]');
    const endInput = form.querySelector('input[name="activityEndTime"]');
    const error = form.querySelector('[data-activity-error]');

    form.addEventListener('submit', function (event) {
      const hasDay = dayInputs.some(function (input) { return input.checked; });
      const hasValidTimeRange = startInput?.value && endInput?.value && startInput.value !== endInput.value;
      if (hasDay && hasValidTimeRange) {
        if (error) error.hidden = true;
        return;
      }

      event.preventDefault();
      if (error) {
        error.textContent = !hasDay
          ? '정기 활동 요일을 하나 이상 선택해 주세요.'
          : '활동 시작 시간과 종료 시간이 같을 수 없습니다.';
        error.hidden = false;
      }
      if (!hasDay) dayInputs[0]?.focus();
      else endInput?.focus();
    });

    form.addEventListener('input', function () { if (error) error.hidden = true; });
    form.addEventListener('change', function () { if (error) error.hidden = true; });
  });

  const disbandForm = document.getElementById('team-disband-form');
  disbandForm?.addEventListener('submit', function (event) {
    const expected = disbandForm.dataset.teamName || '';
    const input = disbandForm.querySelector('input[name="clubNameConfirmation"]');
    const error = disbandForm.querySelector('.team-form-error');
    if (!input || input.value.trim() === expected) {
      if (error) error.hidden = true;
      return;
    }
    event.preventDefault();
    if (error) {
      error.textContent = '입력한 팀 이름이 일치하지 않습니다.';
      error.hidden = false;
    }
    input.focus();
  });

  const message = document.getElementById('team-management-message');
  if (message) {
    message.addEventListener('click', function () { message.hidden = true; });
    window.setTimeout(function () { message.hidden = true; }, 6000);
  }
})();
