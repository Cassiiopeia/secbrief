package kr.suhsaechan.secbrief.advisory.collector;

import static kr.suhsaechan.secbrief.common.json.JsonSupport.OBJECT_MAPPER;

import com.fasterxml.jackson.databind.JsonNode;
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
import kr.suhsaechan.secbrief.advisory.fact.SourceFact;
import kr.suhsaechan.secbrief.advisory.fact.SourceFactLabel;
import kr.suhsaechan.secbrief.advisory.policy.ArticleUrgencyPolicy;
import kr.suhsaechan.secbrief.common.exception.CustomException;
import kr.suhsaechan.secbrief.common.exception.constant.ErrorCode;
import lombok.extern.slf4j.Slf4j;

/**
 * NVD CVE API 2.0 응답 파싱: CVE > 수집 원본 (제목 = ID + 영어 설명 앞 120자, 링크, 점수·버전, 게시일, 요약 입력 = 영어 설명 전체, 사실 값)
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
    // weaknesses 값 중 실제 CWE 번호만 (NVD-CWE-noinfo, NVD-CWE-Other 는 정보 없음 표시라 제외)
    private static final String WEAKNESS_ID_PREFIX = "CWE-";
    private static final String WEAKNESS_SEPARATOR = ", ";
    // 영향 제품(CPE) 사실 값: 쉼표 구분, 최대 5쌍 (ArticleClassificationInputs 가 같은 구분자로 나눔)
    public static final String AFFECTED_PRODUCT_SEPARATOR = ", ";
    private static final int MAXIMUM_CPE_PRODUCT_COUNT = 5;

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
        String description = toEnglishDescription(cve.path("descriptions"));
        return CollectedArticle.ofVulnerability(
                cveId + TITLE_SEPARATOR + toDescriptionTitle(description),
                ORIGINAL_URL_PREFIX + cveId,
                PUBLISHER_NAME,
                publishedAt,
                cveId,
                score.baseScore(),
                score.version(),
                false,
                description.isEmpty() ? null : description,
                toSourceFacts(score, cve.path("weaknesses"), cve.path("configurations")));
    }

    /** cve.descriptions 의 첫 영어 설명 (없으면 빈 문자열): 수집과 요약 입력 다시 받기가 함께 씀 */
    public static String toEnglishDescription(JsonNode descriptions) {
        for (JsonNode descriptionNode : descriptions) {
            if (DESCRIPTION_LANGUAGE.equals(descriptionNode.path("lang").asText())) {
                return descriptionNode.path("value").asText("").strip();
            }
        }
        return "";
    }

    // 영어 설명 앞 120자 (넘으면 끝에 …)
    private static String toDescriptionTitle(String description) {
        if (description.length() <= MAXIMUM_DESCRIPTION_TITLE_LENGTH) {
            return description;
        }
        return description.substring(0, MAXIMUM_DESCRIPTION_TITLE_LENGTH) + TRUNCATION_MARK;
    }

    // 응답 값 그대로 5개: 고른 점수 항목의 점수·버전·벡터·심각도 + CWE (없는 필드는 싣지 않음)
    private static List<SourceFact> toSourceFacts(CvssScore score, JsonNode weaknesses, JsonNode configurations) {
        List<SourceFact> sourceFacts = new ArrayList<>();
        sourceFacts.add(new SourceFact(SourceFactLabel.CVSS_SCORE, score.baseScoreText()));
        sourceFacts.add(new SourceFact(SourceFactLabel.CVSS_VERSION, score.version()));
        addFact(sourceFacts, SourceFactLabel.CVSS_VECTOR, score.vectorString());
        addFact(sourceFacts, SourceFactLabel.SEVERITY, score.baseSeverity());
        addFact(sourceFacts, SourceFactLabel.WEAKNESS, String.join(WEAKNESS_SEPARATOR, toWeaknessIds(weaknesses)));
        addFact(sourceFacts, SourceFactLabel.AFFECTED_PRODUCT,
                String.join(AFFECTED_PRODUCT_SEPARATOR, toCpeVendorProducts(configurations)));
        return sourceFacts;
    }

    /**
     * configurations[].nodes[].cpeMatch[] 중 vulnerable 항목의 CPE 2.3 제조사·제품 (예: cpe:2.3:o:fortinet:fortios:... > fortinet fortios)
     * 중복 제거, 나온 순서, 최대 5쌍 (분석 전인 새 CVE 는 configurations 가 없어 빈 목록)
     */
    static List<String> toCpeVendorProducts(JsonNode configurations) {
        List<String> vendorProducts = new ArrayList<>();
        for (JsonNode configuration : configurations) {
            for (JsonNode node : configuration.path("nodes")) {
                for (JsonNode cpeMatch : node.path("cpeMatch")) {
                    String[] cpeParts = cpeMatch.path("criteria").asText("").split(":");
                    // cpe:2.3:{part}:{vendor}:{product}:... (제조사·제품은 4·5번째)
                    if (!cpeMatch.path("vulnerable").asBoolean(false) || cpeParts.length < 5
                            || vendorProducts.size() >= MAXIMUM_CPE_PRODUCT_COUNT) {
                        continue;
                    }
                    String vendorProduct = (cpeParts[3] + " " + cpeParts[4]).replace('_', ' ').strip();
                    if (!vendorProducts.contains(vendorProduct)) {
                        vendorProducts.add(vendorProduct);
                    }
                }
            }
        }
        return vendorProducts;
    }

    // weaknesses[].description[].value 중 CWE-번호 (중복 제거, 나온 순서)
    private static List<String> toWeaknessIds(JsonNode weaknesses) {
        List<String> weaknessIds = new ArrayList<>();
        for (JsonNode weakness : weaknesses) {
            for (JsonNode weaknessDescription : weakness.path("description")) {
                String weaknessId = weaknessDescription.path("value").asText("").strip();
                if (weaknessId.startsWith(WEAKNESS_ID_PREFIX) && !weaknessIds.contains(weaknessId)) {
                    weaknessIds.add(weaknessId);
                }
            }
        }
        return weaknessIds;
    }

    private static void addFact(List<SourceFact> sourceFacts, String label, String value) {
        if (!value.isEmpty()) {
            sourceFacts.add(new SourceFact(label, value));
        }
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

    // Primary 항목 우선, 없으면 첫 항목의 cvssData (baseScore·vectorString·baseSeverity, baseScore 가 숫자가 아니면 비어 있음)
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
        JsonNode cvssData = chosenMetric.path("cvssData");
        JsonNode baseScore = cvssData.path("baseScore");
        if (!baseScore.isNumber()) {
            return Optional.empty();
        }
        return Optional.of(new CvssScore(baseScore.decimalValue(), version, baseScore.asText(),
                cvssData.path("vectorString").asText("").strip(), cvssData.path("baseSeverity").asText("").strip()));
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

    // baseScoreText·vectorString·baseSeverity: 사실 값용 원문 문자열 (없으면 빈 문자열)
    private record CvssScore(BigDecimal baseScore, String version, String baseScoreText, String vectorString,
                             String baseSeverity) {
    }
}
