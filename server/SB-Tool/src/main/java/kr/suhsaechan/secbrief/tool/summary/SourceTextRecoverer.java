package kr.suhsaechan.secbrief.tool.summary;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Collection;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;
import java.util.stream.Collectors;
import kr.suhsaechan.secbrief.advisory.collector.ArticleCollector;
import kr.suhsaechan.secbrief.advisory.collector.CollectedArticle;
import kr.suhsaechan.secbrief.advisory.collector.FeedHttpClient;
import kr.suhsaechan.secbrief.advisory.collector.GhsaAdvisoryParser;
import kr.suhsaechan.secbrief.advisory.collector.GhsaArticleCollector;
import kr.suhsaechan.secbrief.advisory.collector.KevArticleCollector;
import kr.suhsaechan.secbrief.advisory.collector.NvdArticleCollector;
import kr.suhsaechan.secbrief.advisory.collector.RssArticleCollector;
import kr.suhsaechan.secbrief.advisory.constant.FeedFormat;
import kr.suhsaechan.secbrief.advisory.entity.Source;
import kr.suhsaechan.secbrief.advisory.policy.ArticleDraft;
import kr.suhsaechan.secbrief.advisory.policy.ArticleNormalizer;
import kr.suhsaechan.secbrief.advisory.refetch.SourceTextRefetchResult;
import kr.suhsaechan.secbrief.advisory.refetch.SourceTextRefetchTarget;
import kr.suhsaechan.secbrief.advisory.refetch.SourceTextRefetcher;
import lombok.extern.slf4j.Slf4j;

/**
 * 요약 입력 되찾기: 운영 수집기로 피드를 다시 읽어 CVE ID·원문 주소로 맞춤 > 못 맞춘 건은 1건씩 다시 받기 (SourceTextRefetcher)
 * 피드 읽기가 실패해도 1건씩 받기는 함, 받은 글은 요약 입력으로만 씀 (임시 파일, 끝나면 지움)
 */
@Slf4j
public class SourceTextRecoverer {

    // NVD 게시일 범위 시작을 가장 오래된 대상보다 이만큼 앞당김 (게시 시각 차이 여유)
    private static final Duration NVD_RANGE_MARGIN = Duration.ofDays(1);
    // NVD 가 한 번에 허용하는 게시일 범위 (120일) 안쪽
    private static final Duration NVD_MAXIMUM_RANGE = Duration.ofDays(119);
    // GitHub 권고 1건 조회: https://api.github.com/advisories/{GHSA ID} (목록 1쪽에 없는 오래된 권고용, 인증 없이 시간당 60회)
    private static final String GHSA_ADVISORY_API_PREFIX = "https://api.github.com/advisories/";
    private static final Map<String, String> GITHUB_API_HEADERS = Map.of("Accept", "application/vnd.github+json");
    private static final String CVE_KEY_PREFIX = "cve:";
    private static final String URL_KEY_PREFIX = "url:";

    private final FeedHttpClient feedHttpClient;
    private final Clock clock;
    private final SourceTextRefetcher sourceTextRefetcher;
    private final Map<FeedFormat, ArticleCollector> collectorByFeedFormat;

    public SourceTextRecoverer(FeedHttpClient feedHttpClient, Clock clock, SourceTextRefetcher sourceTextRefetcher) {
        this.feedHttpClient = feedHttpClient;
        this.clock = clock;
        this.sourceTextRefetcher = sourceTextRefetcher;
        this.collectorByFeedFormat = Map.of(
                FeedFormat.RSS, new RssArticleCollector(feedHttpClient),
                FeedFormat.CISA_KEV_JSON, new KevArticleCollector(feedHttpClient),
                FeedFormat.NVD_API, new NvdArticleCollector(feedHttpClient, clock),
                FeedFormat.GHSA_API, new GhsaArticleCollector(feedHttpClient));
    }

    /**
     * 대상 id > 되찾기 결과 (대상 전체): RECOVERED(요약 입력), TOO_THIN(정리만 한 글), NOT_FOUND·NOT_SUPPORTED(사유)
     */
    public Map<Long, SourceTextRefetchResult> recover(List<BackfillTarget> targetsWithoutSourceText) {
        Map<String, List<BackfillTarget>> targetsBySourceCode = targetsWithoutSourceText.stream()
                .collect(Collectors.groupingBy(BackfillTarget::sourceCode, LinkedHashMap::new, Collectors.toList()));
        Map<Long, SourceTextRefetchResult> resultByTargetId = new HashMap<>();
        targetsBySourceCode.forEach((sourceCode, sourceTargets) -> {
            Map<Long, SourceTextRefetchResult> sourceResultByTargetId = recoverFromSource(sourceTargets);
            resultByTargetId.putAll(sourceResultByTargetId);
            log.info("[summary backfill] recovered : source={}, targets={}, {}", sourceCode, sourceTargets.size(),
                    toStatusCounts(sourceResultByTargetId.values()));
        });
        return resultByTargetId;
    }

    // 출처 1곳: 피드로 맞춤 (GitHub 권고는 1건 조회 포함) > 못 맞춘 건은 1건씩 다시 받기
    private Map<Long, SourceTextRefetchResult> recoverFromSource(List<BackfillTarget> sourceTargets) {
        Map<String, String> sourceTextByKey = readFeedSourceTexts(sourceTargets);
        Map<Long, SourceTextRefetchResult> resultByTargetId = new LinkedHashMap<>();
        for (BackfillTarget target : sourceTargets) {
            Optional<String> feedSourceText = findSourceText(target, sourceTextByKey);
            if (feedSourceText.isEmpty() && target.feedFormat() == FeedFormat.GHSA_API) {
                feedSourceText = fetchSingleGhsaAdvisory(target, target.toSource());
            }
            resultByTargetId.put(target.id(), feedSourceText.map(SourceTextRefetchResult::recovered)
                    .orElseGet(() -> sourceTextRefetcher.refetch(toRefetchTarget(target))));
        }
        return resultByTargetId;
    }

    // 지금 피드(또는 API 범위)의 항목 > CVE ID·원문 주소별 요약 입력. 피드 읽기 실패는 빈 값 (1건씩 받기로 넘어감)
    private Map<String, String> readFeedSourceTexts(List<BackfillTarget> sourceTargets) {
        Source source = sourceTargets.get(0).toSource();
        try {
            ArticleCollector collector = collectorByFeedFormat.get(source.getFeedFormat());
            return toSourceTextByKey(collector.collect(source, toCollectSince(source, sourceTargets)).articles(), source);
        } catch (RuntimeException exception) {
            log.warn("[summary backfill] feed read failed : source={}, cause={}", source.getCode(), exception.getMessage());
            return Map.of();
        }
    }

    private static SourceTextRefetchTarget toRefetchTarget(BackfillTarget target) {
        return new SourceTextRefetchTarget(target.sourceCode(), target.title(), target.originalUrl(), target.cveId());
    }

    // 로그용 건수: 상태별 + 받지 못한 사유별 (예: RECOVERED=40, NOT_FOUND=2 {status=403=2})
    private static String toStatusCounts(Collection<SourceTextRefetchResult> results) {
        Map<SourceTextRefetchResult.Status, Long> countByStatus = results.stream().collect(Collectors.groupingBy(
                SourceTextRefetchResult::status, () -> new EnumMap<>(SourceTextRefetchResult.Status.class),
                Collectors.counting()));
        Map<String, Long> countByFailureReason = results.stream().filter(result -> result.failureReason() != null)
                .collect(Collectors.groupingBy(SourceTextRefetchResult::failureReason, TreeMap::new, Collectors.counting()));
        return countByStatus + (countByFailureReason.isEmpty() ? "" : " " + countByFailureReason);
    }

    // NVD 만 게시일 범위가 필요함: 가장 오래된 대상 하루 전부터 (120일 한도 안), 그 밖의 출처는 주는 만큼 전부
    private Instant toCollectSince(Source source, List<BackfillTarget> sourceTargets) {
        if (source.getFeedFormat() != FeedFormat.NVD_API) {
            return null;
        }
        Instant earliestPublishedAt = sourceTargets.stream()
                .map(BackfillTarget::publishedAt).min(Comparator.naturalOrder()).orElseThrow();
        Instant rangeLimit = clock.instant().minus(NVD_MAXIMUM_RANGE);
        Instant rangeStart = earliestPublishedAt.minus(NVD_RANGE_MARGIN);
        return rangeStart.isBefore(rangeLimit) ? rangeLimit : rangeStart;
    }

    // 정리한 요약 입력을 CVE ID·원문 주소로 찾게 묶음 (입력이 없는 항목은 뺌)
    private Map<String, String> toSourceTextByKey(List<CollectedArticle> collectedArticles, Source source) {
        Map<String, String> sourceTextByKey = new HashMap<>();
        Instant collectedAt = clock.instant();
        for (CollectedArticle collectedArticle : collectedArticles) {
            Optional<ArticleDraft> draft = ArticleNormalizer.normalize(collectedArticle, source, collectedAt);
            if (draft.isEmpty() || draft.get().sourceText() == null) {
                continue;
            }
            if (draft.get().cveId() != null) {
                sourceTextByKey.putIfAbsent(CVE_KEY_PREFIX + draft.get().cveId(), draft.get().sourceText());
            }
            sourceTextByKey.putIfAbsent(URL_KEY_PREFIX + draft.get().originalUrl(), draft.get().sourceText());
        }
        return sourceTextByKey;
    }

    private static Optional<String> findSourceText(BackfillTarget target, Map<String, String> sourceTextByKey) {
        String sourceTextByUrl = sourceTextByKey.get(URL_KEY_PREFIX + target.originalUrl());
        if (sourceTextByUrl != null || target.cveId() == null) {
            return Optional.ofNullable(sourceTextByUrl);
        }
        return Optional.ofNullable(sourceTextByKey.get(CVE_KEY_PREFIX + target.cveId()));
    }

    // 권고 1건 응답(객체)을 배열로 감싸 목록 파서를 그대로 씀. 없어진 권고·한도 초과는 되찾지 못한 것으로 둠
    private Optional<String> fetchSingleGhsaAdvisory(BackfillTarget target, Source source) {
        String advisoryId = target.originalUrl().substring(target.originalUrl().lastIndexOf('/') + 1);
        try {
            byte[] advisoryBytes = feedHttpClient.fetch(GHSA_ADVISORY_API_PREFIX + advisoryId, GITHUB_API_HEADERS);
            ByteArrayOutputStream advisoryArray = new ByteArrayOutputStream();
            advisoryArray.write('[');
            advisoryArray.writeBytes(advisoryBytes);
            advisoryArray.write(']');
            List<CollectedArticle> advisories =
                    GhsaAdvisoryParser.parse(new ByteArrayInputStream(advisoryArray.toByteArray()), null);
            return findSourceText(target, toSourceTextByKey(advisories, source));
        } catch (RuntimeException exception) {
            log.warn("[summary backfill] ghsa advisory not recovered : articleId={}, cause={}", target.id(), exception.getMessage());
            return Optional.empty();
        }
    }
}
