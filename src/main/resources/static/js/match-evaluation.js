/* 팀장의 선수 평가: 평가 조회 API 응답으로 대상과 항목을 그리고, 빈 칸 검사와 화면 안 확인 단계를 거쳐 한 번에 제출한다.
   점수에는 기본값을 두지 않는다. 기본 점수를 미리 채우면 평가가 형식적으로 끝나기 쉽기 때문이다. */
(function (global) {
  'use strict';

  const root = document.getElementById('match-evaluation');
  if (!root) return;

  const escapeHTML = global.PlayOnUI.escapeHTML;
  const url = root.dataset.evaluationUrl;
  const csrfHeader = root.dataset.csrfHeader;
  const csrfToken = root.dataset.csrfToken;
  const body = root.querySelector('[data-evaluation-body]');
  const messageBox = root.querySelector('[data-evaluation-message]');

  const NOT_OBSERVED = 'NOT_OBSERVED';
  // 슬라이더는 값을 비워 둘 수 없어 고르기 전에도 가운데에 둔다. 빈 칸 여부는 values로만 판단한다.
  const START_POSITION = '5';
  const SCORE_GUIDE = '5 = 보통 · 7 = 눈에 띄게 잘함 · 3 = 눈에 띄게 아쉬움';
  const FALLBACK_MESSAGES = {
    403: '이 경기의 팀장만 평가를 조회하고 제출할 수 있습니다.',
    404: '경기 정보를 찾을 수 없습니다.'
  };

  let view = null;          // 평가 조회 응답
  let values = {};          // "대상ID:항목ID" → '0'~'10' 또는 NOT_OBSERVED. 다시 조회해도 남겨 둔다.
  let missing = new Set();  // 제출을 눌렀을 때 비어 있던 칸
  let confirming = false;
  let busy = false;

  function keyOf(target, item) {
    return `${target.participantId}:${item.itemId}`;
  }

  function stateCard(label, tone, title, note) {
    return `<div class="match-result-state ${tone}">
      <span class="match-result-state-label">${label}</span>
      <strong>${title}</strong>
      ${note ? `<p>${escapeHTML(note)}</p>` : ''}
    </div>`;
  }

  // 결과 섹션의 팀 표기처럼 우리 팀을 먼저 두고, 팀 이름은 A·B로 쓴다.
  function teams() {
    const opponentSide = view.mySide === 'A' ? 'B' : 'A';
    return [
      { side: view.mySide, label: '우리 팀', mine: true },
      { side: opponentSide, label: '상대 팀', mine: false }
    ]
      .map(team => ({ ...team, targets: view.targets.filter(target => target.matchSide === team.side) }))
      .filter(team => team.targets.length > 0);
  }

  function stateClass(value) {
    if (!value) return 'is-empty';
    return value === NOT_OBSERVED ? 'is-skipped' : 'is-scored';
  }

  function scoreText(value) {
    return !value || value === NOT_OBSERVED ? '–' : value;
  }

  function valueText(itemName, value) {
    if (!value) return `${itemName} 점수 없음`;
    return value === NOT_OBSERVED ? `${itemName} 관찰 불가` : `${itemName} ${value}점`;
  }

  function scoreCell(target, item, locked) {
    const key = keyOf(target, item);
    const value = values[key] ?? '';
    const skipped = value === NOT_OBSERVED;
    const isMissing = missing.has(key);
    const position = value && !skipped ? value : START_POSITION;
    return `<div class="match-evaluation-score ${stateClass(value)}${isMissing ? ' is-missing' : ''}" data-score-cell>
      <input type="range" class="match-evaluation-slider" min="0" max="10" step="1"
        value="${position}" style="--match-evaluation-slider-ratio: ${position / 10}" data-key="${key}" data-item-name="${escapeHTML(item.itemName)}"
        aria-label="${escapeHTML(`${target.nickname} ${item.itemName}`)}" aria-valuetext="${escapeHTML(valueText(item.itemName, value))}"${isMissing ? ' aria-invalid="true"' : ''}${locked || skipped ? ' disabled' : ''}>
      <span class="match-evaluation-score-value" data-score-value aria-hidden="true">${scoreText(value)}</span>
      <button type="button" class="match-evaluation-skip" data-skip aria-pressed="${skipped}"
        aria-label="${escapeHTML(`${target.nickname} ${item.itemName} 못 봄`)}"${locked ? ' disabled' : ''}>못 봄</button>
    </div>`;
  }

  function teamTable(team, locked) {
    const side = escapeHTML(team.side);
    return `<section class="match-result-team match-evaluation-team${team.mine ? ' is-mine' : ''}" aria-label="${side}팀 평가 대상">
      <h3><i class="is-team-${side.toLowerCase()}" aria-hidden="true"></i><span>${side}팀</span><small>${team.label}</small></h3>
      <table class="match-evaluation-table">
        <thead><tr>
          <th scope="col">선수</th>
          ${view.items.map(item => `<th scope="col">${escapeHTML(item.itemName)}</th>`).join('')}
          <th scope="col">한 번에</th>
        </tr></thead>
        <tbody>${team.targets.map(target => `
          <tr>
            <th scope="row">${escapeHTML(target.nickname)}</th>
            ${view.items.map(item => `<td data-label="${escapeHTML(item.itemName)}">${scoreCell(target, item, locked)}</td>`).join('')}
            <td class="match-evaluation-fill">
              <button type="button" class="match-evaluation-fill-button" data-fill="${target.participantId}"
                aria-label="${escapeHTML(target.nickname)} 모든 항목 관찰 불가"${locked ? ' disabled' : ''}>모두 관찰 불가</button>
            </td>
          </tr>`).join('')}
        </tbody>
      </table>
    </section>`;
  }

  function missingAlert() {
    if (missing.size === 0) return '';
    const rows = teams()
      .flatMap(team => team.targets)
      .map(target => ({
        nickname: target.nickname,
        items: view.items.filter(item => missing.has(keyOf(target, item))).map(item => item.itemName)
      }))
      .filter(row => row.items.length > 0);
    return `<div class="match-evaluation-missing" role="alert">
      <strong>빈 칸이 ${missing.size}개 있습니다. 모두 고른 뒤 제출해 주세요.</strong>
      <ul>${rows.map(row => `<li><b>${escapeHTML(row.nickname)}</b> ${row.items.map(escapeHTML).join(', ')}</li>`).join('')}</ul>
    </div>`;
  }

  function missingKeys() {
    const keys = new Set();
    for (const target of view.targets) {
      for (const item of view.items) {
        if (!values[keyOf(target, item)]) keys.add(keyOf(target, item));
      }
    }
    return keys;
  }

  // 진행률도 제출 검사와 같은 빈 칸 기준으로 센다. 모든 칸이 차야 제출할 수 있기 때문이다.
  function progressCounts() {
    const total = view.targets.length * view.items.length;
    return { filled: total - missingKeys().size, total };
  }

  function progressBar(locked) {
    const { filled, total } = progressCounts();
    return `<div class="match-evaluation-progress" data-evaluation-progress>
      <p class="match-evaluation-progress-count">항목 <b data-progress-filled>${filled}</b> / ${total}</p>
      <div class="match-evaluation-progress-bar" role="progressbar" aria-label="평가 진행률"
        aria-valuemin="0" aria-valuemax="${total}" aria-valuenow="${filled}"><span style="width: ${filled / total * 100}%"></span></div>
      <button type="button" class="mypage-primary-button" data-action="review"${locked ? ' disabled' : ''}>평가 제출</button>
    </div>`;
  }

  // 칸을 고를 때마다 표를 다시 그리면 슬라이더 포커스를 잃으므로 숫자와 막대만 바꾼다.
  function updateProgress() {
    const progress = body.querySelector('[data-evaluation-progress]');
    if (!progress) return;
    const { filled, total } = progressCounts();
    progress.querySelector('[data-progress-filled]').textContent = filled;
    progress.querySelector('[role="progressbar"]').setAttribute('aria-valuenow', filled);
    progress.querySelector('.match-evaluation-progress-bar span').style.width = `${filled / total * 100}%`;
  }

  function doneBar() {
    return `<div class="match-evaluation-progress is-done">
      <p class="match-evaluation-progress-count">평가 제출 완료</p>
      <div class="match-evaluation-progress-bar" aria-hidden="true"><span style="width: 100%"></span></div>
      <small>제출한 평가는 고칠 수 없습니다.</small>
    </div>`;
  }

  function confirmPanel() {
    return `<div class="match-result-confirm" role="group" aria-labelledby="match-evaluation-confirm-title">
      <p class="match-result-confirm-label">평가 제출</p>
      <strong id="match-evaluation-confirm-title">제출한 평가는 고칠 수 없습니다</strong>
      <p>선수 ${view.targets.length}명의 평가를 이대로 제출할까요? 고른 점수를 한 번 더 확인해 주세요.</p>
      <div class="match-result-confirm-actions">
        <button type="button" class="mypage-secondary-button" data-action="cancel"${busy ? ' disabled' : ''}>돌아가기</button>
        <button type="button" class="mypage-primary-button" data-action="submit"${busy ? ' disabled' : ''}>${busy ? '제출하는 중…' : '제출하기'}</button>
      </div>
    </div>`;
  }

  /* 이미 제출했는지를 먼저 본다. 조회 응답에는 제출할 수 없는 이유가 없어, 대상·항목이 없는 경우만 따로 안내한다. */
  function render() {
    if (view.submitted) {
      body.innerHTML = doneBar();
      return;
    }
    if (!view.canSubmit) {
      const nothingToRate = view.targets.length === 0 || view.items.length === 0;
      body.innerHTML = nothingToRate
        ? stateCard('제출 불가', 'is-closed', '평가할 참가자나 평가 항목이 없습니다.', null)
        : stateCard('제출 불가', 'is-closed', '지금은 평가를 제출할 수 없습니다.',
          '평가는 경기가 끝난 뒤 제출 마감 전까지, 레이팅 처리가 끝나기 전에만 제출할 수 있습니다.');
      return;
    }
    // 진행률 바가 위에 붙어 있으므로 빈 칸 알림과 확인 단계도 그 바로 아래, 표보다 먼저 둔다.
    const locked = confirming || busy;
    body.innerHTML = `${progressBar(locked)}
      <div data-evaluation-missing>${missingAlert()}</div>
      ${confirming ? confirmPanel() : ''}
      <p class="match-evaluation-guide">${SCORE_GUIDE}</p>
      ${teams().map(team => teamTable(team, locked)).join('')}`;
  }

  /* 제출 결과: 성공은 공용 토스트, 실패는 원인을 담은 공용 안내 모달(ui-feedback.js).
     공용 안내를 쓸 수 없는 환경에서만 평가 영역의 문구로 대신한다. */
  function showMessage(result) {
    const feedback = global.SpoitFeedback;
    if (feedback) {
      messageBox.hidden = true;
      if (result.ok) feedback.toast(result.message);
      else feedback.alert('평가를 제출할 수 없습니다', result.message);
      return;
    }
    messageBox.textContent = result.message;
    messageBox.classList.toggle('is-error', !result.ok);
    messageBox.classList.toggle('is-success', result.ok);
    messageBox.hidden = false;
  }

  // answered는 API가 응답했는지다. 네트워크 오류나 로그인 만료면 요청이 처리되지 않았으므로 입력을 그대로 둔다.
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
        return { ok: false, answered: false, data: {}, message: '로그인이 만료되었습니다. 다시 로그인해 주세요.' };
      }
      const data = await response.json().catch(() => ({}));
      const message = data.message || FALLBACK_MESSAGES[response.status] || '요청을 처리하지 못했습니다. 잠시 후 다시 시도해 주세요.';
      return { ok: response.ok, answered: true, data, message };
    } catch (error) {
      return { ok: false, answered: false, data: {}, message: '서버에 연결하지 못했습니다. 네트워크 상태를 확인해 주세요.' };
    }
  }

  async function load() {
    const result = await request('GET');
    if (!result.ok || !result.data.mySide) {
      view = null;
      body.innerHTML = `<p class="match-result-error">${escapeHTML(result.message)}</p>`;
      announceLoaded();
      return;
    }
    view = result.data;
    confirming = false;
    missing = new Set();
    render();
    announceLoaded();
  }

  // 결과 화면 단계 바(match-result.js)가 평가 단계를 이 응답으로 그린다. 조회에 실패하면 null을 보내 대기로 둔다.
  function announceLoaded() {
    root.dispatchEvent(new CustomEvent('match-evaluation:loaded', {
      detail: view && { submitted: view.submitted, canSubmit: view.canSubmit }
    }));
  }

  function payload() {
    return {
      evaluations: view.targets.map(target => ({
        targetParticipantId: target.participantId,
        scores: view.items.map(item => {
          const value = values[keyOf(target, item)];
          return value === NOT_OBSERVED
            ? { itemId: item.itemId, observationStatus: NOT_OBSERVED, score: null }
            : { itemId: item.itemId, observationStatus: 'OBSERVED', score: Number(value) };
        })
      }))
    };
  }

  function review() {
    messageBox.hidden = true;
    missing = missingKeys();
    if (missing.size > 0) {
      render();
      body.querySelector('.match-evaluation-score.is-missing input[data-key]')?.focus();
      return;
    }
    confirming = true;
    render();
    body.querySelector('[data-action="submit"]')?.focus();
  }

  async function submit() {
    busy = true;
    render();
    const result = await request('POST', payload());
    busy = false;
    showMessage(result);
    if (!result.answered) {
      confirming = false;
      render();
      return;
    }
    // 평가 제출로 레이팅이 반영됐을 수 있어 결과 섹션(match-result.js)도 다시 조회하게 한다.
    if (result.ok) document.dispatchEvent(new CustomEvent('match-evaluation:submitted'));
    await load();
  }

  function fillNotObserved(participantId) {
    const target = view.targets.find(candidate => candidate.participantId === participantId);
    if (!target) return;
    for (const item of view.items) {
      values[keyOf(target, item)] = NOT_OBSERVED;
      missing.delete(keyOf(target, item));
    }
    render();
    body.querySelector(`[data-fill="${participantId}"]`)?.focus();
  }

  function updateCell(cell, slider) {
    const key = slider.dataset.key;
    const value = values[key] ?? '';
    cell.classList.remove('is-empty', 'is-scored', 'is-skipped');
    cell.classList.add(stateClass(value));
    slider.disabled = value === NOT_OBSERVED;
    // Chrome 트랙은 CSS 그라데이션으로 채우므로 손잡이 위치를 넘겨준다(match-result.css).
    slider.style.setProperty('--match-evaluation-slider-ratio', slider.value / 10);
    slider.setAttribute('aria-valuetext', valueText(slider.dataset.itemName, value));
    cell.querySelector('[data-score-value]').textContent = scoreText(value);
    cell.querySelector('[data-skip]').setAttribute('aria-pressed', String(value === NOT_OBSERVED));
    updateProgress();
    if (value && missing.delete(key)) {
      cell.classList.remove('is-missing');
      slider.removeAttribute('aria-invalid');
      body.querySelector('[data-evaluation-missing]').innerHTML = missingAlert();
    }
  }

  function chooseScore(slider) {
    if (slider.disabled) return;
    values[slider.dataset.key] = String(slider.value);
    updateCell(slider.closest('[data-score-cell]'), slider);
  }

  // 못 봄을 끄면 점수를 되살리지 않고 빈 칸으로 돌린다. 다시 고르게 해야 모르고 남은 점수가 제출되지 않는다.
  function toggleSkip(button) {
    const cell = button.closest('[data-score-cell]');
    const slider = cell.querySelector('input[data-key]');
    if (values[slider.dataset.key] === NOT_OBSERVED) {
      delete values[slider.dataset.key];
      slider.value = START_POSITION;
    } else {
      values[slider.dataset.key] = NOT_OBSERVED;
    }
    updateCell(cell, slider);
  }

  root.addEventListener('input', function (event) {
    const slider = event.target.closest('input[data-key]');
    if (!slider || busy || !view) return;
    chooseScore(slider);
  });

  root.addEventListener('click', function (event) {
    if (busy || !view) return;
    // 처음 위치(5)를 그대로 고르면 값이 바뀌지 않아 input이 오지 않으므로, 탭·클릭도 그 위치 점수로 채운다.
    const slider = event.target.closest('input[data-key]');
    if (slider) {
      chooseScore(slider);
      return;
    }
    const skip = event.target.closest('[data-skip]');
    if (skip) {
      toggleSkip(skip);
      return;
    }
    const fill = event.target.closest('[data-fill]');
    if (fill) {
      fillNotObserved(Number(fill.dataset.fill));
      return;
    }
    const action = event.target.closest('[data-action]')?.dataset.action;
    if (action === 'review') {
      review();
    } else if (action === 'cancel') {
      confirming = false;
      render();
      body.querySelector('[data-action="review"]')?.focus();
    } else if (action === 'submit' && confirming) {
      submit();
    }
  });

  root.addEventListener('match-result:captain', load);
})(window);
