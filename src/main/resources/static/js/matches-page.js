(function () {
  'use strict';

  /* 종목 탭 가로 이동 */
  const sportTabsNavigation = document.querySelector('.sport-tabs-navigation');
  const sportTabsTrack = document.getElementById('sport-tabs');
  const previousSportTabsButton = document.getElementById('sport-tabs-previous');
  const nextSportTabsButton = document.getElementById('sport-tabs-next');

  function updateSportTabScrollControls() {
    if (!sportTabsNavigation || !sportTabsTrack || !previousSportTabsButton || !nextSportTabsButton) return;

    const hasOverflow = sportTabsTrack.scrollWidth > sportTabsNavigation.clientWidth + 1;
    previousSportTabsButton.hidden = !hasOverflow;
    nextSportTabsButton.hidden = !hasOverflow;

    if (!hasOverflow) {
      sportTabsTrack.scrollLeft = 0;
      previousSportTabsButton.disabled = true;
      nextSportTabsButton.disabled = true;
      return;
    }

    const maxScrollLeft = Math.max(0, sportTabsTrack.scrollWidth - sportTabsTrack.clientWidth);
    const scrollLeft = Math.max(0, Math.min(maxScrollLeft, sportTabsTrack.scrollLeft));
    previousSportTabsButton.disabled = scrollLeft <= 1;
    nextSportTabsButton.disabled = scrollLeft >= maxScrollLeft - 1;
  }

  function scrollSportTabs(direction) {
    if (!sportTabsTrack) return;
    const maxScrollLeft = Math.max(0, sportTabsTrack.scrollWidth - sportTabsTrack.clientWidth);
    if (maxScrollLeft <= 1) {
      updateSportTabScrollControls();
      return;
    }

    const trackBounds = sportTabsTrack.getBoundingClientRect();
    const tabs = Array.from(sportTabsTrack.querySelectorAll('a'));
    const targetTab = direction > 0
      ? tabs.find((tab) => tab.getBoundingClientRect().right > trackBounds.right + 1)
      : tabs.slice().reverse().find((tab) => tab.getBoundingClientRect().left < trackBounds.left - 1);
    if (!targetTab) {
      updateSportTabScrollControls();
      return;
    }

    const targetBounds = targetTab.getBoundingClientRect();
    const movement = direction > 0
      ? targetBounds.right - trackBounds.right
      : targetBounds.left - trackBounds.left;
    sportTabsTrack.scrollLeft = Math.max(0, Math.min(maxScrollLeft, sportTabsTrack.scrollLeft + movement));
    window.requestAnimationFrame(updateSportTabScrollControls);
  }

  if (sportTabsTrack) {
    sportTabsTrack.addEventListener('scroll', updateSportTabScrollControls, { passive: true });
    window.addEventListener('resize', updateSportTabScrollControls, { passive: true });
    window.addEventListener('load', updateSportTabScrollControls, { once: true });
    if ('ResizeObserver' in window && sportTabsNavigation) {
      const observer = new ResizeObserver(updateSportTabScrollControls);
      observer.observe(sportTabsNavigation);
      observer.observe(sportTabsTrack);
    }
    updateSportTabScrollControls();
  }

  previousSportTabsButton?.addEventListener('click', function () { scrollSportTabs(-1); });
  nextSportTabsButton?.addEventListener('click', function () { scrollSportTabs(1); });

  /* 시간 선택 패널 */
  const timeButton = document.getElementById('time-button');
  const timePopover = document.getElementById('time-popover');
  const applyHours = document.getElementById('apply-hours');
  const searchForm = document.getElementById('search-form');

  function setPopover(open) {
    if (!timePopover || !timeButton) return;
    timePopover.classList.toggle('hidden', !open);
    timeButton.setAttribute('aria-expanded', String(open));
  }

  timeButton?.addEventListener('click', function (event) {
    event.stopPropagation();
    setPopover(timePopover.classList.contains('hidden'));
  });

  applyHours?.addEventListener('click', function () {
    setPopover(false);
    searchForm?.requestSubmit();
  });

  document.addEventListener('click', function (event) {
    if (!timePopover || timePopover.classList.contains('hidden')) return;
    if (timePopover.contains(event.target) || timeButton?.contains(event.target)) return;
    setPopover(false);
  });

  document.addEventListener('keydown', function (event) {
    if (event.key === 'Escape') setPopover(false);
  });

  /* 매칭방 상세 모달 */
  const dialog = document.getElementById('app-dialog');

  function openMatchDialog(matchId) {
    const source = document.querySelector('[data-room-id="' + matchId + '"] [data-room-dialog-content]');
    if (!dialog || !source) return;

    dialog.innerHTML = source.innerHTML;
    const heading = dialog.querySelector('.room-dialog-heading-copy h2');
    if (heading) {
      heading.id = 'room-dialog-title';
      dialog.setAttribute('aria-labelledby', heading.id);
    } else {
      dialog.removeAttribute('aria-labelledby');
    }
    if (!dialog.open) dialog.showModal();
  }

  function closeMatchDialog() {
    if (dialog?.open) dialog.close();
  }

  document.addEventListener('click', function (event) {
    const target = event.target instanceof Element ? event.target : null;
    if (!target) return;

    const detailButton = target.closest('[data-room-detail]');
    if (detailButton) {
      openMatchDialog(detailButton.dataset.roomDetail);
      return;
    }

    const link = target.closest('a[href]');
    if (link && dialog?.contains(link)) return;

    if (target.closest('[data-close]')) {
      closeMatchDialog();
      return;
    }
  });

  dialog?.addEventListener('click', function (event) {
    if (event.target === dialog) closeMatchDialog();
  });

  document.addEventListener('keydown', function (event) {
    if (event.key === 'Escape' && dialog?.open) closeMatchDialog();
  });
})();
