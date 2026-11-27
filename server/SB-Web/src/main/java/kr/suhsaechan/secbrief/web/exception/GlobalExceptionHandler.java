package kr.suhsaechan.secbrief.web.exception;

import jakarta.servlet.http.HttpServletRequest;
import java.util.stream.Collectors;
import kr.suhsaechan.secbrief.common.exception.CustomException;
import kr.suhsaechan.secbrief.common.exception.ErrorResponse;
import kr.suhsaechan.secbrief.common.exception.constant.ErrorCode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.support.DefaultMessageSourceResolvable;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.resource.NoResourceFoundException;
import org.springframework.web.util.DisconnectedClientHelper;

/**
 * 전역 예외 처리: 모든 예외 > 표준 에러 응답 {errorCode, message}
 * 내부 예외 문구는 응답에 싣지 않고 로그에만 남김
 */
@Slf4j
@RestControllerAdvice(basePackages = "kr.suhsaechan.secbrief")
public class GlobalExceptionHandler {

    private static final String VALIDATION_MESSAGE_DELIMITER = ", ";

    /** 업무 예외: 4xx 는 WARN, 5xx 는 ERROR */
    @ExceptionHandler(CustomException.class)
    public ResponseEntity<ErrorResponse> handleCustomException(
        CustomException exception, HttpServletRequest request) {
        ErrorCode errorCode = exception.getErrorCode();
        if (errorCode.getStatus().is5xxServerError()) {
            log.error("[exception] failed {} : code={}, path={}, message={}",
                errorCode.getStatus().value(), errorCode.name(), request.getRequestURI(), exception.getMessage());
        } else {
            log.warn("[exception] rejected {} : code={}, path={}, message={}",
                errorCode.getStatus().value(), errorCode.name(), request.getRequestURI(), exception.getMessage());
        }
        return toResponse(errorCode);
    }

    /** 요청 본문 검증 실패: 필드별 문구를 이어 붙여 응답 */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleValidationException(MethodArgumentNotValidException exception) {
        String validationMessage = exception.getBindingResult().getFieldErrors().stream()
            .map(DefaultMessageSourceResolvable::getDefaultMessage)
            .collect(Collectors.joining(VALIDATION_MESSAGE_DELIMITER));
        log.warn("[exception] rejected 400 : validation failed : {}", validationMessage);
        return ResponseEntity.status(ErrorCode.VALIDATION_ERROR.getStatus())
            .body(ErrorResponse.of(ErrorCode.VALIDATION_ERROR, validationMessage));
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ErrorResponse> handleNotReadable(HttpMessageNotReadableException exception) {
        log.warn("[exception] rejected 400 : body not readable");
        return toResponse(ErrorCode.MESSAGE_NOT_READABLE);
    }

    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ResponseEntity<ErrorResponse> handleMissingParameter(MissingServletRequestParameterException exception) {
        log.warn("[exception] rejected 400 : missing parameter : {}", exception.getParameterName());
        return toResponse(ErrorCode.MISSING_PARAMETER);
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ErrorResponse> handleTypeMismatch(MethodArgumentTypeMismatchException exception) {
        log.warn("[exception] rejected 400 : type mismatch : {}", exception.getName());
        return toResponse(ErrorCode.TYPE_MISMATCH);
    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<ErrorResponse> handleMethodNotSupported(HttpRequestMethodNotSupportedException exception) {
        return toResponse(ErrorCode.METHOD_NOT_ALLOWED);
    }

    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<ErrorResponse> handleNoResource(NoResourceFoundException exception) {
        return toResponse(ErrorCode.RESOURCE_NOT_FOUND);
    }

    /**
     * 처리되지 않은 예외: 원인은 로그에만, 응답은 고정 문구
     * 클라이언트가 끊은 연결 (Broken pipe, ClientAbortException, AsyncRequestNotUsableException): 장애 아님 > debug 한 줄, 응답 안 씀 (#58)
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleUnhandledException(Exception exception, HttpServletRequest request) {
        if (DisconnectedClientHelper.isClientDisconnectedException(exception)) {
            log.debug("[exception] client disconnected : path={}, type={}",
                request.getRequestURI(), exception.getClass().getSimpleName());
            return null;
        }
        log.error("[exception] failed 500 : unhandled : path={}", request.getRequestURI(), exception);
        return toResponse(ErrorCode.INTERNAL_SERVER_ERROR);
    }

    /** ErrorCode > 상태 코드 + 표준 에러 응답 (JSON 고정: 이미지만 받겠다는 요청에도 오류 본문을 쓸 수 있게) */
    private ResponseEntity<ErrorResponse> toResponse(ErrorCode errorCode) {
        return ResponseEntity.status(errorCode.getStatus())
                .contentType(MediaType.APPLICATION_JSON)
                .body(ErrorResponse.of(errorCode));
    }
}
