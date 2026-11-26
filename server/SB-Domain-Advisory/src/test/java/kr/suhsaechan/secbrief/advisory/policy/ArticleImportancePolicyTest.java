package kr.suhsaechan.secbrief.advisory.policy;

import static org.assertj.core.api.Assertions.assertThat;

import kr.suhsaechan.secbrief.advisory.constant.ArticleImportance;
import kr.suhsaechan.secbrief.advisory.constant.SourceCode;
import org.junit.jupiter.api.Test;

/**
 * 중요도 규칙 하한·긴급 판정: KISA 경보단계 상향 > HEADLINE, KEV·KISA 긴급 > NOTABLE, 그 밖 ROUTINE
 */
class ArticleImportancePolicyTest {

    @Test
    void raisedKisaAlertLevelIsHeadlineAndUrgent() {
        String raisedTitle = "사이버위기 경보 단계 '관심' → '주의' 상향 조정";

        assertThat(ArticleImportancePolicy.toImportanceFloor(SourceCode.KISA_ALERT_LEVEL, raisedTitle, false))
                .isEqualTo(ArticleImportance.HEADLINE);
        assertThat(ArticleUrgencyPolicy.isUrgent(SourceCode.KISA_ALERT_LEVEL, raisedTitle, null, false)).isTrue();
    }

    @Test
    void loweredKisaAlertLevelIsRoutineAndNotUrgent() {
        String loweredTitle = "사이버위기 경보 단계 '주의' → '관심' 하향 조정";

        assertThat(ArticleImportancePolicy.toImportanceFloor(SourceCode.KISA_ALERT_LEVEL, loweredTitle, false))
                .isEqualTo(ArticleImportance.ROUTINE);
        assertThat(ArticleUrgencyPolicy.isUrgent(SourceCode.KISA_ALERT_LEVEL, loweredTitle, null, false)).isFalse();
    }

    @Test
    void kevAndUrgentKisaNoticesAreNotable() {
        assertThat(ArticleImportancePolicy.toImportanceFloor(SourceCode.NVD, "CVE-2026-1 flaw", true))
                .isEqualTo(ArticleImportance.NOTABLE);
        // KISA 보호나라 4곳 모두 제목의 '긴급'을 봄
        assertThat(ArticleImportancePolicy.toImportanceFloor(SourceCode.KISA_VULNERABILITY, "긴급 보안 업데이트 권고", false))
                .isEqualTo(ArticleImportance.NOTABLE);
        assertThat(ArticleUrgencyPolicy.isUrgent(SourceCode.KISA_REPORT, "긴급 대응 가이드", null, false)).isTrue();
    }

    @Test
    void otherArticlesAreRoutine() {
        assertThat(ArticleImportancePolicy.toImportanceFloor(SourceCode.NVD, "CVE-2026-2 flaw", false))
                .isEqualTo(ArticleImportance.ROUTINE);
        // 다른 출처 제목의 '긴급'·'상향'은 보지 않음
        assertThat(ArticleImportancePolicy.toImportanceFloor(SourceCode.DAILYSECU, "긴급 점검, 보안 등급 상향", false))
                .isEqualTo(ArticleImportance.ROUTINE);
    }

    @Test
    void comparesImportanceLevels() {
        assertThat(ArticleImportance.higherOf(ArticleImportance.ROUTINE, ArticleImportance.NOTABLE))
                .isEqualTo(ArticleImportance.NOTABLE);
        assertThat(ArticleImportance.NOTABLE.toLevelsAtLeast())
                .containsExactly(ArticleImportance.HEADLINE, ArticleImportance.NOTABLE);
    }
}
