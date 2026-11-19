/* 인증 화면 공통 UI: 비밀번호 표시 전환만 담당합니다. 인증·검증 결과는 서버가 제공합니다. */
(function () {
  'use strict';

  document.addEventListener('click', function (event) {
    let target = null;
    if (event.target instanceof Element) target = event.target.closest('[data-toggle-password]');
    if (!target) return;

    const input = document.getElementById(target.dataset.togglePassword);
    if (!input) return;

    const visible = input.type === 'password';
    if (visible) {
      input.type = 'text';
      target.textContent = '숨기기';
      target.setAttribute('aria-label', '비밀번호 숨기기');
    } else {
      input.type = 'password';
      target.textContent = '보기';
      target.setAttribute('aria-label', '비밀번호 표시');
    }
  });
})();
