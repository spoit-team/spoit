/* 시설 관리자 화면 UI: 모달 열기·닫기, 시간 블록 선택 요약, 위치 미리보기만 담당합니다.
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

  function openModal(modal, focusSelector) {
    admin?.openModal(modal, focusSelector);
  }

  function closeModal(modal) {
    admin?.closeModal(modal);
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
      if (facilityForm) facilityForm.action = facilityForm.dataset.facilityBase || facilityForm.action;
      const title = facilityModal?.querySelector('#facility-modal-title');
      if (title) title.textContent = '시설 등록';
      const address = document.getElementById('facility-map-address');
      if (address) address.textContent = '주소를 입력하면 여기에 표시됩니다.';
      openModal(facilityModal, '#facility-name');
      return;
    }
    if (target.closest('[data-close-facility]')) {
      closeModal(facilityModal);
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

    const edit = target.closest('[data-edit-facility]');
    if (edit) {
      const dataset = edit.dataset;
      document.getElementById('facility-name').value = dataset.facilityName || '';
      document.getElementById('facility-address').value = dataset.facilityAddress || '';
      setFacilitySports(dataset.facilitySports || '');
      document.getElementById('facility-zone-count').value = dataset.facilityZones || '';
      document.getElementById('facility-operating-status').value = dataset.facilityStatus || '운영 중';
      if (facilityForm) facilityForm.action = (facilityForm.dataset.facilityBase || '') + '/' + (dataset.facilityId || '');
      const title = facilityModal?.querySelector('#facility-modal-title');
      if (title) title.textContent = '시설 정보 수정';
      const address = document.getElementById('facility-map-address');
      if (address) address.textContent = dataset.facilityAddress || '주소를 입력하면 여기에 표시됩니다.';
      openModal(facilityModal, '#facility-name');
    }
  });

  [roomEditorModal, facilityModal, maintenanceModal].forEach(function (modal) {
    modal?.addEventListener('click', function (event) {
      if (event.target === modal) closeModal(modal);
    });
  });

  document.addEventListener('keydown', function (event) {
    if (event.key !== 'Escape') return;
    [roomEditorModal, facilityModal, maintenanceModal].forEach(function (modal) {
      if (modal && !modal.hidden) closeModal(modal);
    });
  });

  document.addEventListener('keydown', function (event) {
    [roomEditorModal, facilityModal, maintenanceModal].forEach(function (modal) {
      if (modal && !modal.hidden) admin?.trapFocus(modal, event);
    });
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

    if (form === facilityForm) {
      const selectedSports = facilitySportOptions.filter(function (input) { return input.checked; });
      if (!selectedSports.length) {
        event.preventDefault();
        admin?.toast('시설 이용 종목을 하나 이상 선택해 주세요.', true);
        facilitySportOptions[0]?.focus();
        return;
      }
      const sportsValue = document.getElementById('facility-sports-value');
      if (sportsValue) sportsValue.value = selectedSports.map(function (input) { return input.value; }).join(', ');
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
