package kr.suhsaechan.secbrief.advisory.policy;

import java.math.BigDecimal;
import kr.suhsaechan.secbrief.advisory.constant.SourceCode;

/**
 * 긴급 판정 규칙 4개: CVSS 9.0 이상, KEV 등재, KISA 공지 제목의 '긴급', KISA 경보단계 '상향' 중 하나면 긴급
 * 주제 분류는 규칙이 아니라 AI 가 맡음
 */
public final class ArticleUrgencyPolicy {

    // 긴급 기준 CVSS 기본 점수 (이상)
    public static final BigDecimal URGENT_CVSS_THRESHOLD = new BigDecimal("9.0");
    // KISA 보호나라 공지 제목에 이 단어가 있으면 긴급 (다른 출처 제목은 보지 않음)
    public static final String KISA_URGENT_TITLE_KEYWORD = "긴급";
    // KISA 경보단계 공지 제목에 이 단어가 있으면 긴급 (예: '관심' → '주의' 상향 조정)
    public static final String KISA_ALERT_LEVEL_RAISED_KEYWORD = "상향";

    private ArticleUrgencyPolicy() {
    }

    /**
     * 긴급 여부: 점수 > KEV 등재 > KISA 제목 > KISA 경보단계 상향 순으로 확인
     */
    public static boolean isUrgent(String sourceCode, String title, BigDecimal cvssScore, boolean isKev) {
        return hasUrgentCvssScore(cvssScore)
                || isKev
                || hasUrgentKisaTitle(sourceCode, title)
                || isRaisedKisaAlertLevel(sourceCode, title);
    }

    private static boolean hasUrgentCvssScore(BigDecimal cvssScore) {
        return cvssScore != null && cvssScore.compareTo(URGENT_CVSS_THRESHOLD) >= 0;
    }

    private static boolean hasUrgentKisaTitle(String sourceCode, String title) {
        return SourceCode.isKisa(sourceCode)
                && title != null
                && title.contains(KISA_URGENT_TITLE_KEYWORD);
    }

    /**
     * KISA 사이버위기 경보단계 상향 공지인지 (하향·유지 공지는 아님)
     */
    public static boolean isRaisedKisaAlertLevel(String sourceCode, String title) {
        return SourceCode.KISA_ALERT_LEVEL.equals(sourceCode)
                && title != null
                && title.contains(KISA_ALERT_LEVEL_RAISED_KEYWORD);
    }
}
