/* 시설 관리자 화면 UI: 모달 열기·닫기, 시간 블록 선택 요약, 위치 미리보기,
   시설 수정·삭제 확인, 예약 운영 일정 폼의 사전 안내만 담당합니다.
   시설·일정·매칭방 저장은 모두 서버 폼으로 전송되며, 시간·충돌 검증의 최종 판단은 서버가 합니다. */
(function (global) {
  'use strict';

  const admin = global.PlayOnAdmin;
  const roomEditorModal = document.getElementById('room-editor-modal');
  const roomEditorForm = document.getElementById('room-editor-form');
  const roomEditorHourInputs = Array.from(document.querySelectorAll('#room-editor-hour-grid input[name="hours"]'));
  /* 신청자가 있는 방을 수정할 때 잠그는 입력. 비활성 입력은 전송되지 않고 서버도 그 값을 쓰지 않는다. */
  const roomScheduleFieldIds = ['room-editor-facility', 'room-editor-sport', 'room-editor-date'];
  const facilityModal = document.getElementById('facility-modal');
  const facilityForm = document.getElementById('facility-form');
  const facilitySportOptions = Array.from(document.querySelectorAll('[data-facility-sport]'));
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
    return [roomEditorModal, facilityModal, facilityConfirmModal].filter(function (modal) {
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
    return { count: values.length, rangeCount: ranges.length, text: ranges.join(', ') };
  }

  function updateDateScheduleSummary() {
    const grid = document.getElementById('date-schedule-hour-grid');
    const count = document.getElementById('date-schedule-count');
    const ranges = document.getElementById('date-schedule-ranges');
    const saveState = document.getElementById('date-schedule-save-state');
    if (!grid) return;
    const summary = selectedRanges(grid);
    if (count) count.textContent = summary.count + '시간 선택';
    if (ranges) ranges.textContent = summary.text;
    /* 전체 해제는 그 날짜를 휴무로 저장한다는 뜻임을 미리 알린다. 서버가 다시 검증한다. */
    if (saveState) {
      saveState.textContent = summary.count
        ? '변경은 서버에 저장됩니다.'
        : '선택한 시간이 없으면 이 날짜는 휴무로 저장됩니다.';
    }
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
    const team = Number(document.getElementById('room-editor-team')?.value || 0);
    if (!hint) return;
    if (!team) {
      hint.textContent = '팀당 정원을 입력하면 전체 정원이 표시됩니다.';
      return;
    }
    const reserved = Number(roomEditorForm?.dataset.roomReserved || 0);
    hint.textContent = '최대 참가 인원 ' + team * 2 + '명 (A팀 ' + team + '명 · B팀 ' + team + '명)'
      + (reserved ? ' · 현재 참가·신청 ' + reserved + '명' : '');
  }

  /* 고른 시설이 제공하는 종목만 남긴다. 종목 검증의 최종 판단은 서버가 한다. */
  function filterRoomEditorSports() {
    const facility = document.getElementById('room-editor-facility');
    const sport = document.getElementById('room-editor-sport');
    if (!facility || !sport) return;
    let firstVisible = null;
    Array.from(sport.options).forEach(function (option) {
      const visible = option.dataset.facilityId === facility.value;
      option.hidden = !visible;
      option.disabled = !visible;
      if (visible && !firstVisible) firstVisible = option;
    });
    const selected = sport.options[sport.selectedIndex];
    if (!selected || selected.disabled) sport.value = firstVisible ? firstVisible.value : '';
  }

  /**
   * 생성·수정 모드에 맞춰 폼 주소와 문구를 바꾼다. 신청자가 있는 방(locked)은 시설·종목·날짜·시간을
   * 잠그고 안내를 보여 준다.
   */
  function setRoomEditorMode(mode, room) {
    if (!roomEditorForm) return;
    const editing = mode === 'edit';
    const locked = Boolean(editing && room?.locked);
    const base = roomEditorForm.dataset.roomBase || roomEditorForm.action;
    roomEditorForm.action = editing ? base + '/' + room.id : base;
    roomEditorForm.dataset.roomMode = editing ? 'edit' : 'create';
    roomEditorForm.dataset.roomLocked = locked ? 'true' : '';
    roomEditorForm.dataset.roomReserved = editing ? String(room.reserved || 0) : '';

    const title = document.getElementById('room-editor-title');
    if (title) title.textContent = editing ? '매칭방 수정' : '매칭방 생성';
    const description = document.getElementById('room-editor-description');
    if (description) {
      description.textContent = editing
        ? room.id + '번 매칭방의 정보를 수정합니다.'
        : '담당 시설의 운영 시간에 맞춰 매칭방을 개설합니다. 연속된 1시간 블록을 선택하세요.';
    }
    const submit = document.getElementById('room-editor-submit');
    if (submit) submit.textContent = editing ? '수정 저장' : '매칭방 생성';
    const lockedNote = document.getElementById('room-editor-locked-note');
    if (lockedNote) lockedNote.hidden = !locked;

    roomScheduleFieldIds.forEach(function (id) {
      const field = document.getElementById(id);
      if (field) field.disabled = locked;
    });
    roomEditorHourInputs.forEach(function (input) {
      input.disabled = locked || input.dataset.slotUnavailable === 'true';
    });
  }

  /* 선택한 날짜와 가장 이른 시간 블록으로 만든 시작 시각. 값이 비어 있으면 null */
  function roomEditorStartAt(grid) {
    const date = document.getElementById('room-editor-date')?.value || '';
    const dateMatch = date.match(/^(\d{4})-(\d{2})-(\d{2})$/);
    const hours = Array.from(grid?.querySelectorAll('input[name="hours"]:checked') || [])
      .map(function (input) { return hourToMinutes(input.value); })
      .filter(function (minutes) { return minutes !== null; });
    if (!dateMatch || !hours.length) return null;
    const first = Math.min.apply(null, hours);
    return new Date(Number(dateMatch[1]), Number(dateMatch[2]) - 1, Number(dateMatch[3]),
      Math.floor(first / 60), first % 60);
  }

  function fillRoomEditor(dataset) {
    if (!roomEditorForm) return;
    roomEditorForm.reset();
    const setValue = function (id, value) {
      const field = document.getElementById(id);
      if (field) field.value = value || '';
    };
    setValue('room-editor-facility', dataset.roomFacility);
    filterRoomEditorSports();
    setValue('room-editor-sport', dataset.roomSport);
    setValue('room-editor-title-input', dataset.roomTitle);
    setValue('room-editor-date', dataset.roomDate);
    setValue('room-editor-team', dataset.roomTeam);
    setValue('room-editor-fee', dataset.roomFee);
    setValue('room-editor-level', dataset.roomSkill);
    const hours = new Set(String(dataset.roomHours || '').split(',').filter(Boolean));
    roomEditorHourInputs.forEach(function (input) { input.checked = hours.has(input.value); });
    setRoomEditorMode('edit', {
      id: dataset.roomId,
      locked: dataset.roomLocked === 'true',
      reserved: Number(dataset.roomReserved || 0)
    });
    resetRoomPhoto(dataset.roomPhoto);
    updateRoomEditorSummary();
    updateRoomEditorCapacity();
  }

  /* ---------------- 예약 통계 기간 ---------------- */

  /* 기간 드롭다운이 7/30/90일이면 오늘 기준 날짜를 채워 잠그고,
     '직접 입력'을 고를 때만 사용자가 날짜를 고칠 수 있다. 최종 기간 판정은 서버가 한다. */
  const statsPeriodSelect = document.querySelector('[data-stats-period]');
  const statsPeriodFrom = document.querySelector('[data-stats-period-from]');
  const statsPeriodTo = document.querySelector('[data-stats-period-to]');

  function formatDateInput(date) {
    const year = date.getFullYear();
    const month = String(date.getMonth() + 1).padStart(2, '0');
    const day = String(date.getDate()).padStart(2, '0');
    return year + '-' + month + '-' + day;
  }

  function syncStatsPeriod() {
    if (!statsPeriodSelect || !statsPeriodFrom || !statsPeriodTo) return;
    const days = Number(statsPeriodSelect.value);
    const custom = statsPeriodSelect.value === 'custom';
    statsPeriodFrom.disabled = !custom;
    statsPeriodTo.disabled = !custom;
    if (custom || !days) return;
    const today = new Date();
    const start = new Date(today.getFullYear(), today.getMonth(), today.getDate() - (days - 1));
    statsPeriodFrom.value = formatDateInput(start);
    statsPeriodTo.value = formatDateInput(today);
  }

  statsPeriodSelect?.addEventListener('change', syncStatsPeriod);
  syncStatsPeriod();

  /* 통계 조회는 시설을 골라야 의미가 있다. 시설 미선택(-시설 선택-)으로 제출하면
     서버는 빈 통계를 돌려주므로, 보내기 전에 화면에서 먼저 알린다.
     판정의 최종 책임은 서버에 있지만 여기서는 불필요한 요청을 막는다. */
  const statsFilterForm = document.querySelector('#facility-stats form');
  const statsFacilitySelect = document.getElementById('facility-stats-facility');

  /* ---------------- 매칭방 시설 사진 ---------------- */

  const ROOM_PHOTO_MAX_BYTES = 5 * 1024 * 1024;
  const ROOM_PHOTO_TYPES = ['image/jpeg', 'image/png', 'image/webp'];
  const roomPhotoDrop = document.getElementById('room-editor-photo-drop');
  const roomPhotoInput = document.getElementById('room-editor-photo');
  const roomPhotoImage = document.getElementById('room-editor-photo-image');
  const roomPhotoEmpty = document.getElementById('room-editor-photo-empty');
  const roomPhotoEmptyTitle = document.getElementById('room-editor-photo-empty-title');
  const roomPhotoEmptyHint = document.getElementById('room-editor-photo-empty-hint');
  const roomPhotoUndo = document.getElementById('room-editor-photo-undo');
  const roomPhotoClear = document.getElementById('room-editor-photo-clear');
  const roomPhotoRemove = document.getElementById('room-editor-photo-remove');
  const roomPhotoError = document.getElementById('room-editor-photo-error');
  /** 수정 중인 방의 기존 사진 URL */
  let roomPhotoCurrent = '';
  /** 기존 사진을 지우기로 했는지 */
  let roomPhotoRemoved = false;
  let roomPhotoObjectUrl = '';

  function setRoomPhotoError(message) {
    if (!roomPhotoError) return;
    roomPhotoError.hidden = !message;
    roomPhotoError.textContent = message || '';
  }

  function releaseRoomPhotoObjectUrl() {
    if (roomPhotoObjectUrl) URL.revokeObjectURL(roomPhotoObjectUrl);
    roomPhotoObjectUrl = '';
  }

  /** 새로 고른 파일 → 기존 사진 → 빈 안내 순서로 박스를 그린다. */
  function renderRoomPhoto() {
    const url = roomPhotoObjectUrl || (roomPhotoCurrent && !roomPhotoRemoved ? roomPhotoCurrent : '');
    if (roomPhotoImage) {
      roomPhotoImage.hidden = !url;
      if (url) roomPhotoImage.src = url;
      else roomPhotoImage.removeAttribute('src');
    }
    roomPhotoDrop?.classList.toggle('has-photo', Boolean(url));
    if (roomPhotoEmpty) roomPhotoEmpty.hidden = Boolean(url);
    if (roomPhotoClear) roomPhotoClear.hidden = !url;
    const removing = Boolean(roomPhotoCurrent && roomPhotoRemoved && !roomPhotoObjectUrl);
    if (roomPhotoEmptyTitle) {
      roomPhotoEmptyTitle.textContent = removing ? '저장하면 기존 사진이 삭제됩니다' : '사진을 끌어다 놓거나 클릭해 선택하세요';
    }
    if (roomPhotoEmptyHint) roomPhotoEmptyHint.textContent = removing ? '새 사진을 끌어다 놓으면 교체됩니다' : 'JPG·PNG·WEBP, 5MB 이하';
    if (roomPhotoUndo) roomPhotoUndo.hidden = !removing;
    if (roomPhotoRemove) roomPhotoRemove.value = removing ? 'true' : 'false';
  }

  /** 모달을 열 때 현재 사진(수정) 또는 빈 상태(생성)로 맞춘다. 폼 reset 뒤에 부른다. */
  function resetRoomPhoto(currentUrl) {
    releaseRoomPhotoObjectUrl();
    roomPhotoCurrent = currentUrl || '';
    roomPhotoRemoved = false;
    if (roomPhotoInput) roomPhotoInput.value = '';
    setRoomPhotoError('');
    renderRoomPhoto();
  }

  /** 선택한 파일의 형식·크기 오류 문구. 문제가 없거나 파일이 없으면 빈 문자열 */
  function roomPhotoProblem() {
    const file = roomPhotoInput?.files?.[0];
    if (!file) return '';
    if (!ROOM_PHOTO_TYPES.includes(file.type)) return '사진은 JPG·PNG·WEBP 파일만 올릴 수 있습니다.';
    if (file.size > ROOM_PHOTO_MAX_BYTES) return '사진은 5MB 이하로 올려 주세요.';
    return '';
  }

  /* 클릭 선택과 드롭 모두 투명한 파일 입력이 받으므로 change 한 곳에서 처리한다. */
  roomPhotoInput?.addEventListener('change', function () {
    releaseRoomPhotoObjectUrl();
    const file = roomPhotoInput.files?.[0];
    const problem = roomPhotoProblem();
    setRoomPhotoError(problem);
    if (problem) roomPhotoInput.value = '';
    if (file && !problem) {
      roomPhotoRemoved = false;
      roomPhotoObjectUrl = URL.createObjectURL(file);
    }
    renderRoomPhoto();
  });

  roomPhotoClear?.addEventListener('click', function () {
    setRoomPhotoError('');
    if (roomPhotoObjectUrl) {
      /* 새로 고른 파일만 취소하고 기존 사진(있으면)으로 돌아간다. */
      releaseRoomPhotoObjectUrl();
      if (roomPhotoInput) roomPhotoInput.value = '';
    } else {
      roomPhotoRemoved = true;
    }
    renderRoomPhoto();
  });

  roomPhotoUndo?.addEventListener('click', function () {
    roomPhotoRemoved = false;
    renderRoomPhoto();
  });

  ['dragenter', 'dragover'].forEach(function (type) {
    roomPhotoDrop?.addEventListener(type, function () { roomPhotoDrop.classList.add('is-dragover'); });
  });
  ['dragleave', 'drop'].forEach(function (type) {
    roomPhotoDrop?.addEventListener(type, function () { roomPhotoDrop.classList.remove('is-dragover'); });
  });

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
    setRoomEditorMode('create');
    resetRoomPhoto('');
    filterRoomEditorSports();
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
    const editRoom = target.closest('[data-edit-room]');
    if (editRoom) {
      fillRoomEditor(editRoom.dataset);
      openModal(roomEditorModal, '#room-editor-title-input');
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

  [roomEditorModal, facilityModal].forEach(function (modal) {
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

  /* 서버가 처음부터 막아 둔 블록은 모드를 바꿔도 계속 비활성으로 둔다. */
  roomEditorHourInputs.forEach(function (input) {
    input.dataset.slotUnavailable = input.disabled ? 'true' : '';
  });
  document.getElementById('room-editor-facility')?.addEventListener('change', filterRoomEditorSports);
  document.getElementById('room-editor-hour-grid')?.addEventListener('change', updateRoomEditorSummary);
  document.getElementById('room-editor-team')?.addEventListener('input', updateRoomEditorCapacity);

  /* 주간 폼에서 같은 시작·종료 시각은 잘못된 값이다. 서버도 같은 규칙으로 검증한다. */
  function findInvalidWeeklyRow() {
    const rows = Array.from(document.querySelectorAll('#weekly-hours-form tbody tr'));
    return rows.find(function (row) {
      const openDay = row.querySelector('input[name="openDays"]');
      if (!openDay?.checked) return false;
      const start = row.querySelector('select[name^="start-"]')?.value;
      const end = row.querySelector('select[name^="end-"]')?.value;
      if (!start || !end) return true;
      return hourToMinutes(start) === hourToMinutes(end);
    }) || null;
  }

  document.getElementById('facility-address')?.addEventListener('input', function (event) {
    const address = document.getElementById('facility-map-address');
    if (address) address.textContent = event.target.value.trim() || '주소를 입력하면 여기에 표시됩니다.';
  });

  document.addEventListener('submit', function (event) {
    const form = event.target;
    if (!(form instanceof HTMLFormElement)) return;

    if (form === statsFilterForm) {
      /* 시설 미선택이면 서버로 보내지 않는다. 경고 방식은 다른 검증과 같은 토스트를 쓴다. */
      if (statsFacilitySelect && !statsFacilitySelect.value) {
        event.preventDefault();
        const warning = '통계를 조회할 시설을 선택해 주세요.';
        if (admin && typeof admin.toast === 'function') admin.toast(warning, true);
        else global.alert(warning);
        statsFacilitySelect.focus();
      }
      return;
    }

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

    if (form.matches('.room-cancel-form')) {
      if (confirmedSubmitForm === form) {
        confirmedSubmitForm = null;
        return;
      }
      event.preventDefault();
      const button = form.querySelector('[data-cancel-room]');
      const reserved = Number(button?.dataset.roomReserved || 0);
      confirmOrSubmit({
        form: form,
        name: button?.dataset.roomTitle || '매칭방',
        title: '이 매칭방을 취소할까요?',
        message: reserved
          ? '참가·신청 인원 ' + reserved + '명의 신청도 함께 취소되며 되돌릴 수 없습니다.'
          : '취소한 매칭방은 이용자 화면에서 신청할 수 없으며 되돌릴 수 없습니다.',
        submitLabel: '매칭방 취소',
        danger: true
      });
      return;
    }

    if (form === roomEditorForm) {
      /* 사진 형식·크기는 서버도 다시 검사한다. 여기서는 큰 파일을 보내기 전에 막는다. */
      const photoProblem = roomPhotoProblem();
      if (photoProblem) {
        event.preventDefault();
        setRoomPhotoError(photoProblem);
        admin?.toast(photoProblem, true);
        roomPhotoInput?.focus();
        return;
      }
      /* 일정이 잠긴 수정은 시간 블록을 보내지 않는다. */
      if (form.dataset.roomLocked === 'true') return;
      const grid = document.getElementById('room-editor-hour-grid');
      if (grid && !grid.querySelector('input[name="hours"]:checked')) {
        event.preventDefault();
        admin?.toast('예약 시간 블록을 하나 이상 선택해 주세요.', true);
        grid.querySelector('input:not(:disabled)')?.focus();
        return;
      }
      /* 한 방은 연속된 시간 하나만 쓴다. 수정 때 기존 블록을 해제하지 않고 새 블록을 더하는 실수도 여기서 막는다. */
      if (grid && selectedRanges(grid).rangeCount > 1) {
        event.preventDefault();
        admin?.toast('예약 시간은 연속된 블록만 선택할 수 있습니다. 선택한 시간을 확인해 주세요.', true);
        grid.querySelector('input[name="hours"]:checked')?.focus();
        return;
      }
      /* 시작 시각이 이미 지났으면 보내지 않는다. 서버도 한국 시간 기준으로 다시 검사한다. */
      const startAt = roomEditorStartAt(grid);
      if (startAt && startAt.getTime() <= Date.now()) {
        event.preventDefault();
        admin?.toast('지난 시간에는 매칭방을 만들 수 없습니다. 날짜와 시간을 확인해 주세요.', true);
        document.getElementById('room-editor-date')?.focus();
      }
      return;
    }

    if (form.id === 'weekly-hours-form') {
      /* 모든 요일 휴무도 정상적인 설정이므로 요일 미선택을 막지 않는다.
         대신 저장할 수 없는 값(같은 시작·종료)만 미리 알린다. */
      const invalidRow = findInvalidWeeklyRow();
      if (invalidRow) {
        event.preventDefault();
        admin?.toast('시작과 종료 시간이 같은 요일이 있습니다. 시간을 다시 선택해 주세요.', true);
        invalidRow.querySelector('select[name^="end-"]')?.focus();
      }
      return;
    }

    if (form.id === 'date-schedule-form') {
      /* 전체 해제는 휴무 저장이라는 뜻이므로 막지 않는다. 확인만 한 번 안내한다. */
      const grid = document.getElementById('date-schedule-hour-grid');
      if (grid && !grid.querySelector('input[name="hours"]:checked')) {
        const label = document.getElementById('date-schedule-selected-label')?.textContent?.trim() || '이 날짜';
        if (!global.confirm(label + '의 예약 가능 시간을 모두 해제하면 휴무로 저장됩니다. 계속할까요?')) {
          event.preventDefault();
        }
      }
      return;
    }

    if (form.id === 'closure-form') {
      const start = document.getElementById('closure-start')?.value;
      const end = document.getElementById('closure-end')?.value;
      /* 서버가 다시 검증하지만, 명백히 같은 시각이면 요청을 보내지 않는다. */
      if (start && end && hourToMinutes(start) === hourToMinutes(end)) {
        event.preventDefault();
        admin?.toast('휴관 시작과 종료 시간이 같습니다. 다른 시간을 선택해 주세요.', true);
        document.getElementById('closure-end')?.focus();
      }
      return;
    }
  });

  /* 일정 카드 내부 조작: 카드가 조각으로 교체되면 새 요소에 다시 바인딩한다.
     날짜 슬롯 요약은 문서 위임으로 묶어 교체와 무관하게 동작하게 한다. */
  document.addEventListener('change', function (event) {
    const target = event.target instanceof Element ? event.target : null;
    if (target?.closest('#date-schedule-hour-grid')) updateDateScheduleSummary();
  });

  function initScheduleCard() {
    document.querySelectorAll('#weekly-hours-form tbody tr').forEach(function (row) {
      const openDay = row.querySelector('input[name="openDays"]');
      const startTime = row.querySelector('select[name^="start-"]');
      const endTime = row.querySelector('select[name^="end-"]');
      const status = row.querySelector('[data-weekly-hours-status]');
      const note = row.querySelector('[data-weekly-hours-note]');
      if (!openDay) return;

      /* 24:00은 그날의 마지막 경계(다음 날 00:00)이므로 분으로 환산해 비교한다. */
      function timeText(select) {
        return select?.value || '';
      }

      function updateRowNote() {
        if (!note) return;
        const start = timeText(startTime);
        const end = timeText(endTime);
        note.classList.remove('is-error', 'is-next-day');
        if (!openDay.checked) {
          note.textContent = '휴무';
          return;
        }
        if (!start || !end) {
          note.textContent = '시간 미선택';
          return;
        }
        const startMinutes = hourToMinutes(start);
        const endMinutes = hourToMinutes(end);
        if (startMinutes === null || endMinutes === null) {
          note.textContent = '시간 확인 필요';
          return;
        }
        if (startMinutes === endMinutes) {
          note.textContent = '시작과 종료가 같아 저장할 수 없습니다';
          note.classList.add('is-error');
          return;
        }
        if (endMinutes < startMinutes) {
          note.textContent = '다음 날 ' + end + '까지';
          note.classList.add('is-next-day');
          return;
        }
        note.textContent = '당일 ' + end + ' 종료';
      }

      function syncWeeklyHoursRow() {
        const isOpen = openDay.checked;
        if (startTime) startTime.disabled = !isOpen;
        if (endTime) endTime.disabled = !isOpen;
        if (status) {
          status.classList.toggle('good', isOpen);
          status.classList.toggle('neutral', !isOpen);
          status.textContent = isOpen ? '예약 가능' : '휴무';
        }
        updateRowNote();
      }

      openDay.addEventListener('change', syncWeeklyHoursRow);
      startTime?.addEventListener('change', updateRowNote);
      endTime?.addEventListener('change', updateRowNote);
      syncWeeklyHoursRow();
    });

    /* 휴관 종료 24:00·야간 표시: 선택한 값을 폼 아래 안내에 그대로 반영한다. */
    const closureStart = document.getElementById('closure-start');
    const closureEnd = document.getElementById('closure-end');
    const closureRangeHint = document.getElementById('closure-range-hint');
    const closureHintDefault = closureRangeHint?.innerHTML || '';

    function updateClosureHint() {
      if (!closureRangeHint) return;
      const start = closureStart?.value || '';
      const end = closureEnd?.value || '';
      if (!start || !end) {
        closureRangeHint.innerHTML = closureHintDefault;
        closureRangeHint.classList.remove('is-error', 'is-next-day');
        return;
      }
      const startMinutes = hourToMinutes(start);
      const endMinutes = hourToMinutes(end);
      closureRangeHint.classList.remove('is-error', 'is-next-day');
      if (startMinutes === endMinutes) {
        closureRangeHint.textContent = '시작과 종료가 같은 시각입니다. 종료는 시작보다 뒤여야 합니다.';
        closureRangeHint.classList.add('is-error');
        return;
      }
      if (endMinutes < startMinutes) {
        closureRangeHint.textContent = start + '부터 다음 날 ' + end + '까지 휴관합니다.';
        closureRangeHint.classList.add('is-next-day');
        return;
      }
      closureRangeHint.textContent = start + '부터 ' + (end === '24:00' ? '같은 날 24:00(다음 날 00:00)' : end) + '까지 휴관합니다.';
    }

    closureStart?.addEventListener('change', updateClosureHint);
    closureEnd?.addEventListener('change', updateClosureHint);

    /* 모집 조건: 종목을 바꾸면 서버에 저장된 그 종목 조건을 다시 조회한다.
       저장하지 않은 입력을 말없이 버리지 않도록, 값이 바뀐 경우에만 확인을 받는다. */
    const conditionSportForm = document.getElementById('condition-sport-form');
    const conditionSportTrigger = document.getElementById('condition-sport');
    const conditionSportState = document.getElementById('condition-sport-state');
    const conditionsForm = document.getElementById('conditions-form');
    const conditionSportValue = document.getElementById('condition-sport-value');
    let conditionSnapshot = '';
    let conditionSportSnapshot = conditionSportTrigger?.value || '';

    function conditionSignature() {
      if (!conditionsForm) return '';
      const capacity = document.getElementById('condition-capacity')?.value || '';
      const note = document.getElementById('condition-note')?.value || '';
      return [capacity, note].join('\u0001');
    }

    conditionSnapshot = conditionSignature();

    conditionsForm?.addEventListener('input', function () {
      const state = document.getElementById('condition-save-state');
      if (state && conditionSignature() !== conditionSnapshot) {
        state.textContent = '저장하지 않은 변경 사항이 있습니다.';
      }
    });

    conditionSportTrigger?.addEventListener('change', function () {
      const nextSport = conditionSportTrigger.value;
      if (!conditionSportForm) return;
      if (nextSport === conditionSportSnapshot) return;
      /* 입력을 편집했거나 저장된 조건과 다른 상태라면, 이동 전에 한 번 확인한다. */
      if (conditionSignature() !== conditionSnapshot
        && !global.confirm('저장하지 않은 모집 조건이 있습니다. 다른 종목의 저장된 조건을 불러올까요? 변경 내용은 사라집니다.')) {
        conditionSportTrigger.value = conditionSportSnapshot;
        return;
      }
      conditionSportForm.submit();
    });

    /* 저장은 화면에서 고른 종목이 아니라, 서버가 내려준 조건의 종목을 기준으로 한다. */
    conditionsForm?.addEventListener('submit', function () {
      const selected = conditionSportTrigger?.value || '';
      if (conditionSportValue) conditionSportValue.value = selected;
      if (conditionSportState) {
        conditionSportState.textContent = '종목을 바꾸면 서버에 저장된 그 종목의 조건을 표시합니다.';
      }
    });

    syncScheduleTabInput();
    updateDateScheduleSummary();
    updateClosureHint();
  }

  /* 시설을 바꿔 다시 조회할 때 보고 있던 탭을 유지한다. 탭은 조각 교체 뒤에도 살아 있도록 위임한다. */
  const scheduleTabInput = document.querySelector('[data-schedule-tab-input]');
  function syncScheduleTabInput() {
    if (!scheduleTabInput) return;
    const selected = document.querySelector('[data-schedule-tab][aria-selected="true"]');
    scheduleTabInput.value = selected?.dataset.scheduleTab || 'schedule-hours';
  }
  document.addEventListener('click', function (event) {
    if (event.target instanceof Element && event.target.closest('[data-schedule-tab]')) syncScheduleTabInput();
  });

  /* 시설 선택: 응답으로 받은 일정 카드 조각만 제자리에서 교체한다.
     JS나 fetch를 쓸 수 없으면 폼이 그대로 제출되어 기존처럼 화면 전체가 다시 그려진다. */
  const scheduleView = document.getElementById('facility-schedule');
  const scheduleFacilityForm = document.querySelector('.schedule-facility-form');
  const scheduleFacilitySelect = document.getElementById('schedule-facility-select');

  function syncScheduleContextInputs() {
    syncScheduleTabInput();
    const date = document.querySelector('#date-schedule-form input[name="date"]')?.value || '';
    const sport = document.getElementById('condition-sport-value')?.value || '';
    const dateInput = scheduleFacilityForm?.querySelector('input[name="date"]');
    const sportInput = scheduleFacilityForm?.querySelector('input[name="sportId"]');
    if (dateInput) dateInput.value = date;
    if (sportInput) sportInput.value = sport;
  }

  async function swapScheduleCard() {
    if (!scheduleFacilityForm || !scheduleFacilitySelect || !scheduleFacilitySelect.value) return false;
    try {
      const response = await fetch(scheduleFacilityForm.action, {
        method: 'POST',
        body: new FormData(scheduleFacilityForm),
        headers: { 'X-Requested-With': 'fetch' },
        credentials: 'same-origin'
      });
      if (!response.ok || response.redirected) { scheduleFacilityForm.submit(); return true; }
      const parsed = new DOMParser().parseFromString(await response.text(), 'text/html');
      const nextCard = parsed.querySelector('.schedule-card');
      const currentCard = scheduleView?.querySelector('.schedule-card');
      if (!nextCard || !currentCard) { scheduleFacilityForm.submit(); return true; }
      currentCard.replaceWith(nextCard);
      initScheduleCard();
      syncScheduleContextInputs();
      const params = new URLSearchParams({ view: 'schedule', facilityId: scheduleFacilitySelect.value });
      const date = scheduleFacilityForm.querySelector('input[name="date"]')?.value || '';
      const sport = scheduleFacilityForm.querySelector('input[name="sportId"]')?.value || '';
      const tab = scheduleFacilityForm.querySelector('[data-schedule-tab-input]')?.value || '';
      if (date) params.set('date', date);
      if (sport) params.set('sportId', sport);
      if (tab) params.set('tab', tab);
      global.history.replaceState(null, '', global.location.pathname + '?' + params.toString() + '#facility-schedule');
    } catch (error) {
      scheduleFacilityForm.submit(); // 네트워크 실패 시 기존 동작(전체 제출)로 폴백
      return true;
    }
  }
  scheduleFacilitySelect?.addEventListener('change', swapScheduleCard);

  /* 달력(날짜·이전/다음 달)은 서버가 만든 GET 링크지만, JS가 있으면 같은 조각 교체로
     처리해 전체 새로고침을 막는다. 링크의 date·sportId를 시설 선택 폼의 히든 값에
     옮겨 담고, 탭은 현재 보고 있는 탭을 유지한다. 시설이 선택되지 않았거나 링크에
     날짜가 없으면(이상한 링크) 브라우저 기본 이동에 맡긴다. */
  document.addEventListener('click', function (event) {
    if (event.defaultPrevented) return;
    const target = event.target instanceof Element ? event.target : null;
    const link = target?.closest('a.date-calendar-day, .calendar-month-nav a');
    if (!link) return;
    if (!scheduleFacilitySelect?.value) return;
    const url = new URL(link.href, global.location.href);
    if (url.pathname !== global.location.pathname) return;
    const date = url.searchParams.get('date') || '';
    if (!date) return;
    event.preventDefault();
    syncScheduleContextInputs();
    const dateInput = scheduleFacilityForm.querySelector('input[name="date"]');
    const sportInput = scheduleFacilityForm.querySelector('input[name="sportId"]');
    if (dateInput) dateInput.value = date;
    if (sportInput) sportInput.value = url.searchParams.get('sportId') || '';
    swapScheduleCard();
  });

  /* 일정 충돌 경고: 서버가 충돌 목록과 함께 돌려보낸 경우에만 자동으로 연다.
     목록이 없으면 정상 화면이며 아무것도 열지 않는다. */
  const scheduleConflictModal = document.getElementById('schedule-conflict-modal');
  let scheduleConflictReturnFocus = null;

  /* 자동으로 열린 경고는 사용자가 누른 버튼이 없다. 닫을 때 body로 초점이 가면
     키보드 사용자가 위치를 잃으므로, 지금 화면에서 가장 자연스러운 조작 요소를 찾아 되돌린다. */
  function scheduleReturnTarget() {
    const scheduleView = document.getElementById('facility-schedule');
    if (!scheduleView) return null;
    const activePanel = Array.from(scheduleView.querySelectorAll('[data-schedule-panel]'))
      .find(function (panel) { return !panel.hidden; }) || scheduleView.querySelector('[data-schedule-panel]');
    if (!activePanel) return null;
    if (activePanel.id === 'schedule-changes') {
      return document.getElementById('closure-date')
        || document.getElementById('closure-reason')
        || document.getElementById('closure-start');
    }
    if (activePanel.id === 'schedule-conditions') {
      return document.getElementById('condition-capacity')
        || document.getElementById('condition-sport');
    }
    const hoursPanel = activePanel;
    const firstRow = hoursPanel.querySelector('[data-weekly-hours-row]');
    const dateForm = document.getElementById('date-schedule-form');
    /* 날짜를 골라 편집 중이었다면 날짜 폼이 더 자연스러운 복귀 지점이다. */
    if (dateForm && dateForm.querySelector('input[name="date"]')?.value) {
      return dateForm.querySelector('input[name="hours"]:not(:disabled)')
        || document.getElementById('date-schedule-note')
        || document.getElementById('date-schedule-selected-label');
    }
    return firstRow?.querySelector('input[name="openDays"]')
      || hoursPanel.querySelector('select[name^="start-"]')
      || scheduleView.querySelector('[data-schedule-tab][aria-selected="true"]');
  }

  function openScheduleConflicts() {
    if (!scheduleConflictModal || scheduleConflictModal.dataset.autoOpen !== 'true') return;
    scheduleConflictReturnFocus = scheduleReturnTarget();
    /* 새로고침 뒤에도 다시 열리지 않도록 한 번 연 뒤 표시를 지운다. */
    delete scheduleConflictModal.dataset.autoOpen;
    openModal(scheduleConflictModal, '#schedule-conflict-close');
  }

  function closeScheduleConflicts() {
    if (!scheduleConflictModal || scheduleConflictModal.hidden) return;
    closeModal(scheduleConflictModal);
    /* 공통 셸은 마지막에 초점이 있던 요소(대개 body)로 되돌린다. 여기서는 화면 안 조작 요소로 되돌린다. */
    const target = scheduleConflictReturnFocus || scheduleReturnTarget();
    scheduleConflictReturnFocus = null;
    target?.focus?.();
  }

  document.addEventListener('click', function (event) {
    const target = event.target instanceof Element ? event.target : null;
    if (!target) return;
    if (target.closest('[data-close-schedule-conflict]')) {
      closeScheduleConflicts();
      return;
    }
    if (target === scheduleConflictModal) closeScheduleConflicts();
  });

  document.addEventListener('keydown', function (event) {
    if (!scheduleConflictModal || scheduleConflictModal.hidden) return;
    if (event.key === 'Escape') {
      event.preventDefault();
      closeScheduleConflicts();
      return;
    }
    admin?.trapFocus(scheduleConflictModal, event);
  });

  openScheduleConflicts();

  /* 시설 삭제 차단 경고: 삭제가 거부되어 차단 목록이 돌아온 경우에만 자동으로 연다.
     목록이 없으면 정상 삭제 흐름이며 아무것도 열지 않는다. */
  const deleteBlockedModal = document.getElementById('facility-delete-blocked-modal');
  let deleteBlockedReturnFocus = null;

  /* 자동으로 열린 경고는 사용자가 누른 버튼이 없다. 닫을 때 시설 목록의 같은 조작 요소로
     초점을 되돌려 키보드 사용자가 위치를 잃지 않게 한다. */
  function deleteBlockedReturnTarget() {
    const assetsView = document.getElementById('facility-assets');
    if (!assetsView) return null;
    return assetsView.querySelector('[data-delete-facility]')
      || assetsView.querySelector('[data-open-facility]')
      || assetsView.querySelector('h1');
  }

  function openDeleteBlocked() {
    if (!deleteBlockedModal || deleteBlockedModal.dataset.autoOpen !== 'true') return;
    deleteBlockedReturnFocus = deleteBlockedReturnTarget();
    /* 새로고침 뒤에도 다시 열리지 않도록 한 번 연 뒤 표시를 지운다. */
    delete deleteBlockedModal.dataset.autoOpen;
    openModal(deleteBlockedModal, '#facility-delete-blocked-close');
  }

  function closeDeleteBlocked() {
    if (!deleteBlockedModal || deleteBlockedModal.hidden) return;
    closeModal(deleteBlockedModal);
    /* 공통 셸은 마지막에 초점이 있던 요소(대개 body)로 되돌린다. 여기서는 화면 안 조작 요소로 되돌린다. */
    const target = deleteBlockedReturnFocus || deleteBlockedReturnTarget();
    deleteBlockedReturnFocus = null;
    target?.focus?.();
  }

  document.addEventListener('click', function (event) {
    const target = event.target instanceof Element ? event.target : null;
    if (!target) return;
    if (target.closest('[data-close-facility-delete-blocked]')) {
      closeDeleteBlocked();
      return;
    }
    if (target === deleteBlockedModal) closeDeleteBlocked();
  });

  document.addEventListener('keydown', function (event) {
    if (!deleteBlockedModal || deleteBlockedModal.hidden) return;
    if (event.key === 'Escape') {
      event.preventDefault();
      closeDeleteBlocked();
      return;
    }
    admin?.trapFocus(deleteBlockedModal, event);
  });

  openDeleteBlocked();

  initScheduleCard();

  updateRoomEditorCapacity();
})(window);
