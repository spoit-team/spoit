/* 프로필 수정 화면: 대표 종목 미리보기와 탈퇴 확인 대화상자의 UI만 담당합니다. 저장은 서버 폼이 처리합니다. */
(function () {
  'use strict';

  const form = document.getElementById('profile-form');
  const message = document.getElementById('profile-edit-message');
  const sportSelect = document.getElementById('profile-representative-sport');
  const ratingPreview = document.getElementById('representative-level-preview');
  const ratingNote = document.getElementById('representative-level-note');
  const nicknameInput = document.getElementById('profile-nickname');
  const nicknameError = document.getElementById('profile-nickname-error');
  const withdrawalDialog = document.getElementById('profile-withdrawal-dialog');
  const withdrawalOpen = document.getElementById('open-withdrawal-dialog');
  const withdrawalForm = document.getElementById('profile-withdrawal-form');
  const withdrawalInput = document.getElementById('withdrawal-password');
  const withdrawalError = document.getElementById('withdrawal-error');
  const withdrawalConfirm = document.getElementById('confirm-withdrawal');
  let withdrawalOpener = null;

  function syncRatingPreview() {
    if (!sportSelect || !ratingPreview) return;
    const option = sportSelect.options[sportSelect.selectedIndex];
    const hasSport = Boolean(sportSelect.value) && Boolean(option);
    ratingPreview.textContent = hasSport ? (option.dataset.rating || '기록 없음') : '종목을 선택하세요';
    if (ratingNote) {
      ratingNote.textContent = hasSport
        ? (option.dataset.ratingNote || '확정 매치 기록에 따라 표시하며 직접 수정할 수 없습니다.')
        : '확정 매치 기록에 따라 표시하며 직접 수정할 수 없습니다.';
    }
  }

  sportSelect?.addEventListener('change', syncRatingPreview);
  syncRatingPreview();

  function validateNickname() {
    const value = nicknameInput.value.trim();
    const invalid = !value || !/^[가-힣a-zA-Z0-9_]{2,12}$/.test(value);
    nicknameInput.setAttribute('aria-invalid', invalid ? 'true' : 'false');
    if (nicknameError) {
      nicknameError.textContent = invalid ? '한글, 영문, 숫자, 밑줄로 2~12자 입력해 주세요.' : '';
      nicknameError.hidden = !invalid;
    }
    return !invalid;
  }

  nicknameInput?.addEventListener('blur', validateNickname);
  nicknameInput?.addEventListener('input', function () {
    if (nicknameInput.getAttribute('aria-invalid') === 'true') validateNickname();
  });

  form?.addEventListener('submit', function (event) {
    if (nicknameInput && !validateNickname()) {
      event.preventDefault();
      nicknameInput.focus();
    }
  });

  function openWithdrawal() {
    if (!withdrawalDialog || withdrawalDialog.open) return;
    withdrawalOpener = document.activeElement;
    withdrawalInput.value = '';
    withdrawalInput.setAttribute('aria-invalid', 'false');
    withdrawalConfirm.disabled = true;
    if (withdrawalError) withdrawalError.hidden = true;
    withdrawalDialog.showModal();
    withdrawalInput.focus();
  }

  function closeWithdrawal() {
    if (!withdrawalDialog?.open) return;
    withdrawalDialog.close();
    withdrawalOpener?.focus?.();
    withdrawalOpener = null;
  }

  withdrawalOpen?.addEventListener('click', openWithdrawal);
  document.addEventListener('click', function (event) {
    if (event.target instanceof Element && event.target.closest('[data-close-withdrawal]')) closeWithdrawal();
  });

  withdrawalDialog?.addEventListener('click', function (event) {
    if (event.target === withdrawalDialog) closeWithdrawal();
  });

  withdrawalDialog?.addEventListener('keydown', function (event) {
    if (event.key !== 'Tab') return;
    const focusable = Array.from(withdrawalDialog.querySelectorAll('button:not(:disabled), input:not(:disabled), [href]'));
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

  withdrawalInput?.addEventListener('input', function () {
    const filled = Boolean(withdrawalInput.value.trim());
    withdrawalConfirm.disabled = !filled;
    if (withdrawalError) withdrawalError.hidden = filled;
    withdrawalInput.setAttribute('aria-invalid', filled ? 'false' : 'true');
  });

  withdrawalForm?.addEventListener('submit', function (event) {
    if (!withdrawalInput.value.trim()) {
      event.preventDefault();
      if (withdrawalError) withdrawalError.hidden = false;
      withdrawalInput.setAttribute('aria-invalid', 'true');
      withdrawalInput.focus();
    }
  });

  document.addEventListener('keydown', function (event) {
    if (event.key === 'Escape' && withdrawalDialog?.open) closeWithdrawal();
  });

  if (message) {
    message.addEventListener('click', function () {
      message.hidden = true;
    });
  }
})();
