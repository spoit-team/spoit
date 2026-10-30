/* 매치 상세 화면: 참가 방식·팀원 선택과 정원 안내만 처리하고, 신청 저장은 서버로 넘깁니다. */
(function () {
  'use strict';

  const dialog = document.getElementById('selection-dialog');
  const form = document.getElementById('selection-form');
  if (!dialog || !form) return;

  const script = document.currentScript;
  const availableSeats = Number(script?.dataset.availableSeats || 0);

  const phoneInput = document.getElementById('applicant-phone');
  const kindInput = document.getElementById('selection-kind');
  const individualPanel = document.getElementById('individual-panel');
  const teamPanel = document.getElementById('team-panel');
  const teamStepSelect = document.getElementById('team-step-select');
  const teamStepRoster = document.getElementById('team-step-roster');
  const stepIndicator1 = document.getElementById('team-step-indicator-1');
  const stepIndicator2 = document.getElementById('team-step-indicator-2');
  const teamOptions = document.getElementById('team-options');
  const selectedTeamName = document.getElementById('team-selected-name');
  const selectedTeamCount = document.getElementById('team-selected-count');
  const capacityNotice = document.getElementById('selection-capacity-notice');
  const errorBox = document.getElementById('selection-error');
  const backButton = document.getElementById('selection-back');
  const submitButton = document.getElementById('selection-submit');
  const heading = document.getElementById('selection-dialog-heading');
  const lede = document.getElementById('selection-dialog-lede');
  const clubOptions = Array.from(teamOptions?.querySelectorAll('.md-team-option') || []);
  const clubRadios = Array.from(teamOptions?.querySelectorAll('input[name="clubId"]') || []);
  const rosters = Array.from(document.querySelectorAll('[data-team-roster]'));

  let selectionKind = 'individual';
  let teamStep = 1;

  function selectedClubRadio() {
    return teamOptions?.querySelector('input[name="clubId"]:checked') || null;
  }

  function selectedClubOption() {
    return selectedClubRadio()?.closest('.md-team-option') || null;
  }

  function selectedClubId() {
    return selectedClubRadio()?.value || null;
  }

  function rosterFor(clubId) {
    return document.querySelector('[data-team-roster="' + clubId + '"]');
  }

  // 이미 이 매치에 신청한 회원: 체크된 채 잠겨 있고 인원·제출에서 빠진다 (disabled는 전송되지 않는다).
  function isApplied(checkbox) {
    return checkbox.dataset.applied === 'true';
  }

  function checkedClubMemberIds(clubId) {
    const roster = rosterFor(clubId);
    if (!roster) return [];
    return Array.from(roster.querySelectorAll('input[type="checkbox"]:checked:not(:disabled)')).map(function (input) { return input.value; });
  }

  function seatCount() {
    if (selectionKind === 'individual') return 1;
    const option = selectedClubOption();
    if (!option) return 1;
    return 1 + checkedClubMemberIds(option.dataset.teamIndex).length;
  }

  function showError(message) {
    if (!errorBox) return;
    errorBox.textContent = message || '';
    errorBox.hidden = !message;
  }

  function updateNotice() {
    if (!capacityNotice) return;
    if (selectionKind === 'team' && teamStep === 1) {
      capacityNotice.classList.remove('is-unavailable');
      capacityNotice.textContent = '등록 팀과 참가 팀원을 선택하면 현재 예약 가능 인원을 확인할 수 있습니다.';
      return;
    }
    const seats = seatCount();
    const unavailable = seats > availableSeats || availableSeats === 0;
    capacityNotice.classList.toggle('is-unavailable', unavailable);
    capacityNotice.textContent = unavailable
      ? availableSeats === 0
        ? '정원이 마감되어 예약할 수 없습니다. 자리가 있는 다른 매칭방을 선택해 주세요.'
        : '남은 자리 ' + availableSeats + '명보다 신청 인원 ' + seats + '명이 많아 예약할 수 없습니다. 참가 인원을 줄여 주세요.'
      : '현재 ' + availableSeats + '자리가 남아 있습니다. ' + seats + '명 신청 시 서버 확인과 결제 안내 후 참가가 확정됩니다.';
  }

  function renderTeamStep() {
    const isTeam = selectionKind === 'team';
    const clubId = selectedClubId();

    clubOptions.forEach(function (option) {
      const radio = option.querySelector('input[name="clubId"]');
      option.classList.toggle('is-selected', Boolean(radio?.checked));
    });

    rosters.forEach(function (roster) {
      const isActiveRoster = isTeam && teamStep === 2 && roster.dataset.teamRoster === clubId;
      roster.hidden = roster.dataset.teamRoster !== clubId;
      roster.querySelectorAll('input[type="checkbox"]').forEach(function (checkbox) {
        checkbox.disabled = !isActiveRoster || isApplied(checkbox);
      });
    });
    clubRadios.forEach(function (radio) {
      radio.disabled = !isTeam;
    });

    teamStepSelect.hidden = !isTeam || teamStep !== 1;
    teamStepRoster.hidden = !isTeam || teamStep !== 2;
    stepIndicator1.classList.toggle('is-active', teamStep === 1);
    stepIndicator1.classList.toggle('is-complete', teamStep === 2);
    stepIndicator2.classList.toggle('is-active', teamStep === 2);
    stepIndicator1.setAttribute('aria-current', teamStep === 1 ? 'step' : 'false');
    stepIndicator2.setAttribute('aria-current', teamStep === 2 ? 'step' : 'false');

    if (backButton) backButton.textContent = isTeam && teamStep === 2 ? '이전' : '취소';
    if (submitButton) {
      submitButton.textContent = isTeam
        ? teamStep === 1 ? '참가 팀원 선택' : '참가 신청하기'
        : '참가 신청하기';
      submitButton.disabled = isTeam
        ? teamStep === 1 ? !clubId : !hasParticipants() || seatCount() > availableSeats
        : availableSeats < 1;
    }

    const option = selectedClubOption();
    if (option && selectedTeamName) selectedTeamName.textContent = option.querySelector('b')?.textContent || '';
    if (selectedTeamCount) selectedTeamCount.textContent = '총 ' + seatCount() + '명 · 신청자 포함';
    updateNotice();
  }

  function hasParticipants() {
    const option = selectedClubOption();
    if (!option) return false;
    return checkedClubMemberIds(option.dataset.teamIndex).length > 0;
  }

  function openSelection(kind) {
    selectionKind = kind;
    teamStep = 1;
    if (kindInput) kindInput.value = kind;
    dialog.classList.toggle('is-team', kind === 'team');
    individualPanel.hidden = kind !== 'individual';
    teamPanel.hidden = kind !== 'team';
    if (heading) heading.textContent = kind === 'team' ? '등록 팀 참가 신청' : '개인 참가 신청';
    if (lede) lede.textContent = kind === 'team'
      ? '신청할 등록 팀과 참가할 팀원을 선택해 주세요.'
      : '신청자 정보와 연락처를 확인해 주세요.';
    showError('');
    renderTeamStep();
    if (!dialog.open) dialog.showModal();
    if (kind === 'team') teamOptions?.querySelector('input[name="clubId"]')?.focus({ preventScroll: true });
    else phoneInput?.focus({ preventScroll: true });
  }

  document.querySelectorAll('[data-open-selection]').forEach(function (button) {
    button.addEventListener('click', function () {
      openSelection(button.dataset.openSelection);
    });
  });

  teamOptions?.addEventListener('change', function (event) {
    if (!event.target.matches('input[name="clubId"]')) return;
    const roster = rosterFor(event.target.value);
    // 이미 신청한 회원은 체크된 채 잠겨 있어야 하므로 건드리지 않는다.
    roster?.querySelectorAll('input[type="checkbox"]').forEach(function (checkbox) {
      if (!isApplied(checkbox)) checkbox.checked = false;
    });
    showError('');
    renderTeamStep();
  });

  rosters.forEach(function (roster) {
    roster.addEventListener('change', function () {
      showError('');
      renderTeamStep();
    });
  });

  backButton?.addEventListener('click', function (event) {
    if (selectionKind === 'team' && teamStep === 2) {
      event.preventDefault();
      teamStep = 1;
      renderTeamStep();
      teamOptions?.querySelector('input[name="clubId"]')?.focus({ preventScroll: true });
      return;
    }
    dialog.close();
  });

  document.querySelectorAll('[data-close-dialog]').forEach(function (button) {
    button.addEventListener('click', function () { dialog.close(); });
  });

  dialog.addEventListener('click', function (event) {
    if (event.target === dialog) dialog.close();
  });

  form.addEventListener('submit', function (event) {
    if (selectionKind === 'team' && teamStep === 1) {
      event.preventDefault();
      if (!selectedClubId()) {
        showError('신청할 등록 팀을 선택해 주세요.');
        teamOptions?.querySelector('input[name="clubId"]')?.focus({ preventScroll: true });
        return;
      }
      teamStep = 2;
      renderTeamStep();
      rosterFor(selectedClubId())?.querySelector('input[type="checkbox"]:not(:disabled)')?.focus({ preventScroll: true });
      return;
    }

    if (phoneInput && !phoneInput.reportValidity()) {
      event.preventDefault();
      return;
    }
    if (seatCount() > availableSeats) {
      event.preventDefault();
      showError('남은 자리보다 신청 인원이 많아 신청할 수 없습니다. 참가 인원을 줄여 주세요.');
      return;
    }
    if (selectionKind === 'team' && !hasParticipants()) {
      event.preventDefault();
      showError('신청 대표 외에 참가할 팀원을 한 명 이상 선택해 주세요.');
      rosterFor(selectedClubId())?.querySelector('input[type="checkbox"]:not(:disabled)')?.focus({ preventScroll: true });
      return;
    }
  });
})();
