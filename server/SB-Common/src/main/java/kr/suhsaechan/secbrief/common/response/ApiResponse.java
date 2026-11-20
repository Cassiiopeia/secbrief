package kr.suhsaechan.secbrief.common.response;

import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * API 성공 응답 표준 형식: {data}
 * 실패는 ErrorResponse {errorCode, message} 로만 나감
 */
@Getter
@NoArgsConstructor
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class ApiResponse<T> {

    private T data;

    public static <T> ApiResponse<T> of(T data) {
        return new ApiResponse<>(data);
    }
}
