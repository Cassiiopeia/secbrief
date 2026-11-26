package kr.suhsaechan.secbrief.advisory.summary;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * 브리핑 검증 (목록 필드): 영향 대상은 이름이 걸리면 항목을, 조건만 걸리면 조건만 버림, 대응은 걸린 항목만 버림
 * 걸리는 것: 입력에 없는 대상 이름, 대상 자리의 CVE ID, 입력에 없는 버전이 든 조건, 문장으로 쓴 조건, 길이 초과,
 * 해요체가 아닌 대응, 글 자체를 가리키는 대응. 같은 이름은 처음 것만, 목록은 4개까지
 */
class ArticleSummaryPolicyListFieldTest {

    private static final String TITLE = "CVE-2026-1111: Example VPN authentication bypass";
    private static final String SOURCE_TEXT = "Example VPN 2.4.0 contains an authentication bypass that lets a remote attacker "
            + "reach the admin console. Example Corp released version 2.4.1 and asks customers to upgrade.";
    private static final String ONE_LINE = "Example VPN에서 인증 없이 관리자 콘솔에 들어갈 수 있어요.";
    private static final String WHAT_HAPPENED = "Example VPN에 인증을 건너뛰는 문제가 나왔고, Example Corp가 고친 버전을 내놨어요.";
    private static final String WHY_IT_MATTERS = "원격 공격자가 관리자 콘솔에 접근할 수 있어요.";
    private static final AffectedTarget AFFECTED_TARGET = new AffectedTarget("Example VPN", "2.4.0");
    private static final String ACTION = "2.4.1 버전으로 올리세요.";

    @Test
    void dropsAffectedTargetThatIsNotWrittenInTheInput() {
        ArticleSummaryValidation validation = validate(withAffected(
                new AffectedTarget("Example VPN 제품군", null), new AffectedTarget("example vpn", "2.4.0")));

        // 입력에 없는 이름은 버림, 대소문자만 다른 이름은 입력에 있는 것으로 봄
        assertThat(validation.acceptedBrief().affected()).containsExactly(new AffectedTarget("example vpn", "2.4.0"));
        assertThat(validation.problems()).containsExactly(
                new SummaryProblem("affected.target", "is not written in the input", "Example VPN 제품군"));
    }

    @Test
    void acceptsAffectedTargetWrittenOnlyInTheTitle() {
        assertThat(validate(withAffected(new AffectedTarget("Example VPN authentication bypass", null))).isClean()).isTrue();
    }

    @Test
    void dropsAffectedTargetThatIsCveId() {
        ArticleSummaryValidation validation = validate(withAffected(new AffectedTarget("CVE-2026-1111", null), AFFECTED_TARGET));

        assertThat(validation.acceptedBrief().affected()).containsExactly(AFFECTED_TARGET);
        assertThat(validation.problems())
                .containsExactly(new SummaryProblem("affected.target", "is an identifier", "CVE-2026-1111"));
    }

    @Test
    void dropsOnlyTheDetailWhenItsVersionIsNotInTheInput() {
        ArticleSummaryValidation validation = validate(withAffected(new AffectedTarget("Example VPN", "2.3.9 이하")));

        // 대상 이름은 입력에 있으므로 남기고, 입력에 없는 버전이 든 조건만 버림
        assertThat(validation.acceptedBrief().affected()).containsExactly(new AffectedTarget("Example VPN", null));
        assertThat(validation.problems())
                .containsExactly(new SummaryProblem("affected.detail", "has number not in source", "2.3.9"));
    }

    @Test
    void detailNumbersMustMatchExactlyEvenWhenInputHasTheNumberWord() {
        String sourceTextWithNumberWord = "Two flaws affect Example VPN before 2.4.1.";

        // 문장 필드는 영어 수 낱말을 옮긴 숫자를 받지만, 조건의 숫자는 입력과 글자까지 같아야 함
        ArticleSummaryValidation validation = ArticleSummaryPolicy.validate(TITLE, sourceTextWithNumberWord,
                new ArticleBrief("Example VPN에 결함 2개가 나왔어요.", "Example VPN에서 결함 2개가 확인됐어요.", null,
                        List.of(new AffectedTarget("Example VPN", "2 이전 버전")), List.of()));

        assertThat(validation.isAccepted()).isTrue();
        assertThat(validation.acceptedBrief().affected()).containsExactly(new AffectedTarget("Example VPN", null));
        assertThat(validation.problems()).containsExactly(new SummaryProblem("affected.detail", "has number not in source", "2"));
    }

    @Test
    void dropsDetailWrittenAsSentenceOrTooLong() {
        ArticleSummaryValidation sentenceValidation =
                validate(withAffected(new AffectedTarget("Example VPN", "2.4.0에서 관리자 콘솔에 접근할 수 있어요.")));
        ArticleSummaryValidation longValidation = validate(withAffected(
                new AffectedTarget("Example VPN", "가".repeat(ArticleSummaryPolicy.MAXIMUM_AFFECTED_DETAIL_LENGTH + 1))));

        assertThat(sentenceValidation.acceptedBrief().affected()).containsExactly(new AffectedTarget("Example VPN", null));
        assertThat(sentenceValidation.problems()).extracting(SummaryProblem::reason).containsExactly("is a sentence");
        assertThat(longValidation.acceptedBrief().affected()).containsExactly(new AffectedTarget("Example VPN", null));
        assertThat(longValidation.problems()).extracting(SummaryProblem::reason).containsExactly("is too long");
    }

    @Test
    void acceptsDetailWithoutKorean() {
        assertThat(validate(withAffected(new AffectedTarget("Example VPN", "<= 2.4.0"))).isClean()).isTrue();
    }

    @Test
    void keepsFirstOfDuplicateTargetsAndLimitsCount() {
        String manyProductsSourceText = "Alpha, Bravo, Charlie, Delta and Echo are affected.";
        ArticleBrief brief = new ArticleBrief("제품 여러 개가 영향을 받아요.", "여러 제품에서 문제가 확인됐어요.", null,
                List.of(new AffectedTarget("Alpha", null), new AffectedTarget("alpha", null), new AffectedTarget("Bravo", null),
                        new AffectedTarget("Charlie", null), new AffectedTarget("Delta", null), new AffectedTarget("Echo", null)),
                List.of());

        ArticleSummaryValidation validation = ArticleSummaryPolicy.validate("Several products", manyProductsSourceText, brief);

        assertThat(validation.isClean()).isTrue();
        assertThat(validation.acceptedBrief().affected()).extracting(AffectedTarget::target)
                .containsExactly("Alpha", "Bravo", "Charlie", "Delta");
    }

    @Test
    void dropsOnlyInvalidActionsAndLimitsCount() {
        ArticleBrief brief = new ArticleBrief(ONE_LINE, WHAT_HAPPENED, null, List.of(), List.of(
                "3.0.0 버전으로 올리세요.", "", "Upgrade now.", "자세한 내용은 원문을 확인하세요.", "2.4.1 버전으로 업그레이드",
                "첫째 조치를 하세요.", "둘째 조치를 하세요.", "셋째 조치를 하세요.", "넷째 조치를 하세요.", "다섯째 조치를 하세요."));

        ArticleSummaryValidation validation = validate(brief);

        assertThat(validation.acceptedBrief().actions())
                .containsExactly("첫째 조치를 하세요.", "둘째 조치를 하세요.", "셋째 조치를 하세요.", "넷째 조치를 하세요.");
        // 걸린 목록 항목은 어느 항목인지 알 수 있게 글 조각을 남김 (걸린 말·숫자가 따로 없으면 항목 글)
        assertThat(validation.problems()).containsExactly(
                new SummaryProblem("actions", "has number not in source", "3.0.0"),
                new SummaryProblem("actions", "has no korean", "Upgrade now."),
                new SummaryProblem("actions", "talks about the source text", "원문"),
                new SummaryProblem("actions", "is not a 해요체 sentence", "2.4.1 버전으로 업그레이드"));
    }

    private static ArticleSummaryValidation validate(ArticleBrief brief) {
        return ArticleSummaryPolicy.validate(TITLE, SOURCE_TEXT, brief);
    }

    private static ArticleBrief withAffected(AffectedTarget... affectedTargets) {
        return new ArticleBrief(ONE_LINE, WHAT_HAPPENED, WHY_IT_MATTERS, List.of(affectedTargets), List.of(ACTION));
    }
}
