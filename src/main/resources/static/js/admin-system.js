/* 시스템 관리자 UI: 역할 변경 모달과 서버가 렌더링한 신고 상세 모달만 담당합니다.
   역할 변경·신고 조치·계정 상태 변경은 모두 서버 폼으로 전송됩니다. */
(function (global) {
  'use strict';

  const admin = global.PlayOnAdmin;
  const roleModal = document.getElementById('role-modal');
  const reportModal = document.getElementById('report-modal');
  const reportBody = document.getElementById('report-modal-body');
  const reportTitle = document.getElementById('report-modal-title');
  const reportSubtitle = document.getElementById('report-modal-subtitle');

  function openModal(modal, focusSelector) {
    admin?.openModal(modal, focusSelector);
  }

  function closeModal(modal) {
    admin?.closeModal(modal);
  }

  function closeReport() {
    closeModal(reportModal);
    if (reportBody) reportBody.replaceChildren();
    if (reportTitle) reportTitle.textContent = '신고 상세';
    if (reportSubtitle) reportSubtitle.textContent = '관련 활동 내역을 검토해 주세요.';
  }

  document.addEventListener('click', function (event) {
    const target = event.target instanceof Element ? event.target : null;
    if (!target) return;

    const roleButton = target.closest('[data-user-role]');
    if (roleButton) {
      document.getElementById('role-user-id').value = roleButton.dataset.userId || '';
      document.getElementById('role-user-label').value = roleButton.dataset.userLabel || '';
      document.getElementById('role-select').value = roleButton.dataset.userRoleValue || 'member';
      document.getElementById('role-reason').value = '';
      openModal(roleModal, '#role-select');
      return;
    }

    if (target.closest('[data-close-role]')) {
      closeModal(roleModal);
      return;
    }

    if (target.closest('[data-close-report]')) {
      closeReport();
      return;
    }

    const reportButton = target.closest('[data-open-report]');
    if (reportButton) {
      const id = reportButton.dataset.reportId || '';
      const source = document.getElementById('report-detail-' + id);
      if (!source || !reportBody) {
        admin?.toast('신고 상세 정보를 찾을 수 없습니다.', true);
        return;
      }
      reportBody.replaceChildren(source.content.cloneNode(true));
      if (reportTitle) reportTitle.textContent = source.dataset.reportTitle || '신고 상세';
      if (reportSubtitle) reportSubtitle.textContent = source.dataset.reportSubtitle || '관련 활동 내역을 검토해 주세요.';
      openModal(reportModal, '.report-decision-card select');
    }
  });

  [roleModal, reportModal].forEach(function (modal) {
    modal?.addEventListener('click', function (event) {
      if (event.target !== modal) return;
      if (modal === reportModal) closeReport();
      else closeModal(modal);
    });
  });

  document.addEventListener('keydown', function (event) {
    if (event.key !== 'Escape') return;
    if (reportModal && !reportModal.hidden) {
      closeReport();
      return;
    }
    if (roleModal && !roleModal.hidden) closeModal(roleModal);
  });

  document.addEventListener('keydown', function (event) {
    [roleModal, reportModal].forEach(function (modal) {
      if (modal && !modal.hidden) admin?.trapFocus(modal, event);
    });
  });

  const settingsForm = document.getElementById('system-settings-form');
  function markSettingsDirty() {
    const state = document.getElementById('settings-state');
    if (state) state.textContent = '저장하지 않은 변경 사항이 있습니다.';
  }

  settingsForm?.addEventListener('input', markSettingsDirty);
  settingsForm?.addEventListener('change', markSettingsDirty);
  settingsForm?.addEventListener('click', function (event) {
    if (event.target instanceof Element && event.target.closest('.toggle[role="switch"]')) markSettingsDirty();
  });
})(window);
