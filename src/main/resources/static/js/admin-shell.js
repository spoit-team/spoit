/* 관리자 공통 UI: 사이드바, 화면 전환, 탭, 알림 모달, 확인 폼만 담당합니다.
   데이터와 저장 결과는 모두 서버가 제공합니다. 브라우저 저장소는 새로고침 뒤 보여 줄 토스트 문구를
   한 번 넘길 때만 sessionStorage를 쓰고, 꺼내는 즉시 지웁니다. */
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

  function showView(targetId, updateLocation, skipFocus) {
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
    if (skipFocus) return;
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

  /* 탭: 예약 설정, 시설 콘텐츠, 시스템 CMS.
     일정 카드는 시설 선택 시 조각으로 교체되므로 요소별 바인딩 대신 document 위임으로 처리합니다. */
  const tabGroups = [
    { attr: 'scheduleTab', panelSelector: '[data-schedule-panel]' },
    { attr: 'contentTab', panelSelector: '.admin-panel' },
    { attr: 'cmsTab', panelSelector: '.admin-panel' }
  ];

  function tabSelector(attr) {
    return '[data-' + attr.replace(/[A-Z]/g, function (m) { return '-' + m.toLowerCase(); }) + ']';
  }

  function tabGroupFor(tab) {
    return tabGroups.find(function (group) { return tab.dataset[group.attr] !== undefined; }) || null;
  }

  /* 탭 배열과 패널 범위는 누를 때마다 다시 찾는다. 조각이 교체되면 예전 요소는 문서에 없다. */
  function activateTab(tab) {
    const group = tabGroupFor(tab);
    if (!group) return;
    const panelRoot = tab.closest('.admin-view') || document;
    const tabs = Array.from(panelRoot.querySelectorAll(tabSelector(group.attr)));
    const panelId = tab.dataset[group.attr];
    tabs.forEach(function (item) {
      const active = item === tab;
      item.setAttribute('aria-selected', String(active));
      item.tabIndex = active ? 0 : -1;
    });
    panelRoot.querySelectorAll(group.panelSelector).forEach(function (panel) {
      panel.hidden = panel.id !== panelId;
    });
  }

  function tabFromEvent(event) {
    return event.target instanceof Element
      ? event.target.closest('[data-schedule-tab],[data-content-tab],[data-cms-tab]')
      : null;
  }

  document.addEventListener('click', function (event) {
    const tab = tabFromEvent(event);
    if (tab) activateTab(tab);
  });

  document.addEventListener('keydown', function (event) {
    const tab = tabFromEvent(event);
    if (!tab) return;
    const group = tabGroupFor(tab);
    if (!group) return;
    const panelRoot = tab.closest('.admin-view') || document;
    const tabs = Array.from(panelRoot.querySelectorAll(tabSelector(group.attr)));
    const index = tabs.indexOf(tab);
    if (index < 0 || !tabs.length) return;
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

  /* 모달 내부 포커스 트랩: Escape는 호출부에서 먼저 처리하고, Tab/Shift+Tab만 순환시킵니다. */
  function trapModalFocus(modal, event) {
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

  document.addEventListener('keydown', function (event) {
    if (!notificationModal || notificationModal.hidden) return;
    if (event.key === 'Escape') {
      closeNotificationModal();
      return;
    }
    trapModalFocus(notificationModal, event);
  });

  /* 파괴적 작업 확인: 서버 폼 제출 전에 한 번 더 확인합니다. */
  document.addEventListener('submit', function (event) {
    const form = event.target;
    if (!(form instanceof HTMLFormElement)) return;
    const message = form.dataset.confirm;
    if (!message) return;
    if (!global.confirm(message)) event.preventDefault();
  });

  /* 새로고침 후 토스트: 저장 뒤 화면 전체를 서버 값으로 다시 그려야 할 때 결과 문구가 새로고침에 묻히지 않도록
     sessionStorage에 한 번만 맡기고, 다시 열린 페이지가 꺼내 보여 준 뒤 바로 지운다. 데이터는 저장하지 않는다.
     저장소를 쓸 수 없으면 예전처럼 토스트를 띄우고 잠시 뒤 새로고침한다. */
  const PENDING_TOAST_KEY = 'playon-admin-pending-toast';
  const FALLBACK_RELOAD_DELAY_MS = 700;

  function reloadWithToast(message, isError) {
    try {
      global.sessionStorage.setItem(PENDING_TOAST_KEY, JSON.stringify({ message, isError: Boolean(isError) }));
    } catch (error) {
      toastMessage(message, isError);
      global.setTimeout(function () { global.location.reload(); }, FALLBACK_RELOAD_DELAY_MS);
      return;
    }
    global.location.reload();
  }

  function showPendingToast() {
    let pending = null;
    try {
      const raw = global.sessionStorage.getItem(PENDING_TOAST_KEY);
      global.sessionStorage.removeItem(PENDING_TOAST_KEY);
      pending = raw ? JSON.parse(raw) : null;
    } catch (error) {
      pending = null;
    }
    if (pending && typeof pending.message === 'string') toastMessage(pending.message, pending.isError);
  }

  /* 다른 모달이 아직 열려 있으면 뒤 페이지 스크롤 잠금을 유지한다. */
  function syncModalScrollLock() {
    const anyOpen = Array.from(document.querySelectorAll('.modal-backdrop'))
      .some(function (backdrop) { return !backdrop.hidden; });
    document.body.classList.toggle('has-admin-modal', anyOpen);
  }

  /* 실패 안내 모달: 토스트처럼 사라지지 않고, 사용자가 [확인]을 눌러야 닫힌다.
     화면마다 마크업을 두지 않도록 처음 쓸 때 만들고, 시설 관리자 화면의 확인 모달과 같은 모양을 쓴다. */
  let errorModal = null;
  let errorReturnFocus = null;

  function ensureErrorModal() {
    if (errorModal) return errorModal;
    errorModal = document.createElement('div');
    errorModal.className = 'modal-backdrop';
    errorModal.id = 'admin-error-modal';
    errorModal.hidden = true;
    errorModal.innerHTML = '<section class="admin-modal" role="alertdialog" aria-modal="true"'
      + ' aria-labelledby="admin-error-title" aria-describedby="admin-error-message">'
      + '<div class="modal-header"><div><h2 id="admin-error-title"></h2></div>'
      + '<button class="modal-close" type="button" data-close-admin-error aria-label="닫기">×</button></div>'
      + '<div class="modal-body"><p class="facility-confirm-message" id="admin-error-message"></p></div>'
      + '<div class="modal-footer"><button class="admin-button primary" type="button" id="admin-error-confirm"'
      + ' data-close-admin-error>확인</button></div></section>';
    document.body.appendChild(errorModal);
    errorModal.addEventListener('click', function (event) {
      const target = event.target instanceof Element ? event.target : null;
      if (target === errorModal || target?.closest('[data-close-admin-error]')) closeErrorModal();
    });
    return errorModal;
  }

  function showErrorModal(title, message, returnFocus) {
    if (!message) return;
    const modal = ensureErrorModal();
    modal.querySelector('#admin-error-title').textContent = title || '요청을 처리할 수 없습니다';
    modal.querySelector('#admin-error-message').textContent = message;
    errorReturnFocus = returnFocus || document.activeElement;
    modal.hidden = false;
    document.body.classList.add('has-admin-modal');
    modal.querySelector('#admin-error-confirm')?.focus();
  }

  function closeErrorModal() {
    if (!errorModal || errorModal.hidden) return;
    errorModal.hidden = true;
    syncModalScrollLock();
    const target = errorReturnFocus;
    errorReturnFocus = null;
    if (target && target.isConnected && !target.closest('[hidden]')) target.focus?.();
  }

  /* 안내 모달은 항상 맨 위에 있으므로, 다른 화면 스크립트보다 먼저(capture) Escape·Tab을 처리한다. */
  document.addEventListener('keydown', function (event) {
    if (!errorModal || errorModal.hidden) return;
    if (event.key === 'Escape') {
      event.preventDefault();
      event.stopImmediatePropagation();
      closeErrorModal();
      return;
    }
    if (event.key === 'Tab') {
      event.stopImmediatePropagation();
      trapModalFocus(errorModal, event);
    }
  }, true);

  global.PlayOnAdmin = {
    toast: toastMessage,
    showErrorModal: showErrorModal,
    reloadWithToast: reloadWithToast,
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
      syncModalScrollLock();
      modalOpener?.focus?.();
      modalOpener = null;
    },
    trapFocus: function (modal, event) {
      trapModalFocus(modal, event);
    }
  };

  syncSidebarForViewport();
  showPendingToast();
  /* 초기 진입(첫 화면 선택)에는 초점을 옮기지 않는다. 페이지를 열자마자 본문에
     초점 표시가 그려지는 현상을 막기 위해서다. 이후 화면 전환에서만 초점을 옮겨
     키보드 사용자가 전환된 위치를 알 수 있게 한다. */
  showView(viewFromLocation(), Boolean(document.body.dataset.view), true);
})(window);
