/*
 * 관리자 화면 공통: 테마(라이트·다크, 저장), CSRF 헤더, JSON 요청(AdminApi), 토스트, 확인 모달, flash 토스트
 * admin-head 조각이 <head> 에서 화면 스크립트보다 먼저 읽음 (화면이 그려지기 전에 테마를 정해 번쩍임 방지)
 */
(function () {
    'use strict';

    // 테마 저장 키 (localStorage): light, dark
    const THEME_STORAGE_KEY = 'secbrief-admin-theme';
    const THEME_LIGHT = 'light';
    const THEME_DARK = 'dark';
    // 토스트 표시 시간 (밀리초): 오류는 2배
    const TOAST_VISIBLE_MILLISECONDS = 3500;
    // 서버가 본문 없이 실패했을 때 상태 코드별 문구와 화면 에러 코드
    const DEFAULT_ERROR_BY_STATUS = {
        401: { message: '로그인이 풀렸어요. 다시 로그인해 주세요.', errorCode: 'ADMIN_SESSION_EXPIRED' },
        403: { message: '이 작업을 할 수 없어요. 화면을 새로 고친 뒤 다시 해 주세요.', errorCode: 'ACCESS_DENIED' },
        404: { message: '대상을 찾지 못했어요.', errorCode: 'RESOURCE_NOT_FOUND' }
    };
    const NETWORK_ERROR_CODE = 'ADMIN_NETWORK_ERROR';
    const UNKNOWN_ERROR_CODE = 'ADMIN_REQUEST_FAILED';

    // ── 테마 ──

    function readSavedTheme() {
        try {
            return localStorage.getItem(THEME_STORAGE_KEY);
        } catch (ignored) {
            // 저장소를 못 읽어도 화면은 열림 (이번 방문에만 적용)
            return null;
        }
    }

    function isOperatingSystemDark() {
        return !!(window.matchMedia && window.matchMedia('(prefers-color-scheme: dark)').matches);
    }

    function applyTheme(theme) {
        document.documentElement.setAttribute('data-theme', theme);
        // 전환 버튼 아이콘: 지금 테마 쪽만 보임
        document.querySelectorAll('[data-theme-icon]').forEach(icon => {
            // lucide 가 <i> 를 <svg> 로 바꿔 hidden 프로퍼티가 없음 > 속성으로 다룸
            icon.toggleAttribute('hidden', icon.getAttribute('data-theme-icon') !== theme);
        });
    }

    function currentTheme() {
        return document.documentElement.getAttribute('data-theme') === THEME_DARK ? THEME_DARK : THEME_LIGHT;
    }

    function toggleTheme() {
        const nextTheme = currentTheme() === THEME_DARK ? THEME_LIGHT : THEME_DARK;
        applyTheme(nextTheme);
        try {
            localStorage.setItem(THEME_STORAGE_KEY, nextTheme);
        } catch (ignored) {
            // 기억하지 못할 뿐 이번 방문에는 적용됨
        }
    }

    // 저장값 우선, 없으면 운영체제 설정
    applyTheme(readSavedTheme() || (isOperatingSystemDark() ? THEME_DARK : THEME_LIGHT));

    // ── CSRF: 같은 출처의 변경 요청(GET·HEAD 외)에 토큰 헤더 붙이기 ──

    const csrfToken = document.querySelector('meta[name="_csrf"]')?.content;
    const csrfHeaderName = document.querySelector('meta[name="_csrf_header"]')?.content;
    if (csrfToken && csrfHeaderName) {
        const originalFetch = window.fetch.bind(window);
        window.fetch = function (input, init) {
            const method = ((init && init.method) || (input instanceof Request ? input.method : 'GET')).toUpperCase();
            const url = new URL(input instanceof Request ? input.url : String(input), location.href);
            if (method === 'GET' || method === 'HEAD' || url.origin !== location.origin) {
                return originalFetch(input, init);
            }
            const headers = new Headers((init && init.headers) || (input instanceof Request ? input.headers : undefined));
            if (!headers.has(csrfHeaderName)) headers.set(csrfHeaderName, csrfToken);
            return originalFetch(input, { ...init, headers });
        };
    }

    // ── JSON 요청 ──

    /** 실패한 요청: 화면 문구, 상태 코드, 에러 코드 (서버 {errorCode, message} 우선) */
    class AdminApiError extends Error {
        constructor(message, status, errorCode) {
            super(message);
            this.status = status;
            this.errorCode = errorCode;
        }
    }

    /** 응답 > 본문 (JSON 이면 객체, 비었으면 null), 실패·로그인 화면으로 넘어간 응답은 AdminApiError */
    async function readResponseBody(response) {
        const contentType = response.headers.get('content-type') || '';
        // 세션이 끝나면 로그인 화면(HTML)으로 넘어가 200 이 옴
        if (response.redirected && contentType.indexOf('application/json') < 0) {
            const expired = DEFAULT_ERROR_BY_STATUS[401];
            throw new AdminApiError(expired.message, 401, expired.errorCode);
        }
        const text = await response.text();
        let body = null;
        if (text) {
            try {
                body = JSON.parse(text);
            } catch (ignored) {
                body = text;
            }
        }
        if (response.ok) return body;
        const fallback = DEFAULT_ERROR_BY_STATUS[response.status]
            || { message: '요청을 처리하지 못했어요.', errorCode: UNKNOWN_ERROR_CODE };
        const hasServerError = body && typeof body === 'object';
        throw new AdminApiError((hasServerError && body.message) || fallback.message, response.status,
            (hasServerError && body.errorCode) || fallback.errorCode);
    }

    /** JSON 요청 1건: body 객체 > JSON, 응답 JSON 해석, 실패는 AdminApiError 로 통일 */
    async function request(url, options = {}) {
        const headers = { 'Accept': 'application/json', 'X-Requested-With': 'fetch', ...(options.headers || {}) };
        const init = { method: options.method || 'GET', headers };
        if (options.body !== undefined) {
            headers['Content-Type'] = 'application/json';
            init.body = JSON.stringify(options.body);
        }
        let response;
        try {
            response = await fetch(url, init);
        } catch (networkError) {
            throw new AdminApiError('서버에 연결하지 못했어요. 잠시 뒤 다시 해 주세요.', 0, NETWORK_ERROR_CODE);
        }
        return readResponseBody(response);
    }

    // ── 토스트 ──

    function ensureToastContainer() {
        let container = document.getElementById('adminToastContainer');
        if (!container) {
            container = document.createElement('div');
            container.id = 'adminToastContainer';
            container.className = 'toast toast-top toast-end z-[1000]';
            document.body.appendChild(container);
        }
        return container;
    }

    /** 토스트 1건 (type: success, error, info, warning) */
    function toast(message, type = 'success') {
        const alertElement = document.createElement('div');
        alertElement.className = 'alert alert-' + type + ' shadow-lg max-w-sm';
        alertElement.setAttribute('role', type === 'error' ? 'alert' : 'status');
        const textElement = document.createElement('span');
        textElement.className = 'whitespace-pre-line break-words';
        textElement.textContent = message;
        alertElement.appendChild(textElement);
        ensureToastContainer().appendChild(alertElement);
        setTimeout(() => alertElement.remove(),
            type === 'error' ? TOAST_VISIBLE_MILLISECONDS * 2 : TOAST_VISIBLE_MILLISECONDS);
    }

    /** 오류 > 오류 토스트: 문구 (에러 코드) */
    function showError(error, fallbackMessage) {
        const message = (error && error.message) || fallbackMessage || '요청을 처리하지 못했어요.';
        const errorCode = (error && error.errorCode) || UNKNOWN_ERROR_CODE;
        toast(message + ' (' + errorCode + ')', 'error');
    }

    // 서버가 리다이렉트로 넘긴 문구 (admin-layout 의 flash 자리: flashMessage, flashError)
    function showServerFlash() {
        const flash = document.getElementById('adminFlash');
        if (!flash) return;
        if (flash.dataset.message) toast(flash.dataset.message, 'success');
        if (flash.dataset.error) toast(flash.dataset.error, 'error');
    }

    // ── 확인 모달 (daisyUI modal) ──

    /**
     * 확인 모달: Promise<boolean> (확인 true, 취소·바깥 누름·Esc false)
     * options: title, confirmLabel, cancelLabel, tone(danger 면 빨간 버튼)
     */
    function confirm(message, options = {}) {
        return new Promise(resolve => {
            const dialog = document.createElement('dialog');
            dialog.className = 'modal';
            const box = document.createElement('div');
            box.className = 'modal-box';
            const title = document.createElement('h3');
            title.className = 'text-lg font-bold';
            title.textContent = options.title || '확인해 주세요';
            const body = document.createElement('p');
            body.className = 'py-4 whitespace-pre-line';
            body.textContent = message;
            const actions = document.createElement('div');
            actions.className = 'modal-action';
            const cancelButton = document.createElement('button');
            cancelButton.type = 'button';
            cancelButton.className = 'btn btn-ghost';
            cancelButton.textContent = options.cancelLabel || '취소';
            const confirmButton = document.createElement('button');
            confirmButton.type = 'button';
            confirmButton.className = 'btn ' + (options.tone === 'danger' ? 'btn-error' : 'btn-primary');
            confirmButton.textContent = options.confirmLabel || '확인';
            actions.append(cancelButton, confirmButton);
            box.append(title, body, actions);
            // 바깥을 누르면 닫힘
            const backdrop = document.createElement('form');
            backdrop.method = 'dialog';
            backdrop.className = 'modal-backdrop';
            const backdropButton = document.createElement('button');
            backdropButton.textContent = '닫기';
            backdrop.appendChild(backdropButton);
            dialog.append(box, backdrop);

            let isConfirmed = false;
            confirmButton.addEventListener('click', () => {
                isConfirmed = true;
                dialog.close();
            });
            cancelButton.addEventListener('click', () => dialog.close());
            dialog.addEventListener('close', () => {
                dialog.remove();
                resolve(isConfirmed);
            });
            document.body.appendChild(dialog);
            dialog.showModal();
        });
    }

    // 폼 속성 data-confirm-message 가 있으면 제출 전에 확인 모달 (data-confirm-title, -label, -tone 선택)
    // 켠 체크 상자에 data-confirm-message-when-checked 가 있으면 그 문구로 (예: 푸시도 보내기)
    // 제출은 동기라 Promise 를 기다릴 수 없음 > 막고, 확인하면 같은 버튼으로 다시 제출
    function confirmBeforeSubmit(submitEvent) {
        const form = submitEvent.target;
        if (!(form instanceof HTMLFormElement) || !form.dataset.confirmMessage) return;
        if (form.dataset.isSubmitConfirmed === 'true') {
            delete form.dataset.isSubmitConfirmed;
            return;
        }
        submitEvent.preventDefault();
        const checkedOverride = form.querySelector('input[type="checkbox"][data-confirm-message-when-checked]:checked');
        const confirmMessage = checkedOverride ? checkedOverride.dataset.confirmMessageWhenChecked : form.dataset.confirmMessage;
        confirm(confirmMessage, {
            title: form.dataset.confirmTitle,
            confirmLabel: form.dataset.confirmLabel,
            tone: form.dataset.confirmTone
        }).then(isConfirmed => {
            if (!isConfirmed) return;
            form.dataset.isSubmitConfirmed = 'true';
            form.requestSubmit(submitEvent.submitter || undefined);
        });
    }

    // ── 화면 준비 뒤 ──

    document.addEventListener('DOMContentLoaded', () => {
        // 아이콘 (data-lucide) 그리기: 파일을 못 읽어도 화면은 그대로 씀
        if (window.lucide) window.lucide.createIcons();
        applyTheme(currentTheme());
        showServerFlash();
        document.addEventListener('click', event => {
            if (event.target.closest && event.target.closest('[data-theme-toggle]')) toggleTheme();
        });
        document.addEventListener('submit', confirmBeforeSubmit);
    });

    window.AdminApi = {
        request,
        get: url => request(url),
        post: (url, body) => request(url, { method: 'POST', body }),
        put: (url, body) => request(url, { method: 'PUT', body }),
        delete: (url, body) => request(url, { method: 'DELETE', body }),
        AdminApiError
    };
    window.AdminUi = { toast, showError, confirm, currentTheme };
})();
