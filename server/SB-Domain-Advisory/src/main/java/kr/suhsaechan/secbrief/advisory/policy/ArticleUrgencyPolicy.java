package kr.suhsaechan.secbrief.advisory.policy;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import kr.suhsaechan.secbrief.advisory.constant.SourceCode;

/**
 * 긴급 판정 규칙 4개: CVSS 9.0 이상, KEV 등재, KISA 공지 제목의 '긴급', KISA 경보단계 '상향' 중 하나면 긴급
 * KISA 경보단계 글은 게시 후 7일까지만 긴급 (경보단계는 몇 달 이어지는 상태라 지난 변경 글은 새 소식이 아님, #96)
 * 주제 분류는 규칙이 아니라 AI 가 맡음
 */
public final class ArticleUrgencyPolicy {

    // 긴급 기준 CVSS 기본 점수 (이상)
    public static final BigDecimal URGENT_CVSS_THRESHOLD = new BigDecimal("9.0");
    // KISA 보호나라 공지 제목에 이 단어가 있으면 긴급 (다른 출처 제목은 보지 않음)
    public static final String KISA_URGENT_TITLE_KEYWORD = "긴급";
    // KISA 경보단계 공지 제목에 이 단어가 있으면 긴급 (예: '관심' → '주의' 상향 조정)
    public static final String KISA_ALERT_LEVEL_RAISED_KEYWORD = "상향";
    // KISA 경보단계 글을 긴급으로 두는 기간 (게시 후 7일)
    public static final Duration KISA_ALERT_LEVEL_URGENT_DURATION = Duration.ofDays(7);

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

    /**
     * 기준 시각의 긴급 여부: 규칙 4개 중 하나 + KISA 경보단계 글이면 게시 후 7일 이내 (게시일 없으면 기간 확인 안 함)
     */
    public static boolean isUrgentAt(String sourceCode, String title, BigDecimal cvssScore, boolean isKev,
                                     Instant publishedAt, Instant now) {
        return isUrgent(sourceCode, title, cvssScore, isKev) && !isExpiredAlertLevelArticle(sourceCode, publishedAt, now);
    }

    /**
     * 긴급 기간(게시 후 7일)이 지난 KISA 경보단계 글인지
     */
    public static boolean isExpiredAlertLevelArticle(String sourceCode, Instant publishedAt, Instant now) {
        return SourceCode.KISA_ALERT_LEVEL.equals(sourceCode)
                && publishedAt != null
                && publishedAt.isBefore(now.minus(KISA_ALERT_LEVEL_URGENT_DURATION));
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
