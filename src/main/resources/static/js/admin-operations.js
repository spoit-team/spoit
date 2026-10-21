/* 매치 운영 화면 UI: 팀 배정 변경은 저장 버튼을 눌러 서버 폼으로 제출합니다. */
(function () {
  'use strict';

  document.querySelectorAll('.ops-assignment-form').forEach(function (form) {
    const select = form.querySelector('.ops-assignment');
    const submit = form.querySelector('button[type="submit"]');
    if (!select || !submit) return;

    const initialValue = select.value;
    submit.disabled = true;
    select.addEventListener('change', function () {
      submit.disabled = select.value === initialValue;
    });
  });
})();
