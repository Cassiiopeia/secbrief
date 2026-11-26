/*
 * 관리자 그래프 색 단일 지점: 줄 key 별 범주 색(라이트·다크), 지금 테마의 바탕·축 글자·격자선 색
 * admin-charts.js 보다 먼저 읽음 (admin-chart 조각 scripts). 쓰는 쪽: AdminChartTheme.seriesColorOf(key), readThemeInk()
 * 색: 줄 key 마다 고정 (순위·개수로 다시 칠하지 않음). 라이트·다크 각각 색각 검증을 통과한 값
 */
(function () {
    'use strict';

    // 범주 색 8칸 [라이트, 다크]: 이 순서의 이웃 쌍끼리 구분되도록 검증한 값 (순서를 바꾸지 않음)
    const PALETTE = {
        blue: ['#257de0', '#338ced'],
        orange: ['#f47230', '#e76624'],
        aqua: ['#00b894', '#00a888'],
        yellow: ['#f2a600', '#d28e05'],
        magenta: ['#ee7fac', '#e24d8b'],
        green: ['#008a17', '#0aac23'],
        violet: ['#6a4ce2', '#9887ef'],
        red: ['#ec4647', '#eb6263'],
        brown: ['#9a6b3f', '#c08a5a']
    };
    // 줄 key(서버 응답 series.key) > 색
    const SERIES_COLOR_BY_KEY = {
        // 수집 추이: ArticleKind
        NEWS: PALETTE.blue,
        ADVISORY: PALETTE.orange,
        VULNERABILITY: PALETTE.aqua,
        // AI 사용 추이
        AI_SUCCESS: PALETTE.blue,
        AI_FAILURE: PALETTE.red,
        AI_COST: PALETTE.violet,
        // 이용 추이: ApiUsageGroup (화면 묶음 9개, 팔레트 순서 그대로)
        TODAY: PALETTE.blue,
        ARTICLE_LIST: PALETTE.orange,
        ARTICLE_DETAIL: PALETTE.aqua,
        SEARCH: PALETTE.yellow,
        KEYWORD: PALETTE.magenta,
        TREND: PALETTE.green,
        SOURCE: PALETTE.violet,
        PUSH_DEVICE: PALETTE.red,
        NOTIFICATION: PALETTE.brown,
        // 알림 기기 등록 추이
        REGISTERED: PALETTE.blue
    };
    const FALLBACK_SERIES_COLOR = ['#898781', '#898781'];
    // 변수를 못 읽을 때 쓰는 카드 바탕·글자 색 [라이트, 다크] (daisyUI 기본 테마 근사값)
    const FALLBACK_SURFACE_COLOR = ['#ffffff', '#1d232a'];
    const FALLBACK_TEXT_COLOR = ['#18181b', '#ecf9ff'];
    // 축 글자·격자선 불투명도 (본문 글자 색 기준)
    const AXIS_TEXT_ALPHA = 0.7;
    const GRID_LINE_ALPHA = 0.1;

    let colorProbeContext = null;

    function isDarkTheme() {
        return document.documentElement.getAttribute('data-theme') === 'dark';
    }

    function pickByTheme(lightDarkPair) {
        return lightDarkPair[isDarkTheme() ? 1 : 0];
    }

    function seriesColorOf(seriesKey) {
        return pickByTheme(SERIES_COLOR_BY_KEY[seriesKey] || FALLBACK_SERIES_COLOR);
    }

    // CSS 색(oklch 등) > [r, g, b]: Chart.js 가 oklch 를 못 읽어 1x1 캔버스에 칠해서 읽음
    function toRgbChannels(cssColor, fallbackColor) {
        if (!colorProbeContext) {
            const probeCanvas = document.createElement('canvas');
            probeCanvas.width = 1;
            probeCanvas.height = 1;
            colorProbeContext = probeCanvas.getContext('2d', { willReadFrequently: true });
        }
        colorProbeContext.clearRect(0, 0, 1, 1);
        colorProbeContext.fillStyle = fallbackColor;
        colorProbeContext.fillStyle = cssColor || fallbackColor;
        colorProbeContext.fillRect(0, 0, 1, 1);
        const pixel = colorProbeContext.getImageData(0, 0, 1, 1).data;
        return [pixel[0], pixel[1], pixel[2]];
    }

    // 지금 테마의 카드 바탕색, 축 글자색, 격자선 색 (daisyUI 테마 변수에서 읽음)
    function readThemeInk() {
        const rootStyle = getComputedStyle(document.documentElement);
        const surface = toRgbChannels(rootStyle.getPropertyValue('--color-base-100').trim(), pickByTheme(FALLBACK_SURFACE_COLOR));
        const text = toRgbChannels(rootStyle.getPropertyValue('--color-base-content').trim(), pickByTheme(FALLBACK_TEXT_COLOR));
        return {
            surface: 'rgb(' + surface.join(', ') + ')',
            text: 'rgb(' + text.join(', ') + ')',
            axisText: 'rgba(' + text.join(', ') + ', ' + AXIS_TEXT_ALPHA + ')',
            gridLine: 'rgba(' + text.join(', ') + ', ' + GRID_LINE_ALPHA + ')'
        };
    }

    window.AdminChartTheme = { seriesColorOf, readThemeInk };
})();
