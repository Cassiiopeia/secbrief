package kr.suhsaechan.secbrief.advisory.image;

import java.util.Optional;

/**
 * 기사 대표 이미지 저장소 규약: 준비 여부, 저장, 읽기 (구현: MinIO)
 * 준비되지 않았으면 이미지 처리만 건너뜀 (서버 기동·수집과 무관), 저장소 장애는 IMAGE_STORAGE_ERROR
 */
public interface ArticleImageStorage {

    // 접속 설정이 모두 있는지 (실제 접속은 확인하지 않음)
    boolean isAvailable();

    /**
     * 줄인 JPEG 저장 (같은 키면 덮어씀)
     */
    void store(String objectKey, byte[] jpegBytes);

    /**
     * 저장된 JPEG 읽기 (객체가 없으면 비어 있음)
     */
    Optional<byte[]> read(String objectKey);
}
