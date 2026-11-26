package kr.suhsaechan.secbrief.advisory.policy;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/**
 * 한국어 글 겹침 비율: 조사·어미만 바꾼 글은 높음, 같은 제품 이름만 나눠 가진 글은 낮음, 한글이 없으면 0
 */
class TextSimilarityPolicyTest {

    @Test
    void identicalKoreanTextIsOneAndUnrelatedTextIsLow() {
        assertThat(TextSimilarityPolicy.similarity("공격자가 관리자 계정을 만들 수 있어요.", "공격자가 관리자 계정을 만들 수 있어요."))
                .isEqualTo(1.0);
        assertThat(TextSimilarityPolicy.similarity("공격자가 관리자 계정을 만들 수 있어요.", "웨비나는 온라인으로 진행돼요.")).isLessThan(0.2);
    }

    @Test
    void rewordedSentenceStaysSimilar() {
        double similarity = TextSimilarityPolicy.similarity(
                "Example VPN에서 인증 없이 관리자 콘솔에 들어갈 수 있어요.", "Example VPN에서 인증 없이도 관리자 콘솔에 들어갈 수 있어요");

        assertThat(similarity).isGreaterThan(0.8);
    }

    @Test
    void sharedProductNameDoesNotMakeTextsSimilar() {
        double similarity = TextSimilarityPolicy.similarity(
                "Example Enterprise Linux 9.4 Update Services for SAP Solutions에 수정판이 나왔어요.",
                "Example Enterprise Linux 9.4 Update Services for SAP Solutions의 평가 등급은 Important예요.");

        assertThat(similarity).isLessThan(0.3);
    }

    @Test
    void textWithoutKoreanHasNoSimilarity() {
        assertThat(TextSimilarityPolicy.similarity("Example VPN 2.4.1", "Example VPN 2.4.1")).isZero();
        assertThat(TextSimilarityPolicy.similarity("", "공격자가 접근해요.")).isZero();
    }
}
