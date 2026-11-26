package kr.suhsaechan.secbrief.application.admin.stats;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.Test;

/**
 * 집계 캐시: 60초 안에는 다시 만들지 않음, 지나면 다시 만듦, 실패한 값은 저장하지 않음
 */
class AdminStatsCacheTest {

    private final AtomicLong currentMilliseconds = new AtomicLong(1_000_000L);
    private final AdminStatsCache<Integer> cache = new AdminStatsCache<>(currentMilliseconds::get);
    private final AtomicInteger loadCount = new AtomicInteger();

    @Test
    void reusesValueWithinTimeToLive() {
        cache.getOrLoad("key", loadCount::incrementAndGet);
        currentMilliseconds.addAndGet(AdminStatsCache.TIME_TO_LIVE_MILLISECONDS - 1);

        assertThat(cache.getOrLoad("key", loadCount::incrementAndGet)).isEqualTo(1);
        assertThat(loadCount).hasValue(1);
    }

    @Test
    void reloadsAfterTimeToLive() {
        cache.getOrLoad("key", loadCount::incrementAndGet);
        currentMilliseconds.addAndGet(AdminStatsCache.TIME_TO_LIVE_MILLISECONDS);

        assertThat(cache.getOrLoad("key", loadCount::incrementAndGet)).isEqualTo(2);
    }

    @Test
    void doesNotStoreFailedLoad() {
        assertThatThrownBy(() -> cache.getOrLoad("key", () -> {
            throw new IllegalStateException("load failed");
        })).isInstanceOf(IllegalStateException.class);

        assertThat(cache.getOrLoad("key", loadCount::incrementAndGet)).isEqualTo(1);
    }
}
