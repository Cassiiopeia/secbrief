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
 * 수집 출처: 경로, 주기, 켜기·끄기, 마지막 수집 결과
 * 행은 마이그레이션 시드로만 생김 (출처 추가·변경은 코드가 아니라 DB)
 */
@Entity
@Table(name = "source")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Source extends BaseEntity {

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

    /**
     * 수집 결과 반영: 상태는 항상, 수집 시각은 성공일 때만
     */
    public void recordCollectResult(CollectStatus status, Instant finishedAt) {
        this.lastStatus = status;
        if (status == CollectStatus.SUCCESS) {
            this.lastCollectedAt = finishedAt;
        }
    }
}
