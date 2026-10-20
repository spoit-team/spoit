/* 회원가입 화면: 단계 전환, 약관 펼치기, 입력 검증만 담당합니다. 계정 생성은 서버가 처리합니다. */
(function () {
  'use strict';

  const form = document.getElementById('signup-form');
  if (!form) return;

  const stepOne = document.getElementById('signup-step-1');
  const stepTwo = document.getElementById('signup-step-2');
  const stepOneDot = document.getElementById('step-1-dot');
  const stepTwoDot = document.getElementById('step-2-dot');
  const stepOneLabel = document.getElementById('step-1-label');
  const stepTwoLabel = document.getElementById('step-2-label');
  const roleSelect = document.getElementById('signup-role');
  const regionSelect = document.getElementById('signup-preferred-region');
  const facilitySignupFields = document.getElementById('facility-signup-fields');
  const systemSignupFields = document.getElementById('system-signup-fields');
  const facilityName = document.getElementById('signup-facility-name');
  const adminInvite = document.getElementById('signup-admin-invite');
  const roleError = document.getElementById('signup-role-error');
  const roleHint = document.getElementById('signup-role-hint');
  const message = document.getElementById('signup-message');
  const termsError = document.getElementById('terms-error');
  const requiredConsents = [document.getElementById('agree-service'), document.getElementById('agree-privacy')];
  const allConsents = [document.getElementById('agree-service'), document.getElementById('agree-privacy'), document.getElementById('agree-marketing')];
  const fields = {
    email: document.getElementById('signup-email'),
    phone: document.getElementById('signup-phone'),
    password: document.getElementById('signup-password'),
    passwordConfirm: document.getElementById('signup-password-confirm'),
    nickname: document.getElementById('signup-nickname')
  };

  function showStep(step) {
    const isProfileStep = step === 2;
    stepOne.hidden = isProfileStep;
    stepTwo.hidden = !isProfileStep;
    stepOneDot.classList.toggle('is-current', !isProfileStep);
    stepOneDot.classList.toggle('is-done', isProfileStep);
    stepTwoDot.classList.toggle('is-current', isProfileStep);
    stepOneDot.setAttribute('aria-current', isProfileStep ? 'false' : 'step');
    stepTwoDot.setAttribute('aria-current', isProfileStep ? 'step' : 'false');
    stepOneLabel.className = 'text-xs ' + (isProfileStep ? 'font-semibold text-book-ink' : 'font-bold text-book-ink');
    stepTwoLabel.className = 'text-xs ' + (isProfileStep ? 'font-bold text-book-ink' : 'font-semibold text-faint');
    if (message) message.hidden = true;
    (isProfileStep ? fields.email : document.getElementById('agree-service'))?.focus();
  }

  function setFieldError(key, text) {
    const input = fields[key];
    const error = document.getElementById('signup-' + (key === 'passwordConfirm' ? 'password-confirm' : key) + '-error');
    if (!input || !error) return !text;
    input.setAttribute('aria-invalid', text ? 'true' : 'false');
    error.textContent = text;
    error.hidden = !text;
    return !text;
  }

  function updateRoleFields() {
    if (!roleSelect) return;
    const role = roleSelect.value;
    const isFacility = role === 'facility';
    const isSystem = role === 'system';
    facilitySignupFields.hidden = !isFacility;
    systemSignupFields.hidden = !isSystem;
    facilityName.required = isFacility;
    adminInvite.required = isSystem;
    facilityName.setAttribute('aria-invalid', 'false');
    adminInvite.setAttribute('aria-invalid', 'false');
    roleError.hidden = true;
    roleHint.textContent = isFacility
      ? '소속 시설 확인 후 권한을 승인합니다. 승인 전에는 일반 사용자 권한으로 이용할 수 있습니다.'
      : isSystem
        ? '시스템 관리자 권한은 초대 코드 확인과 별도 승인이 필요합니다. 검증은 서버에서 처리합니다.'
        : '일반 사용자 계정으로 가입합니다.';
  }

  function validateRoleFields() {
    const role = roleSelect.value;
    let input = null;
    let errorText = '';
    if (role === 'facility' && !facilityName.value.trim()) {
      input = facilityName;
      errorText = '소속 시설·기관을 입력해 주세요.';
    } else if (role === 'system' && !adminInvite.value.trim()) {
      input = adminInvite;
      errorText = '관리자 초대 코드를 입력해 주세요.';
    }
    facilityName.setAttribute('aria-invalid', input === facilityName ? 'true' : 'false');
    adminInvite.setAttribute('aria-invalid', input === adminInvite ? 'true' : 'false');
    roleError.textContent = errorText;
    roleError.hidden = !errorText;
    if (input) input.focus();
    return !errorText;
  }

  const validators = {
    email: function () {
      const input = fields.email;
      if (!input.value.trim()) return setFieldError('email', '이메일을 입력해 주세요.');
      if (input.validity.typeMismatch) return setFieldError('email', '올바른 이메일 형식으로 입력해 주세요.');
      return setFieldError('email', '');
    },
    phone: function () {
      const value = fields.phone.value.trim();
      if (!value) return setFieldError('phone', '연락처를 입력해 주세요.');
      if (!/^[0-9+() -]{8,20}$/.test(value)) return setFieldError('phone', '연락처를 8~20자의 숫자와 전화번호 기호로 입력해 주세요.');
      return setFieldError('phone', '');
    },
    password: function () {
      const value = fields.password.value;
      if (!value) return setFieldError('password', '비밀번호를 입력해 주세요.');
      if (value.length < 8 || !/[A-Za-z]/.test(value) || !/[0-9]/.test(value)) return setFieldError('password', '영문과 숫자를 포함해 8자 이상 입력해 주세요.');
      return setFieldError('password', '');
    },
    passwordConfirm: function () {
      if (!fields.passwordConfirm.value) return setFieldError('passwordConfirm', '비밀번호를 한 번 더 입력해 주세요.');
      if (fields.passwordConfirm.value !== fields.password.value) return setFieldError('passwordConfirm', '비밀번호가 일치하지 않습니다.');
      return setFieldError('passwordConfirm', '');
    },
    nickname: function () {
      const value = fields.nickname.value.trim();
      if (!value) return setFieldError('nickname', '닉네임을 입력해 주세요.');
      if (!/^[가-힣a-zA-Z0-9_]{2,12}$/.test(value)) return setFieldError('nickname', '한글, 영문, 숫자, 밑줄로 2~12자 입력해 주세요.');
      return setFieldError('nickname', '');
    }
  };

  Object.keys(fields).forEach(function (key) {
    fields[key]?.addEventListener('blur', function () {
      fields[key].dataset.touched = 'true';
      validators[key]();
    });
    fields[key]?.addEventListener('input', function () {
      if (fields[key].dataset.touched === 'true' || form.dataset.submitted === 'true') validators[key]();
      if (key === 'password' && (fields.passwordConfirm.dataset.touched === 'true' || form.dataset.submitted === 'true')) validators.passwordConfirm();
    });
  });

  document.getElementById('agree-all')?.addEventListener('change', function (event) {
    allConsents.forEach(function (checkbox) { if (checkbox) checkbox.checked = event.target.checked; });
    if (requiredConsents.every(function (item) { return item && item.checked; })) termsError.hidden = true;
  });

  allConsents.forEach(function (checkbox) {
    checkbox?.addEventListener('change', function () {
      document.getElementById('agree-all').checked = allConsents.every(function (item) { return item && item.checked; });
      if (requiredConsents.every(function (item) { return item && item.checked; })) termsError.hidden = true;
    });
  });

  document.getElementById('next-step')?.addEventListener('click', function () {
    if (!requiredConsents.every(function (item) { return item && item.checked; })) {
      termsError.hidden = false;
      requiredConsents.find(function (item) { return item && !item.checked; })?.focus();
      return;
    }
    showStep(2);
  });

  document.getElementById('previous-step')?.addEventListener('click', function () { showStep(1); });
  roleSelect?.addEventListener('change', updateRoleFields);
  [facilityName, adminInvite].forEach(function (input) {
    input?.addEventListener('input', function () {
      if (!roleError.hidden) validateRoleFields();
    });
  });

  document.addEventListener('click', function (event) {
    const termsButton = event.target instanceof Element ? event.target.closest('[data-terms-toggle]') : null;
    if (!termsButton) return;
    const copy = document.getElementById(termsButton.dataset.termsToggle);
    if (!copy) return;
    copy.hidden = !copy.hidden;
    termsButton.setAttribute('aria-expanded', String(!copy.hidden));
    termsButton.textContent = copy.hidden ? '보기' : '닫기';
  });

  form.addEventListener('submit', function (event) {
    form.dataset.submitted = 'true';
    if (!requiredConsents.every(function (item) { return item && item.checked; })) {
      event.preventDefault();
      showStep(1);
      termsError.hidden = false;
      requiredConsents.find(function (item) { return item && !item.checked; })?.focus();
      return;
    }
    const valid = Object.keys(validators).map(function (key) { return validators[key](); }).every(Boolean);
    if (!valid) {
      event.preventDefault();
      Object.keys(fields).map(function (key) { return fields[key]; }).find(function (input) { return input?.getAttribute('aria-invalid') === 'true'; })?.focus();
      return;
    }
    if (!validateRoleFields()) {
      event.preventDefault();
      return;
    }
    if (regionSelect) regionSelect.disabled = false;
  });

  updateRoleFields();
  showStep(Number(form.dataset.startStep) === 2 ? 2 : 1);
})();
