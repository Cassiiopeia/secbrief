package kr.suhsaechan.secbrief.advisory.policy;

import kr.suhsaechan.secbrief.advisory.constant.ArticleImportance;
import kr.suhsaechan.secbrief.advisory.constant.SourceCode;

/**
 * 중요도 규칙 하한: AI 판단과 무관하게 보장하는 최소 단계 (AI 가 없거나 실패해도 큰 소식이 묻히지 않게)
 * 하한 3개: KISA 경보단계 상향 > HEADLINE, KEV 등재 > NOTABLE, KISA 긴급 공지 > NOTABLE
 */
public final class ArticleImportancePolicy {

    private ArticleImportancePolicy() {
    }

    public static ArticleImportance toImportanceFloor(String sourceCode, String title, boolean isKev) {
        if (ArticleUrgencyPolicy.isRaisedKisaAlertLevel(sourceCode, title)) {
            return ArticleImportance.HEADLINE;
        }
        if (isKev || isUrgentKisaNotice(sourceCode, title)) {
            return ArticleImportance.NOTABLE;
        }
        return ArticleImportance.ROUTINE;
    }

    private static boolean isUrgentKisaNotice(String sourceCode, String title) {
        return SourceCode.isKisa(sourceCode)
                && title != null
                && title.contains(ArticleUrgencyPolicy.KISA_URGENT_TITLE_KEYWORD);
    }
}
