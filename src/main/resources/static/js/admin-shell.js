/* 관리자 공통 UI: 사이드바, 화면 전환, 탭, 알림 모달, 확인 폼만 담당합니다.
   데이터와 저장 결과는 모두 서버가 제공하며, 이 스크립트는 브라우저 저장소를 사용하지 않습니다. */
(function (global) {
  'use strict';

  const layout = document.querySelector('.admin-layout');
  const sidebarToggle = document.querySelector('[data-sidebar-toggle]');
  const sidebarBreakpoint = global.matchMedia('(max-width: 840px)');
  const views = Array.from(document.querySelectorAll('.admin-view'));
  const navTargets = Array.from(document.querySelectorAll('[data-view-target]'));
  const breadcrumb = document.getElementById('breadcrumb-current');
  const toast = document.getElementById('admin-toast');
  const notificationModal = document.getElementById('admin-notification-modal');
  let toastTimer = 0;
  let modalOpener = null;

  function toastMessage(message, isError) {
    if (!toast || !message) return;
    toast.textContent = message;
    toast.dataset.kind = isError ? 'error' : 'success';
    toast.hidden = false;
    global.requestAnimationFrame(function () { toast.classList.add('show'); });
    global.clearTimeout(toastTimer);
    toastTimer = global.setTimeout(function () {
      toast.classList.remove('show');
      global.setTimeout(function () { toast.hidden = true; }, 200);
    }, 3600);
  }

  function setSidebarExpanded(expanded) {
    if (!layout || !sidebarToggle) return;
    layout.classList.toggle('is-sidebar-expanded', expanded);
    layout.classList.toggle('is-sidebar-collapsed', !expanded);
    sidebarToggle.setAttribute('aria-expanded', String(expanded));
    sidebarToggle.setAttribute('aria-label', expanded ? '메뉴 접기' : '메뉴 펼치기');
    sidebarToggle.setAttribute('title', expanded ? '메뉴 접기' : '메뉴 펼치기');
  }

  function syncSidebarForViewport() {
    setSidebarExpanded(!sidebarBreakpoint.matches);
  }

  sidebarToggle?.addEventListener('click', function () {
    setSidebarExpanded(!layout?.classList.contains('is-sidebar-expanded'));
  });

  if (typeof sidebarBreakpoint.addEventListener === 'function') {
    sidebarBreakpoint.addEventListener('change', syncSidebarForViewport);
  }

  function viewFromLocation() {
    const role = document.body.dataset.adminRole;
    const explicit = document.body.dataset.view;
    const hash = global.location.hash.replace('#', '');
    if (hash && views.some(function (view) { return view.id === hash; })) return hash;

    if (explicit && views.some(function (view) { return view.id === explicit; })) return explicit;
    const roleView = role && explicit ? role + '-' + explicit : '';
    if (roleView && views.some(function (view) { return view.id === roleView; })) return roleView;
    return views[0]?.id || '';
  }

  function showView(targetId, updateLocation) {
    const target = views.find(function (view) { return view.id === targetId; });
    if (!target) return;
    views.forEach(function (view) { view.hidden = view !== target; });
    navTargets.forEach(function (item) {
      const active = item.dataset.viewTarget === targetId;
      if (active) item.setAttribute('aria-current', 'page');
      else item.removeAttribute('aria-current');
    });
    const title = target.dataset.viewTitle;
    if (breadcrumb && title) breadcrumb.textContent = title;
    if (updateLocation !== false) {
      global.history.replaceState(null, '', '#' + targetId);
      document.title = (title ? title + ' · ' : '') + 'SPO:IT 관리자';
    }
    target.setAttribute('tabindex', '-1');
    target.focus({ preventScroll: true });
  }

  navTargets.forEach(function (item) {
    item.addEventListener('click', function (event) {
      const targetId = item.dataset.viewTarget;
      if (!targetId) return;
      const anchor = item.closest('a');
      if (anchor) {
        const url = new URL(anchor.href, global.location.href);
        if (url.pathname !== global.location.pathname) return;
        event.preventDefault();
      }
      showView(targetId, true);
    });
  });

  global.addEventListener('hashchange', function () { showView(viewFromLocation(), false); });

  /* 탭: 예약 설정, 시설 콘텐츠, 시스템 CMS */
  const tabGroups = [
    { attribute: 'scheduleTab', panelSelector: '[data-schedule-panel]' },
    { attribute: 'contentTab', panelSelector: '.admin-panel' },
    { attribute: 'cmsTab', panelSelector: '.admin-panel' }
  ];

  tabGroups.forEach(function (group) {
    const tabs = Array.from(document.querySelectorAll('[data-' + group.attribute.replace(/[A-Z]/g, function (m) { return '-' + m.toLowerCase(); }) + ']'));
    if (!tabs.length) return;
    const panelRoot = tabs[0].closest('.admin-view') || document;
    tabs.forEach(function (tab, index) {
      tab.addEventListener('click', function () {
        const panelId = tab.dataset[group.attribute];
        tabs.forEach(function (item) {
          const active = item === tab;
          item.setAttribute('aria-selected', String(active));
          item.tabIndex = active ? 0 : -1;
        });
        panelRoot.querySelectorAll(group.panelSelector).forEach(function (panel) {
          panel.hidden = panel.id !== panelId;
        });
      });
      tab.addEventListener('keydown', function (event) {
        let next = null;
        if (event.key === 'ArrowRight') next = tabs[(index + 1) % tabs.length];
        else if (event.key === 'ArrowLeft') next = tabs[(index - 1 + tabs.length) % tabs.length];
        else if (event.key === 'Home') next = tabs[0];
        else if (event.key === 'End') next = tabs[tabs.length - 1];
        if (!next) return;
        event.preventDefault();
        next.focus();
        next.click();
      });
    });
  });

  /* 스위치: 화면 내부 상태만 변경합니다. 서버 저장은 폼 제출로 처리합니다. */
  document.addEventListener('click', function (event) {
    const toggle = event.target instanceof Element ? event.target.closest('.toggle[role="switch"]') : null;
    if (!toggle || toggle.disabled) return;
    const next = toggle.getAttribute('aria-checked') !== 'true';
    toggle.setAttribute('aria-checked', String(next));
    const hidden = toggle.parentElement?.querySelector('[data-setting-input]');
    if (hidden) hidden.value = String(next);
  });

  /* 알림 모달 */
  function openNotificationModal() {
    if (!notificationModal) return;
    modalOpener = document.activeElement;
    notificationModal.hidden = false;
    document.body.classList.add('has-admin-modal');
    document.querySelectorAll('[data-open-admin-notifications]').forEach(function (trigger) {
      trigger.setAttribute('aria-expanded', 'true');
    });
    notificationModal.querySelector('.modal-close')?.focus();
  }

  function closeNotificationModal() {
    if (!notificationModal || notificationModal.hidden) return;
    notificationModal.hidden = true;
    document.body.classList.remove('has-admin-modal');
    document.querySelectorAll('[data-open-admin-notifications]').forEach(function (trigger) {
      trigger.setAttribute('aria-expanded', 'false');
    });
    modalOpener?.focus?.();
    modalOpener = null;
  }

  document.addEventListener('click', function (event) {
    const target = event.target instanceof Element ? event.target : null;
    if (!target) return;
    if (target.closest('[data-open-admin-notifications]')) {
      event.preventDefault();
      openNotificationModal();
      return;
    }
    if (target.closest('[data-close-admin-notifications]')) {
      closeNotificationModal();
      return;
    }
    if (notificationModal && target === notificationModal) closeNotificationModal();
  });

  document.addEventListener('keydown', function (event) {
    if (!notificationModal || notificationModal.hidden) return;
    if (event.key === 'Escape') {
      closeNotificationModal();
      return;
    }
    if (event.key !== 'Tab') return;
    const focusable = Array.from(notificationModal.querySelectorAll('button:not(:disabled), [href], input:not(:disabled), select:not(:disabled), textarea:not(:disabled)'));
    if (!focusable.length) return;
    const first = focusable[0];
    const last = focusable[focusable.length - 1];
    if (event.shiftKey && document.activeElement === first) {
      event.preventDefault();
      last.focus();
    } else if (!event.shiftKey && document.activeElement === last) {
      event.preventDefault();
      first.focus();
    }
  });

  /* 파괴적 작업 확인: 서버 폼 제출 전에 한 번 더 확인합니다. */
  document.addEventListener('submit', function (event) {
    const form = event.target;
    if (!(form instanceof HTMLFormElement)) return;
    const message = form.dataset.confirm;
    if (!message) return;
    if (!global.confirm(message)) event.preventDefault();
  });

  global.PlayOnAdmin = {
    toast: toastMessage,
    openModal: function (modal, focusSelector) {
      if (!modal) return;
      modalOpener = document.activeElement;
      modal.hidden = false;
      document.body.classList.add('has-admin-modal');
      (focusSelector ? modal.querySelector(focusSelector) : null)?.focus();
    },
    closeModal: function (modal) {
      if (!modal) return;
      modal.hidden = true;
      document.body.classList.remove('has-admin-modal');
      modalOpener?.focus?.();
      modalOpener = null;
    },
    trapFocus: function (modal, event) {
      if (!modal || modal.hidden || event.key !== 'Tab') return;
      const focusable = Array.from(modal.querySelectorAll('button:not(:disabled), [href], input:not(:disabled), select:not(:disabled), textarea:not(:disabled)'));
      if (!focusable.length) return;
      const first = focusable[0];
      const last = focusable[focusable.length - 1];
      if (event.shiftKey && document.activeElement === first) {
        event.preventDefault();
        last.focus();
      } else if (!event.shiftKey && document.activeElement === last) {
        event.preventDefault();
        first.focus();
      }
    }
  };

  syncSidebarForViewport();
  showView(viewFromLocation(), Boolean(document.body.dataset.view));
})();
