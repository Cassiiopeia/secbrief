package kr.suhsaechan.secbrief.application.admin.stats;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.LongSupplier;
import java.util.function.Supplier;

/**
 * 관리자 집계 결과 60초 메모리 캐시 (인스턴스별): 키 > 값
 * 키가 200개를 넘으면 통째로 비움 (관리자 몇 명이 기간을 바꿔 가며 보는 정도)
 */
public class AdminStatsCache<V> {

    // 유지 시간 (60초)
    public static final long TIME_TO_LIVE_MILLISECONDS = 60_000L;
    private static final int MAXIMUM_ENTRY_COUNT = 200;

    private final Map<String, CachedValue<V>> cachedValueByKey = new ConcurrentHashMap<>();
    private final LongSupplier currentMilliseconds;

    public AdminStatsCache(LongSupplier currentMilliseconds) {
        this.currentMilliseconds = currentMilliseconds;
    }

    /**
     * 60초 안에 만든 값이 있으면 그대로, 없으면 새로 만들어 저장 (만들다 실패하면 저장하지 않고 던짐)
     */
    public V getOrLoad(String key, Supplier<V> loader) {
        long now = currentMilliseconds.getAsLong();
        CachedValue<V> cached = cachedValueByKey.get(key);
        if (cached != null && now - cached.loadedAtMilliseconds() < TIME_TO_LIVE_MILLISECONDS) {
            return cached.value();
        }
        V loaded = loader.get();
        if (cachedValueByKey.size() >= MAXIMUM_ENTRY_COUNT) {
            cachedValueByKey.clear();
        }
        cachedValueByKey.put(key, new CachedValue<>(loaded, now));
        return loaded;
    }

    public void clear() {
        cachedValueByKey.clear();
    }

    private record CachedValue<V>(V value, long loadedAtMilliseconds) {
    }
}
