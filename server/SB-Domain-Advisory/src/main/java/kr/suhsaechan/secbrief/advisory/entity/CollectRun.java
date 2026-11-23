package kr.suhsaechan.secbrief.advisory.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.Instant;
import kr.suhsaechan.secbrief.advisory.constant.CollectStatus;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 출처별 수집 시도 기록: 시작 > 성공(신규 건수) 또는 실패(에러 코드, 정리된 메시지)
 * 완료 전에는 status·finishedAt 이 null
 */
@Entity
@Table(name = "collect_run")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class CollectRun {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "source_id", nullable = false)
    private Source source;

    @Column(nullable = false)
    private Instant startedAt;

    private Instant finishedAt;

    @Enumerated(EnumType.STRING)
    @Column(length = 16)
    private CollectStatus status;

    @Column(nullable = false)
    private int newCount;

    @Column(length = 64)
    private String errorCode;

    @Column(length = 500)
    private String errorMessage;

    /**
     * 수집 시작 기록 (완료 전 상태)
     */
    public static CollectRun start(Source source, Instant startedAt) {
        CollectRun collectRun = new CollectRun();
        collectRun.source = source;
        collectRun.startedAt = startedAt;
        return collectRun;
    }

    public void finishSuccess(Instant finishedAt, int newCount) {
        this.status = CollectStatus.SUCCESS;
        this.finishedAt = finishedAt;
        this.newCount = newCount;
    }

    /**
     * 실패 기록: 메시지는 호출자가 정리한 값만 받음 (제어문자 제거, 500자 이내)
     */
    public void finishFailed(Instant finishedAt, String errorCode, String sanitizedErrorMessage) {
        this.status = CollectStatus.FAILED;
        this.finishedAt = finishedAt;
        this.errorCode = errorCode;
        this.errorMessage = sanitizedErrorMessage;
    }
}
