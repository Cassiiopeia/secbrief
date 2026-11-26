package kr.suhsaechan.secbrief.advisory.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import kr.suhsaechan.secbrief.advisory.constant.ArticleKind;
import kr.suhsaechan.secbrief.advisory.constant.ArticleRegion;
import kr.suhsaechan.secbrief.advisory.constant.CollectStatus;
import kr.suhsaechan.secbrief.advisory.constant.FeedFormat;
import kr.suhsaechan.secbrief.common.entity.BaseEntity;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 수집 출처: 경로, 주기, 켜기·끄기, 마지막 수집 결과, 피드 캐시 검증값
 * 행은 마이그레이션 시드로만 생김 (출처 추가·변경은 코드가 아니라 DB)
 */
@Entity
@Table(name = "source")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Source extends BaseEntity {

    // source.feed_etag, source.feed_last_modified 컬럼 길이 (V0.5.0 마이그레이션과 같은 값)
    private static final int FEED_ETAG_MAX_LENGTH = 255;
    private static final int FEED_LAST_MODIFIED_MAX_LENGTH = 64;
    // 수집 주기 허용 범위 (분): 5분 ~ 하루
    public static final int MINIMUM_INTERVAL_MINUTES = 5;
    public static final int MAXIMUM_INTERVAL_MINUTES = 1440;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // 출처 식별 코드 (SourceCode 상수와 동일)
    @Column(nullable = false, unique = true, length = 50)
    private String code;

    @Column(nullable = false, length = 100)
    private String displayName;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private ArticleKind kind;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private ArticleRegion region;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private FeedFormat feedFormat;

    @Column(nullable = false, length = 1000)
    private String feedUrl;

    @Column(nullable = false)
    private int intervalMinutes;

    @Column(nullable = false)
    private boolean isEnabled;

    // 마지막 성공 수집 시각 (실패는 갱신 안 함)
    private Instant lastCollectedAt;

    @Enumerated(EnumType.STRING)
    @Column(length = 16)
    private CollectStatus lastStatus;

    // 출처가 준 캐시 검증값 (다음 수집의 If-None-Match / If-Modified-Since, 안 주는 출처는 null)
    @Column(length = FEED_ETAG_MAX_LENGTH)
    private String feedEtag;

    @Column(length = FEED_LAST_MODIFIED_MAX_LENGTH)
    private String feedLastModified;

    /**
     * 수집 설정 변경 (관리자 화면): 켜기·끄기, 주기(분). 범위 검사는 호출부 책임
     */
    public void changeCollectSettings(boolean isEnabled, int intervalMinutes) {
        this.isEnabled = isEnabled;
        this.intervalMinutes = intervalMinutes;
    }

    /**
     * 수집 결과 반영: 상태는 항상, 수집 시각은 성공일 때만
     */
    public void recordCollectResult(CollectStatus status, Instant finishedAt) {
        this.lastStatus = status;
        if (status == CollectStatus.SUCCESS) {
            this.lastCollectedAt = finishedAt;
        }
    }

    /**
     * 캐시 검증값 교체 (수집·저장이 끝난 성공 기록 때만 호출: 저장 전에 바꾸면 다음 수집이 304 로 항목을 놓침)
     * 컬럼 길이를 넘는 값은 버림 (잘린 값은 출처가 알아보지 못함)
     */
    public void replaceFeedValidators(String feedEtag, String feedLastModified) {
        this.feedEtag = toStorableValidator(feedEtag, FEED_ETAG_MAX_LENGTH);
        this.feedLastModified = toStorableValidator(feedLastModified, FEED_LAST_MODIFIED_MAX_LENGTH);
    }

    private static String toStorableValidator(String validator, int maxLength) {
        return validator != null && validator.length() <= maxLength ? validator : null;
    }
}
