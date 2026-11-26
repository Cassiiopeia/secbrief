package kr.suhsaechan.secbrief.advisory.summary;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * 요약 검증: 지어낸 숫자·빈 값·길이 초과·한글 없음·원문 그대로 옮김 > 요약은 건 실패, 핵심 정리·대응 안내는 걸린 것만 버림
 */
class ArticleSummaryPolicyTest {

    private static final String TITLE = "CVE-2026-1111: Example VPN authentication bypass";
    private static final String SOURCE_TEXT = "Example VPN 2.4.0 contains an authentication bypass that lets a remote attacker "
            + "reach the admin console. Example Corp released version 2.4.1 and asks customers to upgrade.";
    private static final String SUMMARY = "Example VPN 2.4.0 에서 인증을 건너뛸 수 있는 문제가 나왔어요. "
            + "원격 공격자가 관리자 콘솔에 접근할 수 있어요. Example Corp 가 2.4.1 버전을 내놨어요.";

    @Test
    void acceptsGroundedSummaryAndTrimsFields() {
        ArticleSummary summary = new ArticleSummary("  " + SUMMARY + "\n",
                List.of(" 인증 우회 문제예요. ", "CVE-2026-1111 로 등록됐어요."), " 2.4.1 버전으로 올리세요. ");

        ArticleSummaryValidation validation = ArticleSummaryPolicy.validate(TITLE, SOURCE_TEXT, summary);

        assertThat(validation.isAccepted()).isTrue();
        assertThat(validation.acceptedSummary()).isEqualTo(new ArticleSummary(SUMMARY,
                List.of("인증 우회 문제예요.", "CVE-2026-1111 로 등록됐어요."), "2.4.1 버전으로 올리세요."));
    }

    @Test
    void rejectsSummaryWithNumberNotInInput() {
        ArticleSummary summary = new ArticleSummary("Example VPN 2.4.2 버전으로 올려야 해요.", List.of("핵심이에요."), null);

        ArticleSummaryValidation validation = ArticleSummaryPolicy.validate(TITLE, SOURCE_TEXT, summary);

        assertThat(validation.isAccepted()).isFalse();
        assertThat(validation.rejectionReason()).isEqualTo("summary has number not in source");
    }

    @Test
    void rejectsEmptyTooLongOrNonKoreanSummary() {
        assertThat(validate(new ArticleSummary("  ", List.of(), null)).rejectionReason()).isEqualTo("summary is empty");
        assertThat(validate(new ArticleSummary(null, List.of(), null)).rejectionReason()).isEqualTo("summary is empty");
        assertThat(validate(new ArticleSummary("가".repeat(ArticleSummaryPolicy.MAXIMUM_SUMMARY_LENGTH + 1), List.of(), null))
                .rejectionReason()).isEqualTo("summary is too long");
        assertThat(validate(new ArticleSummary("Example VPN has an authentication bypass.", List.of(), null))
                .rejectionReason()).isEqualTo("summary has no korean");
    }

    @Test
    void rejectsSummaryCopyingSourceSentence() {
        String koreanSourceText = "공격자는 관리자 화면의 인증 절차를 건너뛰고 설정 파일과 접속 기록을 내려받을 수 있었다고 회사가 밝혔으며, "
                + "피해 규모는 아직 조사하고 있다고 덧붙였다.";
        String copiedSummary = "회사 발표예요. 공격자는 관리자 화면의 인증 절차를 건너뛰고 설정 파일과 접속 기록을 내려받을 수 있었다고 회사가 밝혔으며, "
                + "피해 규모는 아직 조사하고 있어요.";

        ArticleSummaryValidation validation =
                ArticleSummaryPolicy.validate("관리자 화면 인증 우회", koreanSourceText, new ArticleSummary(copiedSummary, List.of(), null));

        assertThat(validation.rejectionReason()).isEqualTo("summary copies source text");
    }

    @Test
    void dropsOnlyInvalidKeyPointsAndLimitsCount() {
        ArticleSummary summary = new ArticleSummary(SUMMARY, List.of(
                "피해 고객이 500곳이에요.", "", "Authentication bypass.", "첫째 핵심이에요.", "둘째 핵심이에요.",
                "셋째 핵심이에요.", "넷째 핵심이에요.", "다섯째 핵심이에요."), null);

        assertThat(validate(summary).acceptedSummary().keyPoints())
                .containsExactly("첫째 핵심이에요.", "둘째 핵심이에요.", "셋째 핵심이에요.", "넷째 핵심이에요.");
    }

    @Test
    void dropsActionGuideWithInventedNumberButKeepsSummary() {
        ArticleSummaryValidation validation =
                validate(new ArticleSummary(SUMMARY, List.of("인증 우회 문제예요."), "3.0.0 버전으로 올리세요."));

        assertThat(validation.isAccepted()).isTrue();
        assertThat(validation.acceptedSummary().summary()).isEqualTo(SUMMARY);
        assertThat(validation.acceptedSummary().actionGuide()).isNull();
    }

    @Test
    void keepsNullActionGuideAsNull() {
        assertThat(validate(new ArticleSummary(SUMMARY, List.of(), null)).acceptedSummary().actionGuide()).isNull();
        assertThat(validate(new ArticleSummary(SUMMARY, List.of(), "  ")).acceptedSummary().actionGuide()).isNull();
    }

    private static ArticleSummaryValidation validate(ArticleSummary summary) {
        return ArticleSummaryPolicy.validate(TITLE, SOURCE_TEXT, summary);
    }
}
