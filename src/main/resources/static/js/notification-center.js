/* 통합 알림: GET /api/notifications를 데이터 원본으로 배지·목록을 갱신하고,
   로그인 사용자만 WebSocket(STOMP) 알림을 구독해 도착 사실을 알린다.
   - 외부 라이브러리 없이 필요한 최소 STOMP 프레임(CONNECT/SUBSCRIBE/MESSAGE)만 다룬다.
   - 수신 payload는 신뢰하지 않고, 연결·재연결·수신·화면 복귀 때마다 목록을 다시 조회한다.
   - 서버 값은 textContent로만 넣고, 이동 주소는 같은 출처의 상대 경로만 허용한다.
   - 문서가 숨거나 페이지를 떠나면 소켓·타이머를 정리하고, 다시 보이면 재연결·재조회한다. */
(function (global) {
  'use strict';

  const config = document.querySelector('[data-notification-config]');
  if (!config) return;

  const apiUrl = config.getAttribute('data-notification-api');
  const streamUrl = config.getAttribute('data-notification-stream');
  const csrfHeader = config.getAttribute('data-notification-csrf-header');
  const csrfToken = config.getAttribute('data-notification-csrf-token');
  const authenticated = config.getAttribute('data-notification-authenticated') !== 'false';

  const surfaces = Array.from(document.querySelectorAll('[data-notification-surface]'));
  if (!apiUrl) return;
  // 비로그인 사용자는 API·웹소켓을 쓰지 않고 서버가 그린 그대로 둔다(종 아이콘은 로그인 안내로 이동).
  if (!authenticated) return;

  const badges = Array.from(document.querySelectorAll('[data-notification-badge]'));
  const openers = Array.from(document.querySelectorAll('[data-notification-open]'));
  // 관리자 알림 모달은 admin-shell.js가 열고 닫는다. 여기서는 목록·배지만 맡는다.
  const dialog = document.querySelector('.playon-notification-backdrop[data-notification-dialog]');
  const statusRegion = document.querySelector('[data-notification-status]');

  const DESTINATION = '/user/queue/notifications';
  const SUBSCRIPTION_ID = 'spoit-notifications';
  const POLL_INTERVAL = 60000;
  const RECONNECT_MIN = 2000;
  const RECONNECT_MAX = 60000;
  const EMPTY_TEXT = '확인할 알림이 없습니다.';

  // 알림 창이 있는 화면에서만 종 아이콘을 대화상자 버튼처럼 보강한다.
  if (dialog) {
    openers.forEach(function (opener) {
      opener.setAttribute('aria-haspopup', 'dialog');
      opener.setAttribute('aria-controls', dialog.id || 'notification-modal');
      opener.setAttribute('aria-expanded', 'false');
    });
  }

  const state = { items: [], unreadCount: 0, loaded: false, signature: '' };

  let socket = null;
  let pollTimer = 0;
  let reconnectTimer = 0;
  let reconnectDelay = RECONNECT_MIN;
  let refreshPromise = null;
  let refreshQueued = false;
  let stateVersion = 0;
  let paused = false;
  let disposed = false;
  let modalOpener = null;

  /* ── 조회 → 표시 ─────────────────────────────────────────────────── */

  function isRecord(value) {
    return Boolean(value) && typeof value === 'object';
  }

  function fetchState() {
    const version = stateVersion;
    return global.fetch(apiUrl, {
      credentials: 'same-origin',
      headers: { Accept: 'application/json' }
    }).then(function (response) {
      // 로그인 세션이 끝나면 로그인 화면 HTML이 온다. 그 응답은 알림 데이터로 쓰지 않는다.
      if (response.redirected || !response.ok) return null;
      return response.json().catch(function () { return null; });
    }).then(function (data) {
      if (disposed || version !== stateVersion || !data || typeof data !== 'object') return;
      const items = Array.isArray(data.items) ? data.items.filter(isRecord) : [];
      const reported = Number(data.unreadCount);
      const previous = state.loaded ? state.signature : '';
      const previousItems = state.items;
      state.items = items;
      state.unreadCount = Number.isFinite(reported) && reported >= 0
        ? Math.floor(reported)
        : items.filter(function (item) { return !item.read; }).length;
      state.signature = signatureOf(items, state.unreadCount);
      state.loaded = true;
      render(previous !== state.signature);
      if (previous && previous !== state.signature) {
        const previousIds = new Set(previousItems.map(function (item) { return item.notificationId; }));
        const fresh = items.find(function (item) {
          return !item.read && !previousIds.has(item.notificationId);
        });
        if (fresh) announce('새 알림: ' + (fresh.title || '알림이 도착했습니다.'));
      }
    }).catch(function () {
      // 네트워크·서버 오류는 조용히 넘긴다. 다음 재조회나 재연결에서 복구된다.
    });
  }

  /* 목록이 실제로 달라졌을 때만 다시 그린다. 같은 데이터로 매번 새로 그리면
     스크롤과 초점이 튀고 화면이 깜박인다. */
  function signatureOf(items, unreadCount) {
    return unreadCount + '|' + items.map(function (item) {
      return [item.notificationId, item.read ? 1 : 0, item.title, item.body, item.time, item.href, item.type].join('~');
    }).join('§');
  }

  /* 동시에 여러 번 조회하지 않고, 조회 중 새 요청이 오면 한 번만 이어서 실행한다. */
  function refresh() {
    if (!authenticated || disposed) return;
    if (refreshPromise) {
      refreshQueued = true;
      return;
    }
    const request = fetchState().then(function () {
      if (refreshPromise !== request) return;
      refreshPromise = null;
      if (refreshQueued) {
        refreshQueued = false;
        refresh();
      }
    });
    refreshPromise = request;
  }

  function render(rerenderList) {
    // 다시 그리기 전에 초점 위치를 기억한다. 누른 버튼이 사라지면 초점이 body로 튀기 때문이다.
    const focusToken = rerenderList ? captureFocus() : null;
    surfaces.forEach(function (surface) {
      if (rerenderList) renderList(surface);
      renderExtras(surface);
    });
    renderBadges();
    if (focusToken) restoreFocus(focusToken);
  }

  /* 목록 안에서 초점이 있던 위치를 알림 번호와 동작 이름으로 기억한다. */
  function captureFocus() {
    const active = document.activeElement;
    if (!(active instanceof Element)) return null;
    const action = active.closest('[data-notification-action]');
    if (action) {
      const item = action.closest('[data-notification-id]');
      return { action: action.getAttribute('data-notification-action'), id: item && item.getAttribute('data-notification-id') };
    }
    if (active.closest('[data-notification-read-all]')) return { action: 'read-all' };
    return null;
  }

  function restoreFocus(token) {
    let target = null;
    if (token.action === 'read-all') {
      target = document.querySelector('[data-notification-read-all]');
    } else if (token.id) {
      const selector = '[data-notification-id="' + cssEscape(token.id) + '"]';
      surfaces.some(function (surface) {
        const item = surface.querySelector(selector);
        if (!item) return false;
        const button = item.querySelector('[data-notification-action="' + token.action + '"]');
        target = button && !button.hidden ? button : item.querySelector('[data-field="link"]') || item;
        return Boolean(target);
      });
    }
    // 사라진 알림이었으면 초점을 억지로 옮기지 않고, 숨은 버튼에 머무르지 않게 한다.
    if (target && typeof target.focus === 'function') target.focus({ preventScroll: true });
  }

  /* 선택자에 넣을 값을 이스케이프한다. 알림 번호는 숫자지만 방어적으로 처리한다. */
  function cssEscape(value) {
    return global.CSS && typeof global.CSS.escape === 'function'
      ? global.CSS.escape(String(value))
      : String(value).replace(/["\\]/g, '\\$&');
  }

  function renderBadges() {
    const count = state.unreadCount > 0 ? String(state.unreadCount) : '';
    badges.forEach(function (badge) {
      badge.textContent = count;
      badge.hidden = state.unreadCount <= 0;
    });
    // 마이페이지 메뉴의 미확인 수 표시도 같은 값으로 맞춘다.
    document.querySelectorAll('[data-notification-nav-count]').forEach(function (node) {
      node.textContent = count;
      node.hidden = state.unreadCount <= 0;
    });
    openers.forEach(function (opener) {
      opener.setAttribute('aria-label', state.unreadCount > 0
        ? '알림 확인, 새 알림 ' + state.unreadCount + '건'
        : '알림 확인');
    });
  }

  function renderExtras(surface) {
    surface.querySelectorAll('[data-notification-summary]').forEach(function (summary) {
      summary.textContent = summary.getAttribute('data-notification-summary') === 'short'
        ? (state.unreadCount > 0 ? '· ' + state.unreadCount + '건 미확인' : '· 모두 확인했습니다')
        : '알림 ' + state.items.length + '건 · 미확인 ' + state.unreadCount + '건';
    });
    surface.querySelectorAll('[data-notification-read-all]').forEach(function (control) {
      control.disabled = state.unreadCount <= 0;
    });
  }

  function renderList(surface) {
    const list = surface.querySelector('[data-notification-list]');
    const template = surface.querySelector('template[data-notification-item-template]');
    if (!list || !template || !template.content) return;
    list.replaceChildren();
    state.items.forEach(function (item) {
      list.appendChild(buildItem(template, item));
    });
    if (!state.items.length) {
      const empty = document.createElement('p');
      empty.className = list.getAttribute('data-empty-class') || 'playon-notification-empty';
      empty.textContent = EMPTY_TEXT;
      empty.setAttribute('data-notification-empty', '');
      list.appendChild(empty);
    }
  }

  function buildItem(template, item) {
    const node = template.content.firstElementChild.cloneNode(true);
    node.setAttribute('data-notification-id', String(item.notificationId));
    node.classList.toggle('is-read', Boolean(item.read));

    setText(node, 'type', item.type || '알림');
    setText(node, 'title', item.title);
    setText(node, 'body', item.body);
    setText(node, 'time', item.time);

    const stateNode = node.querySelector('[data-field="state"]');
    if (stateNode) {
      stateNode.textContent = item.read ? '읽음' : '새 알림';
      stateNode.classList.toggle('is-read-label', Boolean(item.read));
    }

    const link = node.querySelector('[data-field="link"]');
    const href = safeHref(item.href);
    if (link) {
      if (href) {
        link.setAttribute('href', href);
      } else {
        // 이동할 화면이 없으면 링크 대신 같은 내용의 텍스트로 남긴다.
        const copy = link.querySelector('[data-field="copy"]');
        if (copy && typeof link.replaceWith === 'function') link.replaceWith(copy);
        else link.removeAttribute('href');
      }
    }

    const id = String(item.notificationId);
    const readButton = node.querySelector('[data-notification-action="read"]');
    if (readButton) {
      readButton.hidden = Boolean(item.read);
      readButton.setAttribute('data-notification-id', id);
    }
    const deleteButton = node.querySelector('[data-notification-action="delete"]');
    if (deleteButton) {
      deleteButton.setAttribute('data-notification-id', id);
      deleteButton.setAttribute('aria-label', '알림 삭제: ' + (item.title || '알림'));
    }
    return node;
  }

  function setText(node, field, value) {
    const target = node.querySelector('[data-field="' + field + '"]');
    if (target) target.textContent = value == null ? '' : String(value);
  }

  /* 같은 출처 안에서만 이동한다. `//host`, `javascript:`, 역슬래시·제어문자가 섞인 값은 버린다. */
  function safeHref(value) {
    if (typeof value !== 'string') return null;
    const trimmed = value.trim();
    if (!trimmed.startsWith('/') || trimmed.startsWith('//')) return null;
    if (/[\u0000-\u001f\u007f\\]/.test(trimmed)) return null;
    return trimmed;
  }

  function announce(message) {
    if (!statusRegion || !message) return;
    statusRegion.textContent = message;
  }

  /* ── 읽음·삭제 후 서버 상태 재조회 ───────────────────────────────── */

  function withCsrf(headers) {
    if (csrfHeader && csrfToken) headers[csrfHeader] = csrfToken;
    return headers;
  }

  function endpoint(suffix) {
    return apiUrl.replace(/\/+$/, '') + suffix;
  }

  function send(path, options) {
    return global.fetch(path, Object.assign({
      method: 'POST',
      credentials: 'same-origin',
      headers: withCsrf({ Accept: 'application/json' })
    }, options || {})).then(function (response) {
      return response.ok && !response.redirected;
    }).catch(function () {
      return false;
    });
  }

  function readOne(notificationId, keepalive = false) {
    if (!notificationId) return;
    send(endpoint('/' + encodeURIComponent(notificationId) + '/read'), { keepalive }).then(function (ok) {
      if (!ok) announce('알림을 읽음으로 표시하지 못했습니다. 잠시 후 다시 시도해 주세요.');
      stateVersion++;
      refresh();
    });
  }

  function deleteOne(notificationId) {
    if (!notificationId) return;
    send(endpoint('/' + encodeURIComponent(notificationId) + '/delete')).then(function (ok) {
      if (!ok) {
        announce('알림을 삭제하지 못했습니다. 잠시 후 다시 시도해 주세요.');
      }
      stateVersion++;
      refresh();
    });
  }

  function readAll() {
    if (state.loaded && state.unreadCount <= 0) return;
    send(endpoint('/read-all')).then(function (ok) {
      if (!ok) {
        announce('알림을 모두 읽음으로 표시하지 못했습니다. 잠시 후 다시 시도해 주세요.');
      }
      stateVersion++;
      refresh();
    });
  }

  /* 서버가 그린 읽음·삭제·모두 읽음 폼은 JS가 없을 때의 경로다.
     JS가 있으면 같은 일을 /api/notifications로 처리해 화면을 다시 그리지 않는다. */
  function formAction(form) {
    let path;
    try {
      path = new URL(form.getAttribute('action') || form.action, global.location.href).pathname;
    } catch (error) {
      return null;
    }
    if (/(^|\/)notifications\/read-all$/.test(path)) return 'read-all';
    if (/(^|\/)notifications\/[^/]+\/read$/.test(path)) return 'read';
    if (/(^|\/)notifications\/[^/]+\/delete$/.test(path)) return 'delete';
    return null;
  }

  /* 개별 읽음·삭제 경로의 대상 번호는 마지막 조각 앞의 값이다. */
  function formTargetId(form) {
    try {
      const path = new URL(form.getAttribute('action') || form.action, global.location.href).pathname;
      const segments = path.split('/').filter(Boolean);
      return segments[segments.length - 2] || null;
    } catch (error) {
      return null;
    }
  }

  /* ── 이벤트 ─────────────────────────────────────────────────────── */

  document.addEventListener('submit', function (event) {
    const form = event.target;
    if (!(form instanceof HTMLFormElement)) return;
    const action = formAction(form);
    if (!action) return;
    event.preventDefault();
    if (action === 'read-all') {
      readAll();
      return;
    }
    const id = formTargetId(form);
    if (action === 'read') readOne(id);
    else deleteOne(id);
  });

  document.addEventListener('click', function (event) {
    const target = event.target instanceof Element ? event.target : null;
    if (!target) return;

    // '모두 읽음'은 폼 밖에 그려진 버튼이면 API로 처리한다. 폼 안이면 submit 핸들러가 맡는다.
    const readAllButton = target.closest('[data-notification-read-all]');
    if (readAllButton && !readAllButton.closest('form')) {
      event.preventDefault();
      readAll();
      return;
    }

    // JS가 그린 목록의 읽음·삭제 버튼. 폼 안에 있으면 submit 핸들러가 맡는다.
    const action = target.closest('button[data-notification-action]');
    if (action && !action.closest('form')) {
      event.preventDefault();
      const id = action.getAttribute('data-notification-id');
      if (action.getAttribute('data-notification-action') === 'delete') deleteOne(id);
      else readOne(id);
      return;
    }

    // 알림을 열면 읽음으로 남긴다. 이동은 그대로 두고 전송만 keepalive로 마친다.
    const itemLink = target.closest('[data-notification-list] [data-field="link"]');
    if (itemLink && itemLink.getAttribute('href')) {
      const item = itemLink.closest('[data-notification-id]');
      if (item) {
        readOne(item.getAttribute('data-notification-id'), true);
      }
    }

    if (target.closest('[data-notification-open]')) {
      // 알림 창이 없는 화면(JS 미지원·미인증)에서는 링크 기본 이동을 그대로 둡니다.
      if (!dialog) return;
      event.preventDefault();
      openDialog(target.closest('[data-notification-open]'));
      return;
    }
    if (target.closest('[data-notification-close]')) {
      closeDialog();
      return;
    }
    if (dialog && target === dialog) closeDialog();
  });

  /* ── 사용자 알림 모달 ───────────────────────────────────────────── */

  function openDialog(opener) {
    if (!dialog) return;
    modalOpener = opener || null;
    dialog.hidden = false;
    document.body.classList.add('has-playon-notification-modal');
    openers.forEach(function (control) { control.setAttribute('aria-expanded', 'true'); });
    const closeButton = dialog.querySelector('.playon-notification-close');
    if (closeButton) closeButton.focus({ preventScroll: true });
    refresh();
  }

  function closeDialog() {
    if (!dialog || dialog.hidden) return;
    dialog.hidden = true;
    document.body.classList.remove('has-playon-notification-modal');
    openers.forEach(function (control) { control.setAttribute('aria-expanded', 'false'); });
    if (modalOpener && typeof modalOpener.focus === 'function') {
      modalOpener.focus({ preventScroll: true });
    }
    modalOpener = null;
  }

  function trapFocus(event) {
    if (!dialog || dialog.hidden || event.key !== 'Tab') return;
    const focusable = Array.from(dialog.querySelectorAll(
      'button:not(:disabled):not([hidden]), [href], input:not(:disabled), [tabindex]:not([tabindex="-1"])'));
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
    if (!dialog || dialog.hidden) return;
    if (event.key === 'Escape') {
      closeDialog();
      return;
    }
    trapFocus(event);
  });

  /* ── 실시간 연결: WebSocket STOMP ───────────────────────────────── */

  function socketUrl(path) {
    try {
      const url = new URL(path, global.location.href);
      url.protocol = url.protocol === 'https:' ? 'wss:' : 'ws:';
      return url.toString();
    } catch (error) {
      return null;
    }
  }

  /* STOMP 헤더 값에는 줄바꿈을 넣을 수 없다. 프레임 구분자를 깨뜨리는 문자는 지운다. */
  function headerValue(value) {
    return String(value).replace(/[\r\n]/g, ' ');
  }

  function frame(command, headers, body) {
    const lines = [command];
    Object.keys(headers || {}).forEach(function (name) {
      const value = headers[name];
      if (value === undefined || value === null || value === '') return;
      lines.push(name + ':' + headerValue(value));
    });
    return lines.join('\n') + '\n\n' + (body || '') + '\u0000';
  }

  /* 서버가 프레임을 나눠 보낼 수 있으므로 잘린 조각은 다음 조각과 합쳐 읽는다. */
  function parseFrames(chunk) {
    const frames = [];
    let rest = chunk;
    while (true) {
      const terminator = rest.indexOf('\u0000');
      if (terminator < 0) break;
      const raw = rest.slice(0, terminator).replace(/^\n+/, '');
      rest = rest.slice(terminator + 1);
      const split = raw.indexOf('\n\n');
      const head = split < 0 ? raw : raw.slice(0, split);
      const body = split < 0 ? '' : raw.slice(split + 2);
      const lines = head.split('\n');
      const command = (lines.shift() || '').trim();
      const headers = {};
      lines.forEach(function (line) {
        const separator = line.indexOf(':');
        if (separator > 0) headers[line.slice(0, separator).trim()] = line.slice(separator + 1).trim();
      });
      if (command) frames.push({ command: command, headers: headers, body: body });
    }
    return { frames: frames, rest: rest };
  }

  function connect() {
    if (!authenticated || disposed || paused || socket) return;
    if (!streamUrl || typeof global.WebSocket !== 'function') {
      startPolling();
      return;
    }
    const url = socketUrl(streamUrl);
    if (!url) {
      startPolling();
      return;
    }
    let next;
    try {
      // native STOMP는 WebSocket 하위 프로토콜 협상이 필요하다. 서버가 허용하는 STOMP 버전을 순서대로 제안한다.
      next = new global.WebSocket(url, ['v12.stomp', 'v11.stomp', 'v10.stomp']);
    } catch (error) {
      scheduleReconnect();
      return;
    }
    socket = next;
    let buffer = '';

    next.addEventListener('open', function () {
      if (socket !== next) return;
      const headers = { 'accept-version': '1.2', 'heart-beat': '0,0', host: global.location.host };
      if (csrfHeader && csrfToken) headers[csrfHeader] = csrfToken;
      try {
        next.send(frame('CONNECT', headers));
      } catch (error) {
        closeSocket();
      }
    });

    next.addEventListener('message', function (event) {
      if (socket !== next || typeof event.data !== 'string') return;
      const parsed = parseFrames(buffer + event.data);
      buffer = parsed.rest;
      parsed.frames.forEach(function (item) {
        if (item.command === 'CONNECTED') {
          reconnectDelay = RECONNECT_MIN;
          stopPolling();
          try {
            next.send(frame('SUBSCRIBE', { id: SUBSCRIPTION_ID, destination: DESTINATION, ack: 'auto' }));
          } catch (error) {
            closeSocket();
            return;
          }
          // 접속·재접속 시 목록과 읽지 않은 수를 다시 조회해 다중 세션의 변경도 복원한다.
          refresh();
        } else if (item.command === 'MESSAGE') {
          // payload는 쓰지 않는다. 도착 사실만으로 서버 목록을 다시 읽는다.
          refresh();
        } else if (item.command === 'ERROR') {
          closeSocket();
        }
      });
    });

    next.addEventListener('close', function () {
      if (socket && socket !== next) return;
      socket = null;
      scheduleReconnect();
    });

    next.addEventListener('error', function () {
      if (socket !== next) return;
      closeSocket();
    });
  }

  function closeSocket() {
    const current = socket;
    socket = null;
    if (!current) return;
    try {
      current.close();
    } catch (error) {
      // 이미 닫힌 소켓은 무시한다.
    }
  }

  function scheduleReconnect() {
    if (disposed || paused || reconnectTimer || !authenticated) return;
    const delay = reconnectDelay;
    reconnectDelay = Math.min(RECONNECT_MAX, Math.round(reconnectDelay * 1.7));
    reconnectTimer = global.setTimeout(function () {
      reconnectTimer = 0;
      if (disposed || paused) return;
      connect();
      // 실시간 연결 없이 오래 머무는 경우에도 배지가 뒤처지지 않도록 주기 조회를 켠다.
      startPolling();
    }, delay);
  }

  function startPolling() {
    if (pollTimer || disposed) return;
    pollTimer = global.setInterval(function () {
      if (document.hidden) return;
      refresh();
    }, POLL_INTERVAL);
  }

  function stopPolling() {
    if (!pollTimer) return;
    global.clearInterval(pollTimer);
    pollTimer = 0;
  }

  function stop() {
    closeSocket();
    stopPolling();
    if (reconnectTimer) {
      global.clearTimeout(reconnectTimer);
      reconnectTimer = 0;
    }
  }

  document.addEventListener('visibilitychange', function () {
    if (document.hidden) {
      paused = true;
      stop();
      return;
    }
    paused = false;
    reconnectDelay = RECONNECT_MIN;
    refresh();
    connect();
  });

  global.addEventListener('focus', function () {
    if (!document.hidden) refresh();
  });

  global.addEventListener('pagehide', function () {
    disposed = true;
    stateVersion++;
    refreshPromise = null;
    refreshQueued = false;
    stop();
  });

  global.addEventListener('pageshow', function (event) {
    if (!event.persisted) return;
    disposed = false;
    paused = document.hidden;
    reconnectDelay = RECONNECT_MIN;
    if (!paused) {
      refresh();
      connect();
    }
  });

  if (!authenticated) return;
  refresh();
  connect();
})(window);
