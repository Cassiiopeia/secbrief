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

    @Test
    void reportsEveryProblemWithFieldReasonAndOffendingFragment() {
        ArticleSummaryValidation validation = validate(new ArticleSummary("Example VPN 2.4.2 와 2.5 버전으로 올려야 해요.",
                List.of("피해 고객이 500곳이에요.", "인증 우회 문제예요."), "3.0.0 버전으로 올리세요."));

        assertThat(validation.isAccepted()).isFalse();
        assertThat(validation.isClean()).isFalse();
        assertThat(validation.problems()).containsExactly(
                new SummaryProblem("summary", "has number not in source", "2.4.2, 2.5"),
                new SummaryProblem("keyPoints", "has number not in source", "500"),
                new SummaryProblem("actionGuide", "has number not in source", "3.0.0"));
    }

    @Test
    void acceptedValidationListsDroppedPartsAndCleanValidationListsNone() {
        ArticleSummaryValidation droppedValidation =
                validate(new ArticleSummary(SUMMARY, List.of("인증 우회 문제예요.", ""), "3.0.0 버전으로 올리세요."));
        ArticleSummaryValidation cleanValidation = validate(new ArticleSummary(SUMMARY, List.of("인증 우회 문제예요."), null));

        assertThat(droppedValidation.isAccepted()).isTrue();
        assertThat(droppedValidation.isClean()).isFalse();
        // 빈 핵심 정리는 걸린 곳으로 세지 않음
        assertThat(droppedValidation.problems())
                .containsExactly(new SummaryProblem("actionGuide", "has number not in source", "3.0.0"));
        assertThat(cleanValidation.isClean()).isTrue();
    }

    @Test
    void reportsCopiedFragmentOfSourceText() {
        String koreanSourceText = "공격자는 관리자 화면의 인증 절차를 건너뛰고 설정 파일과 접속 기록을 내려받을 수 있었다고 회사가 밝혔으며, "
                + "피해 규모는 아직 조사하고 있다고 덧붙였다.";
        String copiedSentence = "공격자는 관리자 화면의 인증 절차를 건너뛰고 설정 파일과 접속 기록을 내려받을 수 있었다고 회사가 밝혔으며, 피해 규모는 아직 조사하고 있";

        ArticleSummaryValidation validation = ArticleSummaryPolicy.validate("관리자 화면 인증 우회", koreanSourceText,
                new ArticleSummary("회사 발표예요. " + copiedSentence + "어요.", List.of(), null));

        assertThat(validation.problems())
                .containsExactly(new SummaryProblem("summary", "copies source text", copiedSentence));
    }

    @Test
    void acceptsKoreanMonthTranslatedFromEnglishMonthNameButNotOtherDateDigits() {
        String datedSourceText = "On October 28, Example Corp confirmed that attackers were exploiting the flaw against exposed servers.";

        // October 28 > 10월 28일: 달 이름을 옮긴 숫자는 통과, 입력에 없는 달·일은 거부
        assertThat(ArticleSummaryPolicy.validate(TITLE, datedSourceText,
                new ArticleSummary("Example Corp 가 10월 28일에 공격을 확인했어요.", List.of(), null)).isAccepted()).isTrue();
        assertThat(ArticleSummaryPolicy.validate(TITLE, datedSourceText,
                new ArticleSummary("Example Corp 가 11월 28일에 공격을 확인했어요.", List.of(), null)).rejectionReason())
                .isEqualTo("summary has number not in source");
        assertThat(ArticleSummaryPolicy.validate(TITLE, datedSourceText,
                new ArticleSummary("Example Corp 가 10월 29일에 공격을 확인했어요.", List.of(), null)).rejectionReason())
                .isEqualTo("summary has number not in source");
    }

    @Test
    void acceptsExactLongProductNameAndDigitForNumberWord() {
        String advisorySourceText = "An update for example-httpd is now available for Example Enterprise Linux 9.4 Update Services "
                + "for SAP Solutions. It fixes two flaws.";

        ArticleSummaryValidation validation = ArticleSummaryPolicy.validate("EXSA-0000:0001: example-httpd security update",
                advisorySourceText, new ArticleSummary(
                        "Example Enterprise Linux 9.4 Update Services for SAP Solutions 용 example-httpd 업데이트가 나왔어요. 결함 2개를 고쳐요.",
                        List.of(), null));

        assertThat(validation.isClean()).isTrue();
    }

    @Test
    void rejectsTextThatTalksAboutTheSourceTextButNotOrdinaryUseOfInput() {
        assertThat(validate(new ArticleSummary("원문에는 영향받는 버전이 나와 있지 않아요.", List.of(), null)).problems())
                .containsExactly(new SummaryProblem("summary", "talks about the source text", "원문"));
        assertThat(validate(new ArticleSummary("입력에 따르면 Example VPN 에 인증 우회 문제가 있어요.", List.of(), null)).rejectionReason())
                .isEqualTo("summary talks about the source text");
        // 핵심 정리는 걸린 것만 버림
        ArticleSummaryValidation validation =
                validate(new ArticleSummary(SUMMARY, List.of("이 기사는 인증 우회를 다뤄요.", "인증 우회 문제예요."), null));
        assertThat(validation.acceptedSummary().keyPoints()).containsExactly("인증 우회 문제예요.");
        // 취약점 설명의 "입력"은 글 자체를 가리키는 말이 아님
        assertThat(validate(new ArticleSummary("Example VPN 이 사용자 입력을 검증하지 않아 인증을 건너뛸 수 있어요.", List.of(), null))
                .isClean()).isTrue();
    }

    private static ArticleSummaryValidation validate(ArticleSummary summary) {
        return ArticleSummaryPolicy.validate(TITLE, SOURCE_TEXT, summary);
    }
}
