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
 * 출처별 수집 시도 기록: 시작 > 성공(신규 건수) 또는 실패(에러 코드, 정리된 메시지) 또는 중단(실패 + INTERRUPTED_ERROR_CODE)
 * 완료 전에는 status·finishedAt 이 null
 */
@Entity
@Table(name = "collect_run")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class CollectRun {

    // 끝나기 전에 서버가 멈춘 기록의 에러 코드 (상태는 FAILED: 새 상태값은 배포 전환·되돌리기 중 이전 서버가 읽지 못함, #58)
    public static final String INTERRUPTED_ERROR_CODE = "COLLECT_INTERRUPTED";
    // 중단 기록의 문구 (관리자 화면은 AdminDisplayLabels 의 한국어 이름으로 보임)
    public static final String INTERRUPTED_ERROR_MESSAGE = "server stopped before collect finished";

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

    /**
     * 성공 기록: 중단으로 표시된 뒤 이전 서버가 끝낸 경우도 있어 에러 값을 비움 (배포 전환 중)
     */
    public void finishSuccess(Instant finishedAt, int newCount) {
        this.status = CollectStatus.SUCCESS;
        this.finishedAt = finishedAt;
        this.newCount = newCount;
        this.errorCode = null;
        this.errorMessage = null;
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

    /**
     * 중단 기록: 끝나지 않은 채 남은 시도 (서버 재시작·배포 전환으로 수집 스레드가 사라짐)
     */
    public void markInterrupted(Instant markedAt) {
        finishFailed(markedAt, INTERRUPTED_ERROR_CODE, INTERRUPTED_ERROR_MESSAGE);
    }
}
