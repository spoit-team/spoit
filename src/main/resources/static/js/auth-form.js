/* 인증 화면 공통 UI: 비밀번호 표시 전환만 담당합니다. 인증·검증 결과는 서버가 제공합니다. */
(function () {
  'use strict';

  document.addEventListener('click', function (event) {
    const target = event.target instanceof Element ? event.target.closest('[data-toggle-password]') : null;
    if (!target) return;

    const input = document.getElementById(target.dataset.togglePassword);
    if (!input) return;

    const visible = input.type === 'password';
    input.type = visible ? 'text' : 'password';
    target.textContent = visible ? '숨기기' : '보기';
    target.setAttribute('aria-label', visible ? '비밀번호 숨기기' : '비밀번호 표시');
  });
})();
