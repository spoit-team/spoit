/* 비밀번호 재설정 화면의 입력 안내만 담당합니다. 인증 상태와 결과는 서버가 결정합니다. */
(function () {
  'use strict';

  const requestForm = document.getElementById('recovery-request-form');
  const codeForm = document.getElementById('recovery-verify-form');
  const resetForm = document.getElementById('recovery-reset-form');
  const codeInput = document.getElementById('recovery-code');
  const codeError = document.getElementById('recovery-code-error');
  const passwordInput = document.getElementById('recovery-password');
  const passwordConfirmInput = document.getElementById('recovery-password-confirm');
  const PASSWORD_MAX_BYTES = 72;

  function setFieldError(input, error, message) {
    if (!input || !error) return;
    input.setAttribute('aria-invalid', message ? 'true' : 'false');
    error.textContent = message;
    error.hidden = !message;
  }

  function utf8ByteLength(value) {
    if (typeof TextEncoder === 'function') return new TextEncoder().encode(value).length;
    return encodeURIComponent(value).replace(/%[0-9A-F]{2}/g, 'x').length;
  }

  if (requestForm) {
    requestForm.addEventListener('submit', function () {
      const email = requestForm.querySelector('input[name="email"]');
      if (email) email.value = email.value.trim();
    });
  }

  if (codeInput) {
    codeInput.addEventListener('input', function () {
      const digits = codeInput.value.replace(/[^0-9]/g, '').slice(0, 6);
      if (codeInput.value !== digits) codeInput.value = digits;
      if (digits.length === 6) setFieldError(codeInput, codeError, '');
    });
  }

  if (codeForm) {
    codeForm.addEventListener('submit', function (event) {
      if (!codeInput || !/^[0-9]{6}$/.test(codeInput.value)) {
        event.preventDefault();
        setFieldError(codeInput, codeError, '숫자 6자리 인증번호를 입력해 주세요.');
        codeInput?.focus();
      }
    });
  }

  function validatePassword() {
    if (!passwordInput) return true;
    const error = document.getElementById('recovery-password-error');
    const password = passwordInput.value;
    let message = '';
    if (!password) message = '새 비밀번호를 입력해 주세요.';
    else if (password.length < 8 || !/[A-Za-z]/.test(password) || !/[0-9]/.test(password)) {
      message = '영문과 숫자를 포함해 8자 이상 입력해 주세요.';
    } else if (utf8ByteLength(password) > PASSWORD_MAX_BYTES) {
      message = '비밀번호는 UTF-8 기준 72바이트 이하로 입력해 주세요.';
    }
    setFieldError(passwordInput, error, message);
    return !message;
  }

  function validatePasswordConfirm() {
    if (!passwordConfirmInput || !passwordInput) return true;
    const error = document.getElementById('recovery-password-confirm-error');
    let message = '';
    if (!passwordConfirmInput.value) message = '새 비밀번호를 한 번 더 입력해 주세요.';
    else if (passwordConfirmInput.value !== passwordInput.value) message = '비밀번호가 일치하지 않습니다.';
    setFieldError(passwordConfirmInput, error, message);
    return !message;
  }

  if (passwordInput) {
    passwordInput.addEventListener('input', function () {
      if (passwordInput.dataset.touched === 'true') validatePassword();
      if (passwordConfirmInput?.dataset.touched === 'true') validatePasswordConfirm();
    });
    passwordInput.addEventListener('blur', function () {
      passwordInput.dataset.touched = 'true';
      validatePassword();
    });
  }

  if (passwordConfirmInput) {
    passwordConfirmInput.addEventListener('input', function () {
      if (passwordConfirmInput.dataset.touched === 'true') validatePasswordConfirm();
    });
    passwordConfirmInput.addEventListener('blur', function () {
      passwordConfirmInput.dataset.touched = 'true';
      validatePasswordConfirm();
    });
  }

  if (resetForm) {
    resetForm.addEventListener('submit', function (event) {
      if (!validatePassword() || !validatePasswordConfirm()) {
        event.preventDefault();
        const invalid = resetForm.querySelector('[aria-invalid="true"]');
        invalid?.focus();
      }
    });
  }

  const timer = document.getElementById('recovery-code-timer');
  const resendButton = document.querySelector('[data-resend-button]');
  const countdown = timer?.querySelector('[data-expiry-countdown]');
  if (timer && countdown) {
    const expirySeconds = Number.parseInt(timer.dataset.expiresInSeconds || '0', 10);
    const resendSeconds = Number.parseInt(timer.dataset.resendInSeconds || '0', 10);
    const expiryAt = Date.now() + Math.max(0, expirySeconds) * 1000;
    const resendAt = Date.now() + Math.max(0, resendSeconds) * 1000;
    let intervalId = null;

    function formatRemaining(totalSeconds) {
      const minutes = Math.floor(totalSeconds / 60);
      const seconds = totalSeconds % 60;
      return minutes > 0 ? minutes + '분 ' + String(seconds).padStart(2, '0') + '초' : seconds + '초';
    }

    function updateTimer() {
      const expiryRemaining = Math.max(0, Math.ceil((expiryAt - Date.now()) / 1000));
      const resendRemaining = Math.max(0, Math.ceil((resendAt - Date.now()) / 1000));

      if (expiryRemaining > 0) {
        countdown.textContent = '남은 시간: ' + formatRemaining(expiryRemaining);
        timer.classList.remove('is-expired');
      } else {
        countdown.textContent = '인증번호가 만료되었습니다. 다시 요청해 주세요.';
        timer.classList.add('is-expired');
      }

      if (resendButton) {
        resendButton.disabled = resendRemaining > 0;
        resendButton.textContent = resendRemaining > 0
          ? '인증번호 다시 받기 (' + resendRemaining + '초 후 가능)'
          : '인증번호 다시 받기';
      }

      if (expiryRemaining === 0 && resendRemaining === 0) window.clearInterval(intervalId);
    }

    updateTimer();
    if (expirySeconds > 0 || resendSeconds > 0) {
      intervalId = window.setInterval(updateTimer, 1000);
    }
  }
})();
