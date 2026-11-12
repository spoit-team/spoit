/*
 * 매치 참가 신청 화면: 단계 전환과 동의 검사, 유료 매치의 참가비 결제를 담당합니다.
 * 무료 매치는 신청 내용(1단계)에서 폼을 그대로 제출합니다. 유료 매치는 1단계 제출을 막고 결제(2단계)로 넘어간 뒤
 * 결제 준비(prepare)를 fetch로 보내고 받은 카카오페이 결제 페이지로 이동합니다. 금액과 신청 접수는 서버가 정합니다.
 */
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
  const payButton = document.getElementById('submit-payment');

  if (!form) return;

  const paid = form.dataset.paid === 'true';
  const totalSteps = paid ? 3 : 2;
  let currentStep = 1;
  let paying = false;

  function setError(target, message) {
    if (!target) return;
    target.textContent = message || '';
    target.hidden = !message;
  }

  /* 서버가 결제를 거부했거나 연결이 끊긴 경우: 원인을 공용 실패 안내 모달로 보여 준다.
     동의 체크처럼 입력 바로 옆에서 고칠 수 있는 안내는 setError로 그 자리에 둔다. */
  function showPaymentFailure(message) {
    const feedback = window.SpoitFeedback;
    if (feedback) feedback.alert('결제를 시작할 수 없습니다', message, { returnFocus: payButton });
    else setError(paymentError, message);
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
    if (stageIndex) stageIndex.textContent = 'STEP 0' + step + ' / 0' + totalSteps;
    if (stageHeading) {
      stageHeading.textContent = step === 1
        ? '신청 내용을 확인해 주세요'
        : step === 2 ? '참가비를 결제해 주세요' : '신청 처리가 완료되었습니다';
    }
    (step === 1 || step === 2 ? stageHeading : null)?.focus?.({ preventScroll: true });
    window.scrollTo({ top: 0, behavior: 'smooth' });
  }

  backButton?.addEventListener('click', function () {
    if (paying) return;
    setError(paymentError, '');
    setStep(1);
  });

  // 무료 매치는 신청 내용(1단계)에서 바로 접수하고, 유료 매치는 결제(2단계)로 넘어간다.
  form.addEventListener('submit', function (event) {
    if (!rulesAgree?.checked) {
      event.preventDefault();
      setError(joinError, '매치 운영 규칙과 취소·노쇼 안내에 동의해 주세요.');
      rulesAgree?.focus();
      return;
    }
    setError(joinError, '');
    if (paid) {
      // 유료 매치는 폼을 제출하지 않는다. 서버도 결제 없는 유료 신청은 받지 않는다.
      event.preventDefault();
      setStep(2);
      return;
    }
    // 두 번 누르면 두 번째 요청이 "이미 신청" 오류로 끝나 완료 화면 대신 오류가 보이므로 막는다.
    if (continueButton) continueButton.disabled = true;
  });

  /* ---------------- 참가비 결제 (유료 매치) ---------------- */

  function teamMemberIds() {
    return Array.from(form.querySelectorAll('input[name="clubMemberIds"]'))
      .map(function (input) { return Number(input.value); })
      .filter(function (value) { return Number.isFinite(value); });
  }

  async function postJson(url, body) {
    const headers = { 'Content-Type': 'application/json', Accept: 'application/json' };
    if (form.dataset.csrfHeader && form.dataset.csrfToken) headers[form.dataset.csrfHeader] = form.dataset.csrfToken;
    const response = await fetch(url, {
      method: 'POST',
      headers: headers,
      credentials: 'same-origin',
      body: JSON.stringify(body)
    });
    const data = await response.json().catch(function () { return {}; });
    return { ok: response.ok && !response.redirected && data.ok === true, data: data };
  }

  function setPaying(value) {
    paying = value;
    if (payButton) {
      payButton.disabled = value;
      payButton.setAttribute('aria-busy', value ? 'true' : 'false');
    }
    if (backButton) backButton.disabled = value;
  }

  // 결제 준비가 끝나면 카카오페이 결제 페이지로 이동한다. 결제 뒤에는 카카오페이가 서버의 승인 주소로 돌려보낸다.
  async function pay() {
    if (paying) return;
    if (!paymentAgree?.checked) {
      setError(paymentError, '결제 및 환불 안내에 동의해 주세요.');
      paymentAgree?.focus();
      return;
    }
    setError(paymentError, '');
    setPaying(true);
    try {
      const clubId = form.querySelector('input[name="clubId"]')?.value;
      const prepared = await postJson(form.dataset.paymentPrepareUrl, {
        applicationType: form.dataset.applicationType,
        clubId: clubId ? Number(clubId) : null,
        clubMemberIds: teamMemberIds(),
        agreeRules: !!rulesAgree?.checked,
        agreePayment: !!paymentAgree?.checked
      });
      if (prepared.ok && prepared.data.redirectUrl) {
        window.location.assign(prepared.data.redirectUrl);
        return;
      }
      setPaying(false);
      showPaymentFailure(prepared.data.message || '결제를 시작하지 못했습니다. 잠시 후 다시 시도해 주세요.');
    } catch (error) {
      setPaying(false);
      showPaymentFailure('서버에 연결하지 못했습니다. 네트워크 상태를 확인해 주세요.');
    }
  }

  payButton?.addEventListener('click', pay);

  /* 시간 겹침: 서버가 겹침을 찾았을 때만 모달이 렌더링되며, 화면을 열자마자 띄웁니다. 신청 버튼은 서버가 이미 막아 둡니다. */
  const conflictDialog = document.getElementById('time-conflict-dialog');
  if (conflictDialog && typeof conflictDialog.showModal === 'function') {
    conflictDialog.querySelectorAll('[data-close-time-conflict]').forEach(function (button) {
      button.addEventListener('click', function () { conflictDialog.close(); });
    });
    conflictDialog.addEventListener('click', function (event) {
      if (event.target === conflictDialog) conflictDialog.close();
    });
    conflictDialog.showModal();
    conflictDialog.querySelector('.join-conflict-actions [data-close-time-conflict]')?.focus({ preventScroll: true });
  }

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
