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

  /*
   * 종목 탭 전환: 페이지를 새로 불러오지 않고 같은 GET 주소의 HTML을 받아 탭·개수·목록만 바꾸고,
   * 상단 배경은 새 종목에서 사진이 있는 첫 매칭방의 사진으로 서서히 바꾼다.
   * 주소창도 함께 바꿔(pushState) 새로고침·뒤로가기·링크 공유가 그대로 동작한다.
   * fetch가 실패하면 원래 링크로 이동한다.
   */
  const sportVisual = document.getElementById('sport-visual');
  const sportShowcase = document.getElementById('sports-showcase');
  const SWAP_IDS = ['sport-total', 'result-count', 'match-count', 'active-filters', 'match-results'];
  let sportRequest = null;

  function preloadImage(url) {
    return new Promise(function (resolve) {
      if (!url) { resolve(); return; }
      const image = new Image();
      const done = function () { resolve(); };
      image.onload = done;
      image.onerror = done;
      image.src = url;
      window.setTimeout(done, 1500); /* 느린 사진 때문에 전환이 멈추지 않게 한다. */
    });
  }

  function showSportImage(url) {
    if (!sportVisual) return;
    const current = sportVisual.querySelector('.sport-visual-layer.is-active');
    if ((sportVisual.dataset.image || '') === (url || '') && current) return;
    sportVisual.dataset.image = url || '';

    const layer = document.createElement('div');
    layer.className = 'sport-visual-layer';
    if (url) layer.style.backgroundImage = 'url("' + url.replace(/"/g, '%22') + '")';
    sportVisual.appendChild(layer);
    void layer.offsetWidth; /* 새 레이어의 시작 상태를 적용한 뒤 전환한다. */
    layer.classList.add('is-active');

    sportVisual.querySelectorAll('.sport-visual-layer').forEach(function (old) {
      if (old === layer) return;
      old.classList.remove('is-active');
      window.setTimeout(function () { old.remove(); }, 1200);
    });
  }

  function applySportPage(doc) {
    SWAP_IDS.forEach(function (id) {
      const target = document.getElementById(id);
      const source = doc.getElementById(id);
      if (target && source) target.innerHTML = source.innerHTML;
    });

    const nextTabs = doc.getElementById('sport-tabs');
    if (sportTabsTrack && nextTabs) {
      const scrollLeft = sportTabsTrack.scrollLeft;
      sportTabsTrack.innerHTML = nextTabs.innerHTML;
      sportTabsTrack.scrollLeft = scrollLeft;
      updateSportTabScrollControls();
    }

    const nextSportInput = doc.querySelector('#search-form input[name="sportId"]');
    const sportInput = searchForm?.querySelector('input[name="sportId"]');
    if (sportInput && nextSportInput) sportInput.value = nextSportInput.value;

    const nextReset = doc.getElementById('time-reset');
    const reset = document.getElementById('time-reset');
    if (reset && nextReset) reset.setAttribute('href', nextReset.getAttribute('href'));

    const nextAnnouncement = doc.getElementById('sport-visual-announcement');
    const announcement = document.getElementById('sport-visual-announcement');
    if (announcement && nextAnnouncement) announcement.textContent = nextAnnouncement.textContent;

    const nextVisual = doc.getElementById('sport-visual');
    const sport = nextVisual?.dataset.sport || '';
    if (sportVisual) sportVisual.dataset.sport = sport;
    if (sportShowcase) sportShowcase.dataset.sport = sport;

    if (doc.title) document.title = doc.title;
    return nextVisual?.dataset.image || '';
  }

  function loadSportPage(url, push) {
    if (sportRequest) sportRequest.abort();
    resetMoreCards(); /* 이전 목록의 다음 페이지 요청이 새 목록에 붙지 않게 한다. */
    const controller = new AbortController();
    sportRequest = controller;
    const results = document.getElementById('match-results');
    results?.setAttribute('aria-busy', 'true');

    fetch(url, { signal: controller.signal, headers: { Accept: 'text/html' }, credentials: 'same-origin' })
      .then(function (response) {
        if (!response.ok) throw new Error('HTTP ' + response.status);
        return response.text();
      })
      .then(function (html) {
        const doc = new DOMParser().parseFromString(html, 'text/html');
        if (!doc.getElementById('match-results')) throw new Error('unexpected page');
        const image = applySportPage(doc);
        observeMoreCards();
        if (push) {
          history.pushState({ sportTab: true }, '', url);
          currentListUrl = window.location.pathname + window.location.search;
        }
        return preloadImage(image).then(function () {
          if (sportRequest === controller) showSportImage(image);
        });
      })
      .catch(function (error) {
        if (error.name === 'AbortError') return;
        window.location.href = url;
      })
      .finally(function () {
        if (sportRequest === controller) {
          sportRequest = null;
          document.getElementById('match-results')?.removeAttribute('aria-busy');
        }
      });
  }

  sportTabsTrack?.addEventListener('click', function (event) {
    const tab = event.target instanceof Element ? event.target.closest('a[data-sport-tab]') : null;
    if (!tab || !window.fetch || !window.DOMParser) return;
    if (event.button !== 0 || event.metaKey || event.ctrlKey || event.shiftKey || event.altKey) return;
    event.preventDefault();
    if (tab.getAttribute('aria-current') === 'true') return;
    loadSportPage(tab.href, true);
  });

  /* 해시(#top 등)만 바뀐 이동은 무시하고, 주소·검색 조건이 바뀐 뒤로가기·앞으로가기만 다시 불러온다. */
  let currentListUrl = window.location.pathname + window.location.search;
  window.addEventListener('popstate', function () {
    const next = window.location.pathname + window.location.search;
    if (next === currentListUrl) return;
    currentListUrl = next;
    loadSportPage(window.location.href, false);
  });

  /*
   * 무한 스크롤: 목록 끝의 #match-more가 화면 가까이 오면 그 data-next-url(커서 포함)로 다음 카드 조각을 받아
   * sentinel 앞에 붙인다. 조각 끝에 새 #match-more가 있으면 그것으로 바꾸고, 없으면 마지막 페이지다.
   * 실패하면 잠시 뒤 다시 시도하고, 계속 실패하면 안내 문구만 남긴다.
   */
  const MORE_RETRY_LIMIT = 3;
  let moreRequest = null;
  let moreFailures = 0;
  let moreRetryTimer = null;
  const moreObserver = 'IntersectionObserver' in window
    ? new IntersectionObserver(function (entries) {
      if (entries.some(function (entry) { return entry.isIntersecting; })) loadMoreCards();
    }, { rootMargin: '400px 0px' })
    : null;

  function observeMoreCards() {
    if (!moreObserver) return;
    moreObserver.disconnect();
    const sentinel = document.getElementById('match-more');
    if (sentinel) moreObserver.observe(sentinel);
  }

  function resetMoreCards() {
    if (moreRequest) moreRequest.abort();
    moreRequest = null;
    moreFailures = 0;
    window.clearTimeout(moreRetryTimer);
    moreObserver?.disconnect();
  }

  function loadMoreCards() {
    const sentinel = document.getElementById('match-more');
    const url = sentinel?.dataset.nextUrl;
    if (!sentinel || !url || moreRequest) return;

    const controller = new AbortController();
    moreRequest = controller;
    const results = document.getElementById('match-results');
    results?.setAttribute('aria-busy', 'true');

    fetch(url, { signal: controller.signal, headers: { Accept: 'text/html' }, credentials: 'same-origin' })
      .then(function (response) {
        if (!response.ok) throw new Error('HTTP ' + response.status);
        return response.text();
      })
      .then(function (html) {
        if (moreRequest !== controller) return;
        const template = document.createElement('template');
        template.innerHTML = html;
        const nextSentinel = template.content.getElementById('match-more');
        sentinel.before(template.content);
        if (nextSentinel) {
          sentinel.replaceWith(nextSentinel);
        } else {
          sentinel.remove();
        }
        moreFailures = 0;
      })
      .catch(function (error) {
        if (error.name === 'AbortError' || moreRequest !== controller) return;
        moreFailures += 1;
        if (moreFailures >= MORE_RETRY_LIMIT) {
          moreObserver?.disconnect();
          sentinel.classList.add('is-failed');
          const message = sentinel.querySelector('[data-more-message]');
          if (message) message.textContent = '매칭방을 더 불러오지 못했습니다. 새로고침해 주세요.';
          return;
        }
        moreObserver?.disconnect();
        moreRetryTimer = window.setTimeout(observeMoreCards, 1500 * moreFailures);
      })
      .finally(function () {
        if (moreRequest !== controller) return;
        moreRequest = null;
        results?.removeAttribute('aria-busy');
        /* 붙인 카드가 적어 새 sentinel이 이미 보이는 경우에도 다시 감지되도록 새로 관찰한다. */
        if (moreFailures === 0) observeMoreCards();
      });
  }

  observeMoreCards();

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
