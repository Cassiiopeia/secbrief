package kr.suhsaechan.secbrief.application.collect;

import java.time.Instant;
import java.util.List;
import kr.suhsaechan.secbrief.advisory.collector.FeedValidators;
import kr.suhsaechan.secbrief.advisory.constant.CollectStatus;
import kr.suhsaechan.secbrief.advisory.entity.CollectRun;
import kr.suhsaechan.secbrief.advisory.entity.Source;
import kr.suhsaechan.secbrief.advisory.repository.CollectRunRepository;
import kr.suhsaechan.secbrief.advisory.repository.SourceRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * 수집 기록: 시도 시작 > 성공(신규 건수, 출처 마지막 수집 시각·캐시 검증값 갱신) 또는 실패(출처 시각·검증값 유지)
 * 기록마다 짧은 트랜잭션 (외부 호출 동안 트랜잭션을 잡지 않음)
 */
@Component
@RequiredArgsConstructor
public class CollectRunRecorder {

    private final CollectRunRepository collectRunRepository;
    private final SourceRepository sourceRepository;

    @Transactional
    public Long start(Long sourceId, Instant startedAt) {
        Source source = sourceRepository.getReferenceById(sourceId);
        return collectRunRepository.save(CollectRun.start(source, startedAt)).getId();
    }

    /**
     * feedValidators: 다음 조건부 수집에 쓸 값 (항목 저장이 끝난 뒤에만 저장: 먼저 저장하면 다음 수집이 304 로 항목을 놓침)
     */
    @Transactional
    public void finishSuccess(Long runId, Long sourceId, Instant finishedAt, int newCount, FeedValidators feedValidators) {
        collectRunRepository.findById(runId).orElseThrow().finishSuccess(finishedAt, newCount);
        Source source = sourceRepository.findById(sourceId).orElseThrow();
        source.recordCollectResult(CollectStatus.SUCCESS, finishedAt);
        source.replaceFeedValidators(feedValidators.etag(), feedValidators.lastModified());
    }

    /**
     * sanitizedErrorMessage: 호출부가 정리한 값 (ArticleNormalizer.toSanitizedErrorMessage)
     */
    @Transactional
    public void finishFailed(Long runId, Long sourceId, Instant finishedAt, String errorCode, String sanitizedErrorMessage) {
        collectRunRepository.findById(runId).orElseThrow().finishFailed(finishedAt, errorCode, sanitizedErrorMessage);
        sourceRepository.findById(sourceId).orElseThrow().recordCollectResult(CollectStatus.FAILED, finishedAt);
    }

    /**
     * 끝나지 않은 시도 > 중단 표시 (기준 시각 전에 시작한 것만), 반환: 표시한 기록의 출처 코드 목록
     * 출처의 마지막 상태·수집 시각은 그대로 (끝난 시도만 반영)
     */
    @Transactional
    public List<String> markUnfinishedAsInterrupted(Instant startedBefore, Instant markedAt) {
        List<CollectRun> unfinishedRuns = collectRunRepository.findByFinishedAtIsNullAndStartedAtBefore(startedBefore);
        unfinishedRuns.forEach(collectRun -> collectRun.markInterrupted(markedAt));
        return unfinishedRuns.stream().map(collectRun -> collectRun.getSource().getCode()).toList();
    }
}
