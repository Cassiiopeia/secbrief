package kr.suhsaechan.secbrief.advisory.policy;

import static kr.suhsaechan.secbrief.advisory.policy.SourceGroundingPolicy.hasNumberNotInSource;
import static kr.suhsaechan.secbrief.advisory.policy.SourceGroundingPolicy.hasVerbatimCopy;
import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/**
 * 원문 근거 확인: 원문에 없는 숫자열(버전·건수·날짜 지어내기), 원문 문장 그대로 옮기기
 */
class SourceGroundingPolicyTest {

    private static final String SOURCE = "Example VPN 2.4.0 has 3 flaws (CVE-2026-1111). Patched on 2026-11-20.";

    @Test
    void acceptsNumbersWrittenInSource() {
        assertThat(hasNumberNotInSource(SOURCE, "Example VPN 2.4.0 에서 결함 3개(CVE-2026-1111)가 나왔어요.")).isFalse();
        assertThat(hasNumberNotInSource(SOURCE, "숫자가 없는 문장이에요.")).isFalse();
    }

    @Test
    void detectsInventedOrChangedNumbers() {
        // 버전 한 자리 변경, 건수 변경(2.4.0 안의 4 는 건수 4 의 근거가 아님), 버전 줄여 쓰기, 날짜 표기 변환(월 앞 0 제거)
        assertThat(hasNumberNotInSource(SOURCE, "Example VPN 2.4.1 로 올리세요.")).isTrue();
        assertThat(hasNumberNotInSource(SOURCE, "결함 4개가 나왔어요.")).isTrue();
        assertThat(hasNumberNotInSource(SOURCE, "Example VPN 2.4 계열이 대상이에요.")).isTrue();
        assertThat(hasNumberNotInSource("Patched on 2026-09-01.", "2026년 9월 1일에 고쳤어요.")).isTrue();
    }

    @Test
    void acceptsNumberAtSentenceEndAndThousandsSeparator() {
        assertThat(hasNumberNotInSource("Fixed in 2.4.1 for 1,200 customers.", "1,200곳이 영향받고 조치 버전은 2.4.1.")).isFalse();
        assertThat(hasNumberNotInSource("Fixed in 2.4.1 for 1,200 customers.", "1200곳이 영향받아요.")).isTrue();
    }

    @Test
    void detectsLongVerbatimCopyIgnoringWhitespaceDifferences() {
        String source = "공격자는 관리자 화면의  인증 절차를 건너뛰고 설정 파일을 내려받을 수 있다고 회사가 밝혔다.";

        assertThat(hasVerbatimCopy(source, "공격자는 관리자 화면의 인증 절차를 건너뛰고 설정 파일을 내려받을 수 있다고 해요.", 30)).isTrue();
        assertThat(hasVerbatimCopy(source, "관리자 화면 인증을 우회해 설정 파일을 가져갈 수 있는 문제예요.", 30)).isFalse();
        // 기준 길이보다 짧은 글은 옮겨 실은 것으로 보지 않음
        assertThat(hasVerbatimCopy(source, "인증 절차", 30)).isFalse();
    }

    @Test
    void findsNumbersNotInSourceInWrittenOrderWithoutDuplicates() {
        assertThat(SourceGroundingPolicy.findNumbersNotInSource(SOURCE, "결함 4개, 2.4.1 로 올리세요. 다시 4개예요."))
                .containsExactly("4", "2.4.1");
        assertThat(SourceGroundingPolicy.findNumbersNotInSource(SOURCE, "Example VPN 2.4.0 이에요.")).isEmpty();
    }

    @Test
    void findsCopiedProseExtendedToWholeIdenticalStretch() {
        String source = "공격자는 관리자 화면의 인증 절차를 건너뛰고 설정 파일을 내려받을 수 있다고 회사가 밝혔다.";

        assertThat(SourceGroundingPolicy.findCopiedProse(source,
                "회사 발표예요. 공격자는 관리자 화면의 인증 절차를 건너뛰고 설정 파일을 내려받을 수 있다고 해요.", 30))
                .contains("공격자는 관리자 화면의 인증 절차를 건너뛰고 설정 파일을 내려받을 수 있다고 ");
        assertThat(SourceGroundingPolicy.findCopiedProse(source, "관리자 화면 인증을 우회하는 문제예요.", 30)).isEmpty();
    }

    @Test
    void doesNotTreatLongNamesVersionListsOrIdentifiersAsCopiedProse() {
        String source = "An update for example-httpd is now available for Example Enterprise Linux 9.4 Update Services for SAP Solutions "
                + "and Example Enterprise Linux 9.4 Extended Update Support. Affected: Example Browser 161, Example Browser ESR 115.46, "
                + "Example Browser ESR 153.8, Example Mail 161. Vector CVSS:4.0/AV:N/AC:L/AT:N/PR:N/UI:N/VC:H/VI:H/VA:H/SC:N/SI:N/SA:N.";

        // 60자 넘게 같아도 문장이 아닌 값: 긴 제품 이름, 버전 나열, CVSS 벡터 (그대로 적어야 하는 값)
        assertThat(SourceGroundingPolicy.findCopiedProse(source,
                "Example Enterprise Linux 9.4 Update Services for SAP Solutions 가 대상이에요.", 60)).isEmpty();
        assertThat(SourceGroundingPolicy.findCopiedProse(source,
                "Example Browser 161, Example Browser ESR 115.46, Example Browser ESR 153.8, Example Mail 161 이 영향을 받아요.", 60)).isEmpty();
        assertThat(SourceGroundingPolicy.findCopiedProse(source,
                "벡터는 CVSS:4.0/AV:N/AC:L/AT:N/PR:N/UI:N/VC:H/VI:H/VA:H/SC:N/SI:N/SA:N 이에요.", 60)).isEmpty();
        // 영어 문장을 그대로 옮기면 문장으로 봄 (소문자 낱말 4개 이상)
        assertThat(SourceGroundingPolicy.findCopiedProse(source,
                "공지 내용이에요. An update for example-httpd is now available for Example Enterprise Linux 9.4 라고 해요.", 60))
                .isPresent();
    }

    @Test
    void acceptsKoreanMonthOnlyWhenSourceNamesThatMonthInEnglish() {
        String source = "Example Corp confirmed the attack on October 28 and shipped a fix in early Nov.";

        // 달 이름(전체·줄임)이 원문에 있는 달만 근거 있는 숫자로 봄 (일은 그대로 검사)
        assertThat(SourceGroundingPolicy.findUngroundedNumbers(source, "10월 28일에 확인하고 11월 초에 고쳤어요.")).isEmpty();
        assertThat(SourceGroundingPolicy.findUngroundedNumbers(source, "12월 28일에 확인했어요.")).containsExactly("12");
        assertThat(SourceGroundingPolicy.findUngroundedNumbers(source, "10월 29일에 확인했어요.")).containsExactly("29");
        // 버전·소수의 일부는 달 표기로 보지 않음
        assertThat(SourceGroundingPolicy.findUngroundedNumbers(source, "2.10월")).containsExactly("2.10");
    }

    @Test
    void acceptsDigitTranslatedFromEnglishNumberWordUpToTwelve() {
        String source = "WordPress removed six plugins after four years. A division by zero and Twelve servers were reported, "
                + "with dozens of sites and twenty users affected.";

        assertThat(SourceGroundingPolicy.findUngroundedNumbers(source, "플러그인 6종을 4년 만에 지웠어요. 0으로 나누는 문제와 서버 12대예요."))
                .isEmpty();
        // 원문에 그 수의 낱말이 없거나 (7), 12 보다 크거나 (20), 묶음의 일부면 (6.4) 근거 없음
        assertThat(SourceGroundingPolicy.findUngroundedNumbers(source, "플러그인 7종, 사용자 20명, 버전 6.4 예요."))
                .containsExactly("7", "20", "6.4");
    }

    @Test
    void findsNameWrittenInSourceIgnoringCaseAndWhitespaceOnly() {
        String sourceText = "An update for example-httpd is now available for Example  Enterprise\nLinux 9.4 and `example-auth-kit`.";

        assertThat(SourceGroundingPolicy.isWrittenInSource(sourceText, "Example Enterprise Linux 9.4")).isTrue();
        assertThat(SourceGroundingPolicy.isWrittenInSource(sourceText, "example enterprise linux")).isTrue();
        assertThat(SourceGroundingPolicy.isWrittenInSource(sourceText, "example-auth-kit")).isTrue();
        // 입력에 없는 이름: 낱말을 더했거나, 버전이 다르거나, 번역한 이름
        assertThat(SourceGroundingPolicy.isWrittenInSource(sourceText, "Example Enterprise Linux 9.5")).isFalse();
        assertThat(SourceGroundingPolicy.isWrittenInSource(sourceText, "Example Enterprise Linux 제품군")).isFalse();
        assertThat(SourceGroundingPolicy.isWrittenInSource(sourceText, "예시 엔터프라이즈 리눅스")).isFalse();
        assertThat(SourceGroundingPolicy.isWrittenInSource(sourceText, "  ")).isFalse();
    }
}
