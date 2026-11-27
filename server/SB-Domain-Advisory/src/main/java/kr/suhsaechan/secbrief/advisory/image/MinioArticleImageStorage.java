package kr.suhsaechan.secbrief.advisory.image;

import io.minio.BucketExistsArgs;
import io.minio.GetObjectArgs;
import io.minio.MakeBucketArgs;
import io.minio.MinioClient;
import io.minio.PutObjectArgs;
import io.minio.errors.ErrorResponseException;
import io.minio.errors.MinioException;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.security.GeneralSecurityException;
import java.time.Duration;
import java.util.Optional;
import kr.suhsaechan.secbrief.common.exception.CustomException;
import kr.suhsaechan.secbrief.common.exception.constant.ErrorCode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * MinIO 저장소: 줄인 JPEG 저장·읽기, 버킷이 없으면 첫 저장 때 만듦 (비공개 그대로: 앱에는 서버가 대신 읽어 줌)
 * 주소·키 중 하나라도 비었거나 주소 형식이 틀리면 준비 안 됨 (서버는 그대로 기동), 주소·키는 로그에 남기지 않음
 */
@Slf4j
@Component
public class MinioArticleImageStorage implements ArticleImageStorage {

    private static final String JPEG_CONTENT_TYPE = "image/jpeg";
    // 객체가 없을 때 저장소가 주는 오류 코드
    private static final String NO_SUCH_KEY_ERROR_CODE = "NoSuchKey";
    // 저장 시 조각 크기 자동 (-1): 크기를 아는 한 덩어리 업로드
    private static final long AUTOMATIC_PART_SIZE = -1;
    private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(5);
    private static final Duration WRITE_TIMEOUT = Duration.ofSeconds(20);
    private static final Duration READ_TIMEOUT = Duration.ofSeconds(20);

    private final String bucket;
    // 설정이 없으면 null (준비 안 됨)
    private final MinioClient minioClient;
    // 버킷 확인·생성을 마쳤는지 (첫 저장 때 1회)
    private volatile boolean isBucketReady;

    public MinioArticleImageStorage(@Value("${secbrief.image-storage.endpoint}") String endpoint,
                                    @Value("${secbrief.image-storage.access-key}") String accessKey,
                                    @Value("${secbrief.image-storage.secret-key}") String secretKey,
                                    @Value("${secbrief.image-storage.bucket}") String bucket) {
        this.bucket = bucket;
        this.minioClient = createClientIfConfigured(endpoint, accessKey, secretKey, bucket);
    }

    @Override
    public boolean isAvailable() {
        return minioClient != null;
    }

    @Override
    public void store(String objectKey, byte[] jpegBytes) {
        try {
            ensureBucket();
            requireClient().putObject(PutObjectArgs.builder()
                    .bucket(bucket)
                    .object(objectKey)
                    .stream(new ByteArrayInputStream(jpegBytes), jpegBytes.length, AUTOMATIC_PART_SIZE)
                    .contentType(JPEG_CONTENT_TYPE)
                    .build());
        } catch (MinioException | IOException | GeneralSecurityException | IllegalArgumentException exception) {
            throw storageError("store failed", exception);
        }
    }

    @Override
    public Optional<byte[]> read(String objectKey) {
        try (InputStream objectStream = requireClient().getObject(
                GetObjectArgs.builder().bucket(bucket).object(objectKey).build())) {
            // 우리가 저장한 줄인 JPEG 라 작지만, 버킷에 다른 것이 놓였을 때를 대비해 상한까지만 읽음
            return Optional.of(objectStream.readNBytes(ArticleImageDownloader.MAXIMUM_IMAGE_BYTES));
        } catch (ErrorResponseException exception) {
            if (NO_SUCH_KEY_ERROR_CODE.equals(exception.errorResponse().code())) {
                return Optional.empty();
            }
            throw storageError("read failed", exception);
        } catch (MinioException | IOException | GeneralSecurityException | IllegalArgumentException exception) {
            throw storageError("read failed", exception);
        }
    }

    // 버킷 확인 > 없으면 생성 (성공한 뒤로는 다시 확인하지 않음)
    private void ensureBucket() throws MinioException, IOException, GeneralSecurityException {
        if (isBucketReady) {
            return;
        }
        MinioClient client = requireClient();
        if (!client.bucketExists(BucketExistsArgs.builder().bucket(bucket).build())) {
            client.makeBucket(MakeBucketArgs.builder().bucket(bucket).build());
            log.info("[article image] bucket created");
        }
        isBucketReady = true;
    }

    private MinioClient requireClient() {
        if (minioClient == null) {
            throw new CustomException(ErrorCode.IMAGE_STORAGE_ERROR, "storage not configured");
        }
        return minioClient;
    }

    // 값이 비었거나 주소 형식이 틀리면 null (기동을 막지 않음)
    private static MinioClient createClientIfConfigured(String endpoint, String accessKey, String secretKey, String bucket) {
        if (endpoint.isBlank() || accessKey.isBlank() || secretKey.isBlank() || bucket.isBlank()) {
            return null;
        }
        try {
            MinioClient client = MinioClient.builder()
                    .endpoint(endpoint.strip())
                    .credentials(accessKey.strip(), secretKey.strip())
                    .build();
            client.setTimeout(CONNECT_TIMEOUT.toMillis(), WRITE_TIMEOUT.toMillis(), READ_TIMEOUT.toMillis());
            return client;
        } catch (IllegalArgumentException exception) {
            log.warn("[article image] storage disabled : invalid endpoint");
            return null;
        }
    }

    // 원인 종류와 저장소 오류 코드만 남김 (예외 문구에는 주소가 들어갈 수 있음)
    private static CustomException storageError(String action, Exception exception) {
        String cause = exception instanceof ErrorResponseException errorResponseException
                ? errorResponseException.errorResponse().code()
                : exception.getClass().getSimpleName();
        return new CustomException(ErrorCode.IMAGE_STORAGE_ERROR, action + " : " + cause);
    }
}
