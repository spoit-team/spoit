/* 주요 사용자 페이지 모바일 메뉴. 계정 상태는 서버가 렌더링한 헤더를 그대로 사용합니다. */
(function (global) {
  'use strict';

  const header = document.querySelector('body > header.sticky, body > header.mypage-topbar');
  const headerInner = header?.querySelector('.mobile-nav-header');
  const sourceNav = headerInner?.querySelector('nav.primary-nav');
  const actions = headerInner?.querySelector('.ml-auto');
  if (!headerInner || !sourceNav || !actions) return;

  const toggle = document.createElement('button');
  toggle.type = 'button';
  toggle.className = 'mobile-menu-toggle';
  toggle.setAttribute('aria-controls', 'mobile-primary-menu');
  toggle.setAttribute('aria-expanded', 'false');
  toggle.setAttribute('aria-label', '메뉴 열기');
  toggle.title = '메뉴';
  toggle.innerHTML = '<svg viewBox="0 0 24 24" fill="none" aria-hidden="true"><path d="M4 6h16M4 12h16M4 18h16" stroke="currentColor" stroke-width="1.8" stroke-linecap="round"/></svg>';

  const menu = document.createElement('div');
  menu.id = 'mobile-primary-menu';
  menu.className = 'mobile-menu-panel';
  menu.hidden = true;

  const account = document.createElement('div');
  account.className = 'mobile-menu-account';
  account.setAttribute('role', 'group');
  account.setAttribute('aria-label', '계정');

  const mobileNav = sourceNav.cloneNode(true);
  mobileNav.className = 'mobile-nav-links';
  mobileNav.setAttribute('aria-label', '모바일 주요 메뉴');
  mobileNav.querySelectorAll('a').forEach(function (link) {
    link.className = 'mobile-nav-link';
    link.removeAttribute('aria-current');
  });

  function makeElement(tagName, className, content) {
    const element = document.createElement(tagName);
    if (className) element.className = className;
    if (content != null) element.textContent = content;
    return element;
  }

  const memberArea = headerInner.querySelector('[data-auth-member]');
  const guestArea = headerInner.querySelector('[data-auth-guest]');
  const accountLink = memberArea?.querySelector('a[href]');
  const logoutForm = memberArea?.querySelector('form');

  function renderAccount() {
    account.replaceChildren();

    if (memberArea && accountLink) {
      const userLink = makeElement('a', 'mobile-menu-user-link');
      userLink.href = accountLink.getAttribute('href') || '/mypage';
      userLink.append(
        makeElement('span', 'mobile-menu-user-label', '로그인 계정'),
        makeElement('strong', 'mobile-menu-user-name', accountLink.textContent.trim())
      );
      account.append(userLink);

      if (logoutForm) {
        const logout = makeElement('button', 'mobile-menu-logout', '로그아웃');
        logout.type = 'submit';
        logout.setAttribute('form', logoutForm.id || 'mobile-logout-form');
        if (!logoutForm.id) {
          logoutForm.id = 'mobile-logout-form';
          logoutForm.classList.add('contents');
        }
        account.append(logout);
      }
      return;
    }

    account.append(makeElement('span', 'mobile-menu-user-label', '로그인하면 내 정보를 확인할 수 있습니다.'));
    const guestLinks = makeElement('div', 'mobile-menu-guest-links');
    const desktopLinks = guestArea ? Array.from(guestArea.querySelectorAll('a[href]')) : [];
    desktopLinks.forEach(function (link, index) {
      const clone = link.cloneNode(true);
      clone.className = index === 0 ? 'mobile-menu-login' : 'mobile-menu-signup';
      guestLinks.append(clone);
    });
    account.append(guestLinks);
  }

  function setMenuOpen(open) {
    menu.hidden = !open;
    toggle.setAttribute('aria-expanded', String(open));
    toggle.setAttribute('aria-label', open ? '메뉴 닫기' : '메뉴 열기');
    toggle.title = open ? '메뉴 닫기' : '메뉴';
    toggle.innerHTML = open
      ? '<svg viewBox="0 0 24 24" fill="none" aria-hidden="true"><path d="m6 6 12 12M18 6 6 18" stroke="currentColor" stroke-width="1.8" stroke-linecap="round"/></svg>'
      : '<svg viewBox="0 0 24 24" fill="none" aria-hidden="true"><path d="M4 6h16M4 12h16M4 18h16" stroke="currentColor" stroke-width="1.8" stroke-linecap="round"/></svg>';
  }

  function closeMenu(restoreFocus) {
    if (menu.hidden) return;
    setMenuOpen(false);
    if (restoreFocus) toggle.focus();
  }

  menu.append(account, mobileNav);
  headerInner.append(menu);
  actions.append(toggle);

  toggle.addEventListener('click', function () {
    setMenuOpen(menu.hidden);
  });

  menu.addEventListener('click', function (event) {
    if (event.target.closest('a')) closeMenu(false);
  });

  document.addEventListener('click', function (event) {
    if (!headerInner.contains(event.target)) closeMenu(false);
  });

  document.addEventListener('keydown', function (event) {
    if (event.key === 'Escape') closeMenu(true);
  });

  global.matchMedia('(min-width: 768px)').addEventListener?.('change', function (event) {
    if (event.matches) closeMenu(false);
  });

  renderAccount();
})(window);
