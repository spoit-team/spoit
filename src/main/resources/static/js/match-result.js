/* 경기 결과 화면: 확정 참가자는 결과 요약 API만 읽고, 팀장은 팀장 전용 조회 API가 열렸을 때만 제출 영역을 쓴다.
   - 어떤 결과를 보여 줄지는 서버 응답(outcome·ratingStatus·finalResult)만 따른다.
   - 모든 값은 textContent로만 그리고, 상대 팀장의 제출 값은 팀장 본인에게만 표시한다.
   - 사설망 API가 403/404를 주면 화면은 안내 문구로 끝내고, 빈 상태를 꾸며 내지 않는다. */
(function (global) {
  'use strict';

  const root = document.querySelector('[data-result-root]');
  if (!root) return;

  const apiBase = (root.getAttribute('data-api-base') || '/mypage/matches').replace(/\/+$/, '');
  const csrfHeader = root.getAttribute('data-csrf-header');
  const csrfToken = root.getAttribute('data-csrf-token');

  const summarySection = root.querySelector('[data-result-summary]');
  const stateBody = root.querySelector('[data-result-body]');
  const stateMessage = root.querySelector('[data-result-message]');
  const submitSection = root.querySelector('[data-result-submit]');
  const submitBody = root.querySelector('[data-result-submit-body]');
  const submitMessage = root.querySelector('[data-result-submit-message]');
  const evaluationSection = root.querySelector('#match-evaluation');

  const FALLBACK = {
    403: '이 경기의 확정 참가자만 결과를 확인할 수 있습니다.',
    404: '경기 정보를 찾을 수 없습니다.'
  };

  const RESULT_LABELS = { A_WIN: 'A팀 승', DRAW: '무승부', B_WIN: 'B팀 승' };
  const REQUEST_RESULTS = ['A_WIN', 'DRAW', 'B_WIN'];

  let summary = null;   // 확정 참가자용 결과 요약
  let captain = null;    // 팀장 전용 결과 조회 응답(팀장이 아니면 null)
  let mySide = '';
  let pending = null;    // 제출 확인 단계에 올린 결과
  let busy = false;

  /* ── 경로·요청 ───────────────────────────────────────────────────── */

  function matchId() {
    const fromDom = Number(root.getAttribute('data-match-id'));
    if (Number.isFinite(fromDom) && fromDom > 0) return fromDom;
    // 페이지 주소에서 경기 번호를 읽는다(예: /mypage/matches/42/result-page).
    const found = global.location.pathname.match(/\/mypage\/matches\/(\d+)(?:\/|$)/);
    return found ? Number(found[1]) : 0;
  }

  function apiUrl(suffix) {
    const id = matchId();
    if (!id) return null;
    return apiBase + '/' + id + '/' + suffix;
  }

  function request(url, options) {
    const config = Object.assign({
      method: 'GET',
      credentials: 'same-origin',
      headers: { Accept: 'application/json' }
    }, options || {});
    if (config.method !== 'GET' && csrfHeader && csrfToken) {
      config.headers = Object.assign({}, config.headers);
      config.headers[csrfHeader] = csrfToken;
    }
    return global.fetch(url, config).then(function (response) {
      // 로그인 세션이 끝나면 로그인 화면 HTML이 온다. 그것을 결과 JSON으로 쓰지 않는다.
      if (response.redirected) {
        return { ok: false, status: 401, data: null, message: '로그인이 만료되었습니다. 다시 로그인해 주세요.' };
      }
      return response.json().catch(function () { return null; }).then(function (data) {
        const message = (data && data.message) || FALLBACK[response.status] || '요청을 처리하지 못했습니다.';
        return { ok: response.ok, status: response.status, data: data, message: message };
      });
    }).catch(function () {
      return { ok: false, status: 0, data: null, message: '서버에 연결하지 못했습니다. 네트워크 상태를 확인해 주세요.' };
    });
  }

  /* ── 서버 값 읽기 ───────────────────────────────────────────────── */

  function resultText(value) {
    if (!value) return '—';
    if (value === 'DRAW') return '무승부';
    if (!RESULT_LABELS[value]) return String(value);
    const winner = value === mySide + '_WIN' ? '우리 팀 승리' : '상대 팀 승리';
    return RESULT_LABELS[value] + ' · ' + winner;
  }

  function ratingText(status) {
    if (status === 'APPLIED') return '레이팅 반영 완료';
    if (status === 'EXCLUDED') return '레이팅 반영 제외';
    if (status === 'WAITING') return '레이팅 반영 대기';
    return '레이팅 처리 전';
  }

  function resultStatusText(status) {
    if (status === 'CONFIRMED') return '결과 확정';
    if (status === 'CONFLICT') return '팀장 제출 불일치';
    if (status === 'SUBMITTED') return '한 팀장 제출 완료';
    return '결과 미제출';
  }

  function sideText(side) {
    return side === 'A' || side === 'B' ? side + '팀' : '—';
  }

  /* ── 그리기 ─────────────────────────────────────────────────────── */

  function setField(name, value) {
    root.querySelectorAll('[data-field="' + name + '"]').forEach(function (node) {
      node.textContent = value == null || value === '' ? '—' : String(value);
    });
  }

  function stateCard(label, tone, title, note) {
    const wrapper = document.createElement('div');
    wrapper.className = 'match-result-state ' + tone;
    const labelNode = document.createElement('span');
    labelNode.className = 'match-result-state-label';
    labelNode.textContent = label;
    const titleNode = document.createElement('strong');
    titleNode.textContent = title;
    wrapper.append(labelNode, titleNode);
    if (note) {
      const noteNode = document.createElement('p');
      noteNode.textContent = note;
      wrapper.appendChild(noteNode);
    }
    return wrapper;
  }

  function comparisonBlock() {
    const wrapper = document.createElement('div');
    wrapper.className = 'match-result-compare';
    [
      ['내 제출', resultText(captain.myResult)],
      ['상대 팀장 제출', resultText(captain.opponentResult)]
    ].forEach(function (pair) {
      const cell = document.createElement('div');
      const label = document.createElement('span');
      label.textContent = pair[0];
      const value = document.createElement('strong');
      value.textContent = pair[1];
      cell.append(label, value);
      wrapper.appendChild(cell);
    });
    return wrapper;
  }

  function choiceButton(result) {
    const button = document.createElement('button');
    button.type = 'button';
    button.className = 'match-result-choice';
    button.setAttribute('data-result', result);
    button.disabled = busy;
    const strong = document.createElement('strong');
    strong.textContent = RESULT_LABELS[result];
    const small = document.createElement('small');
    small.textContent = result === 'DRAW' ? '두 팀 무승부' : (result === mySide + '_WIN' ? '우리 팀 승리' : '상대 팀 승리');
    button.append(strong, small);
    return button;
  }

  function choicesBlock() {
    const group = document.createElement('div');
    group.className = 'match-result-choices';
    group.setAttribute('role', 'group');
    group.setAttribute('aria-label', '경기 결과 선택');
    REQUEST_RESULTS.forEach(function (result) {
      group.appendChild(choiceButton(result));
    });
    return group;
  }

  function confirmBlock() {
    const wrapper = document.createElement('div');
    wrapper.className = 'match-result-confirm';
    wrapper.setAttribute('role', 'group');
    wrapper.setAttribute('aria-labelledby', 'match-result-confirm-title');

    const label = document.createElement('p');
    label.className = 'match-result-confirm-label';
    label.textContent = '제출할 결과';
    const title = document.createElement('strong');
    title.id = 'match-result-confirm-title';
    title.textContent = resultText(pending);
    const note = document.createElement('p');
    note.textContent = '이 결과로 제출할까요? 상대 팀장과 결과가 같으면 바로 확정되고, 다르면 마감 전까지 다시 제출할 수 있습니다.';

    const actions = document.createElement('div');
    actions.className = 'match-result-confirm-actions';
    const cancel = document.createElement('button');
    cancel.type = 'button';
    cancel.className = 'mypage-secondary-button';
    cancel.setAttribute('data-action', 'cancel');
    cancel.disabled = busy;
    cancel.textContent = '돌아가기';
    const submit = document.createElement('button');
    submit.type = 'button';
    submit.className = 'mypage-primary-button';
    submit.setAttribute('data-action', 'submit');
    submit.disabled = busy;
    submit.textContent = busy ? '제출하는 중…' : '제출하기';
    actions.append(cancel, submit);

    wrapper.append(label, title, note, actions);
    return wrapper;
  }

  /* 제출할 수 없는 이유. 팀장 조회 응답의 마감 시각으로 문구를 고른다. */
  function closedReason() {
    if (!captain) return null;
    const deadline = captain.deadlineAt ? new Date(captain.deadlineAt) : null;
    if (deadline && !Number.isNaN(deadline.getTime()) && deadline.getTime() <= Date.now()) {
      return '제출 마감 시각이 지나 결과를 제출할 수 없습니다.';
    }
    return '지금은 결과를 제출할 수 없습니다. 경기가 끝난 뒤 마감 전까지 제출할 수 있습니다.';
  }

  /* 상태 판단 순서: 레이팅 처리 → 확정 결과 → 불일치 → 내 제출 → 제출 가능 → 불가. */
  function renderSubmit() {
    if (!submitSection || !submitBody) return;
    submitSection.hidden = false;
    submitBody.replaceChildren();

    if (busy || pending) {
      submitBody.appendChild(confirmBlock());
      return;
    }

    if (captain.ratingStatus === 'APPLIED') {
      submitBody.appendChild(stateCard('확정', 'is-confirmed',
        '최종 결과 ' + resultText(captain.finalResult), '레이팅에 반영되었습니다.'));
      return;
    }
    if (captain.ratingStatus === 'EXCLUDED') {
      submitBody.appendChild(stateCard('제외', 'is-excluded', '레이팅 반영에서 제외된 경기입니다.',
        captain.finalResult ? null : '제출 마감까지 결과가 확정되지 않았거나 시설 관리자가 제외했습니다.'));
      return;
    }
    if (captain.finalResult) {
      submitBody.appendChild(stateCard('확정', 'is-confirmed',
        '최종 결과 ' + resultText(captain.finalResult), '레이팅 반영을 기다리고 있습니다.'));
      return;
    }
    if (captain.conflict) {
      submitBody.appendChild(stateCard('불일치', 'is-conflict', '상대 팀장과 제출한 결과가 다릅니다.',
        captain.canSubmit ? '결과를 다시 확인해 제출해 주세요.' : null));
      submitBody.appendChild(comparisonBlock());
      if (captain.canSubmit) submitBody.appendChild(choicesBlock());
      return;
    }
    if (captain.myResult) {
      submitBody.appendChild(stateCard('제출 완료', 'is-waiting',
        '내 제출 ' + resultText(captain.myResult), '상대 팀장의 제출을 기다리고 있습니다.'));
      return;
    }
    if (captain.canSubmit) {
      if (captain.opponentSubmitted) {
        const hint = document.createElement('p');
        hint.className = 'match-result-hint';
        hint.textContent = '상대 팀장은 이미 결과를 제출했습니다.';
        submitBody.appendChild(hint);
      }
      submitBody.appendChild(choicesBlock());
      return;
    }
    submitBody.appendChild(stateCard('제출 불가', 'is-closed', '지금은 결과를 제출할 수 없습니다.',
      closedReason()));
  }

  function renderSummary() {
    if (!summary) return;
    if (summarySection) summarySection.hidden = false;
    setField('matchId', '#' + summary.matchId);
    setField('side', sideText(summary.mySide));
    setField('resultStatus', resultStatusText(summary.resultStatus));
    setField('ratingStatus', ratingText(summary.ratingStatus));
    setField('title', summary.finalResult
      ? '최종 결과 ' + resultText(summary.finalResult)
      : '경기 결과를 확인하고 있습니다');
  }

  function renderState() {
    if (!stateBody) return;
    stateBody.replaceChildren();
    if (!summary) {
      const error = document.createElement('p');
      error.className = 'match-result-error';
      error.textContent = '경기 결과를 불러오지 못했습니다.';
      stateBody.appendChild(error);
      return;
    }

    if (summary.ratingStatus === 'APPLIED') {
      stateBody.appendChild(stateCard('레이팅 반영', 'is-confirmed',
        '최종 결과 ' + resultText(summary.finalResult), '레이팅에 반영되었습니다.'));
    } else if (summary.ratingStatus === 'EXCLUDED') {
      stateBody.appendChild(stateCard('레이팅 제외', 'is-excluded', '레이팅 반영에서 제외된 경기입니다.',
        summary.finalResult ? '최종 결과 ' + resultText(summary.finalResult) : '제출 마감까지 결과가 확정되지 않았습니다.'));
    } else if (summary.finalResult) {
      stateBody.appendChild(stateCard('결과 확정', 'is-confirmed',
        '최종 결과 ' + resultText(summary.finalResult), '레이팅 반영을 기다리고 있습니다.'));
    } else if (summary.resultStatus === 'CONFLICT') {
      stateBody.appendChild(stateCard('확인 중', 'is-conflict',
        '두 팀장의 제출 결과가 달라 확인 중입니다.', '경기 결과가 확정되면 이 화면에서 확인할 수 있습니다.'));
    } else if (summary.resultStatus === 'SUBMITTED') {
      stateBody.appendChild(stateCard('제출 완료', 'is-waiting',
        '한 팀장이 결과를 제출했습니다.', '두 팀장의 결과가 같으면 확정됩니다.'));
    } else {
      stateBody.appendChild(stateCard('결과 대기', 'is-waiting',
        '아직 결과가 확정되지 않았습니다.', '경기가 끝나고 팀장이 결과를 제출하면 확정됩니다.'));
    }

    if (summary.mySide === 'A' || summary.mySide === 'B') {
      const facts = document.createElement('dl');
      facts.className = 'match-result-facts';
      [
        ['내 진영', sideText(summary.mySide)],
        ['결과 상태', resultStatusText(summary.resultStatus)],
        ['레이팅', ratingText(summary.ratingStatus)]
      ].forEach(function (pair) {
        const cell = document.createElement('div');
        const term = document.createElement('dt');
        term.textContent = pair[0];
        const value = document.createElement('dd');
        value.textContent = pair[1];
        cell.append(term, value);
        facts.appendChild(cell);
      });
      stateBody.appendChild(facts);
    }
  }

  function showMessage(target, result, tone) {
    if (!target) return;
    target.textContent = result.message;
    target.classList.toggle('is-error', tone === 'error');
    target.classList.toggle('is-success', tone === 'success');
    target.hidden = false;
  }

  /* ── 조회·제출 ─────────────────────────────────────────────────── */

  function loadSummary() {
    const url = apiUrl('result/summary');
    if (!url) {
      if (stateMessage) showMessage(stateMessage, { message: FALLBACK[404] }, 'error');
      return Promise.resolve(false);
    }
    return request(url).then(function (result) {
      if (!result.ok || !result.data || !result.data.matchId) {
        summary = null;
        if (stateMessage) showMessage(stateMessage, result, 'error');
        renderState();
        return false;
      }
      summary = result.data;
      mySide = summary.mySide || mySide;
      renderSummary();
      renderState();
      return true;
    });
  }

  function loadCaptain() {
    const url = apiUrl('result');
    if (!url) return Promise.resolve();
    return request(url).then(function (result) {
      if (evaluationSection && (result.status === 403 || result.status === 401)) {
        evaluationSection.hidden = true;
      }
      // 403은 "팀장이 아님"이다. 오류가 아니라 제출 영역을 감추는 신호로 쓴다.
      if (result.status === 403 || result.status === 401 || !result.ok || !result.data) {
        captain = null;
        if (submitSection) submitSection.hidden = true;
        return;
      }
      captain = result.data;
      if (captain.mySide) mySide = captain.mySide;
      renderSubmit();
      if (captain.mySide && evaluationSection?.hidden) {
        evaluationSection.hidden = false;
        evaluationSection.dispatchEvent(new Event('match-result:captain'));
      }
    });
  }

  function submitResult() {
    const url = apiUrl('result');
    if (!url || !pending) return;
    busy = true;
    renderSubmit();
    request(url, {
      method: 'POST',
      headers: { Accept: 'application/json', 'Content-Type': 'application/json' },
      body: JSON.stringify({ result: pending })
    }).then(function (result) {
      busy = false;
      pending = null;
      const outcome = result.data && result.data.outcome;
      // 불일치는 제출 자체는 성공이지만 다시 확인해야 하므로 성공색을 쓰지 않는다.
      showMessage(submitMessage, result, result.ok && outcome !== 'CONFLICT' ? 'success' : 'error');
      return Promise.all([loadSummary(), loadCaptain()]);
    });
  }

  /* ── 이벤트 ─────────────────────────────────────────────────────── */

  root.addEventListener('click', function (event) {
    const target = event.target instanceof Element ? event.target : null;
    if (!target || !captain) return;

    const choice = target.closest('[data-result]');
    if (choice && !busy) {
      pending = choice.getAttribute('data-result');
      if (submitMessage) submitMessage.hidden = true;
      renderSubmit();
      const submitButton = submitBody && submitBody.querySelector('[data-action="submit"]');
      if (submitButton) submitButton.focus();
      return;
    }

    const action = target.closest('[data-action]');
    if (!action) return;
    if (action.getAttribute('data-action') === 'cancel' && !busy) {
      const chosen = pending;
      pending = null;
      renderSubmit();
      const previous = chosen && submitBody && submitBody.querySelector('[data-result="' + chosen + '"]');
      if (previous) previous.focus();
      return;
    }
    if (action.getAttribute('data-action') === 'submit' && pending && !busy) {
      submitResult();
    }
  });

  // 평가 제출로 레이팅이 반영됐을 수 있어 평가 영역(match-evaluation.js)이 알리면 다시 조회한다.
  document.addEventListener('match-evaluation:submitted', function () {
    if (busy) return;
    if (submitMessage) submitMessage.hidden = true;
    loadSummary();
    loadCaptain();
  });

  loadSummary().then(function (ok) {
    if (!ok) return;
    // 팀장 전용 조회는 확정 참가자 조회가 성공한 뒤에만 시도한다.
    loadCaptain();
  });
})(window);
