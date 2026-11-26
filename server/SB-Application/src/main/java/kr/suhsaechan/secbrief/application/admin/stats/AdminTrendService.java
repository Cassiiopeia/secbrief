package kr.suhsaechan.secbrief.application.admin.stats;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.function.LongSupplier;
import kr.suhsaechan.secbrief.advisory.constant.ArticleKind;
import kr.suhsaechan.secbrief.application.admin.label.AdminDisplayLabels;
import kr.suhsaechan.secbrief.application.admin.usage.ApiUsageGroup;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

/**
 * 관리자 추이 그래프 4종: 수집(종류별), AI 사용(성공·실패 또는 비용), 이용(화면 묶음별), 알림 기기 등록
 * DB 는 기록 있는 칸만 줌 > 빈 칸 0 채움, 줄은 기록이 없어도 항상 같은 순서로 반환. 결과는 60초 캐시
 */
@Service
public class AdminTrendService {

    private static final String SERIES_AI_SUCCESS = "AI_SUCCESS";
    private static final String SERIES_AI_FAILURE = "AI_FAILURE";
    private static final String SERIES_AI_COST = "AI_COST";

    private final AdminTrendQueryRepository adminTrendQueryRepository;
    private final AdminStatsCache<AdminTrendChart> trendCache;

    @Autowired
    public AdminTrendService(AdminTrendQueryRepository adminTrendQueryRepository) {
        this(adminTrendQueryRepository, System::currentTimeMillis);
    }

    // 테스트에서 캐시 시계를 바꿔 끼우는 생성자
    AdminTrendService(AdminTrendQueryRepository adminTrendQueryRepository, LongSupplier currentMilliseconds) {
        this.adminTrendQueryRepository = adminTrendQueryRepository;
        this.trendCache = new AdminStatsCache<>(currentMilliseconds);
    }

    /**
     * 수집 추이: 종류(뉴스·공지·취약점)별 수집 건수 (article.collected_at)
     */
    public AdminTrendChart loadCollectTrend(AdminTrendRange range) {
        return trendCache.getOrLoad("collect|" + range.cacheKey(), () -> {
            List<LocalDate> bucketStarts = bucketStartsOf(range);
            List<AdminTrendBucketCount> counts = adminTrendQueryRepository.countCollectedArticlesByKind(
                    range.unit(), bucketStarts.get(0), range.to().plusDays(1));
            return toChart(range, bucketStarts, toCountSeries(bucketStarts, counts,
                    Arrays.asList(ArticleKind.values()), ArticleKind::name, AdminDisplayLabels::ofArticleKind));
        });
    }

    /**
     * AI 사용 추이: 호출 수(성공·실패 2줄) 또는 추정 비용(USD 1줄)
     */
    public AdminTrendChart loadAiUsageTrend(AdminTrendRange range, AdminAiUsageMetric metric) {
        return trendCache.getOrLoad("ai|" + metric.getParameterValue() + "|" + range.cacheKey(), () -> {
            List<LocalDate> bucketStarts = bucketStartsOf(range);
            Map<String, AdminAiUsageBucket> usageByBucketStart = new HashMap<>();
            adminTrendQueryRepository.sumAiCalls(range.unit(), bucketStarts.get(0), range.to().plusDays(1))
                    .forEach(usage -> usageByBucketStart.put(usage.bucketStart(), usage));
            return toChart(range, bucketStarts, toAiUsageSeries(bucketStarts, usageByBucketStart, metric));
        });
    }

    /**
     * 이용 추이: 화면 묶음별 요청 수 (api_usage_daily, 이미지 요청 제외)
     */
    public AdminTrendChart loadApiUsageTrend(AdminTrendRange range) {
        return trendCache.getOrLoad("usage|" + range.cacheKey(), () -> {
            List<LocalDate> bucketStarts = bucketStartsOf(range);
            List<AdminTrendBucketCount> counts = adminTrendQueryRepository.sumApiUsageByGroup(
                    range.unit(), bucketStarts.get(0), range.to().plusDays(1));
            return toChart(range, bucketStarts, toCountSeries(bucketStarts, counts,
                    ApiUsageGroup.screenViewGroups(), ApiUsageGroup::name, ApiUsageGroup::getLabel));
        });
    }

    /**
     * 알림 기기 등록 추이: 지금 남아 있는 기기의 등록일 기준 (알림을 모두 끈 기기는 행이 지워져 빠짐)
     */
    public AdminTrendChart loadPushDeviceTrend(AdminTrendRange range) {
        return trendCache.getOrLoad("push-device|" + range.cacheKey(), () -> {
            List<LocalDate> bucketStarts = bucketStartsOf(range);
            List<AdminTrendBucketCount> counts = adminTrendQueryRepository.countNewPushDevices(
                    range.unit(), bucketStarts.get(0), range.to().plusDays(1));
            return toChart(range, bucketStarts, toCountSeries(bucketStarts, counts,
                    List.of(AdminTrendQueryRepository.PUSH_DEVICE_SERIES_KEY), Function.identity(), key -> "신규 등록"));
        });
    }

    private static List<LocalDate> bucketStartsOf(AdminTrendRange range) {
        return range.unit().bucketStartsBetween(range.from(), range.to());
    }

    private static AdminTrendChart toChart(AdminTrendRange range, List<LocalDate> bucketStarts, List<AdminTrendSeries> series) {
        return new AdminTrendChart(range.unit().getSqlName(),
                bucketStarts.stream().map(range.unit()::labelOf).toList(),
                bucketStarts.stream().map(LocalDate::toString).toList(),
                series);
    }

    // 정해진 줄 순서대로 칸별 건수 (기록 없는 칸 0, 모르는 줄 key 는 버림)
    private static <T> List<AdminTrendSeries> toCountSeries(List<LocalDate> bucketStarts, List<AdminTrendBucketCount> counts,
                                                           List<T> seriesItems, Function<T, String> toSeriesKey,
                                                           Function<T, String> toSeriesLabel) {
        Map<String, Map<String, Long>> countBySeriesKeyAndBucketStart = new HashMap<>();
        counts.forEach(count -> countBySeriesKeyAndBucketStart
                .computeIfAbsent(count.seriesKey(), key -> new HashMap<>())
                .merge(count.bucketStart(), count.totalCount(), Long::sum));
        List<AdminTrendSeries> series = new ArrayList<>(seriesItems.size());
        for (T seriesItem : seriesItems) {
            String seriesKey = toSeriesKey.apply(seriesItem);
            Map<String, Long> countByBucketStart = countBySeriesKeyAndBucketStart.getOrDefault(seriesKey, Map.of());
            List<Number> values = new ArrayList<>(bucketStarts.size());
            bucketStarts.forEach(bucketStart -> values.add(countByBucketStart.getOrDefault(bucketStart.toString(), 0L)));
            series.add(new AdminTrendSeries(seriesKey, toSeriesLabel.apply(seriesItem), values));
        }
        return series;
    }

    private static List<AdminTrendSeries> toAiUsageSeries(List<LocalDate> bucketStarts,
                                                          Map<String, AdminAiUsageBucket> usageByBucketStart,
                                                          AdminAiUsageMetric metric) {
        List<Number> successCounts = new ArrayList<>(bucketStarts.size());
        List<Number> failureCounts = new ArrayList<>(bucketStarts.size());
        List<Number> costs = new ArrayList<>(bucketStarts.size());
        for (LocalDate bucketStart : bucketStarts) {
            AdminAiUsageBucket usage = usageByBucketStart.get(bucketStart.toString());
            successCounts.add(usage == null ? 0L : usage.successCount());
            failureCounts.add(usage == null ? 0L : usage.failureCount());
            costs.add(usage == null ? 0.0 : usage.estimatedCostUsd().doubleValue());
        }
        return switch (metric) {
            case CALL_COUNT -> List.of(new AdminTrendSeries(SERIES_AI_SUCCESS, "성공", successCounts),
                    new AdminTrendSeries(SERIES_AI_FAILURE, "실패", failureCounts));
            case COST_USD -> List.of(new AdminTrendSeries(SERIES_AI_COST, "추정 비용 (USD)", costs));
        };
    }
}
