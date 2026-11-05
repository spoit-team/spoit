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
  const SCORES = Array.from({ length: 11 }, (_, score) => String(score));
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

  function scoreSelect(target, item, locked) {
    const key = keyOf(target, item);
    const value = values[key] ?? '';
    const isMissing = missing.has(key);
    const option = (optionValue, text) =>
      `<option value="${optionValue}"${optionValue === value ? ' selected' : ''}>${text}</option>`;
    return `<select class="match-evaluation-select${isMissing ? ' is-missing' : ''}" data-key="${key}"
      aria-label="${escapeHTML(`${target.nickname} · ${item.itemName}`)}"${isMissing ? ' aria-invalid="true"' : ''}${locked ? ' disabled' : ''}>
      ${option('', '선택')}${SCORES.map(score => option(score, `${score}점`)).join('')}${option(NOT_OBSERVED, '관찰 불가')}
    </select>`;
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
            ${view.items.map(item => `<td data-label="${escapeHTML(item.itemName)}">${scoreSelect(target, item, locked)}</td>`).join('')}
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

  function actions() {
    if (!confirming) {
      return `<div class="match-evaluation-actions">
        <p>선수 ${view.targets.length}명의 항목 ${view.items.length}개를 모두 고르면 제출할 수 있습니다.</p>
        <button type="button" class="mypage-primary-button" data-action="review">평가 제출</button>
      </div>`;
    }
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
      body.innerHTML = stateCard('제출 완료', 'is-confirmed', '평가를 제출했습니다.', '제출한 평가는 고칠 수 없습니다.');
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
    const locked = confirming || busy;
    body.innerHTML = `${teams().map(team => teamTable(team, locked)).join('')}
      <div data-evaluation-missing>${missingAlert()}</div>
      ${actions()}`;
  }

  function showMessage(result) {
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
      return;
    }
    view = result.data;
    confirming = false;
    missing = new Set();
    render();
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
    missing = new Set();
    for (const target of view.targets) {
      for (const item of view.items) {
        if (!values[keyOf(target, item)]) missing.add(keyOf(target, item));
      }
    }
    if (missing.size > 0) {
      render();
      body.querySelector('.match-evaluation-select.is-missing')?.focus();
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

  root.addEventListener('change', function (event) {
    const select = event.target.closest('select[data-key]');
    if (!select || !view) return;
    values[select.dataset.key] = select.value;
    if (select.value && missing.delete(select.dataset.key)) {
      select.classList.remove('is-missing');
      select.removeAttribute('aria-invalid');
      body.querySelector('[data-evaluation-missing]').innerHTML = missingAlert();
    }
  });

  root.addEventListener('click', function (event) {
    if (busy || !view) return;
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
