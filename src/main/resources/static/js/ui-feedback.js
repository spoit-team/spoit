/* 공용 결과 안내 (사용자·관리자 화면 공통)
   - 성공: 오른쪽 아래 토스트. 4초 뒤 사라지고, 마우스를 올리면 멈춘다.
   - 실패: 가운데 안내 모달(제목 + 원인 + [확인]). 사용자가 닫을 때까지 남는다.
   서버가 리다이렉트로 넘긴 결과(flash)는 fragments/feedback :: flash 요소에서 읽어 한 번만 보여 준다.
   화면 스크립트보다 먼저 실행되도록 head에서 defer 없이 불러온다. DOM 요소는 처음 쓸 때 만든다. */
(function (global) {
  'use strict';

  const document = global.document;
  const TOAST_DURATION_MS = 4000;
  const TOAST_LEAVE_MS = 200;
  const DEFAULT_ERROR_TITLE = '요청을 처리할 수 없습니다';
  /* 새로고침 뒤 보여 줄 성공 문구. 문구만 잠시 맡기고, 다시 열린 화면이 꺼내 보여 준 뒤 바로 지운다. */
  const PENDING_TOAST_KEY = 'spoit-pending-toast';

  let toastRegion = null;
  let alertDialog = null;
  let alertReturnFocus = null;
  let alertOnClose = null;

  /* ---------------- 성공 토스트 ---------------- */

  function supportsPopover(element) {
    return typeof element.showPopover === 'function' && typeof element.hidePopover === 'function';
  }

  function ensureToastRegion() {
    if (toastRegion && toastRegion.isConnected) return toastRegion;
    toastRegion = document.createElement('div');
    toastRegion.className = 'ui-toast-region';
    toastRegion.id = 'ui-toast-region';
    toastRegion.setAttribute('role', 'status');
    toastRegion.setAttribute('aria-live', 'polite');
    /* 다른 모달(<dialog>)이 열려 있어도 토스트가 그 위에 보이도록 브라우저 최상위 층(popover)을 쓴다. */
    if (supportsPopover(toastRegion)) toastRegion.setAttribute('popover', 'manual');
    document.body.appendChild(toastRegion);
    return toastRegion;
  }

  function raiseToastRegion(region) {
    if (!supportsPopover(region)) return;
    try {
      /* 이미 열려 있어도 닫았다 다시 열어야 나중에 열린 모달보다 위로 올라온다. */
      if (region.matches(':popover-open')) region.hidePopover();
      region.showPopover();
    } catch (error) {
      /* popover를 쓸 수 없으면 z-index로만 띄운다. */
    }
  }

  function settleToastRegion(region) {
    if (region.children.length > 0 || !supportsPopover(region)) return;
    try {
      if (region.matches(':popover-open')) region.hidePopover();
    } catch (error) {
      /* 이미 닫혔으면 무시한다. */
    }
  }

  function toast(message) {
    if (!message || !document.body) return null;
    const region = ensureToastRegion();
    const item = document.createElement('div');
    item.className = 'ui-toast';
    item.style.setProperty('--ui-toast-duration', TOAST_DURATION_MS + 'ms');

    const icon = document.createElement('span');
    icon.className = 'ui-toast-icon';
    icon.setAttribute('aria-hidden', 'true');
    icon.textContent = '✓';
    const text = document.createElement('p');
    text.className = 'ui-toast-text';
    text.textContent = message;
    const close = document.createElement('button');
    close.className = 'ui-toast-close';
    close.type = 'button';
    close.setAttribute('aria-label', '알림 닫기');
    close.textContent = '×';
    const bar = document.createElement('span');
    bar.className = 'ui-toast-bar';
    bar.setAttribute('aria-hidden', 'true');
    item.append(icon, text, close, bar);

    region.appendChild(item);
    raiseToastRegion(region);

    let timer = 0;
    let startedAt = 0;
    let remaining = TOAST_DURATION_MS;
    let removed = false;

    function remove() {
      if (removed) return;
      removed = true;
      global.clearTimeout(timer);
      item.classList.remove('is-shown');
      global.setTimeout(function () {
        item.remove();
        settleToastRegion(region);
      }, TOAST_LEAVE_MS);
    }

    function start() {
      startedAt = Date.now();
      timer = global.setTimeout(remove, remaining);
      item.classList.remove('is-paused');
    }

    function pause() {
      global.clearTimeout(timer);
      remaining = Math.max(0, remaining - (Date.now() - startedAt));
      item.classList.add('is-paused');
    }

    item.addEventListener('mouseenter', pause);
    item.addEventListener('mouseleave', start);
    close.addEventListener('click', remove);
    global.requestAnimationFrame(function () { item.classList.add('is-shown'); });
    start();
    return item;
  }

  /* 저장 뒤 화면 전체를 서버 값으로 다시 그려야 할 때, 성공 문구가 새로고침에 묻히지 않게 맡겨 둔다.
     저장소를 쓸 수 없으면 토스트를 먼저 띄우고 잠시 뒤 새로고침한다. */
  function reloadWithToast(message) {
    try {
      global.sessionStorage.setItem(PENDING_TOAST_KEY, String(message || ''));
    } catch (error) {
      toast(message);
      global.setTimeout(function () { global.location.reload(); }, 700);
      return;
    }
    global.location.reload();
  }

  function showPendingToast() {
    let message = null;
    try {
      message = global.sessionStorage.getItem(PENDING_TOAST_KEY);
      global.sessionStorage.removeItem(PENDING_TOAST_KEY);
    } catch (error) {
      message = null;
    }
    if (message) toast(message);
  }

  /* ---------------- 실패 안내 모달 ---------------- */

  function ensureAlertDialog() {
    if (alertDialog && alertDialog.isConnected) return alertDialog;
    alertDialog = document.createElement('dialog');
    alertDialog.className = 'ui-alert';
    alertDialog.id = 'ui-alert';
    alertDialog.setAttribute('role', 'alertdialog');
    alertDialog.setAttribute('aria-modal', 'true');
    alertDialog.setAttribute('aria-labelledby', 'ui-alert-title');
    alertDialog.setAttribute('aria-describedby', 'ui-alert-reason');
    alertDialog.innerHTML = '<div class="ui-alert-body">'
      + '<div class="ui-alert-icon" aria-hidden="true">!</div>'
      + '<h2 class="ui-alert-title" id="ui-alert-title"></h2>'
      + '<p class="ui-alert-reason" id="ui-alert-reason"></p>'
      + '<button class="ui-alert-confirm" type="button" data-ui-alert-close>확인</button>'
      + '</div>';
    document.body.appendChild(alertDialog);

    alertDialog.addEventListener('click', function (event) {
      const target = event.target instanceof Element ? event.target : null;
      /* 모달 바깥(배경)을 누르면 대상이 dialog 자신이 된다. */
      if (target === alertDialog || target?.closest('[data-ui-alert-close]')) closeAlert();
    });
    /* 브라우저가 Esc 등으로 직접 닫은 경우에도 정리한다. */
    alertDialog.addEventListener('close', finishClose);
    return alertDialog;
  }

  function isAlertOpen() {
    return Boolean(alertDialog && alertDialog.open);
  }

  /**
   * 실패 안내를 띄운다. reason에는 사용자가 이해할 수 있는 원인 문구를 넣는다.
   * options.returnFocus: 닫은 뒤 초점을 보낼 요소(문제가 된 입력칸 등). 없으면 연 순간 초점이 있던 곳.
   * options.onClose: 닫힌 뒤 실행할 함수.
   */
  function showAlert(title, reason, options) {
    if (!document.body) return;
    const dialog = ensureAlertDialog();
    const settings = options || {};
    dialog.querySelector('#ui-alert-title').textContent = title || DEFAULT_ERROR_TITLE;
    const reasonNode = dialog.querySelector('#ui-alert-reason');
    reasonNode.textContent = reason || '';
    reasonNode.hidden = !reason;
    if (!dialog.open) alertReturnFocus = settings.returnFocus || document.activeElement;
    else if (settings.returnFocus) alertReturnFocus = settings.returnFocus;
    alertOnClose = typeof settings.onClose === 'function' ? settings.onClose : null;

    if (!dialog.open) {
      if (typeof dialog.showModal === 'function') {
        try {
          dialog.showModal();
        } catch (error) {
          dialog.setAttribute('open', '');
        }
      } else {
        dialog.setAttribute('open', '');
        dialog.open = true;
      }
    }
    dialog.querySelector('[data-ui-alert-close]')?.focus();
  }

  function closeAlert() {
    if (!isAlertOpen()) return;
    if (typeof alertDialog.close === 'function') {
      alertDialog.close();
    } else {
      alertDialog.removeAttribute('open');
      alertDialog.open = false;
      finishClose();
    }
  }

  function finishClose() {
    const target = alertReturnFocus;
    const callback = alertOnClose;
    alertReturnFocus = null;
    alertOnClose = null;
    if (target && target.isConnected && !target.closest?.('[hidden]')) target.focus?.();
    if (callback) callback();
  }

  /* 안내 모달은 항상 맨 위에 있으므로, 아래 화면 스크립트가 Esc로 자기 모달을 닫거나
     Tab으로 초점을 끌어가지 않도록 가장 먼저(capture) 처리하고 전파를 막는다. */
  document.addEventListener('keydown', function (event) {
    if (!isAlertOpen()) return;
    if (event.key === 'Escape') {
      event.preventDefault();
      event.stopImmediatePropagation();
      closeAlert();
      return;
    }
    if (event.key === 'Tab') {
      /* 모달 안 버튼이 하나뿐이라 초점을 그 자리에 둔다. */
      event.preventDefault();
      event.stopImmediatePropagation();
      alertDialog.querySelector('[data-ui-alert-close]')?.focus();
    }
  }, true);

  /* ---------------- 서버가 넘긴 결과(flash) ---------------- */

  function showServerFeedback() {
    const flash = document.getElementById('ui-feedback-flash');
    if (flash) {
      const error = flash.getAttribute('data-error');
      const message = flash.getAttribute('data-toast');
      /* 새로고침해도 다시 뜨지 않도록 읽은 뒤 지운다. */
      flash.remove();
      if (message) toast(message);
      if (error) showAlert(flash.getAttribute('data-error-title') || DEFAULT_ERROR_TITLE, error);
    }
    showPendingToast();
  }

  if (document.readyState === 'loading') {
    document.addEventListener('DOMContentLoaded', showServerFeedback, { once: true });
  } else {
    showServerFeedback();
  }

  global.SpoitFeedback = {
    toast: toast,
    alert: showAlert,
    closeAlert: closeAlert,
    isAlertOpen: isAlertOpen,
    reloadWithToast: reloadWithToast,
    DEFAULT_ERROR_TITLE: DEFAULT_ERROR_TITLE
  };
}(window));
