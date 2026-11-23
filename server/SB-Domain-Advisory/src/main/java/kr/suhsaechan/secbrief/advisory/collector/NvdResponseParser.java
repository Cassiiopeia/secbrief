package kr.suhsaechan.secbrief.advisory.collector;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import kr.suhsaechan.secbrief.advisory.policy.ArticleUrgencyPolicy;
import kr.suhsaechan.secbrief.common.exception.CustomException;
import kr.suhsaechan.secbrief.common.exception.constant.ErrorCode;
import lombok.extern.slf4j.Slf4j;

/**
 * NVD CVE API 2.0 응답 파싱: CVE > 수집 원본 (제목 = ID + 영어 설명 앞 120자, 링크, 점수·버전, 게시일)
 * 점수 규칙: v3.1 (Primary 우선) > v3.1 이 없을 때만 v4.0, 9.0 미만·점수 없음은 버림
 */
@Slf4j
public final class NvdResponseParser {

    public static final String PUBLISHER_NAME = "NVD";
    // 설명은 제목 대용으로 앞부분만 (본문 옮겨 싣기 방지)
    public static final int MAXIMUM_DESCRIPTION_TITLE_LENGTH = 120;

    private static final String ORIGINAL_URL_PREFIX = "https://nvd.nist.gov/vuln/detail/";
    private static final String TITLE_SEPARATOR = ": ";
    private static final String TRUNCATION_MARK = "…";
    private static final String DESCRIPTION_LANGUAGE = "en";
    private static final String PRIMARY_METRIC_TYPE = "Primary";
    private static final String CVSS_V31_METRICS_FIELD = "cvssMetricV31";
    private static final String CVSS_V40_METRICS_FIELD = "cvssMetricV40";
    private static final String CVSS_V31_VERSION = "3.1";
    private static final String CVSS_V40_VERSION = "4.0";
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private NvdResponseParser() {
    }

    /**
     * 응답 1페이지 > CVSS 9.0 이상 항목 + 위치 값 (JSON 오류·vulnerabilities 없음은 FEED_PARSE_ERROR)
     */
    public static NvdPage parse(InputStream responseStream) {
        JsonNode root = readRoot(responseStream);
        JsonNode vulnerabilities = root.path("vulnerabilities");
        if (!vulnerabilities.isArray()) {
            throw new CustomException(ErrorCode.FEED_PARSE_ERROR, "vulnerabilities array is missing");
        }

        // 건별 변환 (깨진 항목은 건수만 기록)
        List<CollectedArticle> articles = new ArrayList<>();
        int malformedEntryCount = 0;
        for (JsonNode vulnerability : vulnerabilities) {
            JsonNode cve = vulnerability.path("cve");
            Optional<Instant> publishedAt = toPublishedAt(cve.path("published").asText(""));
            String cveId = cve.path("id").asText("").strip();
            if (cveId.isEmpty() || publishedAt.isEmpty()) {
                malformedEntryCount++;
                continue;
            }
            toCriticalScore(cve.path("metrics"))
                    .ifPresent(score -> articles.add(toCollectedArticle(cve, cveId, publishedAt.get(), score)));
        }
        if (malformedEntryCount > 0) {
            log.warn("[article collect] nvd skipped malformed entries : count={}", malformedEntryCount);
        }

        return new NvdPage(articles,
                root.path("startIndex").asInt(0),
                root.path("resultsPerPage").asInt(0),
                root.path("totalResults").asInt(0));
    }

    private static JsonNode readRoot(InputStream responseStream) {
        try {
            return OBJECT_MAPPER.readTree(responseStream);
        } catch (IOException exception) {
            throw new CustomException(ErrorCode.FEED_PARSE_ERROR, exception);
        }
    }

    private static CollectedArticle toCollectedArticle(JsonNode cve, String cveId, Instant publishedAt, CvssScore score) {
        return new CollectedArticle(
                cveId + TITLE_SEPARATOR + toDescriptionTitle(cve.path("descriptions")),
                ORIGINAL_URL_PREFIX + cveId,
                PUBLISHER_NAME,
                publishedAt,
                cveId,
                score.baseScore(),
                score.version(),
                false);
    }

    // 영어 설명 앞 120자 (넘으면 끝에 …)
    private static String toDescriptionTitle(JsonNode descriptions) {
        String description = "";
        for (JsonNode descriptionNode : descriptions) {
            if (DESCRIPTION_LANGUAGE.equals(descriptionNode.path("lang").asText())) {
                description = descriptionNode.path("value").asText("").strip();
                break;
            }
        }
        if (description.length() <= MAXIMUM_DESCRIPTION_TITLE_LENGTH) {
            return description;
        }
        return description.substring(0, MAXIMUM_DESCRIPTION_TITLE_LENGTH) + TRUNCATION_MARK;
    }

    // v3.1 이 있으면 v3.1 만 보고, 없을 때만 v4.0 > 9.0 이상만
    private static Optional<CvssScore> toCriticalScore(JsonNode metrics) {
        JsonNode v31Metrics = metrics.path(CVSS_V31_METRICS_FIELD);
        Optional<CvssScore> score = hasMetric(v31Metrics)
                ? toScore(v31Metrics, CVSS_V31_VERSION)
                : toScore(metrics.path(CVSS_V40_METRICS_FIELD), CVSS_V40_VERSION);
        return score.filter(NvdResponseParser::isCriticalScore);
    }

    private static boolean hasMetric(JsonNode metricList) {
        return metricList.isArray() && !metricList.isEmpty();
    }

    // Primary 항목 우선, 없으면 첫 항목의 cvssData.baseScore
    private static Optional<CvssScore> toScore(JsonNode metricList, String version) {
        if (!hasMetric(metricList)) {
            return Optional.empty();
        }
        JsonNode chosenMetric = metricList.get(0);
        for (JsonNode metric : metricList) {
            if (PRIMARY_METRIC_TYPE.equals(metric.path("type").asText())) {
                chosenMetric = metric;
                break;
            }
        }
        JsonNode baseScore = chosenMetric.path("cvssData").path("baseScore");
        if (!baseScore.isNumber()) {
            return Optional.empty();
        }
        return Optional.of(new CvssScore(baseScore.decimalValue(), version));
    }

    private static boolean isCriticalScore(CvssScore score) {
        return score.baseScore().compareTo(ArticleUrgencyPolicy.URGENT_CVSS_THRESHOLD) >= 0;
    }

    // 게시일: 오프셋 없으면 UTC (NVD 기본), 있으면 그대로
    private static Optional<Instant> toPublishedAt(String published) {
        if (published.isBlank()) {
            return Optional.empty();
        }
        try {
            return Optional.of(LocalDateTime.parse(published).toInstant(ZoneOffset.UTC));
        } catch (DateTimeParseException zonelessParseFailure) {
            try {
                return Optional.of(OffsetDateTime.parse(published).toInstant());
            } catch (DateTimeParseException exception) {
                return Optional.empty();
            }
        }
    }

    private record CvssScore(BigDecimal baseScore, String version) {
    }
}
