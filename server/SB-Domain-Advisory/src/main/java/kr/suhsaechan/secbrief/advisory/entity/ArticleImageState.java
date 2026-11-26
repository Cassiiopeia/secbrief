package kr.suhsaechan.secbrief.advisory.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import kr.suhsaechan.secbrief.advisory.constant.ImageStatus;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 기사 대표 이미지 상태 (Article 에 포함): 피드가 준 주소 > 대기 > 저장(객체 키) 또는 실패
 * 주소가 없으면 NONE, 실패 3회째면 FAILED (다시 내려받지 않음)
 */
@Embeddable
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ArticleImageState {

    // 이미지 최대 시도 횟수 (넘으면 FAILED, 다시 내려받지 않음)
    public static final int MAXIMUM_ATTEMPT_COUNT = 3;

    // 피드가 준 원본 이미지 주소 (없으면 null)
    @Column(name = "image_source_url", length = 1000)
    private String sourceUrl;

    // 저장소 객체 키 (STORED 일 때만 값 있음)
    @Column(name = "image_object_key", length = 200)
    private String objectKey;

    @Enumerated(EnumType.STRING)
    @Column(name = "image_status", nullable = false, length = 16)
    private ImageStatus status;

    @Column(name = "image_attempt_count", nullable = false)
    private int attemptCount;

    /**
     * 새 기사의 이미지 상태: 주소가 있으면 대기(PENDING), 없으면 NONE
     */
    public static ArticleImageState ofSourceUrl(String sourceUrl) {
        ArticleImageState imageState = new ArticleImageState();
        imageState.sourceUrl = sourceUrl;
        imageState.status = sourceUrl == null ? ImageStatus.NONE : ImageStatus.PENDING;
        return imageState;
    }

    /**
     * 주소가 없던(NONE) 기사에 주소 받기 > 대기 (반환: 받았는지)
     */
    public boolean attachSourceUrlIfNone(String sourceUrl) {
        if (status != ImageStatus.NONE || sourceUrl == null) {
            return false;
        }
        this.sourceUrl = sourceUrl;
        this.status = ImageStatus.PENDING;
        return true;
    }

    /**
     * 저장 성공: 객체 키 기록 > STORED
     */
    public void markStored(String objectKey) {
        this.objectKey = objectKey;
        this.status = ImageStatus.STORED;
    }

    /**
     * 실패 기록: 시도 3회째면 FAILED 아니면 PENDING 유지
     */
    public void recordFailure() {
        this.attemptCount++;
        this.status = attemptCount >= MAXIMUM_ATTEMPT_COUNT ? ImageStatus.FAILED : ImageStatus.PENDING;
    }
}
