/* 마이페이지: 경기 이력 펼치기와 섹션 메뉴 표시만 처리합니다. 데이터는 서버가 렌더링합니다. */
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

  /* 알림 모달: 헤더 종 버튼으로 열고 닫습니다. 목록은 서버가 렌더링합니다. */
  const notificationModal = document.getElementById('notification-modal');
  const closeButtons = document.querySelectorAll('[data-close-notifications]');
  const headerBell = document.querySelector('header a[href$="#notifications"]');
  let modalOpener = null;

  function openNotificationModal(opener) {
    if (!notificationModal) return;
    modalOpener = opener || headerBell || null;
    notificationModal.hidden = false;
    document.body.classList.add('has-notification-modal');
    notificationModal.querySelector('.mypage-notification-close')?.focus({ preventScroll: true });
  }

  function closeNotificationModal() {
    if (!notificationModal || notificationModal.hidden) return;
    notificationModal.hidden = true;
    document.body.classList.remove('has-notification-modal');
    if (modalOpener && typeof modalOpener.focus === 'function') {
      modalOpener.focus({ preventScroll: true });
    }
  }

  headerBell?.addEventListener('click', function (event) {
    if (!notificationModal) return;
    event.preventDefault();
    openNotificationModal(headerBell);
  });

  closeButtons.forEach(function (button) {
    button.addEventListener('click', closeNotificationModal);
  });

  notificationModal?.addEventListener('click', function (event) {
    if (event.target === notificationModal) closeNotificationModal();
  });

  document.addEventListener('keydown', function (event) {
    if (!notificationModal || notificationModal.hidden) return;
    if (event.key === 'Escape') {
      closeNotificationModal();
      return;
    }
    if (event.key !== 'Tab') return;

    const focusable = Array.from(notificationModal.querySelectorAll('button:not(:disabled), [href], input:not(:disabled), [tabindex]:not([tabindex="-1"])'));
    if (!focusable.length) {
      event.preventDefault();
      return;
    }
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
})();
