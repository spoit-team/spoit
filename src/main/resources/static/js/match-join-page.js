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

  continueButton?.addEventListener('click', function () {
    if (continueButton.disabled) return;
    if (!rulesAgree?.checked) {
      setError(joinError, '매치 운영 규칙과 취소·노쇼 안내에 동의해 주세요.');
      rulesAgree?.focus();
      return;
    }
    setError(joinError, '');
    setError(paymentError, '');
    setStep(2);
  });

  backButton?.addEventListener('click', function () {
    setStep(1);
  });

  form.addEventListener('submit', function (event) {
    if (currentStep !== 2) {
      event.preventDefault();
      return;
    }
    if (!paymentAgree?.checked) {
      event.preventDefault();
      setError(paymentError, '참가비 안내와 취소 안내를 확인해 주세요.');
      paymentAgree?.focus();
      return;
    }
    document.querySelectorAll('[data-payment-method]').forEach(function (input) { input.disabled = true; });
  });
})();
