package kr.suhsaechan.secbrief.advisory.indicator;

import java.time.Clock;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import kr.suhsaechan.secbrief.advisory.collector.FeedHttpClient;
import kr.suhsaechan.secbrief.common.exception.CustomException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * 해외 지표 보관: 주기 틱(1시간)이 지표마다 읽어 메모리에 둠 > 화면 요청은 보관값만 씀 (외부 응답을 기다리지 않음)
 * 읽기 실패면 이전 값과 그 시각을 그대로 둠, 한 번도 못 읽었으면 값 없음 (화면은 지표 없이 열림)
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class InternationalAlertIndicatorStore {

    private final FeedHttpClient feedHttpClient;
    private final Clock clock;
    private final Map<InternationalAlertIndicator, InternationalAlertIndicatorReading> latestReadingByIndicator =
            new ConcurrentHashMap<>();

    /**
     * 지표 전부 다시 읽기 (지표마다 격리: 하나가 실패해도 나머지는 읽음)
     */
    public void refreshAll() {
        for (InternationalAlertIndicator indicator : InternationalAlertIndicator.values()) {
            refresh(indicator);
        }
    }

    public Optional<InternationalAlertIndicatorReading> findLatestReading(InternationalAlertIndicator indicator) {
        return Optional.ofNullable(latestReadingByIndicator.get(indicator));
    }

    private void refresh(InternationalAlertIndicator indicator) {
        try {
            Optional<String> levelKey = InternationalAlertIndicatorParser.toLevelKey(
                    indicator, feedHttpClient.fetch(indicator.getFeedUrl()));
            if (levelKey.isEmpty()) {
                log.warn("[alert indicator] rejected 502 : indicator={}, reason=unknown level", indicator);
                return;
            }
            latestReadingByIndicator.put(indicator,
                    new InternationalAlertIndicatorReading(indicator, levelKey.get(), clock.instant()));
        } catch (CustomException exception) {
            log.warn("[alert indicator] failed 502 : indicator={}, code={}", indicator, exception.getErrorCode().name());
        }
    }
}
