package kr.suhsaechan.secbrief.web.exception;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.util.List;
import org.apache.catalina.connector.ClientAbortException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.converter.HttpMessageNotWritableException;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.context.request.async.AsyncRequestNotUsableException;

/**
 * 전역 예외 처리: 클라이언트가 끊은 연결 3가지는 500·스택을 남기지 않고 응답도 쓰지 않음, 그 밖의 예외는 500 그대로 (#58)
 */
@ExtendWith(OutputCaptureExtension.class)
class GlobalExceptionHandlerClientDisconnectTest {

    private static final String IMAGE_PATH = "/api/v1/articles/1/image";

    private final GlobalExceptionHandler globalExceptionHandler = new GlobalExceptionHandler();
    private final MockHttpServletRequest imageRequest = new MockHttpServletRequest("GET", IMAGE_PATH);

    @Test
    void clientDisconnectsAreNotLoggedAsServerErrors(CapturedOutput output) {
        // 끊김 3가지: Tomcat 쓰기 중단, 비동기 응답 사용 불가, 변환기가 감싼 Broken pipe
        List<Exception> disconnectExceptions = List.of(
                new ClientAbortException(new IOException("Broken pipe")),
                new AsyncRequestNotUsableException("Response not usable after response errors."),
                new HttpMessageNotWritableException("Could not write", new ClientAbortException(new IOException("Broken pipe"))));

        for (Exception disconnectException : disconnectExceptions) {
            assertThat(globalExceptionHandler.handleUnhandledException(disconnectException, imageRequest)).isNull();
        }
        assertThat(output).doesNotContain("failed 500").doesNotContain("Broken pipe");
    }

    @Test
    void otherUnhandledExceptionsStayServerErrors(CapturedOutput output) {
        assertThat(globalExceptionHandler.handleUnhandledException(new IllegalStateException("boom"), imageRequest).getStatusCode())
                .isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
        assertThat(output).contains("[exception] failed 500 : unhandled : path=" + IMAGE_PATH);
    }
}
