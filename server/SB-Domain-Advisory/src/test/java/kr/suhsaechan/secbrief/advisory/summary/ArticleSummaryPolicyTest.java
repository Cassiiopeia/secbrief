package kr.suhsaechan.secbrief.advisory.summary;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * 브리핑 검증 (문장 필드): 필수 2개(무슨 일, 한 줄 결론)가 걸리면 건 실패, 왜 중요는 걸리면 그 필드만 버림
 * 걸리는 것: 지어낸 숫자, 빈 값, 길이 초과, 한글 없음, 원문 그대로 옮김, 글 자체를 가리키는 말, 해요체가 아닌 한 줄 결론,
 * 한 줄 결론·무슨 일의 되풀이 (영향 대상·대응 목록은 ArticleSummaryPolicyListFieldTest)
 */
class ArticleSummaryPolicyTest {

    private static final String TITLE = "CVE-2026-1111: Example VPN authentication bypass";
    private static final String SOURCE_TEXT = "Example VPN 2.4.0 contains an authentication bypass that lets a remote attacker "
            + "reach the admin console. Example Corp released version 2.4.1 and asks customers to upgrade.";
    private static final String ONE_LINE = "Example VPN에서 인증 없이 관리자 콘솔에 들어갈 수 있어요.";
    private static final String WHAT_HAPPENED = "Example VPN에 인증을 건너뛰는 문제가 나왔고, Example Corp가 고친 버전을 내놨어요.";
    private static final String WHY_IT_MATTERS = "원격 공격자가 관리자 콘솔에 접근할 수 있어요.";
    private static final AffectedTarget AFFECTED_TARGET = new AffectedTarget("Example VPN", "2.4.0");
    private static final String ACTION = "2.4.1 버전으로 올리세요.";
    private static final ArticleBrief CLEAN_BRIEF =
            new ArticleBrief(ONE_LINE, WHAT_HAPPENED, WHY_IT_MATTERS, List.of(AFFECTED_TARGET), List.of(ACTION));

    @Test
    void acceptsGroundedBriefAndTrimsEveryField() {
        ArticleBrief brief = new ArticleBrief("  " + ONE_LINE + "\n", " " + WHAT_HAPPENED + " ", WHY_IT_MATTERS + " ",
                List.of(new AffectedTarget(" Example VPN ", " 2.4.0 ")), List.of(" " + ACTION + " "));

        ArticleSummaryValidation validation = validate(brief);

        assertThat(validation.isClean()).isTrue();
        assertThat(validation.acceptedBrief()).isEqualTo(CLEAN_BRIEF);
    }

    @Test
    void joinsLineBreaksInsideFieldsIntoOneLine() {
        ArticleBrief brief = withWhatHappened("Example VPN에 인증을 건너뛰는 문제가 나왔어요.\n\nExample Corp가 고친 버전을 내놨어요.");

        assertThat(validate(brief).acceptedBrief().whatHappened())
                .isEqualTo("Example VPN에 인증을 건너뛰는 문제가 나왔어요. Example Corp가 고친 버전을 내놨어요.");
    }

    @Test
    void rejectsBriefWhenRequiredFieldHasNumberNotInInput() {
        assertThat(validate(withWhatHappened("Example VPN 2.4.2 버전으로 올려야 해요.")).rejectionReason())
                .isEqualTo("whatHappened has number not in source");
        assertThat(validate(withOneLine("Example VPN 2.4.2에 인증 우회 문제가 있어요.")).rejectionReason())
                .isEqualTo("oneLine has number not in source");
    }

    @Test
    void rejectsEmptyTooLongOrNonKoreanRequiredFields() {
        assertThat(validate(withWhatHappened("  ")).rejectionReason()).isEqualTo("whatHappened is empty");
        assertThat(validate(withWhatHappened(null)).rejectionReason()).isEqualTo("whatHappened is empty");
        assertThat(validate(withOneLine(null)).rejectionReason()).isEqualTo("oneLine is empty");
        assertThat(validate(withWhatHappened("가".repeat(ArticleSummaryPolicy.MAXIMUM_WHAT_HAPPENED_LENGTH + 1))).rejectionReason())
                .isEqualTo("whatHappened is too long");
        assertThat(validate(withOneLine("가".repeat(ArticleSummaryPolicy.MAXIMUM_ONE_LINE_LENGTH) + "요")).rejectionReason())
                .isEqualTo("oneLine is too long");
        assertThat(validate(withWhatHappened("Example VPN has an authentication bypass.")).rejectionReason())
                .isEqualTo("whatHappened has no korean");
    }

    @Test
    void acceptsOneLineAtTheLengthLimit() {
        String sixtyCharacterOneLine = "가".repeat(ArticleSummaryPolicy.MAXIMUM_ONE_LINE_LENGTH - 1) + "요";

        assertThat(validate(withOneLine(sixtyCharacterOneLine)).isAccepted()).isTrue();
    }

    @Test
    void rejectsOneLineWrittenAsNewspaperHeadline() {
        ArticleSummaryValidation validation = validate(withOneLine("Example Corp, Example VPN 인증 우회 패치 공개"));

        assertThat(validation.rejectionReason()).isEqualTo("oneLine is not a 해요체 sentence");
    }

    @Test
    void rejectsWhatHappenedWhoseFirstSentenceRepeatsOneLine() {
        ArticleSummaryValidation validation = validate(new ArticleBrief(ONE_LINE,
                "Example VPN에서 인증 없이도 관리자 콘솔에 들어갈 수 있어요. Example Corp가 고친 버전을 내놨어요.",
                null, List.of(), List.of()));

        assertThat(validation.isAccepted()).isFalse();
        assertThat(validation.problems()).containsExactly(new SummaryProblem("whatHappened", "repeats oneLine",
                "Example VPN에서 인증 없이도 관리자 콘솔에 들어갈 수 있어요"));
    }

    @Test
    void sharedProductNameAloneIsNotARepeat() {
        // 긴 제품 이름이 두 필드(무슨 일, 왜 중요)에 똑같이 있어도 한국어 내용이 다르면 되풀이가 아님
        String longNameSourceText = "An update for example-httpd is now available for Example Enterprise Linux 9.4 Update Services "
                + "for SAP Solutions. Example Product Security has rated this update as Important.";
        ArticleBrief brief = new ArticleBrief("example-httpd 보안 수정판이 나왔어요.",
                "Example Enterprise Linux 9.4 Update Services for SAP Solutions에서 쓰는 example-httpd가 대상이에요.",
                "Example Enterprise Linux 9.4 Update Services for SAP Solutions의 평가 등급은 Important예요.", List.of(), List.of());

        assertThat(ArticleSummaryPolicy.validate("EXSA-0000:0001: example-httpd security update", longNameSourceText, brief)
                .isClean()).isTrue();
    }

    @Test
    void rejectsRequiredFieldCopyingSourceSentenceAndReportsTheCopiedStretch() {
        String koreanSourceText = "공격자는 관리자 화면의 인증 절차를 건너뛰고 설정 파일과 접속 기록을 내려받을 수 있었다고 회사가 밝혔으며, "
                + "피해 규모는 아직 조사하고 있다고 덧붙였다.";
        String copiedSentence = "공격자는 관리자 화면의 인증 절차를 건너뛰고 설정 파일과 접속 기록을 내려받을 수 있었다고 회사가 밝혔으며, 피해 규모는 아직 조사하고 있";

        ArticleSummaryValidation validation = ArticleSummaryPolicy.validate("관리자 화면 인증 우회", koreanSourceText,
                new ArticleBrief("관리자 화면 인증을 건너뛴 침해가 확인됐어요.", "회사 발표예요. " + copiedSentence + "어요.", null,
                        List.of(), List.of()));

        assertThat(validation.rejectionReason()).isEqualTo("whatHappened copies source text");
        assertThat(validation.problems())
                .containsExactly(new SummaryProblem("whatHappened", "copies source text", copiedSentence));
    }

    @Test
    void dropsOnlyInvalidWhyItMattersAndKeepsTheBrief() {
        ArticleSummaryValidation validation = validate(withWhyItMatters("피해 고객이 500곳이에요."));

        assertThat(validation.isAccepted()).isTrue();
        assertThat(validation.isClean()).isFalse();
        assertThat(validation.acceptedBrief().whyItMatters()).isNull();
        assertThat(validation.acceptedBrief().whatHappened()).isEqualTo(WHAT_HAPPENED);
        assertThat(validation.problems())
                .containsExactly(new SummaryProblem("whyItMatters", "has number not in source", "500"));
    }

    @Test
    void dropsWhyItMattersThatRepeatsWhatHappened() {
        ArticleBrief brief = new ArticleBrief(ONE_LINE,
                "Example VPN의 인증 우회로 원격 공격자가 관리자 콘솔에 접근할 수 있어요.",
                "원격 공격자가 관리자 콘솔에 접근할 수 있어요.", List.of(), List.of());

        ArticleSummaryValidation validation = validate(brief);

        assertThat(validation.acceptedBrief().whyItMatters()).isNull();
        assertThat(validation.problems()).containsExactly(
                new SummaryProblem("whyItMatters", "repeats whatHappened", "원격 공격자가 관리자 콘솔에 접근할 수 있어요."));
    }

    @Test
    void keepsNullAndBlankOptionalFieldsEmptyWithoutProblems() {
        ArticleSummaryValidation validation = validate(new ArticleBrief(ONE_LINE, WHAT_HAPPENED, "  ",
                List.of(new AffectedTarget(" ", "2.4.0"), new AffectedTarget("Example VPN", " ")), List.of("", "  ")));

        assertThat(validation.isClean()).isTrue();
        assertThat(validation.acceptedBrief()).isEqualTo(new ArticleBrief(ONE_LINE, WHAT_HAPPENED, null,
                List.of(new AffectedTarget("Example VPN", null)), List.of()));
        assertThat(validate(new ArticleBrief(ONE_LINE, WHAT_HAPPENED, null, null, null)).isClean()).isTrue();
    }

    @Test
    void reportsEveryProblemWithFieldReasonAndOffendingFragment() {
        ArticleSummaryValidation validation = validate(new ArticleBrief(ONE_LINE, "Example VPN 2.4.2와 2.5 버전으로 올려야 해요.",
                "피해 고객이 500곳이에요.", List.of(new AffectedTarget("Example Gateway", null)), List.of("3.0.0 버전으로 올리세요.")));

        assertThat(validation.isAccepted()).isFalse();
        assertThat(validation.isClean()).isFalse();
        assertThat(validation.rejectionReason()).isEqualTo("whatHappened has number not in source");
        assertThat(validation.problems()).containsExactly(
                new SummaryProblem("whatHappened", "has number not in source", "2.4.2, 2.5"),
                new SummaryProblem("whyItMatters", "has number not in source", "500"),
                new SummaryProblem("affected.target", "is not written in the input", "Example Gateway"),
                new SummaryProblem("actions", "has number not in source", "3.0.0"));
    }

    @Test
    void acceptsKoreanMonthTranslatedFromEnglishMonthNameButNotOtherDateDigits() {
        String datedSourceText = "On October 28, Example Corp confirmed that attackers were exploiting the flaw against exposed servers.";

        // October 28 > 10월 28일: 달 이름을 옮긴 숫자는 통과, 입력에 없는 달·일은 거부
        assertThat(ArticleSummaryPolicy.validate(TITLE, datedSourceText,
                withWhatHappened("Example Corp가 10월 28일에 공격을 확인했어요.")).isAccepted()).isTrue();
        assertThat(ArticleSummaryPolicy.validate(TITLE, datedSourceText,
                withWhatHappened("Example Corp가 11월 28일에 공격을 확인했어요.")).rejectionReason())
                .isEqualTo("whatHappened has number not in source");
        assertThat(ArticleSummaryPolicy.validate(TITLE, datedSourceText,
                withWhatHappened("Example Corp가 10월 29일에 공격을 확인했어요.")).rejectionReason())
                .isEqualTo("whatHappened has number not in source");
    }

    @Test
    void acceptsExactLongProductNameAsTargetAndInSentence() {
        String advisorySourceText = "An update for example-httpd is now available for Example Enterprise Linux 9.4 Update Services "
                + "for SAP Solutions. It fixes two flaws.";
        ArticleBrief brief = new ArticleBrief("example-httpd 보안 업데이트가 나왔어요.",
                "Example Enterprise Linux 9.4 Update Services for SAP Solutions용 example-httpd 수정판이 제공되고, 결함 2개를 고쳐요.",
                null, List.of(new AffectedTarget("Example Enterprise Linux 9.4 Update Services for SAP Solutions", null)),
                List.of());

        assertThat(ArticleSummaryPolicy.validate("EXSA-0000:0001: example-httpd security update", advisorySourceText, brief)
                .isClean()).isTrue();
    }

    @Test
    void rejectsTextThatTalksAboutTheSourceTextButNotOrdinaryUseOfInput() {
        assertThat(validate(withWhatHappened("원문에는 영향받는 버전이 나와 있지 않아요.")).problems())
                .containsExactly(new SummaryProblem("whatHappened", "talks about the source text", "원문"));
        assertThat(validate(withWhatHappened("입력에 따르면 Example VPN에 인증 우회 문제가 있어요.")).rejectionReason())
                .isEqualTo("whatHappened talks about the source text");
        assertThat(validate(withWhyItMatters("기사는 이 결함을 심각한 문제로 소개했어요.")).problems())
                .containsExactly(new SummaryProblem("whyItMatters", "talks about the source text", "기사는"));
        assertThat(validate(withWhyItMatters("관리자 콘솔 접근이 가능하다고 설명돼요.")).problems())
                .containsExactly(new SummaryProblem("whyItMatters", "talks about the source text", "설명돼요"));
        // 취약점 설명의 "입력"은 글 자체를 가리키는 말이 아님
        assertThat(validate(withWhatHappened("Example VPN이 사용자 입력을 검증하지 않아 인증을 건너뛸 수 있어요.")).isClean()).isTrue();
    }

    private static ArticleSummaryValidation validate(ArticleBrief brief) {
        return ArticleSummaryPolicy.validate(TITLE, SOURCE_TEXT, brief);
    }

    private static ArticleBrief withOneLine(String oneLine) {
        return new ArticleBrief(oneLine, WHAT_HAPPENED, WHY_IT_MATTERS, List.of(AFFECTED_TARGET), List.of(ACTION));
    }

    private static ArticleBrief withWhatHappened(String whatHappened) {
        return new ArticleBrief(ONE_LINE, whatHappened, null, List.of(AFFECTED_TARGET), List.of(ACTION));
    }

    private static ArticleBrief withWhyItMatters(String whyItMatters) {
        return new ArticleBrief(ONE_LINE, WHAT_HAPPENED, whyItMatters, List.of(AFFECTED_TARGET), List.of(ACTION));
    }
}
