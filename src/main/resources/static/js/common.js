/*=====================================================================
 파일명   : common.js
 기능     : 모든 화면이 공유하는 요청·안내 함수(설계서 5.0절).
            CSRF 토큰과 세션 쿠키를 붙여 REST API를 호출하고, 상태 코드별 공통 처리를 한다.
=====================================================================*/

const LOGIN_PAGE = '/login.html';

/** 쿠키 값 읽기 */
function readCookie(name) {
  const m = document.cookie.match(new RegExp('(?:^|; )' + name.replace(/[.$?*|{}()[\]\\/+^]/g, '\\$&') + '=([^;]*)'));
  return m ? decodeURIComponent(m[1]) : null;
}

/*---------------------------------------------------------------------
1) 함수명: Promise<Object> api(String method, String url, Object body)
2) 매개변수: method = HTTP 방식; url = API 경로; body = 요청 본문(없으면 생략)
3) 기능: 10초 제한 시간을 걸고, 상태를 바꾸는 요청에는 CSRF 토큰 헤더를 붙인다(EIR-NW-03~05).
         미로그인(401)은 로그인 화면으로 보낸다. 오류는 ApiError(code, message)로 던진다.
4) 반환값: 성공 시 응답 JSON(본문이 없으면 null)
---------------------------------------------------------------------*/
async function api(method, url, body) {
  const controller = new AbortController();
  const timer = setTimeout(() => controller.abort(), 10000);
  const headers = { 'Accept': 'application/json' };
  if (body !== undefined) headers['Content-Type'] = 'application/json';
  if (method !== 'GET') {
    const token = readCookie('XSRF-TOKEN');
    if (token) headers['X-XSRF-TOKEN'] = token;
  }
  let res;
  try {
    res = await fetch(url, {
      method,
      headers,
      body: body === undefined ? undefined : JSON.stringify(body),
      credentials: 'same-origin',
      signal: controller.signal,
    });
  } catch (e) {
    throw { code: 'NETWORK', message: e.name === 'AbortError' ? '응답이 늦어지고 있습니다. 다시 시도하세요' : '서버에 연결할 수 없습니다' };
  } finally {
    clearTimeout(timer);
  }
  const text = await res.text();
  let data = null;
  try { data = text ? JSON.parse(text) : null; } catch (e) { data = null; }
  if (res.status === 401 && !(data && data.code === 'LOGIN_FAILED')) {
    location.href = LOGIN_PAGE;
    throw { code: 'UNAUTHORIZED', message: '로그인이 필요합니다' };
  }
  if (!res.ok) {
    if (res.status === 403 && !(data && data.code)) {
      throw { code: 'FORBIDDEN', message: '요청이 거부되었습니다. 화면을 새로고침한 뒤 다시 시도하세요' };
    }
    throw data && data.message ? data : { code: 'HTTP_' + res.status, message: '요청을 처리하지 못했습니다' };
  }
  return data;
}

/** 화면 진입 시 로그인과 역할을 확인한다. 다른 역할이면 그 역할의 첫 화면으로 보낸다(NFR-02). */
async function requireRole(role) {
  try {
    const me = await api('GET', '/api/auth/me');
    if (me.role !== role) {
      location.href = homeOf(me.role);
      return null;
    }
    document.querySelectorAll('[data-user-name]').forEach(el => { el.textContent = me.name; });
    return me;
  } catch (e) {
    return null;
  }
}

function homeOf(role) {
  return role === 'ADMIN' ? '/admin/menus.html' : '/student/menus.html';
}

/** 로그아웃(UC-02): 성공·실패와 관계없이 로그인 화면으로 이동한다. */
async function logout() {
  try { await api('POST', '/api/auth/logout'); } catch (e) { /* 무시 */ }
  location.href = LOGIN_PAGE;
}

/** 처리 결과 안내(하위 상자 토스트) */
function showToast(message, ms = 2400) {
  let el = document.querySelector('.toast');
  if (!el) {
    el = document.createElement('div');
    el.className = 'toast';
    el.setAttribute('role', 'status');
    document.body.appendChild(el);
  }
  el.textContent = message;
  el.classList.remove('hidden');
  clearTimeout(el._timer);
  el._timer = setTimeout(() => el.classList.add('hidden'), ms);
}

/** 입력란 바로 아래에 빨간 글씨로 오류를 표시한다(NFR-06). */
function showFieldError(input, message) {
  input.classList.add('invalid');
  const box = document.getElementById(input.id + '-error');
  if (box) box.textContent = message;
  input.focus();
}

function clearFieldErrors(root = document) {
  root.querySelectorAll('.invalid').forEach(el => el.classList.remove('invalid'));
  root.querySelectorAll('.field-error').forEach(el => { el.textContent = ''; });
}

/** 처리 중 버튼 비활성화와 진행 표시(NFR-05) */
function setBusy(button, busy, busyLabel) {
  if (busy) {
    button.dataset.label = button.dataset.label || button.innerHTML;
    button.disabled = true;
    button.innerHTML = '<span class="spinner"></span>' + (busyLabel ? ' ' + busyLabel : '');
  } else {
    button.disabled = false;
    if (button.dataset.label) button.innerHTML = button.dataset.label;
  }
}

function escapeHtml(s) {
  return String(s ?? '').replace(/[&<>"']/g, c => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;' }[c]));
}

function won(n) {
  return Number(n).toLocaleString('ko-KR') + '원';
}

function hm(t) {
  return t ? String(t).slice(0, 5) : '';
}

function slotLabel(start, end) {
  return hm(start) + '~' + hm(end);
}

const MEAL_LABEL = { LUNCH: '중식', DINNER: '석식' };
const STATUS_LABEL = { PENDING: '결제대기', RESERVED: '예약완료', PICKED_UP: '수령완료', CANCELED: '취소' };
const STATUS_BADGE = { RESERVED: 'badge-reserved', PICKED_UP: 'badge-picked', CANCELED: 'badge-canceled' };

function statusBadge(status) {
  return '<span class="badge ' + (STATUS_BADGE[status] || '') + '">' + (STATUS_LABEL[status] || status) + '</span>';
}

function todayLabel() {
  const d = new Date();
  const days = ['일', '월', '화', '수', '목', '금', '토'];
  return (d.getMonth() + 1) + '월 ' + d.getDate() + '일(' + days[d.getDay()] + ')';
}

/** 로컬 날짜를 YYYY-MM-DD로(EIR-NW-02) */
function isoDate(d = new Date()) {
  const p = n => String(n).padStart(2, '0');
  return d.getFullYear() + '-' + p(d.getMonth() + 1) + '-' + p(d.getDate());
}

function queryParam(name) {
  return new URLSearchParams(location.search).get(name);
}
