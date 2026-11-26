package kr.suhsaechan.secbrief.advisory.ai;

import static org.assertj.core.api.Assertions.assertThat;

import com.anthropic.core.JsonValue;
import com.anthropic.core.http.Headers;
import com.anthropic.errors.AnthropicInvalidDataException;
import com.anthropic.errors.AnthropicIoException;
import com.anthropic.errors.BadRequestException;
import com.anthropic.errors.GatewayTimeoutException;
import com.anthropic.errors.InternalServerException;
import com.anthropic.errors.NotFoundException;
import com.anthropic.errors.OverloadedException;
import com.anthropic.errors.PaymentRequiredException;
import com.anthropic.errors.PermissionDeniedException;
import com.anthropic.errors.RateLimitException;
import com.anthropic.errors.ServiceUnavailableException;
import com.anthropic.errors.UnauthorizedException;
import java.net.SocketTimeoutException;
import java.util.Map;
import kr.suhsaechan.secbrief.advisory.ai.calllog.AiFailureKind;
import org.junit.jupiter.api.Test;

/**
 * Anthropic SDK 예외 > 실패 종류 (SDK 예외 객체를 직접 만들어 판정만 확인, 실제 호출 없음)
 */
class AnthropicFailuresTest {

    private static final Headers NO_HEADERS = Headers.builder().build();
    private static final JsonValue ERROR_BODY = toErrorBody("invalid x-api-key");

    @Test
    void keyAndBillingTroublesMoveToNextKey() {
        assertKind(UnauthorizedException.builder().headers(NO_HEADERS).body(ERROR_BODY).build(),
                AiFailureKind.UNAUTHORIZED);
        assertKind(PaymentRequiredException.builder().headers(NO_HEADERS).body(ERROR_BODY).build(),
                AiFailureKind.PAYMENT_REQUIRED);
        assertKind(PermissionDeniedException.builder().headers(NO_HEADERS).body(ERROR_BODY).build(),
                AiFailureKind.PERMISSION_DENIED);
        assertKind(RateLimitException.builder().headers(NO_HEADERS).body(ERROR_BODY).build(),
                AiFailureKind.RATE_LIMITED);
    }

    @Test
    void providerTroublesMoveToNextKey() {
        assertKind(OverloadedException.builder().statusCode(529).headers(NO_HEADERS).body(ERROR_BODY).build(),
                AiFailureKind.OVERLOADED);
        assertKind(InternalServerException.builder().statusCode(500).headers(NO_HEADERS).body(ERROR_BODY).build(),
                AiFailureKind.SERVER_ERROR);
        assertKind(ServiceUnavailableException.builder().statusCode(503).headers(NO_HEADERS).body(ERROR_BODY).build(),
                AiFailureKind.SERVER_ERROR);
        assertKind(GatewayTimeoutException.builder().statusCode(504).headers(NO_HEADERS).body(ERROR_BODY).build(),
                AiFailureKind.SERVER_ERROR);
    }

    @Test
    void connectionFailureAndTimeoutMoveToNextKey() {
        assertKind(new AnthropicIoException("Request failed", new SocketTimeoutException("timeout")),
                AiFailureKind.CONNECTION_FAILED);
    }

    @Test
    void badRequestMovesToNextKeyOnlyWhenCreditIsExhausted() {
        assertKind(BadRequestException.builder().headers(NO_HEADERS)
                        .body(toErrorBody("Your credit balance is too low to access the Anthropic API.")).build(),
                AiFailureKind.CREDIT_EXHAUSTED);
        assertKind(BadRequestException.builder().headers(NO_HEADERS)
                        .body(toErrorBody("max_tokens: must be greater than 0")).build(),
                AiFailureKind.BAD_REQUEST);
    }

    @Test
    void notFoundAndNonSdkFailuresDoNotMoveToNextKey() {
        assertKind(NotFoundException.builder().headers(NO_HEADERS).body(toErrorBody("model: not found")).build(),
                AiFailureKind.NOT_FOUND);
        assertKind(new AnthropicInvalidDataException("unexpected response shape"), AiFailureKind.UNKNOWN);
        assertKind(new IllegalStateException("bug"), AiFailureKind.UNKNOWN);
    }

    private static void assertKind(RuntimeException failure, AiFailureKind expectedKind) {
        assertThat(AnthropicFailures.classify(failure)).isEqualTo(expectedKind);
    }

    // Anthropic 오류 본문 모양: {"type":"error","error":{"type":"...","message":"..."}}
    private static JsonValue toErrorBody(String message) {
        return JsonValue.from(Map.of("type", "error",
                "error", Map.of("type", "invalid_request_error", "message", message)));
    }
}
