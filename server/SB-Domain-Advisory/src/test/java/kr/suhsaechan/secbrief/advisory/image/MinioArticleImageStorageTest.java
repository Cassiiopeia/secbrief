package kr.suhsaechan.secbrief.advisory.image;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import kr.suhsaechan.secbrief.common.exception.CustomException;
import kr.suhsaechan.secbrief.common.exception.constant.ErrorCode;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/**
 * MinIO 저장소 준비 여부와 객체 키 (저장소 호출 없음): 설정이 비었거나 주소가 틀리면 준비 안 됨, 준비 안 된 채 부르면 IMAGE_STORAGE_ERROR
 */
class MinioArticleImageStorageTest {

    @ParameterizedTest
    @CsvSource(value = {
            "'', key, secret, secbrief",
            "http://minio.example.test:9000, '', secret, secbrief",
            "http://minio.example.test:9000, key, '', secbrief",
            "http://minio.example.test:9000, key, secret, ''",
            // 형식이 틀린 주소 (경로 포함, 스킴 없음): 기동을 막지 않고 준비 안 됨
            "http://minio.example.test:9000/some/path, key, secret, secbrief",
            "not a url, key, secret, secbrief"})
    void isUnavailableWhenSettingIsMissingOrInvalid(String endpoint, String accessKey, String secretKey, String bucket) {
        assertThat(new MinioArticleImageStorage(endpoint, accessKey, secretKey, bucket).isAvailable()).isFalse();
    }

    @Test
    void isAvailableWhenAllSettingsAreGiven() {
        // 객체를 만들 때는 접속하지 않음
        assertThat(new MinioArticleImageStorage("http://minio.example.test:9000", "key", "secret", "secbrief").isAvailable())
                .isTrue();
    }

    @Test
    void throwsImageStorageErrorWhenUsedWithoutSettings() {
        MinioArticleImageStorage storage = new MinioArticleImageStorage("", "", "", "secbrief");

        assertThatThrownBy(() -> storage.store("articles/2026/11/1.jpg", new byte[] {1}))
                .isInstanceOf(CustomException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.IMAGE_STORAGE_ERROR);
        assertThatThrownBy(() -> storage.read("articles/2026/11/1.jpg"))
                .isInstanceOf(CustomException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.IMAGE_STORAGE_ERROR);
    }

    @Test
    void buildsObjectKeyFromSeoulYearMonthAndArticleId() {
        // UTC 11월 30일 16시 = 서울 12월 1일 01시
        assertThat(ArticleImageObjectKey.toObjectKey(123L, Instant.parse("2026-11-30T16:00:00Z")))
                .isEqualTo("articles/2026/12/123.jpg");
        assertThat(ArticleImageObjectKey.toObjectKey(7L, Instant.parse("2027-01-05T00:00:00Z")))
                .isEqualTo("articles/2027/01/7.jpg");
    }
}
