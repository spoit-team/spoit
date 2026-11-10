/* 마이페이지: 경기 이력 펼치기, 섹션 메뉴 표시, 팀 신청 취소(팀원 선택)·결제 신청 취소(환불 안내) 확인창을 처리합니다. 데이터는 서버가 렌더링합니다. */
(function () {
  'use strict';

  function setCurrentNav(hash) {
    const currentHash = hash || '#profile';
    document.querySelectorAll('.mypage-nav-link').forEach(function (link) {
      if (link.hash === currentHash) link.setAttribute('aria-current', 'location');
      else link.removeAttribute('aria-current');
    });
  }

  document.addEventListener('click', function (event) {
    const button = event.target.closest('[data-match-history-toggle]');
    if (!button) return;

    const item = button.closest('.mypage-match-history-item');
    const detail = item?.querySelector('.mypage-match-history-detail');
    if (!item || !detail) return;

    const open = item.classList.toggle('is-open');
    detail.hidden = !open;
    button.setAttribute('aria-expanded', String(open));
  });

  document.querySelectorAll('.mypage-nav-link').forEach(function (link) {
    if (link.hash) link.addEventListener('click', function () { setCurrentNav(link.hash); });
  });

  window.addEventListener('hashchange', function () {
    setCurrentNav(window.location.hash);
  });

  setCurrentNav(window.location.hash);

  /*
   * RES-04 팀 신청 취소: 대표가 취소할 팀원을 체크박스로 고릅니다.
   * 전원을 고르면 팀 전체 취소로, 대표가 빠지고 남는 팀원이 있으면 새 대표를 골라야 제출할 수 있습니다.
   * 스크립트가 없으면 폼이 그대로 제출되어 팀 전체가 취소됩니다.
   */
  const teamCancelDialog = document.getElementById('team-cancel-dialog');
  const teamCancelMatch = document.getElementById('team-cancel-match');
  const teamCancelMessage = document.getElementById('team-cancel-message');
  const teamCancelConfirm = document.getElementById('team-cancel-confirm');
  const teamCancelAll = document.getElementById('team-cancel-all');
  const teamCancelMembers = document.getElementById('team-cancel-members');
  const teamCancelLeader = document.getElementById('team-cancel-leader');
  const teamCancelLeaders = document.getElementById('team-cancel-leaders');
  let pendingTeamCancelForm = null;
  let teamMembers = [];
  let refundUnit = 0;

  function won(amount) {
    return Number(amount).toLocaleString('ko-KR') + '원';
  }

  /* 결제한 팀 신청이면 취소 인원만큼 환불 금액을 덧붙입니다. */
  function refundNote(count) {
    return refundUnit > 0 && count > 0 ? ' ' + won(refundUnit * count) + '이 결제 수단으로 환불됩니다.' : '';
  }

  function readTeamMembers(form) {
    return Array.from(form.querySelectorAll('[data-team-members] > li')).map(function (item) {
      return {
        applicationId: item.dataset.applicationId,
        nickname: item.dataset.nickname,
        state: item.dataset.state,
        leader: item.dataset.leader === 'true'
      };
    });
  }

  function optionLabel(input, name, meta) {
    const label = document.createElement('label');
    label.className = 'mypage-team-cancel-option';
    const nameText = document.createElement('span');
    nameText.className = 'mypage-team-cancel-name';
    nameText.textContent = name;
    label.append(input, nameText);
    if (meta) {
      const metaText = document.createElement('span');
      metaText.className = 'mypage-team-cancel-meta';
      metaText.textContent = meta;
      label.append(metaText);
    }
    return label;
  }

  function renderTeamMembers() {
    teamCancelMembers.replaceChildren();
    teamMembers.forEach(function (member) {
      const checkbox = document.createElement('input');
      checkbox.type = 'checkbox';
      checkbox.value = member.applicationId;
      checkbox.dataset.teamCancelMember = '';
      const name = member.nickname + (member.leader ? ' (대표 · 나)' : '');
      teamCancelMembers.append(optionLabel(checkbox, name, member.state));
    });
    teamCancelAll.checked = false;
    teamCancelAll.indeterminate = false;
    updateTeamCancel();
  }

  function checkedMemberIds() {
    return Array.from(teamCancelMembers.querySelectorAll('[data-team-cancel-member]:checked'))
      .map(function (input) { return input.value; });
  }

  function selectedLeaderId() {
    return teamCancelLeaders.querySelector('input[name="team-cancel-new-leader"]:checked')?.value || null;
  }

  /* 새 대표 후보는 체크하지 않은 팀원입니다. 후보가 바뀌어도 고른 사람이 남아 있으면 그대로 둡니다. */
  function renderLeaderOptions(remaining) {
    const previous = selectedLeaderId();
    teamCancelLeaders.replaceChildren();
    remaining.forEach(function (member) {
      const radio = document.createElement('input');
      radio.type = 'radio';
      radio.name = 'team-cancel-new-leader';
      radio.value = member.applicationId;
      radio.checked = member.applicationId === previous;
      teamCancelLeaders.append(optionLabel(radio, member.nickname, null));
    });
  }

  /* refreshLeaders: 팀원 체크가 바뀌었을 때만 후보를 다시 그립니다 (라디오를 고를 때 다시 그리면 포커스를 잃습니다). */
  function updateTeamCancel(refreshLeaders) {
    const checked = checkedMemberIds();
    const total = teamMembers.length;
    const remaining = teamMembers.filter(function (member) { return !checked.includes(member.applicationId); });
    const leaderLeaves = teamMembers.some(function (member) { return member.leader && checked.includes(member.applicationId); });
    const needsLeader = leaderLeaves && remaining.length > 0;

    teamCancelAll.checked = total > 0 && checked.length === total;
    teamCancelAll.indeterminate = checked.length > 0 && checked.length < total;

    teamCancelLeader.hidden = !needsLeader;
    if (!needsLeader) teamCancelLeaders.replaceChildren();
    else if (refreshLeaders !== false) renderLeaderOptions(remaining);

    let message;
    let label;
    let ready;
    if (checked.length === 0) {
      message = '취소할 팀원을 선택해 주세요.';
      label = '팀원 취소';
      ready = false;
    } else if (remaining.length === 0) {
      message = '팀원 ' + total + '명의 신청이 모두 함께 취소됩니다.' + refundNote(total);
      label = '팀 전체 취소';
      ready = true;
    } else {
      message = '선택한 ' + checked.length + '명의 신청만 취소되고, 남은 ' + remaining.length + '명은 그대로 참가합니다.' + refundNote(checked.length);
      label = checked.length + '명 취소';
      ready = !needsLeader || selectedLeaderId() !== null;
      if (!ready) message += ' 새 대표를 선택해 주세요.';
    }
    teamCancelMessage.textContent = message;
    teamCancelConfirm.textContent = label;
    teamCancelConfirm.disabled = !ready;
  }

  document.querySelectorAll('form[data-team-cancel]').forEach(function (form) {
    form.addEventListener('submit', function (event) {
      if (!teamCancelDialog || typeof teamCancelDialog.showModal !== 'function') return;
      const members = readTeamMembers(form);
      if (!members.length) return;
      event.preventDefault();
      pendingTeamCancelForm = form;
      teamMembers = members;
      refundUnit = Number(form.dataset.refundUnit || 0);
      if (teamCancelMatch) {
        teamCancelMatch.textContent = [form.dataset.teamCancelTitle, form.dataset.teamCancelMeta].filter(Boolean).join(' · ');
      }
      renderTeamMembers();
      teamCancelDialog.showModal();
      teamCancelDialog.querySelector('.mypage-confirm-back')?.focus({ preventScroll: true });
    });
  });

  teamCancelAll?.addEventListener('change', function () {
    teamCancelMembers.querySelectorAll('[data-team-cancel-member]').forEach(function (input) {
      input.checked = teamCancelAll.checked;
    });
    updateTeamCancel();
  });

  teamCancelMembers?.addEventListener('change', function () { updateTeamCancel(true); });
  teamCancelLeaders?.addEventListener('change', function () { updateTeamCancel(false); });

  teamCancelDialog?.querySelectorAll('[data-close-team-cancel]').forEach(function (button) {
    button.addEventListener('click', function () { teamCancelDialog.close(); });
  });

  teamCancelDialog?.addEventListener('click', function (event) {
    if (event.target === teamCancelDialog) teamCancelDialog.close();
  });

  teamCancelDialog?.addEventListener('close', function () {
    pendingTeamCancelForm = null;
  });

  function appendHidden(form, name, value) {
    const input = document.createElement('input');
    input.type = 'hidden';
    input.name = name;
    input.value = value;
    input.dataset.teamCancelField = '';
    form.append(input);
  }

  teamCancelConfirm?.addEventListener('click', function () {
    const form = pendingTeamCancelForm;
    if (!form || teamCancelConfirm.disabled) return;
    const checked = checkedMemberIds();
    if (!checked.length) return;
    form.querySelectorAll('[data-team-cancel-field]').forEach(function (input) { input.remove(); });
    // 전원을 고르면 팀원 목록 없이 보내 팀 전체 취소로 처리합니다.
    if (checked.length < teamMembers.length) {
      checked.forEach(function (id) { appendHidden(form, 'memberApplicationIds', id); });
      const leaderId = teamCancelLeader.hidden ? null : selectedLeaderId();
      if (leaderId) appendHidden(form, 'newLeaderApplicationId', leaderId);
    }
    teamCancelConfirm.disabled = true;
    // form.submit()은 submit 이벤트를 다시 일으키지 않아 확인창이 반복되지 않는다.
    form.submit();
  });

  /* 결제한 개인 신청 취소: 환불 금액을 알리고 확인하면 폼을 제출합니다. */
  const refundCancelDialog = document.getElementById('refund-cancel-dialog');
  const refundCancelMatch = document.getElementById('refund-cancel-match');
  const refundCancelMessage = document.getElementById('refund-cancel-message');
  const refundCancelConfirm = document.getElementById('refund-cancel-confirm');
  let pendingRefundCancelForm = null;

  document.querySelectorAll('form[data-refund-cancel]').forEach(function (form) {
    form.addEventListener('submit', function (event) {
      if (!refundCancelDialog || typeof refundCancelDialog.showModal !== 'function') return;
      event.preventDefault();
      pendingRefundCancelForm = form;
      if (refundCancelMatch) {
        refundCancelMatch.textContent = [form.dataset.refundCancelTitle, form.dataset.refundCancelMeta].filter(Boolean).join(' · ');
      }
      if (refundCancelMessage) {
        refundCancelMessage.textContent = '신청을 취소할까요?' + (Number(form.dataset.refundUnit || 0) > 0
          ? ' ' + won(form.dataset.refundUnit) + '이 결제 수단으로 환불됩니다.' : '');
      }
      if (refundCancelConfirm) refundCancelConfirm.disabled = false;
      refundCancelDialog.showModal();
      refundCancelDialog.querySelector('.mypage-confirm-back')?.focus({ preventScroll: true });
    });
  });

  refundCancelDialog?.querySelectorAll('[data-close-refund-cancel]').forEach(function (button) {
    button.addEventListener('click', function () { refundCancelDialog.close(); });
  });

  refundCancelDialog?.addEventListener('click', function (event) {
    if (event.target === refundCancelDialog) refundCancelDialog.close();
  });

  refundCancelDialog?.addEventListener('close', function () {
    pendingRefundCancelForm = null;
  });

  refundCancelConfirm?.addEventListener('click', function () {
    const form = pendingRefundCancelForm;
    if (!form || refundCancelConfirm.disabled) return;
    refundCancelConfirm.disabled = true;
    // form.submit()은 submit 이벤트를 다시 일으키지 않아 확인창이 반복되지 않는다.
    form.submit();
  });

  /* 알림 창은 통합 알림 스크립트(notification-center.js)가 공통 조각으로 처리합니다. */
})();
