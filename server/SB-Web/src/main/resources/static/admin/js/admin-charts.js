/*
 * 관리자 추이 그래프 공통: [data-trend-chart] 카드마다 JSON 조회 > Chart.js 그리기 > 표로 보기
 *
 * 카드 속성 (admin-chart 조각 trendCard 가 붙임)
 *   data-endpoint  /admin/stats/api/... (unit, metric 을 붙여 부름)
 *   data-type      stacked(쌓은 막대) · bar(막대)
 *   data-unit      day(일별 30일, 기본) · month(월별 12개월)   — [data-trend-unit] 버튼이 바꿈
 *   data-metric    count(호출 수) · cost(비용 USD)             — [data-trend-metric] 버튼이 바꿈
 * 카드 안 요소: canvas[data-trend-canvas], [data-trend-overlay], [data-trend-table]
 *
 * 덮개 3가지: 불러오는 중 / 빈 상태(기간에 기록 없음) / 오류(문구 + 에러 코드 + 다시 시도)
 * 색은 admin-chart-theme.js (AdminChartTheme) 가 정함: 줄 key 마다 고정, 테마별 바탕·축 색
 */
(function () {
    'use strict';

    // 막대 모양: 끝 모서리 4px, 쌓은 조각 사이 틈(바탕색 테두리 1px씩 = 2px), 최대 굵기
    const BAR_END_RADIUS_PIXELS = 4;
    const STACK_GAP_BORDER_PIXELS = 1;
    const MAXIMUM_BAR_THICKNESS_PIXELS = 28;
    // 비용(USD) 소수 자릿수
    const COST_FRACTION_DIGITS = 4;
    // 고른 전환 버튼 표시 (admin-chart 조각의 처음 상태와 같은 클래스)
    const ACTIVE_TOGGLE_CLASSES = ['btn-active', 'btn-primary'];
    const METRIC_COST = 'cost';
    const UNIT_DAY = 'day';
    // 화면 에러 코드: 서버가 {errorCode} 를 주면 그 값을 우선
    const ERROR_CODE_FETCH_FAILED = 'ADMIN_STATS_FETCH_FAILED';
    const ERROR_CODE_SESSION_EXPIRED = 'ADMIN_SESSION_EXPIRED';
    const ERROR_CODE_CHART_LIBRARY_MISSING = 'ADMIN_CHART_LIBRARY_MISSING';
    const ERROR_CODE_UNEXPECTED_RESPONSE = 'ADMIN_STATS_UNEXPECTED_RESPONSE';

    function isCostMetric(card) {
        return card.dataset.metric === METRIC_COST;
    }

    // 값 > 화면 글자: 건수는 천 단위 쉼표, 비용은 $0.0000
    function formatValue(card, value) {
        if (value == null) return '-';
        if (isCostMetric(card)) {
            return '$' + Number(value).toLocaleString('en-US',
                { minimumFractionDigits: COST_FRACTION_DIGITS, maximumFractionDigits: COST_FRACTION_DIGITS });
        }
        return Number(value).toLocaleString('ko-KR') + '건';
    }

    function requestUrlOf(card) {
        const parameters = new URLSearchParams();
        parameters.set('unit', card.dataset.unit || UNIT_DAY);
        if (card.dataset.metric) parameters.set('metric', card.dataset.metric);
        return card.dataset.endpoint + '?' + parameters.toString();
    }

    // ── 덮개 (불러오는 중 · 빈 상태 · 오류) ──

    function overlayOf(card) {
        return card.querySelector('[data-trend-overlay]');
    }

    function hideOverlay(card) {
        const overlay = overlayOf(card);
        overlay.textContent = '';
        overlay.hidden = true;
    }

    function showOverlay(card, children) {
        const overlay = overlayOf(card);
        overlay.textContent = '';
        children.forEach(child => overlay.appendChild(child));
        overlay.hidden = false;
    }

    function createTextElement(tagName, className, text) {
        const element = document.createElement(tagName);
        element.className = className;
        element.textContent = text;
        return element;
    }

    function showLoading(card) {
        showOverlay(card, [
            createTextElement('span', 'loading loading-spinner loading-md', ''),
            createTextElement('span', '', '불러오는 중이에요')
        ]);
    }

    function showEmpty(card) {
        showOverlay(card, [
            createTextElement('span', 'font-medium', '이 기간에는 기록이 없어요'),
            createTextElement('span', 'text-xs text-base-content/50', '기록이 쌓이면 여기에 그래프가 보여요')
        ]);
    }

    function showError(card, message, errorCode) {
        const retryButton = createTextElement('button', 'btn btn-outline btn-xs', '다시 시도');
        retryButton.type = 'button';
        retryButton.addEventListener('click', () => load(card));
        showOverlay(card, [
            createTextElement('span', 'font-medium text-error', message),
            createTextElement('span', 'badge badge-ghost badge-sm', errorCode),
            retryButton
        ]);
    }

    // ── 그리기 ──

    function isStacked(card) {
        return card.dataset.type === 'stacked';
    }

    function datasetsOf(card, chartData, ink) {
        const hasSeveralSeries = chartData.series.length > 1;
        return chartData.series.map(series => ({
            type: 'bar',
            label: series.label,
            data: series.values,
            backgroundColor: window.AdminChartTheme.seriesColorOf(series.key),
            // 쌓은 조각·이웃 막대 사이를 바탕색 테두리로 띄움
            borderColor: ink.surface,
            borderWidth: hasSeveralSeries ? STACK_GAP_BORDER_PIXELS : 0,
            borderRadius: BAR_END_RADIUS_PIXELS,
            borderSkipped: 'start',
            maxBarThickness: MAXIMUM_BAR_THICKNESS_PIXELS
        }));
    }

    function optionsOf(card, chartData, ink) {
        const isCost = isCostMetric(card);
        return {
            maintainAspectRatio: false,
            animation: false,
            interaction: { mode: 'index', intersect: false },
            plugins: {
                // 줄이 하나면 카드 제목이 이름을 대신함
                legend: {
                    display: chartData.series.length > 1,
                    position: 'bottom',
                    labels: { color: ink.text, boxWidth: 10, boxHeight: 10 }
                },
                tooltip: {
                    callbacks: {
                        label: context => context.dataset.label + ': ' + formatValue(card, context.parsed.y)
                    }
                }
            },
            scales: {
                x: {
                    stacked: isStacked(card),
                    grid: { display: false },
                    border: { color: ink.gridLine },
                    ticks: { color: ink.axisText, maxRotation: 0, autoSkipPadding: 12 }
                },
                y: {
                    stacked: isStacked(card),
                    beginAtZero: true,
                    grid: { color: ink.gridLine },
                    border: { display: false },
                    ticks: isCost
                        ? { color: ink.axisText, callback: value => '$' + value }
                        : { color: ink.axisText, precision: 0 }
                }
            }
        };
    }

    // 표로 보기: 색 없이도 값을 읽게 함 (밝은 색 줄의 낮은 대비 보완)
    function renderTable(card, chartData) {
        const holder = card.querySelector('[data-trend-table]');
        if (!holder) return;
        const table = document.createElement('table');
        table.className = 'table table-xs';
        const headRow = document.createElement('tr');
        ['기간'].concat(chartData.series.map(series => series.label)).forEach(title => {
            headRow.appendChild(createTextElement('th', '', title));
        });
        const head = document.createElement('thead');
        head.appendChild(headRow);
        const body = document.createElement('tbody');
        chartData.labels.forEach((label, index) => {
            const row = document.createElement('tr');
            row.appendChild(createTextElement('td', 'whitespace-nowrap', label));
            chartData.series.forEach(series => {
                row.appendChild(createTextElement('td', 'admin-number', formatValue(card, series.values[index])));
            });
            body.appendChild(row);
        });
        table.append(head, body);
        holder.textContent = '';
        holder.appendChild(table);
    }

    function draw(card, chartData) {
        const ink = window.AdminChartTheme.readThemeInk();
        if (card.trendChart) card.trendChart.destroy();
        card.trendChart = new window.Chart(card.querySelector('[data-trend-canvas]'), {
            data: { labels: chartData.labels, datasets: datasetsOf(card, chartData, ink) },
            options: optionsOf(card, chartData, ink)
        });
        renderTable(card, chartData);
    }

    function hasAnyValue(chartData) {
        return chartData.series.some(series => series.values.some(value => value != null && Number(value) !== 0));
    }

    // ── 조회 ──

    // 응답 > 그래프 데이터. 실패는 { message, errorCode } 를 던짐
    async function readChartData(response) {
        const contentType = response.headers.get('content-type') || '';
        const isJson = contentType.indexOf('application/json') >= 0;
        // 세션이 끝나면 로그인 화면(HTML)으로 넘어감
        if (response.redirected && !isJson) {
            throw { message: '로그인이 풀렸어요. 다시 로그인해 주세요', errorCode: ERROR_CODE_SESSION_EXPIRED };
        }
        if (!response.ok) {
            let serverErrorCode = null;
            if (isJson) {
                try {
                    serverErrorCode = (await response.json()).errorCode;
                } catch (ignored) {
                    // 본문을 못 읽으면 화면 코드로 안내
                }
            }
            throw { message: '그래프를 불러오지 못했어요', errorCode: serverErrorCode || ERROR_CODE_FETCH_FAILED };
        }
        const chartData = isJson ? await response.json() : null;
        if (!chartData || !Array.isArray(chartData.labels) || !Array.isArray(chartData.series)) {
            throw { message: '그래프 값을 읽지 못했어요', errorCode: ERROR_CODE_UNEXPECTED_RESPONSE };
        }
        return chartData;
    }

    function load(card) {
        if (typeof window.Chart !== 'function') {
            showError(card, '그래프를 그리지 못했어요', ERROR_CODE_CHART_LIBRARY_MISSING);
            return;
        }
        // 늦게 온 이전 응답이 새 그래프를 덮지 않게 요청 번호로 가림
        const requestSequence = (card.trendRequestSequence || 0) + 1;
        card.trendRequestSequence = requestSequence;
        showLoading(card);
        fetch(requestUrlOf(card), { headers: { Accept: 'application/json' } })
            .then(readChartData)
            .then(chartData => {
                if (card.trendRequestSequence !== requestSequence) return;
                card.trendData = chartData;
                draw(card, chartData);
                if (hasAnyValue(chartData)) {
                    hideOverlay(card);
                } else {
                    showEmpty(card);
                }
            })
            .catch(error => {
                if (card.trendRequestSequence !== requestSequence) return;
                console.warn('[admin stats] trend load failed', card.dataset.endpoint, error);
                showError(card, (error && error.message && error.errorCode) ? error.message : '그래프를 불러오지 못했어요',
                    (error && error.errorCode) || ERROR_CODE_FETCH_FAILED);
            });
    }

    // 버튼 묶음(일별·월별, 호출 수·비용): 누른 버튼만 켜고 다시 조회
    function bindToggleButtons(card, buttonSelector, applySelection) {
        const buttons = card.querySelectorAll(buttonSelector);
        buttons.forEach(button => {
            button.addEventListener('click', () => {
                applySelection(button);
                buttons.forEach(other => {
                    ACTIVE_TOGGLE_CLASSES.forEach(className => other.classList.toggle(className, other === button));
                    other.setAttribute('aria-pressed', other === button ? 'true' : 'false');
                });
                load(card);
            });
        });
    }

    function initialize() {
        const cards = document.querySelectorAll('[data-trend-chart]');
        cards.forEach(card => {
            bindToggleButtons(card, '[data-trend-unit]', button => { card.dataset.unit = button.dataset.trendUnit; });
            bindToggleButtons(card, '[data-trend-metric]', button => { card.dataset.metric = button.dataset.trendMetric; });
            load(card);
        });
        // 테마를 바꾸면 그 테마 색으로 다시 그림 (조회는 다시 하지 않음, 변수 반영 뒤 다음 프레임에)
        new MutationObserver(() => requestAnimationFrame(() => {
            cards.forEach(card => {
                if (card.trendData && typeof window.Chart === 'function') draw(card, card.trendData);
            });
        })).observe(document.documentElement, { attributes: true, attributeFilter: ['data-theme'] });
    }

    if (document.readyState === 'loading') {
        document.addEventListener('DOMContentLoaded', initialize);
    } else {
        initialize();
    }
})();
