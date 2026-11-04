/* 매치 참가 신청 화면: 단계 전환과 동의 검사만 담당합니다. 신청은 서버가 저장하며 결제 UI는 안내용입니다. */
(function () {
  'use strict';

  const form = document.getElementById('join-form');
  const panels = Array.from(document.querySelectorAll('[data-step-panel]'));
  const indicators = Array.from(document.querySelectorAll('[data-step-indicator]'));
  const stageIndex = document.getElementById('stage-index');
  const stageHeading = document.getElementById('stage-heading');
  const continueButton = document.getElementById('continue-button');
  const backButton = document.getElementById('back-button');
  const rulesAgree = document.getElementById('rules-agree');
  const paymentAgree = document.getElementById('payment-agree');
  const joinError = document.getElementById('join-error');
  const paymentError = document.getElementById('payment-error');

  if (!form) return;

  let currentStep = 1;

  function setError(target, message) {
    if (!target) return;
    target.textContent = message || '';
    target.hidden = !message;
  }

  function setStep(step) {
    currentStep = step;
    panels.forEach(function (panel) {
      panel.hidden = Number(panel.dataset.stepPanel) !== step;
    });
    indicators.forEach(function (indicator) {
      const value = Number(indicator.dataset.stepIndicator);
      indicator.classList.toggle('is-current', value === step);
      indicator.classList.toggle('is-done', value < step);
      if (value === step) indicator.setAttribute('aria-current', 'step');
      else indicator.removeAttribute('aria-current');
      const number = indicator.querySelector('.step-number');
      if (number) number.textContent = value < step ? '✓' : String(value);
    });
    if (stageIndex) stageIndex.textContent = 'STEP 0' + step + ' / 03';
    if (stageHeading) {
      stageHeading.textContent = step === 1
        ? '신청 내용을 확인해 주세요'
        : step === 2 ? '결제 안내를 확인해 주세요' : '신청 처리가 완료되었습니다';
    }
    (step === 1 || step === 2 ? stageHeading : null)?.focus?.({ preventScroll: true });
    window.scrollTo({ top: 0, behavior: 'smooth' });
  }

  document.querySelectorAll('input[data-payment-method]').forEach(function (input) {
    input.addEventListener('change', function () {
      document.querySelectorAll('.join-payment-option').forEach(function (option) {
        option.classList.toggle('is-selected', !!option.querySelector('input')?.checked);
      });
    });
  });

  backButton?.addEventListener('click', function () {
    setStep(1);
  });

  // 무료 결제만 지원: 신청 내용(1단계)에서 바로 접수한다. 결제 안내(2단계)는 유료 결제 도입 시 다시 연결한다.
  form.addEventListener('submit', function (event) {
    if (!rulesAgree?.checked) {
      event.preventDefault();
      setError(joinError, '매치 운영 규칙과 취소·노쇼 안내에 동의해 주세요.');
      rulesAgree?.focus();
      return;
    }
    setError(joinError, '');
    document.querySelectorAll('[data-payment-method]').forEach(function (input) { input.disabled = true; });
    // 두 번 누르면 두 번째 요청이 "이미 신청" 오류로 끝나 완료 화면 대신 오류가 보이므로 막는다.
    if (continueButton) continueButton.disabled = true;
  });

  /*
   * 신청 동의 안내 모달: 체크박스를 체크하려 하면 체크하지 않고 안내를 먼저 띄웁니다.
   * "확인했습니다"를 눌러야 체크되고, 체크 해제는 바로 됩니다. "안내 보기"로 언제든 다시 열 수 있습니다.
   */
  const rulesDialog = document.getElementById('rules-guide-dialog');
  const rulesOpen = document.getElementById('rules-guide-open');
  const rulesConfirm = document.getElementById('rules-guide-confirm');

  if (rulesDialog && typeof rulesDialog.showModal === 'function') {
    const openRulesGuide = function () {
      rulesDialog.showModal();
      rulesConfirm?.focus({ preventScroll: true });
    };

    rulesAgree?.addEventListener('click', function (event) {
      // click 시점의 checked는 바뀐 뒤 값입니다. 체크하려는 경우만 막고 안내를 띄웁니다.
      if (!rulesAgree.checked) return;
      event.preventDefault();
      openRulesGuide();
    });

    rulesOpen?.addEventListener('click', function (event) {
      // 라벨 안의 버튼이라 체크박스가 같이 바뀌지 않도록 막습니다.
      event.preventDefault();
      event.stopPropagation();
      openRulesGuide();
    });

    rulesConfirm?.addEventListener('click', function () {
      if (rulesAgree) {
        rulesAgree.checked = true;
        rulesAgree.dispatchEvent(new Event('change', { bubbles: true }));
      }
      setError(joinError, '');
      rulesDialog.close();
    });

    rulesDialog.querySelectorAll('[data-close-rules-guide]').forEach(function (button) {
      button.addEventListener('click', function () { rulesDialog.close(); });
    });

    rulesDialog.addEventListener('click', function (event) {
      if (event.target === rulesDialog) rulesDialog.close();
    });

    rulesDialog.addEventListener('close', function () {
      rulesAgree?.focus({ preventScroll: true });
    });
  }
})();
