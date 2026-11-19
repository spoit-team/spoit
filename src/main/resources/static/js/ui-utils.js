(function () {
  function query(selector, root = document) {
    return root.querySelector(selector);
  }

  function escapeHTML(value) {
    return String(value ?? '').replace(/[&<>"']/g, (character) => ({
      '&': '&amp;',
      '<': '&lt;',
      '>': '&gt;',
      '"': '&quot;',
      "'": '&#39;'
    }[character]));
  }

  function formatNumber(value, locale = 'ko-KR') {
    return Number(value || 0).toLocaleString(locale);
  }

  function formatMatchLevel(value, fallback = '모든 실력') {
    if (value === null || value === undefined || value === '') return fallback;
    if (value === 'all' || value === '모든 실력') return '모든 실력';
    const level = Number(value);
    if (Number.isInteger(level) && level >= 1 && level <= 5) return `${level}단계`;
    return fallback;
  }

  function formatKoreanDate(value, knownDates = []) {
    let knownDate = null;
    if (Array.isArray(knownDates)) knownDate = knownDates.find((item) => item.iso === value);
    if (knownDate) return knownDate.label;

    const date = new Date(`${String(value || '')}T00:00:00`);
    if (Number.isNaN(date.getTime())) return String(value || '');
    return new Intl.DateTimeFormat('ko-KR', {
        month: 'long',
        day: 'numeric',
        weekday: 'short'
    }).format(date);
  }

  const announcementTimers = new WeakMap();

  function announce(target, text, options = {}) {
    if (!target) return;

    target.textContent = String(text ?? '');
    target.classList.remove('is-error', 'is-success');
    if (options.kind) target.classList.add(options.kind);
    target.hidden = false;

    const previousTimer = announcementTimers.get(target);
    if (previousTimer) window.clearTimeout(previousTimer);
    announcementTimers.delete(target);

    if (options.scrollIntoView) {
      const prefersReducedMotion = window.matchMedia?.('(prefers-reduced-motion: reduce)').matches;
      let behavior = 'smooth';
      if (prefersReducedMotion) behavior = 'auto';
      target.scrollIntoView({ behavior: behavior, block: 'nearest' });
    }

    const timeout = Number(options.timeout);
    if (Number.isFinite(timeout) && timeout > 0) {
      announcementTimers.set(target, window.setTimeout(() => {
        target.hidden = true;
        announcementTimers.delete(target);
      }, timeout));
    }
  }

  window.PlayOnUI = Object.freeze({ query, escapeHTML, formatNumber, formatKoreanDate, formatMatchLevel, announce });
})();
