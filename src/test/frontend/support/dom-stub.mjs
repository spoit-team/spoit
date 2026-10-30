// 의존성 없는 브라우저 스크립트 실행 검사에서 공통으로 쓰는 DOM 스텁입니다.
// 여기에는 여러 테스트가 실제로 같은 방식으로 쓰는 최소 primitive만 둡니다.
// 포커스 이동 방식, requestSubmit, 전용 querySelector 스텁처럼 테스트마다 다른 능력은
// 각 테스트 파일에서 이 클래스를 확장해 정의합니다.

/* 스크립트가 instanceof로 참조하는 브라우저 생성자를 준비합니다.
   이 모듈을 가져오는 것만으로 준비되며, 이미 정의되어 있으면 그대로 둡니다. */
export function installDomGlobals() {
  globalThis.Element ??= class Element {};
  globalThis.HTMLFormElement ??= class HTMLFormElement extends globalThis.Element {};
}

installDomGlobals();

const toCamelCase = name => name.replace(/^data-/, '').replace(/-([a-z])/g, (_, c) => c.toUpperCase());

/* 선택자 해석: 콤마로 나눈 각 토큰을 순서대로 검사합니다.
   #id, .class, [attr], [data-*] / [data-*="값"], input|select[name]과
   :checked / :disabled / :not(:disabled)를 지원합니다. */
export function matchesSelector(element, selector) {
  return String(selector).split(',').some(part => matchesToken(element, part.trim()));
}

function matchesToken(element, token) {
  if (!element || !token) return false;
  if (token.startsWith('#')) return element.id === token.slice(1);
  if (token.startsWith('.')) return Boolean(element.classes?.has(token.slice(1)));
  if (token.startsWith('[data-')) {
    const [rawName, rawValue] = token.slice(1, -1).split('=');
    const name = rawName.trim();
    const value = element.dataset?.[toCamelCase(name)] ?? element.attributes?.[name];
    if (value === undefined) return false;
    if (rawValue === undefined) return true;
    return String(value) === rawValue.replace(/^["']|["']$/g, '');
  }
  if (token.startsWith('[')) {
    const name = token.slice(1, -1).split('=')[0].trim();
    return element.attributes?.[name] !== undefined;
  }
  if (token.startsWith('input') || token.startsWith('select')) {
    const name = token.match(/\[name(?:\^=|\*=|=)?["']?([A-Za-z-]+)["']?\]/)?.[1];
    if (!name) return (element.type || '').startsWith(token.slice(0, -1));
    const matchesName = token.includes('^=') ? String(element.name || '').startsWith(name)
      : token.includes('*=') ? String(element.name || '').includes(name)
        : element.name === name;
    if (!matchesName) return false;
    if (token.includes(':checked')) return element.checked === true;
    if (token.includes(':not(:disabled)')) return element.disabled !== true;
    if (token.includes(':disabled')) return element.disabled === true;
    return true;
  }
  return false;
}

/* Base는 Element 또는 HTMLFormElement입니다. 폼은 HTMLFormElement 체인을 유지해야
   스크립트의 `form instanceof HTMLFormElement` 판정을 그대로 통과합니다. */
export function createDomClass(Base = globalThis.Element) {
  return class DomNode extends Base {
    constructor(properties = {}) {
      super();
      Object.assign(this, {
        hidden: false,
        disabled: false,
        checked: false,
        value: '',
        name: '',
        type: '',
        dataset: {},
        textContent: '',
        id: '',
        className: ''
      }, properties);
      this.listeners = {};
      this.attributes = {};
      this.children = [];
      this.parentElement = null;
      this.focused = false;
      this.classes = new Set(String(this.className ?? '').split(' ').filter(Boolean));
      this.classList = {
        add: (...names) => names.forEach(name => this.classes.add(name)),
        remove: (...names) => names.forEach(name => this.classes.delete(name)),
        contains: name => this.classes.has(name),
        toggle: (name, active) => active ? this.classes.add(name) : this.classes.delete(name)
      };
    }
    addEventListener(type, handler) { (this.listeners[type] ??= []).push(handler); }
    /* 두 가지 예전 호출 형태를 모두 받습니다.
       - dispatch(type, { key, ... }) : 이벤트 속성 덮어쓰기
       - dispatch(type, element)      : 대상 요소 지정 (event.target)
       요소를 넘기면 target으로만 쓰고, 나머지는 속성으로 병합합니다. */
    dispatch(type, properties = {}) {
      const event = { target: this, defaultPrevented: false, preventDefault() { this.defaultPrevented = true; } };
      if (properties instanceof globalThis.Element) event.target = properties;
      else Object.assign(event, properties);
      for (const handler of this.listeners[type] ?? []) handler(event);
      return event;
    }
    setAttribute(name, value) { this.attributes[name] = value; }
    removeAttribute(name) { delete this.attributes[name]; }
    getAttribute(name) { return this.attributes[name] ?? null; }
    matches(selector) { return matchesSelector(this, selector); }
    closest(selector) {
      let node = this;
      while (node) {
        if (matchesSelector(node, selector)) return node;
        node = node.parentElement;
      }
      return null;
    }
    append(...items) {
      for (const item of items) item.parentElement = this;
      this.children.push(...items);
      return items[0];
    }
    focus() { this.focused = true; }
    reset() { this.resetCount = (this.resetCount ?? 0) + 1; }
    querySelector() { return null; }
    querySelectorAll() { return []; }
  };
}

export const DomElement = createDomClass();
export const DomFormElement = createDomClass(globalThis.HTMLFormElement);
