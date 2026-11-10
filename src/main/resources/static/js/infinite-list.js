/* 무한 스크롤 공용: [data-infinite-list] 아래 [data-infinite-box]가 보이면 다음 장 카드 조각을 받아 이어 붙입니다. */
(function () {
  'use strict';

  // 지금 돌아가는 목록(다시 시작하면 예전 것은 멈춤)
  let current = null;

  function start() {
    if (current) current.stop();
    current = null;

    const list = document.querySelector('[data-infinite-list]');
    const box = document.querySelector('[data-infinite-box]');
    // 1장뿐이면 상자가 없어서 할 일 없음
    if (!list || !box) return;

    const sentinel = box.querySelector('[data-infinite-sentinel]');
    const moreButton = box.querySelector('[data-infinite-more]');
    const endText = box.querySelector('[data-infinite-end]');
    const lastPage = Number(box.dataset.lastPage);
    let page = Number(box.dataset.page);
    let loading = false;
    let stopped = false;
    let observer = null;

    // 끝: 감지 멈추고 끝 문구
    function finish() {
      if (observer) observer.disconnect();
      moreButton.hidden = true;
      endText.hidden = false;
    }

    async function loadNext() {
      // 요청 중이면 또 부르지 않음
      if (loading || stopped) return;
      loading = true;
      moreButton.hidden = true;

      // 지금 주소(검색 조건 그대로) + 다음 장 번호
      const nextPage = page + 1;
      const url = new URL(window.location.href);
      url.searchParams.set('page', String(nextPage));

      try {
        const response = await fetch(url, { headers: { 'X-Requested-With': 'fetch' } });
        // 형식 오류로 /teams로 돌아간 응답도 실패로 봄
        if (!response.ok || response.redirected) throw new Error('status ' + response.status);
        const html = await response.text();
        // 그사이 다시 시작했으면 늦게 온 카드는 버림
        if (stopped) return;

        const template = document.createElement('template');
        template.innerHTML = html;
        const items = template.content.querySelectorAll('[data-item-id]');
        if (items.length === 0) {
          finish();
          return;
        }
        items.forEach(function (item) {
          // 그사이 목록이 밀려 이미 있는 카드는 건너뜀
          if (list.querySelector('[data-item-id="' + CSS.escape(item.dataset.itemId) + '"]')) return;
          list.appendChild(item);
        });

        page = nextPage;
        // 뒤로 가기로 오면 이 장까지 다시 그리게 주소만 바꿈(기록은 안 늘림)
        window.history.replaceState(window.history.state, '', url);

        if (page >= lastPage) {
          finish();
        } else if (observer) {
          // 붙인 뒤에도 감지 칸이 보이면 한 번 더 부르게 다시 감시
          observer.unobserve(sentinel);
          observer.observe(sentinel);
        } else {
          moreButton.hidden = false;
        }
      } catch (error) {
        // 실패하면 버튼으로 다시 시도
        if (!stopped) moreButton.hidden = false;
      } finally {
        loading = false;
      }
    }

    moreButton.addEventListener('click', loadNext);

    if (page >= lastPage) {
      // 뒤로 가기로 마지막 장까지 이미 그렸으면 바로 끝
      finish();
    } else if ('IntersectionObserver' in window) {
      // 감지 칸이 화면 아래 200px 안에 들어오면 미리 부름
      observer = new IntersectionObserver(function (entries) {
        if (entries.some(function (entry) { return entry.isIntersecting; })) loadNext();
      }, { rootMargin: '0px 0px 200px 0px' });
      observer.observe(sentinel);
    } else {
      // 감지 기능이 없는 브라우저는 버튼
      moreButton.hidden = false;
    }

    current = {
      stop: function () {
        stopped = true;
        if (observer) observer.disconnect();
        moreButton.removeEventListener('click', loadNext);
      }
    };
  }

  window.PlayOnInfiniteList = Object.freeze({ start: start });
  start();
})();
