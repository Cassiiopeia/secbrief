package kr.suhsaechan.secbrief.advisory.ai.calllog;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/**
 * 실패 종류 판정: 상태 코드 > 종류, 다음 키로 넘길지 (401·402·403·429·529·5xx·크레딧 부족만 넘김)
 */
class AiFailureKindTest {

    @ParameterizedTest
    @CsvSource({
            "401, UNAUTHORIZED, true",
            "402, PAYMENT_REQUIRED, true",
            "403, PERMISSION_DENIED, true",
            "429, RATE_LIMITED, true",
            "529, OVERLOADED, true",
            "500, SERVER_ERROR, true",
            "503, SERVER_ERROR, true",
            "504, SERVER_ERROR, true",
            "400, BAD_REQUEST, false",
            "404, NOT_FOUND, false",
            "413, CLIENT_ERROR, false",
            "422, CLIENT_ERROR, false",
            "302, UNKNOWN, false"})
    void classifiesStatusCode(int statusCode, AiFailureKind expectedKind, boolean shouldTryNextKey) {
        AiFailureKind failureKind = AiFailureKind.fromStatusCode(statusCode, "{\"error\":{\"message\":\"something\"}}");

        assertThat(failureKind).isEqualTo(expectedKind);
        assertThat(failureKind.shouldTryNextKey()).isEqualTo(shouldTryNextKey);
    }

    @Test
    void badRequestWithCreditBalanceMessageIsCreditExhausted() {
        AiFailureKind failureKind = AiFailureKind.fromStatusCode(400,
                "Your CREDIT BALANCE is too low to access the Anthropic API.");

        assertThat(failureKind).isEqualTo(AiFailureKind.CREDIT_EXHAUSTED);
        assertThat(failureKind.shouldTryNextKey()).isTrue();
    }

    @Test
    void creditBalanceMarkerOnlyMattersForBadRequest() {
        assertThat(AiFailureKind.fromStatusCode(404, "credit balance")).isEqualTo(AiFailureKind.NOT_FOUND);
        assertThat(AiFailureKind.fromStatusCode(400, null)).isEqualTo(AiFailureKind.BAD_REQUEST);
    }
}
