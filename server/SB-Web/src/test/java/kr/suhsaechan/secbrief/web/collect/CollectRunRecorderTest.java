package kr.suhsaechan.secbrief.web.collect;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import kr.suhsaechan.secbrief.advisory.entity.CollectRun;
import kr.suhsaechan.secbrief.advisory.collector.FeedValidators;
import kr.suhsaechan.secbrief.advisory.constant.CollectStatus;
import kr.suhsaechan.secbrief.advisory.constant.SourceCode;
import kr.suhsaechan.secbrief.advisory.entity.Source;
import kr.suhsaechan.secbrief.advisory.repository.CollectRunRepository;
import kr.suhsaechan.secbrief.advisory.repository.SourceRepository;
import kr.suhsaechan.secbrief.application.admin.label.AdminDisplayLabels;
import kr.suhsaechan.secbrief.application.admin.source.AdminSourceRow;
import kr.suhsaechan.secbrief.application.admin.source.AdminSourceService;
import kr.suhsaechan.secbrief.application.collect.CollectRunRecorder;
import kr.suhsaechan.secbrief.web.support.PostgresContainerTestSupport;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

/**
 * 수집 기록 (DB): 성공이면 출처 수집 시각·캐시 검증값 저장, 실패면 둘 다 유지
 * 끝나지 않은 시도: 기준 시각 전 시작분만 중단 표시 (출처 상태 유지), 관리자 화면 사유는 한국어 이름, 뒤늦게 끝나면 덮임 (#58)
 */
@SpringBootTest
@ActiveProfiles("test")
class CollectRunRecorderTest extends PostgresContainerTestSupport {

    private static final Instant STARTED_AT = Instant.parse("2026-11-26T00:00:00Z");
    private static final Instant FINISHED_AT = Instant.parse("2026-11-26T00:00:05Z");
    private static final FeedValidators FIRST_VALIDATORS = new FeedValidators("\"v1\"", "Wed, 25 Nov 2026 00:00:00 GMT");

    @Autowired
    private CollectRunRecorder collectRunRecorder;
    @Autowired
    private SourceRepository sourceRepository;
    @Autowired
    private CollectRunRepository collectRunRepository;
    @Autowired
    private AdminSourceService adminSourceService;

    private Long sourceId;

    @BeforeEach
    void clearRunsAndLoadSource() {
        collectRunRepository.deleteAll();
        sourceId = sourceRepository.findByCode(SourceCode.KISA).orElseThrow().getId();
    }

    @Test
    void successStoresFeedValidatorsAndCollectedAt() {
        Long runId = collectRunRecorder.start(sourceId, STARTED_AT);

        collectRunRecorder.finishSuccess(runId, sourceId, FINISHED_AT, 3, FIRST_VALIDATORS);

        Source source = sourceRepository.findById(sourceId).orElseThrow();
        assertThat(source.getFeedEtag()).isEqualTo(FIRST_VALIDATORS.etag());
        assertThat(source.getFeedLastModified()).isEqualTo(FIRST_VALIDATORS.lastModified());
        assertThat(source.getLastCollectedAt()).isEqualTo(FINISHED_AT);
        assertThat(collectRunRepository.findById(runId).orElseThrow().getNewCount()).isEqualTo(3);
    }

    @Test
    void failureKeepsStoredFeedValidators() {
        collectRunRecorder.finishSuccess(collectRunRecorder.start(sourceId, STARTED_AT), sourceId, FINISHED_AT, 0, FIRST_VALIDATORS);

        Long failedRunId = collectRunRecorder.start(sourceId, FINISHED_AT.plusSeconds(60));
        collectRunRecorder.finishFailed(failedRunId, sourceId, FINISHED_AT.plusSeconds(65), "EXTERNAL_API_ERROR", "status=503");

        Source source = sourceRepository.findById(sourceId).orElseThrow();
        assertThat(source.getLastStatus()).isEqualTo(CollectStatus.FAILED);
        assertThat(source.getFeedEtag()).isEqualTo(FIRST_VALIDATORS.etag());
        assertThat(source.getLastCollectedAt()).isEqualTo(FINISHED_AT);
    }

    @Test
    void successWithoutValidatorsClearsStoredOnes() {
        collectRunRecorder.finishSuccess(collectRunRecorder.start(sourceId, STARTED_AT), sourceId, FINISHED_AT, 0, FIRST_VALIDATORS);

        Long runId = collectRunRecorder.start(sourceId, FINISHED_AT.plusSeconds(60));
        collectRunRecorder.finishSuccess(runId, sourceId, FINISHED_AT.plusSeconds(65), 0, FeedValidators.NONE);

        assertThat(sourceRepository.findById(sourceId).orElseThrow().getFeedEtag()).isNull();
    }

    @Test
    void marksOnlyRunsStartedBeforeCutoffAsInterrupted() {
        collectRunRecorder.finishSuccess(collectRunRecorder.start(sourceId, STARTED_AT), sourceId, FINISHED_AT, 0, FIRST_VALIDATORS);
        Long leftoverRunId = collectRunRecorder.start(sourceId, FINISHED_AT.plusSeconds(60));
        Instant serverStartedAt = FINISHED_AT.plusSeconds(120);
        Long currentServerRunId = collectRunRecorder.start(sourceId, serverStartedAt.plusSeconds(30));

        assertThat(collectRunRecorder.markUnfinishedAsInterrupted(serverStartedAt, serverStartedAt))
                .containsExactly(SourceCode.KISA);

        CollectRun leftoverRun = collectRunRepository.findById(leftoverRunId).orElseThrow();
        assertThat(leftoverRun.getStatus()).isEqualTo(CollectStatus.FAILED);
        assertThat(leftoverRun.getErrorCode()).isEqualTo(CollectRun.INTERRUPTED_ERROR_CODE);
        assertThat(leftoverRun.getFinishedAt()).isEqualTo(serverStartedAt);
        assertThat(collectRunRepository.findById(currentServerRunId).orElseThrow().getFinishedAt()).isNull();
        // 출처의 마지막 상태는 끝난 시도 기준 그대로
        assertThat(sourceRepository.findById(sourceId).orElseThrow().getLastStatus()).isEqualTo(CollectStatus.SUCCESS);
    }

    @Test
    void adminSourceRowShowsInterruptedReason() {
        Long leftoverRunId = collectRunRecorder.start(sourceId, STARTED_AT);
        collectRunRecorder.markUnfinishedAsInterrupted(FINISHED_AT, FINISHED_AT);

        AdminSourceRow sourceRow = adminSourceService.findAllSources().stream()
                .filter(row -> row.id() == sourceId).findFirst().orElseThrow();
        assertThat(sourceRow.lastFailureReason()).isEqualTo(AdminDisplayLabels.COLLECT_INTERRUPTED_REASON);
        assertThat(sourceRow.lastFailedAt()).isEqualTo(FINISHED_AT);
        assertThat(collectRunRepository.findById(leftoverRunId).orElseThrow().getErrorMessage())
                .isEqualTo(CollectRun.INTERRUPTED_ERROR_MESSAGE);
    }

    @Test
    void lateFinishOfInterruptedRunOverwritesInterruption() {
        Long runId = collectRunRecorder.start(sourceId, STARTED_AT);
        collectRunRecorder.markUnfinishedAsInterrupted(FINISHED_AT, FINISHED_AT);

        // 배포 전환 중 이전 서버가 그 시도를 끝냄
        collectRunRecorder.finishSuccess(runId, sourceId, FINISHED_AT.plusSeconds(5), 2, FIRST_VALIDATORS);

        CollectRun finishedRun = collectRunRepository.findById(runId).orElseThrow();
        assertThat(finishedRun.getStatus()).isEqualTo(CollectStatus.SUCCESS);
        assertThat(finishedRun.getErrorCode()).isNull();
        assertThat(finishedRun.getErrorMessage()).isNull();
    }
}
