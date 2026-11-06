/* 팀장의 경기 결과 제출: 결과 조회 API 응답으로 제출 영역을 그리고, 화면 안 확인 단계를 거쳐 제출한다.
   제출하면 서버 message를 보여 준 뒤 다시 조회해 그린다. 상태 판단은 조회 응답만 따른다. */
(function (global) {
  'use strict';

  const root = document.getElementById('match-result');
  if (!root) return;

  const escapeHTML = global.PlayOnUI.escapeHTML;
  const url = root.dataset.resultUrl;
  const mySide = root.dataset.mySide;
  const closedNote = root.dataset.closedNote;
  const csrfHeader = root.dataset.csrfHeader;
  const csrfToken = root.dataset.csrfToken;
  const body = root.querySelector('[data-result-body]');
  const messageBox = root.querySelector('[data-result-message]');
  const steps = document.querySelector('[data-result-steps]');

  const RESULTS = ['A_WIN', 'DRAW', 'B_WIN'];
  const RESULT_LABELS = { A_WIN: 'A팀 승', DRAW: '무승부', B_WIN: 'B팀 승' };
  const STEP_LABELS = { done: '완료', current: '진행 중', waiting: '대기', excluded: '제외' };
  const FALLBACK_MESSAGES = {
    403: '이 경기의 팀장만 결과를 제출할 수 있습니다.',
    404: '경기 정보를 찾을 수 없습니다.'
  };

  let view = null;        // 결과 조회 응답
  let evaluation = null;  // 평가 조회 응답의 submitted·canSubmit. 평가 영역(match-evaluation.js)이 알려 준다.
  let pending = null;     // 확인 단계에 올린 결과
  let busy = false;

  function teamLabel(result) {
    if (result === 'DRAW') return '두 팀 무승부';
    return result === `${mySide}_WIN` ? '우리 팀 승리' : '상대 팀 승리';
  }

  function resultText(result) {
    if (!RESULT_LABELS[result]) return escapeHTML(result ?? '—');
    return result === 'DRAW' ? '무승부' : `${RESULT_LABELS[result]} · ${teamLabel(result)}`;
  }

  function stateCard(label, tone, title, note) {
    return `<div class="match-result-state ${tone}">
      <span class="match-result-state-label">${label}</span>
      <strong>${title}</strong>
      ${note ? `<p>${escapeHTML(note)}</p>` : ''}
    </div>`;
  }

  function choices(heading) {
    return `<div class="match-result-choices" role="group" aria-label="${heading}">${RESULTS.map(result => `
      <button type="button" class="match-result-choice" data-result="${result}"${busy ? ' disabled' : ''}>
        <strong>${RESULT_LABELS[result]}</strong><small>${teamLabel(result)}</small>
      </button>`).join('')}</div>`;
  }

  function confirmPanel() {
    return `<div class="match-result-confirm" role="group" aria-labelledby="match-result-confirm-title">
      <p class="match-result-confirm-label">제출할 결과</p>
      <strong id="match-result-confirm-title">${resultText(pending)}</strong>
      <p>이 결과로 제출할까요? 상대 팀장과 결과가 같으면 바로 확정되고, 다르면 마감 전까지 다시 제출할 수 있습니다.</p>
      <div class="match-result-confirm-actions">
        <button type="button" class="mypage-secondary-button" data-action="cancel"${busy ? ' disabled' : ''}>돌아가기</button>
        <button type="button" class="mypage-primary-button" data-action="submit"${busy ? ' disabled' : ''}>${busy ? '제출하는 중…' : '제출하기'}</button>
      </div>
    </div>`;
  }

  function submitArea(heading) {
    return pending ? confirmPanel() : choices(heading);
  }

  function comparison() {
    return `<div class="match-result-compare">
      <div><span>내 제출</span><strong>${resultText(view.myResult)}</strong></div>
      <div><span>상대 팀장 제출</span><strong>${resultText(view.opponentResult)}</strong></div>
    </div>`;
  }

  /* 레이팅 처리 상태를 먼저 보고, 처리 전이면 확정 → 불일치 → 내 제출 → 제출 가능 순서로 판단한다. */
  function render() {
    const parts = [];
    if (view.ratingStatus === 'APPLIED') {
      parts.push(stateCard('확정', 'is-confirmed', `최종 결과 ${resultText(view.finalResult)}`, '레이팅에 반영되었습니다.'));
    } else if (view.ratingStatus === 'EXCLUDED') {
      parts.push(stateCard('제외', 'is-excluded', '레이팅 반영에서 제외된 경기입니다.',
        view.finalResult ? null : '제출 마감까지 결과가 확정되지 않았거나 시설 관리자가 제외했습니다.'));
    } else if (view.finalResult) {
      parts.push(stateCard('확정', 'is-confirmed', `최종 결과 ${resultText(view.finalResult)}`, '레이팅 반영을 기다리고 있습니다.'));
    } else if (view.conflict) {
      parts.push(stateCard('불일치', 'is-conflict', '상대 팀장과 제출한 결과가 다릅니다.',
        view.canSubmit ? '결과를 다시 확인해 제출해 주세요.' : closedNote));
      parts.push(comparison());
      if (view.canSubmit) parts.push(submitArea('다시 제출할 결과'));
    } else if (view.myResult) {
      parts.push(stateCard('제출 완료', 'is-waiting', `내 제출 ${resultText(view.myResult)}`,
        '상대 팀장의 제출을 기다리고 있습니다.'));
    } else if (view.canSubmit) {
      if (view.opponentSubmitted) parts.push('<p class="match-result-hint">상대 팀장은 이미 결과를 제출했습니다.</p>');
      parts.push(submitArea('경기 결과 선택'));
    } else {
      parts.push(stateCard('제출 불가', 'is-closed', escapeHTML(closedNote), null));
    }
    body.innerHTML = parts.join('');
  }

  /* 단계 바는 응답이 온 쪽부터 그린다. 응답이 없는 단계는 대기로 둔다.
     서버가 결과 확정 전에도 평가를 받으므로 1·2단계가 함께 진행 중일 수 있다. */
  function resultStep() {
    if (!view) return 'waiting';
    if (view.finalResult) return 'done';
    const processed = view.ratingStatus === 'APPLIED' || view.ratingStatus === 'EXCLUDED';
    return !processed && (view.canSubmit || view.myResult) ? 'current' : 'waiting';
  }

  function evaluationStep() {
    if (!evaluation) return 'waiting';
    if (evaluation.submitted) return 'done';
    return evaluation.canSubmit ? 'current' : 'waiting';
  }

  // 확정 뒤 내 평가가 끝났거나 낼 수 없으면 반영만 남는다. 평가 응답이 없으면 알 수 없어 대기로 둔다.
  function ratingStep() {
    if (!view) return 'waiting';
    if (view.ratingStatus === 'APPLIED') return 'done';
    if (view.ratingStatus === 'EXCLUDED') return 'excluded';
    return view.finalResult && evaluation && !evaluation.canSubmit ? 'current' : 'waiting';
  }

  function renderSteps() {
    if (!steps) return;
    const states = { result: resultStep(), evaluation: evaluationStep(), rating: ratingStep() };
    for (const step of steps.querySelectorAll('[data-step]')) {
      const state = states[step.dataset.step];
      step.classList.toggle('is-done', state === 'done');
      step.classList.toggle('is-current', state === 'current');
      step.querySelector('[data-step-state]').textContent = STEP_LABELS[state];
      if (state === 'current') step.setAttribute('aria-current', 'step');
      else step.removeAttribute('aria-current');
    }
  }

  // 불일치는 제출 자체는 성공이지만 다시 확인해야 하므로 성공색을 쓰지 않는다.
  function showMessage(result) {
    messageBox.textContent = result.message;
    messageBox.classList.toggle('is-error', !result.ok);
    messageBox.classList.toggle('is-success', result.ok && result.data.outcome !== 'CONFLICT');
    messageBox.hidden = false;
  }

  async function request(method, payload) {
    const options = {
      method,
      headers: { Accept: 'application/json' },
      credentials: 'same-origin'
    };
    if (method !== 'GET' && csrfHeader && csrfToken) options.headers[csrfHeader] = csrfToken;
    if (payload !== undefined) {
      options.headers['Content-Type'] = 'application/json';
      options.body = JSON.stringify(payload);
    }
    try {
      const response = await fetch(url, options);
      // 로그인 세션이 끝나면 로그인 화면으로 리다이렉트된 HTML을 받는다.
      if (response.redirected) {
        return { ok: false, data: {}, message: '로그인이 만료되었습니다. 다시 로그인해 주세요.' };
      }
      const data = await response.json().catch(() => ({}));
      const message = data.message || FALLBACK_MESSAGES[response.status] || '요청을 처리하지 못했습니다. 잠시 후 다시 시도해 주세요.';
      return { ok: response.ok, data, message };
    } catch (error) {
      return { ok: false, data: {}, message: '서버에 연결하지 못했습니다. 네트워크 상태를 확인해 주세요.' };
    }
  }

  async function load() {
    const result = await request('GET');
    busy = false;
    if (!result.ok || !result.data.mySide) {
      view = null;
      body.innerHTML = `<p class="match-result-error">${escapeHTML(result.message)}</p>`;
      renderSteps();
      return;
    }
    view = result.data;
    render();
    renderSteps();
  }

  async function submit() {
    busy = true;
    render();
    const result = await request('POST', { result: pending });
    pending = null;
    showMessage(result);
    await load();
  }

  root.addEventListener('click', function (event) {
    if (busy || !view) return;
    const choice = event.target.closest('[data-result]');
    if (choice) {
      pending = choice.dataset.result;
      messageBox.hidden = true;
      render();
      body.querySelector('[data-action="submit"]')?.focus();
      return;
    }
    const action = event.target.closest('[data-action]')?.dataset.action;
    if (action === 'cancel') {
      const chosen = pending;
      pending = null;
      render();
      body.querySelector(`[data-result="${chosen}"]`)?.focus();
    } else if (action === 'submit' && pending) {
      submit();
    }
  });

  // 평가 제출로 레이팅이 반영됐을 수 있어 평가 영역(match-evaluation.js)이 알리면 다시 조회한다.
  document.addEventListener('match-evaluation:submitted', function () {
    if (busy) return;
    messageBox.hidden = true;
    load();
  });

  document.addEventListener('match-evaluation:loaded', function (event) {
    evaluation = event.detail;
    renderSteps();
  });

  load();
})(window);