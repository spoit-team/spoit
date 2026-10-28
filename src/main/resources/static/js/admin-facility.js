/* 시설 관리자 화면 UI: 모달 열기·닫기, 시간 블록 선택 요약, 위치 미리보기,
   시설 수정·삭제 확인만 담당합니다.
   시설·일정·매칭방 저장은 모두 서버 폼으로 전송됩니다. */
(function (global) {
  'use strict';

  const admin = global.PlayOnAdmin;
  const roomEditorModal = document.getElementById('room-editor-modal');
  const roomEditorForm = document.getElementById('room-editor-form');
  const facilityModal = document.getElementById('facility-modal');
  const facilityForm = document.getElementById('facility-form');
  const facilitySportOptions = Array.from(document.querySelectorAll('[data-facility-sport]'));
  const maintenanceModal = document.getElementById('maintenance-modal');
  const facilityConfirmModal = document.getElementById('facility-confirm-modal');
  const facilityConfirmTitle = document.getElementById('facility-confirm-title');
  const facilityConfirmMessage = document.getElementById('facility-confirm-message');
  const facilityConfirmName = document.querySelector('[data-facility-confirm-name]');
  const facilityConfirmSubmit = document.getElementById('facility-confirm-submit');

  /* 확인을 기다리는 작업: { form, name, title, message, submitLabel, danger } */
  let pendingFacilityConfirm = null;
  /* 사용자가 확인을 마친 폼. submit 핸들러가 같은 폼을 다시 가로막지 않게 한다. */
  let confirmedSubmitForm = null;
  /* 수정·등록 모달을 연 행 버튼. 모달을 닫았을 때 이 버튼으로 초점을 되돌린다. */
  let facilityModalOpener = null;

  function openModal(modal, focusSelector) {
    admin?.openModal(modal, focusSelector);
  }

  function closeModal(modal) {
    admin?.closeModal(modal);
  }

  /* 공통 셸은 마지막으로 초점이 있던 요소로 되돌립니다.
     확인 창을 거친 뒤에는 그 요소가 이미 닫힌 모달 안일 수 있어, 연 버튼으로 직접 되돌립니다. */
  function closeFacilityModal() {
    closeModal(facilityModal);
    facilityModalOpener?.focus?.();
    facilityModalOpener = null;
  }

  function activeFacilityModals() {
    return [roomEditorModal, facilityModal, maintenanceModal, facilityConfirmModal].filter(function (modal) {
      return modal && !modal.hidden;
    });
  }

  /* 확인 창은 항상 다른 모달 위에 열리므로 마지막에 확인합니다. */
  function topModal() {
    const stack = activeFacilityModals();
    return stack[stack.length - 1] || null;
  }

  function isFacilityEditMode() {
    return facilityForm?.dataset.facilityMode === 'edit';
  }

  /**
   * 수정·삭제처럼 되돌리기 어려운 작업을 서버로 보내기 전에 확인 창을 띄운다.
   * 폼은 여기서 제출하지 않고, 확인 버튼을 눌렀을 때 한 번만 requestSubmit으로 보낸다.
   */
  function requestFacilityConfirmation(action) {
    if (!facilityConfirmModal || !action?.form) return false;
    pendingFacilityConfirm = action;
    if (facilityConfirmTitle) facilityConfirmTitle.textContent = action.title;
    if (facilityConfirmMessage) facilityConfirmMessage.textContent = action.message;
    if (facilityConfirmName) facilityConfirmName.textContent = action.name;
    if (facilityConfirmSubmit) {
      facilityConfirmSubmit.textContent = action.submitLabel;
      facilityConfirmSubmit.className = action.danger ? 'admin-button danger-solid' : 'admin-button primary';
      facilityConfirmSubmit.disabled = false;
    }
    openModal(facilityConfirmModal, '#facility-confirm-cancel');
    return true;
  }

  /**
   * 확인을 마친 폼을 한 번만 제출한다. requestSubmit이 브라우저 검증에 막혀
   * submit 이벤트가 발생하지 않아도 확인 대기 상태가 남지 않게 finally에서 지운다.
   */
  function submitConfirmed(form) {
    confirmedSubmitForm = form;
    try {
      form.requestSubmit();
    } finally {
      confirmedSubmitForm = null;
    }
  }

  /**
   * 확인 창을 먼저 띄우고, 확인 창을 쓸 수 없는 환경에서만 브라우저 기본 확인으로 대체한다.
   * 어느 경우에도 사용자의 확인 없이 서버로 보내지 않는다.
   */
  function confirmOrSubmit(action) {
    if (requestFacilityConfirmation(action)) return;
    if (!global.confirm(action.title + ' ' + action.message)) return;
    submitConfirmed(action.form);
  }

  /* 확인을 취소하면 아무 요청도 보내지 않는다. 초점은 공통 셸이 확인 창을 연 버튼으로 되돌린다. */
  function cancelFacilityConfirmation() {
    pendingFacilityConfirm = null;
    closeModal(facilityConfirmModal);
  }

  function confirmFacilityAction() {
    const action = pendingFacilityConfirm;
    /* 확인 버튼을 두 번 눌러도 폼은 한 번만 전송된다. */
    if (!action) return;
    if (facilityConfirmSubmit) facilityConfirmSubmit.disabled = true;
    pendingFacilityConfirm = null;
    /* 확인을 마친 폼은 submit 핸들러에서 다시 가로막지 않고 그대로 서버로 보낸다. */
    closeModal(facilityConfirmModal);
    submitConfirmed(action.form);
  }

  function hourToMinutes(value) {
    const match = String(value || '').match(/^(\d{2}):(\d{2})$/);
    return match ? Number(match[1]) * 60 + Number(match[2]) : null;
  }

  function minutesToHour(value) {
    const hours = Math.floor(value / 60);
    return String(hours).padStart(2, '0') + ':00';
  }

  function selectedRanges(container) {
    const values = Array.from(container.querySelectorAll('input[name="hours"]:checked'))
      .map(function (input) { return input.value; })
      .sort();
    if (!values.length) return { count: 0, text: '선택된 시간이 없습니다.' };

    const ranges = [];
    let start = values[0];
    let previous = values[0];
    values.slice(1).forEach(function (value) {
      const previousEnd = hourToMinutes(previous) + 60;
      if (hourToMinutes(value) === previousEnd) {
        previous = value;
        return;
      }
      ranges.push(start + '–' + minutesToHour(hourToMinutes(previous) + 60));
      start = value;
      previous = value;
    });
    ranges.push(start + '–' + minutesToHour(hourToMinutes(previous) + 60));
    return { count: values.length, text: ranges.join(', ') };
  }

  function updateDateScheduleSummary() {
    const grid = document.getElementById('date-schedule-hour-grid');
    const count = document.getElementById('date-schedule-count');
    const ranges = document.getElementById('date-schedule-ranges');
    if (!grid) return;
    const summary = selectedRanges(grid);
    if (count) count.textContent = summary.count + '시간 선택';
    if (ranges) ranges.textContent = summary.text;
  }

  function updateRoomEditorSummary() {
    const grid = document.getElementById('room-editor-hour-grid');
    const summary = document.getElementById('room-editor-time-summary');
    if (!grid || !summary) return;
    const result = selectedRanges(grid);
    summary.textContent = result.count ? result.count + '시간 선택 · ' + result.text : '시간대를 선택해 주세요.';
  }

  function updateRoomEditorCapacity() {
    const hint = document.getElementById('room-editor-capacity-hint');
    const teamA = Number(document.getElementById('room-editor-team-a')?.value || 0);
    const teamB = Number(document.getElementById('room-editor-team-b')?.value || 0);
    if (!hint) return;
    const maxParticipants = teamA + teamB;
    if (!maxParticipants) {
      hint.textContent = 'A팀과 B팀 정원을 입력하면 전체 정원이 표시됩니다.';
      return;
    }
    hint.textContent = '최대 참가 인원 ' + maxParticipants + '명 (A팀 ' + teamA + '명 · B팀 ' + teamB + '명)';
  }

  function setFacilitySports(value) {
    const selected = new Set(String(value || '').split(',').map(function (sport) { return sport.trim(); }).filter(Boolean));
    facilitySportOptions.forEach(function (input) {
      input.checked = selected.has(input.value);
    });
  }

  function resetRoomEditor() {
    if (!roomEditorForm) return;
    roomEditorForm.reset();
    roomEditorForm.querySelectorAll('input[name="hours"]').forEach(function (input) { input.checked = false; });
    updateRoomEditorSummary();
    updateRoomEditorCapacity();
  }

  document.addEventListener('click', function (event) {
    const target = event.target instanceof Element ? event.target : null;
    if (!target) return;

    if (target.closest('[data-open-room-editor]')) {
      resetRoomEditor();
      openModal(roomEditorModal, '#room-editor-facility');
      return;
    }
    if (target.closest('[data-close-room-editor]')) {
      closeModal(roomEditorModal);
      return;
    }
    if (target.closest('[data-open-facility]')) {
      facilityForm?.reset();
      if (facilityForm) {
        facilityForm.action = facilityForm.dataset.facilityBase || facilityForm.action;
        facilityForm.dataset.facilityMode = 'create';
        delete facilityForm.dataset.facilityName;
      }
      const title = facilityModal?.querySelector('#facility-modal-title');
      if (title) title.textContent = '시설 등록';
      const address = document.getElementById('facility-map-address');
      if (address) address.textContent = '주소를 입력하면 여기에 표시됩니다.';
      facilityModalOpener = target.closest('[data-open-facility]');
      openModal(facilityModal, '#facility-name');
      return;
    }
    if (target.closest('[data-close-facility]')) {
      closeFacilityModal();
      return;
    }
    if (target.closest('[data-open-maintenance]')) {
      openModal(maintenanceModal, '#maintenance-date');
      return;
    }
    if (target.closest('[data-close-maintenance]')) {
      closeModal(maintenanceModal);
      return;
    }
    if (target.closest('[data-close-facility-confirm]')) {
      cancelFacilityConfirmation();
      return;
    }
    if (target.closest('[data-confirm-facility-action]')) {
      confirmFacilityAction();
      return;
    }

    const edit = target.closest('[data-edit-facility]');
    if (edit) {
      const dataset = edit.dataset;
      document.getElementById('facility-name').value = dataset.facilityName || '';
      document.getElementById('facility-address').value = dataset.facilityAddress || '';
      setFacilitySports(dataset.facilitySports || '');
      document.getElementById('facility-zone-count').value = dataset.facilityZones || '';
      document.getElementById('facility-operating-status').value = dataset.facilityStatus || '운영 중';
      if (facilityForm) {
        facilityForm.action = (facilityForm.dataset.facilityBase || '') + '/' + (dataset.facilityId || '');
        facilityForm.dataset.facilityMode = 'edit';
        facilityForm.dataset.facilityName = dataset.facilityName || '';
      }
      const title = facilityModal?.querySelector('#facility-modal-title');
      if (title) title.textContent = '시설 정보 수정';
      const address = document.getElementById('facility-map-address');
      if (address) address.textContent = dataset.facilityAddress || '주소를 입력하면 여기에 표시됩니다.';
      facilityModalOpener = edit;
      openModal(facilityModal, '#facility-name');
    }
  });

  [roomEditorModal, facilityModal, maintenanceModal].forEach(function (modal) {
    modal?.addEventListener('click', function (event) {
      if (event.target !== modal) return;
      if (modal === facilityModal) closeFacilityModal();
      else closeModal(modal);
    });
  });

  facilityConfirmModal?.addEventListener('click', function (event) {
    if (event.target === facilityConfirmModal) cancelFacilityConfirmation();
  });

  /* Escape는 가장 위에 열린 모달 하나만 닫습니다. 확인 창이 열려 있으면 확인 창만 닫힙니다. */
  /* 확인 창이 열려 있으면 Escape는 확인 창만 닫고, 아래 수정 모달은 그대로 둡니다. */
  function hasOpenNotificationModal() {
    const notificationModal = document.getElementById('admin-notification-modal');
    return Boolean(notificationModal && !notificationModal.hidden);
  }

  document.addEventListener('keydown', function (event) {
    if (event.key !== 'Escape') return;
    /* 알림 모달이 열려 있으면 admin-shell.js가 그 모달만 닫습니다. */
    if (hasOpenNotificationModal()) return;
    const top = topModal();
    if (!top) return;
    if (top === facilityConfirmModal) cancelFacilityConfirmation();
    else if (top === facilityModal) closeFacilityModal();
    else closeModal(top);
  });

  document.addEventListener('keydown', function (event) {
    if (hasOpenNotificationModal()) return;
    const top = topModal();
    if (top) admin?.trapFocus(top, event);
  });

  document.getElementById('room-editor-hour-grid')?.addEventListener('change', updateRoomEditorSummary);
  ['room-editor-team-a', 'room-editor-team-b'].forEach(function (id) {
    document.getElementById(id)?.addEventListener('input', updateRoomEditorCapacity);
  });

  document.getElementById('date-schedule-hour-grid')?.addEventListener('change', updateDateScheduleSummary);

  document.querySelectorAll('#weekly-hours-form tbody tr').forEach(function (row) {
    const openDay = row.querySelector('input[name="openDays"]');
    const startTime = row.querySelector('select[name^="start-"]');
    const endTime = row.querySelector('select[name^="end-"]');
    const status = row.querySelector('[data-weekly-hours-status]');
    if (!openDay) return;

    function syncWeeklyHoursRow() {
      const isOpen = openDay.checked;
      if (startTime) startTime.disabled = !isOpen;
      if (endTime) endTime.disabled = !isOpen;
      if (status) {
        status.classList.toggle('good', isOpen);
        status.classList.toggle('neutral', !isOpen);
        status.textContent = isOpen ? '예약 가능' : '휴무';
      }
    }

    openDay.addEventListener('change', syncWeeklyHoursRow);
    syncWeeklyHoursRow();
  });

  document.getElementById('facility-address')?.addEventListener('input', function (event) {
    const address = document.getElementById('facility-map-address');
    if (address) address.textContent = event.target.value.trim() || '주소를 입력하면 여기에 표시됩니다.';
  });

  document.addEventListener('submit', function (event) {
    const form = event.target;
    if (!(form instanceof HTMLFormElement)) return;

    if (form.matches('.facility-delete-form')) {
      /* 확인을 마친 제출은 그대로 서버로 보낸다. */
      if (confirmedSubmitForm === form) {
        confirmedSubmitForm = null;
        return;
      }
      event.preventDefault();
      const button = form.querySelector('[data-delete-facility]');
      confirmOrSubmit({
        form: form,
        name: button?.dataset.facilityName || '시설',
        title: '이 시설을 삭제할까요?',
        message: '삭제한 시설은 담당 시설 목록에서 사라지며 되돌릴 수 없습니다.',
        submitLabel: '삭제',
        danger: true
      });
      return;
    }

    if (form === facilityForm) {
      /* 필수 검증을 먼저 통과한 뒤에만 확인 창을 띄웁니다. */
      const selectedSports = facilitySportOptions.filter(function (input) { return input.checked; });
      if (!selectedSports.length) {
        event.preventDefault();
        confirmedSubmitForm = null;
        admin?.toast('시설 이용 종목을 하나 이상 선택해 주세요.', true);
        facilitySportOptions[0]?.focus();
        return;
      }
      const sportsValue = document.getElementById('facility-sports-value');
      if (sportsValue) sportsValue.value = selectedSports.map(function (input) { return input.value; }).join(', ');

      /* 등록은 바로 저장하고, 기존 시설 수정은 실수로 덮어쓰지 않도록 확인을 받습니다. */
      if (confirmedSubmitForm === form) {
        confirmedSubmitForm = null;
        return;
      }
      if (isFacilityEditMode()) {
        event.preventDefault();
        const nameInput = document.getElementById('facility-name');
        confirmOrSubmit({
          form: form,
          name: facilityForm.dataset.facilityName || nameInput?.value?.trim() || '시설',
          title: '시설 정보를 수정할까요?',
          message: '저장하면 담당 시설 목록과 예약 화면에 바로 반영됩니다.',
          submitLabel: '수정 저장',
          danger: false
        });
      }
      return;
    }

    if (form === roomEditorForm) {
      const grid = document.getElementById('room-editor-hour-grid');
      if (grid && !grid.querySelector('input[name="hours"]:checked')) {
        event.preventDefault();
        admin?.toast('예약 시간 블록을 하나 이상 선택해 주세요.', true);
        grid.querySelector('input:not(:disabled)')?.focus();
      }
      return;
    }

    if (form.id === 'weekly-hours-form' && !form.querySelector('input[name="openDays"]:checked')) {
      event.preventDefault();
      admin?.toast('운영할 요일을 하나 이상 선택해 주세요.', true);
    }
  });

  updateRoomEditorCapacity();
})(window);
