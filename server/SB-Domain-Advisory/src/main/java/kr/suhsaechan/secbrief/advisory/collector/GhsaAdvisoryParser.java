package kr.suhsaechan.secbrief.advisory.collector;

import static kr.suhsaechan.secbrief.common.json.JsonSupport.OBJECT_MAPPER;

import com.fasterxml.jackson.databind.JsonNode;
import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import kr.suhsaechan.secbrief.advisory.fact.SourceFact;
import kr.suhsaechan.secbrief.advisory.fact.SourceFactLabel;
import kr.suhsaechan.secbrief.common.exception.CustomException;
import kr.suhsaechan.secbrief.common.exception.constant.ErrorCode;
import lombok.extern.slf4j.Slf4j;

/**
 * GitHub 보안 권고 목록(JSON 배열) 파싱: 권고 > 수집 원본 (요약 제목, 링크, CVE ID, 점수·버전, 게시일, 요약 입력, 사실 값)
 * 점수: cvss_v3 우선, 없거나 0 이면 cvss_v4, 둘 다 없으면 null. since 이전 게시분은 버림
 */
@Slf4j
public final class GhsaAdvisoryParser {

    public static final String PUBLISHER_NAME = "GitHub Advisory";

    // 벡터 접두사에서 버전 추출 (예: CVSS:3.1/... > 3.1)
    private static final Pattern CVSS_VECTOR_VERSION = Pattern.compile("^CVSS:(\\d+\\.\\d+)/");
    // 사실 값으로 싣는 패키지 수 상한 (권고 하나가 패키지 수십 개를 묶는 경우)
    private static final int MAXIMUM_FACT_PACKAGE_COUNT = 5;

    private GhsaAdvisoryParser() {
    }

    /**
     * since: 게시일이 이보다 이전이면 버림 (null 이면 전부). 배열이 아니면(한도 초과 응답 등) FEED_PARSE_ERROR
     */
    public static List<CollectedArticle> parse(InputStream responseStream, Instant since) {
        JsonNode advisories = readAdvisories(responseStream);

        List<CollectedArticle> articles = new ArrayList<>();
        int malformedEntryCount = 0;
        for (JsonNode advisory : advisories) {
            Optional<CollectedArticle> article = toCollectedArticle(advisory);
            if (article.isEmpty()) {
                malformedEntryCount++;
            } else if (since == null || !article.get().publishedAt().isBefore(since)) {
                articles.add(article.get());
            }
        }
        if (malformedEntryCount > 0) {
            log.warn("[article collect] ghsa skipped malformed entries : count={}", malformedEntryCount);
        }
        return articles;
    }

    private static JsonNode readAdvisories(InputStream responseStream) {
        JsonNode advisories;
        try {
            advisories = OBJECT_MAPPER.readTree(responseStream);
        } catch (IOException exception) {
            throw new CustomException(ErrorCode.FEED_PARSE_ERROR, exception);
        }
        if (advisories == null || !advisories.isArray()) {
            throw new CustomException(ErrorCode.FEED_PARSE_ERROR, "advisory array is missing");
        }
        return advisories;
    }

    // summary·html_url·published_at 중 하나라도 없거나 날짜가 깨졌으면 비어 있음
    private static Optional<CollectedArticle> toCollectedArticle(JsonNode advisory) {
        String summary = advisory.path("summary").asText("").strip();
        String htmlUrl = advisory.path("html_url").asText("").strip();
        Optional<Instant> publishedAt = toPublishedAt(advisory.path("published_at").asText(""));
        if (summary.isEmpty() || htmlUrl.isEmpty() || publishedAt.isEmpty()) {
            return Optional.empty();
        }
        String cveId = advisory.path("cve_id").isTextual() ? advisory.path("cve_id").asText().strip() : null;
        Optional<CvssScore> score = toPreferredScore(advisory.path("cvss_severities"));
        return Optional.of(CollectedArticle.ofVulnerability(
                summary,
                htmlUrl,
                PUBLISHER_NAME,
                publishedAt.get(),
                cveId == null || cveId.isEmpty() ? null : cveId,
                score.map(CvssScore::baseScore).orElse(null),
                score.map(CvssScore::version).orElse(null),
                false,
                toSourceText(summary, advisory.path("description")),
                toSourceFacts(advisory)));
    }

    // 요약 입력: 요약 + 설명 (설명이 없으면 제목과 같아 null)
    private static String toSourceText(String summary, JsonNode description) {
        String descriptionText = description.isTextual() ? description.asText().strip() : "";
        return descriptionText.isEmpty() ? null : summary + "\n" + descriptionText;
    }

    // 권고 값 그대로: 패키지마다 3줄 (조치 버전·영향 버전이 없으면 "원문에 없음"), 마지막에 심각도
    private static List<SourceFact> toSourceFacts(JsonNode advisory) {
        List<SourceFact> sourceFacts = new ArrayList<>();
        int packageCount = 0;
        for (JsonNode vulnerability : advisory.path("vulnerabilities")) {
            String packageName = toTextOrEmpty(vulnerability.path("package").path("name"));
            if (packageName.isEmpty() || packageCount >= MAXIMUM_FACT_PACKAGE_COUNT) {
                continue;
            }
            packageCount++;
            String ecosystem = toTextOrEmpty(vulnerability.path("package").path("ecosystem"));
            sourceFacts.add(new SourceFact(SourceFactLabel.AFFECTED_PACKAGE,
                    ecosystem.isEmpty() ? packageName : packageName + " (" + ecosystem + ")"));
            sourceFacts.add(new SourceFact(SourceFactLabel.AFFECTED_VERSION,
                    toTextOrAbsent(vulnerability.path("vulnerable_version_range"))));
            sourceFacts.add(new SourceFact(SourceFactLabel.PATCHED_VERSION,
                    toTextOrAbsent(vulnerability.path("first_patched_version"))));
        }
        String severity = toTextOrEmpty(advisory.path("severity"));
        if (!severity.isEmpty()) {
            // NVD 표기(CRITICAL, HIGH)와 맞춰 대문자로
            sourceFacts.add(new SourceFact(SourceFactLabel.SEVERITY, severity.toUpperCase(Locale.ROOT)));
        }
        return sourceFacts;
    }

    private static String toTextOrEmpty(JsonNode textNode) {
        return textNode.isTextual() ? textNode.asText().strip() : "";
    }

    // 값이 없거나 문자열이 아니면 "원문에 없음" (버전을 지어내지 않음)
    private static String toTextOrAbsent(JsonNode textNode) {
        String text = toTextOrEmpty(textNode);
        return text.isEmpty() ? SourceFactLabel.ABSENT_VALUE : text;
    }

    // cvss_v3 > cvss_v4 (점수 0 은 없음으로 봄)
    private static Optional<CvssScore> toPreferredScore(JsonNode cvssSeverities) {
        return toSeverityScore(cvssSeverities.path("cvss_v3"))
                .or(() -> toSeverityScore(cvssSeverities.path("cvss_v4")));
    }

    private static Optional<CvssScore> toSeverityScore(JsonNode severity) {
        JsonNode score = severity.path("score");
        if (!score.isNumber() || score.decimalValue().signum() <= 0) {
            return Optional.empty();
        }
        return Optional.of(new CvssScore(score.decimalValue(), toVersion(severity.path("vector_string").asText(""))));
    }

    // 벡터가 없으면 버전도 null (지어내지 않음)
    private static String toVersion(String vectorString) {
        Matcher matcher = CVSS_VECTOR_VERSION.matcher(vectorString);
        return matcher.find() ? matcher.group(1) : null;
    }

    private static Optional<Instant> toPublishedAt(String publishedAt) {
        try {
            return Optional.of(Instant.parse(publishedAt));
        } catch (DateTimeParseException exception) {
            return Optional.empty();
        }
    }

    private record CvssScore(BigDecimal baseScore, String version) {
    }
}
