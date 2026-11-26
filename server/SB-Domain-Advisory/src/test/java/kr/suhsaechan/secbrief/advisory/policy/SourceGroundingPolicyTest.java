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
}
