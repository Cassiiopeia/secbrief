package kr.suhsaechan.secbrief.advisory.policy;

import java.math.BigDecimal;
import kr.suhsaechan.secbrief.advisory.constant.SourceCode;

/**
 * 긴급 판정 규칙: CVSS 9.0 이상, KEV 등재, KISA 공지 제목의 '긴급' 중 하나면 긴급
 * 주제 분류는 규칙이 아니라 AI 가 맡음
 */
public final class ArticleUrgencyPolicy {

    // 긴급 기준 CVSS 기본 점수 (이상)
    public static final BigDecimal URGENT_CVSS_THRESHOLD = new BigDecimal("9.0");
    // KISA 공지 제목에 이 단어가 있으면 긴급 (다른 출처 제목은 보지 않음)
    public static final String KISA_URGENT_TITLE_KEYWORD = "긴급";

    private ArticleUrgencyPolicy() {
    }

    /**
     * 긴급 여부: 점수 > KEV 등재 > KISA 제목 순으로 확인
     */
    public static boolean isUrgent(String sourceCode, String title, BigDecimal cvssScore, boolean isKev) {
        return hasUrgentCvssScore(cvssScore)
                || isKev
                || hasUrgentKisaTitle(sourceCode, title);
    }

    private static boolean hasUrgentCvssScore(BigDecimal cvssScore) {
        return cvssScore != null && cvssScore.compareTo(URGENT_CVSS_THRESHOLD) >= 0;
    }

    private static boolean hasUrgentKisaTitle(String sourceCode, String title) {
        return SourceCode.KISA.equals(sourceCode)
                && title != null
                && title.contains(KISA_URGENT_TITLE_KEYWORD);
    }
}
