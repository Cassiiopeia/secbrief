package kr.suhsaechan.secbrief.application.collect;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

/**
 * 기동 시 수집 기록 정리: 이 서버 기동 전에 시작해 끝나지 않은 시도 > 중단 표시 (#58)
 * 첫 수집 틱(30초 뒤)보다 먼저 돎, 아직 도는 이전 서버의 시도는 그 서버가 끝내면 다시 덮임
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class UnfinishedCollectRunCleaner implements ApplicationRunner {

    private final CollectRunRecorder collectRunRecorder;
    private final Clock clock;

    @Override
    public void run(ApplicationArguments arguments) {
        Instant startedAt = clock.instant();
        List<String> interruptedSourceCodes = collectRunRecorder.markUnfinishedAsInterrupted(startedAt, startedAt);
        if (!interruptedSourceCodes.isEmpty()) {
            log.info("[collect run] marked interrupted : count={}, sources={}",
                    interruptedSourceCodes.size(), interruptedSourceCodes);
        }
    }
}
