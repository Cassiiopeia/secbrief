package kr.suhsaechan.secbrief.advisory.indicator;

import java.util.List;

/**
 * 해외 공개 사이버 경보 지표 2개: 미국 CIS(MS-ISAC) 사이버 경보 5단계, SANS 인터넷 스톰 센터 InfoCon 4단계
 * 단계 값은 각 지표가 주는 글자 그대로 (낮은 단계부터), 읽는 주소는 공개 텍스트·JSON 피드
 */
public enum InternationalAlertIndicator {
    // 피드 응답 예: "elevated\nyellow" (keys=level,color), 설명: https://www.cisecurity.org/text-feed-help
    CIS_MS_ISAC("https://feeds.cisecurity.org/text?keys=level,color",
            "https://www.cisecurity.org/cybersecurity-threats/alert-level",
            List.of("low", "guarded", "elevated", "high", "severe")),
    // 응답 예: {"status":"green"}, blue 는 시험용 단계라 단계 목록에 없음 (읽지 못함으로 처리)
    SANS_ISC_INFOCON("https://isc.sans.edu/api/infocon?json",
            "https://isc.sans.edu/infocon.html",
            List.of("green", "yellow", "orange", "red"));

    private final String feedUrl;
    private final String sourceUrl;
    private final List<String> levelKeys;

    InternationalAlertIndicator(String feedUrl, String sourceUrl, List<String> levelKeys) {
        this.feedUrl = feedUrl;
        this.sourceUrl = sourceUrl;
        this.levelKeys = levelKeys;
    }

    public String getFeedUrl() {
        return feedUrl;
    }

    /** 사람이 보는 설명 페이지 (단계 뜻) */
    public String getSourceUrl() {
        return sourceUrl;
    }

    public List<String> getLevelKeys() {
        return levelKeys;
    }

    /** 단계 값 > 1부터 세는 위치 (목록 밖이면 0) */
    public int toLevelStep(String levelKey) {
        return levelKeys.indexOf(levelKey) + 1;
    }
}
