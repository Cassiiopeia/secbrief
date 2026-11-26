package kr.suhsaechan.secbrief.application.admin.usage;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import kr.suhsaechan.secbrief.advisory.policy.SeoulCalendar;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataAccessResourceFailureException;

/**
 * 이용 건수 저장: 날짜(서울)·묶음별 합계 저장, 저장 실패한 건수는 다음 저장 때 다시, 0건이면 저장 안 함
 */
class ApiUsageFlushServiceTest {

    // 서울 2026-11-26 00:30 (UTC 로는 전날)
    private static final Instant NOW = Instant.parse("2026-11-25T15:30:00Z");
    private static final LocalDate SEOUL_DATE = LocalDate.of(2026, 11, 26);

    private final ApiUsageCounter apiUsageCounter = new ApiUsageCounter(Clock.fixed(NOW, SeoulCalendar.SEOUL));
    private final ApiUsageDailyRepository apiUsageDailyRepository = mock(ApiUsageDailyRepository.class);
    private final ApiUsageFlushService apiUsageFlushService = new ApiUsageFlushService(apiUsageCounter, apiUsageDailyRepository);

    @Test
    void savesSumPerSeoulDateAndGroup() {
        apiUsageCounter.increment(ApiUsageGroup.TODAY);
        apiUsageCounter.increment(ApiUsageGroup.TODAY);
        apiUsageCounter.increment(ApiUsageGroup.SEARCH);

        assertThat(apiUsageFlushService.flush()).isEqualTo(2);

        verify(apiUsageDailyRepository).addRequestCount(new ApiUsageKey(SEOUL_DATE, ApiUsageGroup.TODAY), 2L);
        verify(apiUsageDailyRepository).addRequestCount(new ApiUsageKey(SEOUL_DATE, ApiUsageGroup.SEARCH), 1L);
    }

    @Test
    void doesNotSaveWhenNothingCounted() {
        apiUsageCounter.increment(ApiUsageGroup.TODAY);
        apiUsageFlushService.flush();

        assertThat(apiUsageFlushService.flush()).isZero();

        verify(apiUsageDailyRepository, never()).addRequestCount(any(), eq(0L));
    }

    @Test
    void keepsFailedCountForNextFlush() {
        ApiUsageKey usageKey = new ApiUsageKey(SEOUL_DATE, ApiUsageGroup.ARTICLE_LIST);
        doThrow(new DataAccessResourceFailureException("connection lost"))
                .doNothing()
                .when(apiUsageDailyRepository).addRequestCount(eq(usageKey), anyLong());
        apiUsageCounter.increment(ApiUsageGroup.ARTICLE_LIST);

        assertThat(apiUsageFlushService.flush()).isZero();
        apiUsageCounter.increment(ApiUsageGroup.ARTICLE_LIST);
        assertThat(apiUsageFlushService.flush()).isEqualTo(1);

        // 실패한 1건 + 새 1건을 한 번에 저장
        verify(apiUsageDailyRepository).addRequestCount(usageKey, 2L);
    }
}
