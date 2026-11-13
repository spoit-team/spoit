/* 매치 운영 화면의 팀 배정: 배정 화면 조회 API로 보드를 그리고, 수동·자동 배정 확정과 되돌리기를 보낸다.
   버튼 활성화는 서버가 내려준 canAssign·canReset을 따르고, 저장·되돌리기에 성공하면 상단 상태와
   참가자 표까지 맞추기 위해 페이지를 새로 불러온다. 실패하면 서버 message를 알리고 다시 조회한다. */
(function (global) {
  'use strict';

  const root = document.getElementById('ops-assignment');
  if (!root) return;

  const admin = global.PlayOnAdmin;

  /* 실패 안내는 사라지지 않는 모달로 보여 준다. 공통 셸이 없으면 예전처럼 토스트로 알린다. */
  function showError(title, message) {
    if (admin?.showErrorModal) admin.showErrorModal(title, message);
    else admin?.toast(message, true);
  }
  const escapeHTML = global.PlayOnUI.escapeHTML;
  const baseUrl = root.dataset.assignmentUrl;
  const csrfHeader = root.dataset.csrfHeader;
  const csrfToken = root.dataset.csrfToken;

  const stateBadge = root.querySelector('[data-assignment-state]');
  const summary = root.querySelector('[data-assignment-summary]');
  const board = root.querySelector('[data-assignment-board]');
  const actions = root.querySelector('[data-assignment-actions]');
  const difference = root.querySelector('[data-assignment-difference]');
  const columns = Array.from(root.querySelectorAll('.ops-assignment-column'));
  const buttons = Object.fromEntries(Array.from(root.querySelectorAll('[data-action]'))
    .map(button => [button.dataset.action, button]));

  const FALLBACK_MESSAGES = {
    403: '담당 시설의 매칭방이 아니라 팀 배정을 할 수 없습니다.',
    404: '매칭방 정보를 찾을 수 없습니다.'
  };

  let view = null;          // 배정 화면 조회 응답
  let sides = new Map();    // participantId → 'A' | 'B' | null (화면에서 옮긴 배치)
  let preview = null;       // 자동 배정 미리보기 결과(저장 전)
  let busy = false;

  function formatMmr(value) {
    if (value === null || value === undefined) return '—';
    return Number(value).toLocaleString('ko-KR', { maximumFractionDigits: 1 });
  }

  function membersOf(side) {
    return view.participants
      .filter(participant => (sides.get(participant.participantId) ?? null) === side)
      // 참가자 표와 같은 기준: 팀장 먼저, 그다음 레이팅 높은 순, 같으면 참가자 행 ID 순.
      .sort((left, right) => Number(isCaptain(right)) - Number(isCaptain(left))
        || Number(right.entryMmr ?? 0) - Number(left.entryMmr ?? 0)
        || left.participantId - right.participantId);
  }

  function statsOf(members) {
    const sum = members.reduce((total, member) => total + Number(member.entryMmr ?? 0), 0);
    return { sum, average: members.length ? sum / members.length : 0 };
  }

  function editable() {
    return Boolean(view?.canAssign) && !view.assigned && !preview && !busy;
  }

  function isCaptain(participant) {
    if (preview) {
      return participant.participantId === preview.captainAParticipantId
        || participant.participantId === preview.captainBParticipantId;
    }
    return view.assigned && participant.captain;
  }

  function moveButton(participant, side, label) {
    const full = side && membersOf(side).length >= view.teamCapacity;
    return `<button class="ops-assignment-move" type="button" data-move="${side ?? ''}"
      data-participant-id="${participant.participantId}"${full ? ' disabled' : ''}
      aria-label="${escapeHTML(participant.nickname)} ${side ? side + '팀으로 이동' : '미배정으로 이동'}">${label}</button>`;
  }

  function cardHtml(participant, side) {
    const canEdit = editable();
    const captainLabel = preview ? '예상 팀장' : '팀장';
    const moves = canEdit
      ? [side !== 'A' && moveButton(participant, 'A', 'A'),
        side !== 'B' && moveButton(participant, 'B', 'B'),
        side !== null && moveButton(participant, null, '빼기')].filter(Boolean).join('')
      : '';
    return `<li class="ops-assignment-card" data-participant-id="${participant.participantId}"${canEdit ? ' draggable="true"' : ''}>
      <span class="ops-assignment-name">${escapeHTML(participant.nickname)}${participant.clubName
        ? ` <span class="ops-club-chip">${escapeHTML(participant.clubName)}</span>` : ''}${isCaptain(participant)
        ? ` <span class="ops-assignment-captain">${captainLabel}</span>` : ''}</span>
      <span class="ops-assignment-mmr">${formatMmr(participant.entryMmr)}</span>
      ${moves ? `<span class="ops-assignment-moves">${moves}</span>` : ''}
    </li>`;
  }

  function renderColumn(column) {
    const side = column.dataset.side || null;
    const members = membersOf(side);
    const list = column.querySelector('[data-column-list]');
    const count = column.querySelector('[data-column-count]');
    const stats = column.querySelector('[data-column-stats]');
    count.textContent = side ? `${members.length} / ${view.teamCapacity}명` : `${members.length}명`;
    if (stats) {
      const { sum, average } = statsOf(members);
      stats.textContent = members.length ? `합 ${formatMmr(sum)} · 평균 ${formatMmr(average)}` : '레이팅 합 —';
    }
    list.innerHTML = members.length
      ? members.map(member => cardHtml(member, side)).join('')
      : `<li class="ops-assignment-empty">${side ? '배정된 참가자가 없습니다.' : '모든 참가자를 배정했습니다.'}</li>`;
    column.classList.toggle('is-editable', editable());
  }

  function stateLabel() {
    if (preview) return ['자동 배정 미리보기', ''];
    if (view.assigned) {
      return [view.assignmentMethod === 'AUTO' ? '자동 배정 완료' : '수동 배정 완료', 'good'];
    }
    return view.canAssign ? ['배정 전', 'warn'] : ['배정 불가', 'neutral'];
  }

  function summaryText() {
    const required = view.teamCapacity * 2;
    if (preview) return '저장 전 자동 배정 결과입니다. 확정하면 서버가 같은 기준으로 다시 계산해 저장합니다.';
    if (view.assigned) {
      return view.canReset
        ? '팀 배정이 확정되었습니다. 다시 나누려면 되돌린 뒤 배정해 주세요.'
        : '팀 배정이 확정되었습니다. 경기가 시작되어 팀을 바꿀 수 없습니다.';
    }
    if (view.canAssign) return `확정 참가자 ${view.confirmedCount}명을 팀당 ${view.teamCapacity}명씩 나눠 주세요.`;
    if (view.confirmedCount < required) {
      return `확정 참가자가 ${view.confirmedCount} / ${required}명이라 아직 배정할 수 없습니다.`;
    }
    return '경기가 시작되었거나 모집 중인 경기가 아니라 배정할 수 없습니다.';
  }

  function render() {
    const [label, tone] = stateLabel();
    stateBadge.textContent = label;
    stateBadge.className = `status-badge ${tone || ''}`.trim();
    summary.textContent = summaryText();
    board.hidden = false;
    actions.hidden = false;
    columns.forEach(renderColumn);

    const teamA = membersOf('A');
    const teamB = membersOf('B');
    const filled = teamA.length === view.teamCapacity && teamB.length === view.teamCapacity
      && membersOf(null).length === 0;
    difference.textContent = teamA.length || teamB.length
      ? `레이팅 합 차이 ${formatMmr(Math.abs(statsOf(teamA).sum - statsOf(teamB).sum))}`
      : '';

    const editing = !view.assigned && !preview;
    buttons['auto-preview'].hidden = !editing;
    buttons['auto-preview'].disabled = busy || !view.canAssign;
    buttons.clear.hidden = !editing;
    buttons.clear.disabled = busy || !view.canAssign || (teamA.length + teamB.length) === 0;
    buttons['manual-confirm'].hidden = !editing;
    buttons['manual-confirm'].disabled = busy || !view.canAssign || !filled;
    buttons['preview-close'].hidden = !preview;
    buttons['preview-close'].disabled = busy;
    buttons['auto-confirm'].hidden = !preview;
    buttons['auto-confirm'].disabled = busy || !view.canAssign;
    buttons.reset.hidden = !view.assigned;
    buttons.reset.disabled = busy || !view.canReset;
  }

  function renderUnavailable(message) {
    stateBadge.textContent = '조회 실패';
    stateBadge.className = 'status-badge bad';
    summary.textContent = message;
    board.hidden = true;
    actions.hidden = true;
  }

  async function request(method, path, body) {
    const options = {
      method,
      headers: { Accept: 'application/json' },
      credentials: 'same-origin'
    };
    if (method !== 'GET' && csrfHeader && csrfToken) options.headers[csrfHeader] = csrfToken;
    if (body !== undefined) {
      options.headers['Content-Type'] = 'application/json';
      options.body = JSON.stringify(body);
    }
    try {
      const response = await fetch(baseUrl + path, options);
      const data = await response.json().catch(() => ({}));
      const message = data.message || FALLBACK_MESSAGES[response.status] || '요청을 처리하지 못했습니다. 잠시 후 다시 시도해 주세요.';
      return { ok: response.ok && !response.redirected, data, message };
    } catch (error) {
      return { ok: false, data: {}, message: '서버에 연결하지 못했습니다. 네트워크 상태를 확인해 주세요.' };
    }
  }

  async function load() {
    const result = await request('GET', '/assignment');
    busy = false;
    preview = null;
    if (!result.ok || !Array.isArray(result.data.participants)) {
      renderUnavailable(result.message);
      return;
    }
    view = result.data;
    sides = new Map(view.participants.map(participant => [participant.participantId, participant.side ?? null]));
    render();
  }

  /* 저장·되돌리기 결과 처리: 성공은 새로 불러온 화면에서 알리고, 실패는 알림 후 배정 화면만 다시 조회한다. */
  async function submit(method, path, body) {
    busy = true;
    render();
    const result = await request(method, path, body);
    if (result.ok) {
      if (admin?.reloadWithToast) admin.reloadWithToast(result.message, false);
      else global.location.reload();
      return;
    }
    showError('팀 배정을 처리할 수 없습니다', result.message);
    await load();
  }

  async function showPreview() {
    busy = true;
    render();
    const result = await request('GET', '/auto-assignment');
    if (!result.ok || !result.data.assignment) {
      showError('자동 배정을 불러올 수 없습니다', result.message);
      await load();
      return;
    }
    const assignment = result.data.assignment;
    sides = new Map(view.participants.map(participant => [participant.participantId, null]));
    assignment.teamAParticipantIds.forEach(id => sides.set(id, 'A'));
    assignment.teamBParticipantIds.forEach(id => sides.set(id, 'B'));
    preview = assignment;
    busy = false;
    render();
  }

  function move(participantId, side) {
    if (!editable() || !sides.has(participantId)) return;
    if (side && sides.get(participantId) !== side && membersOf(side).length >= view.teamCapacity) {
      showError('팀원을 옮길 수 없습니다', `${side}팀은 정원 ${view.teamCapacity}명이 모두 찼습니다.`);
      return;
    }
    sides.set(participantId, side);
    render();
  }

  const handlers = {
    'auto-preview': showPreview,
    clear() {
      sides = new Map(view.participants.map(participant => [participant.participantId, null]));
      render();
    },
    'manual-confirm'() {
      submit('POST', '/assignment', {
        teamA: membersOf('A').map(member => member.participantId),
        teamB: membersOf('B').map(member => member.participantId)
      });
    },
    'preview-close'() {
      preview = null;
      sides = new Map(view.participants.map(participant => [participant.participantId, participant.side ?? null]));
      render();
    },
    'auto-confirm'() {
      submit('POST', '/auto-assignment');
    },
    reset() {
      if (!global.confirm('팀 배정을 되돌릴까요? 참가자의 팀과 팀장이 지워지고 경기가 모집 중으로 돌아갑니다.')) return;
      submit('DELETE', '/assignment');
    }
  };

  root.addEventListener('click', function (event) {
    const moveTarget = event.target.closest('[data-move]');
    if (moveTarget) {
      move(Number(moveTarget.dataset.participantId), moveTarget.dataset.move || null);
      return;
    }
    const action = event.target.closest('[data-action]')?.dataset.action;
    if (action && !busy && handlers[action]) handlers[action]();
  });

  /* 드래그 이동: 버튼 이동과 같은 move()를 거치므로 정원 검사도 같다. */
  root.addEventListener('dragstart', function (event) {
    const card = event.target.closest?.('.ops-assignment-card[draggable="true"]');
    if (!card) return;
    event.dataTransfer.setData('text/plain', card.dataset.participantId);
    event.dataTransfer.effectAllowed = 'move';
    card.classList.add('is-dragging');
  });
  root.addEventListener('dragend', function (event) {
    event.target.closest?.('.ops-assignment-card')?.classList.remove('is-dragging');
    columns.forEach(column => column.classList.remove('is-drop-target'));
  });
  columns.forEach(function (column) {
    column.addEventListener('dragover', function (event) {
      if (!editable()) return;
      event.preventDefault();
      event.dataTransfer.dropEffect = 'move';
      column.classList.add('is-drop-target');
    });
    column.addEventListener('dragleave', function (event) {
      if (!column.contains(event.relatedTarget)) column.classList.remove('is-drop-target');
    });
    column.addEventListener('drop', function (event) {
      event.preventDefault();
      column.classList.remove('is-drop-target');
      const participantId = Number(event.dataTransfer.getData('text/plain'));
      if (participantId) move(participantId, column.dataset.side || null);
    });
  });

  load();
})(window);
