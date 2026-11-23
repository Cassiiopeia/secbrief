package kr.suhsaechan.secbrief.advisory.collector;

import static java.nio.charset.StandardCharsets.UTF_8;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.io.InputStream;
import java.net.URLEncoder;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import kr.suhsaechan.secbrief.common.exception.CustomException;
import kr.suhsaechan.secbrief.common.exception.constant.ErrorCode;
import lombok.extern.slf4j.Slf4j;

/**
 * CISA KEV 카탈로그(JSON) 파싱: 등재 항목 > 수집 원본 (제목, 링크, CVE ID, 등재일), since 이전 등재분은 버림
 * 설명·조치 문구는 읽지 않음 (CVSS 점수는 KEV 가 안 줌 > null)
 */
@Slf4j
public final class KevCatalogParser {

    public static final String PUBLISHER_NAME = "CISA KEV";

    // 카탈로그 검색 화면에서 해당 CVE 만 보이는 링크
    private static final String ORIGINAL_URL_PREFIX = "https://www.cisa.gov/known-exploited-vulnerabilities-catalog?search_api_fulltext=";
    private static final String TITLE_SEPARATOR = ": ";
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private KevCatalogParser() {
    }

    /**
     * since: 등재일(dateAdded)이 이보다 이전이면 버림 (null 이면 전부)
     * 항목 하나가 깨져도 나머지는 살리되, 항목이 있는데 전부 깨졌으면 형식이 바뀐 것으로 보고 실패
     */
    public static List<CollectedArticle> parse(InputStream catalogStream, Instant since) {
        JsonNode vulnerabilities = readVulnerabilities(catalogStream);

        List<CollectedArticle> articles = new ArrayList<>();
        int malformedEntryCount = 0;
        for (JsonNode entry : vulnerabilities) {
            Optional<CollectedArticle> article = toCollectedArticle(entry);
            if (article.isEmpty()) {
                malformedEntryCount++;
            } else if (isAddedSince(article.get(), since)) {
                articles.add(article.get());
            }
        }
        if (malformedEntryCount > 0 && malformedEntryCount == vulnerabilities.size()) {
            throw new CustomException(ErrorCode.FEED_PARSE_ERROR, "every catalog entry is malformed");
        }
        if (malformedEntryCount > 0) {
            log.warn("[article collect] kev skipped malformed entries : count={}", malformedEntryCount);
        }
        return articles;
    }

    // vulnerabilities 배열 읽기 (JSON 오류·배열 없음은 FEED_PARSE_ERROR)
    private static JsonNode readVulnerabilities(InputStream catalogStream) {
        JsonNode vulnerabilities;
        try {
            vulnerabilities = OBJECT_MAPPER.readTree(catalogStream).path("vulnerabilities");
        } catch (IOException exception) {
            throw new CustomException(ErrorCode.FEED_PARSE_ERROR, exception);
        }
        if (!vulnerabilities.isArray()) {
            throw new CustomException(ErrorCode.FEED_PARSE_ERROR, "vulnerabilities array is missing");
        }
        return vulnerabilities;
    }

    // cveID·vulnerabilityName·dateAdded 중 하나라도 없거나 날짜가 깨졌으면 비어 있음
    private static Optional<CollectedArticle> toCollectedArticle(JsonNode entry) {
        String cveId = entry.path("cveID").asText("").strip();
        String vulnerabilityName = entry.path("vulnerabilityName").asText("").strip();
        Optional<Instant> addedAt = toAddedAt(entry.path("dateAdded").asText(""));
        if (cveId.isEmpty() || vulnerabilityName.isEmpty() || addedAt.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(new CollectedArticle(
                cveId + TITLE_SEPARATOR + vulnerabilityName,
                ORIGINAL_URL_PREFIX + URLEncoder.encode(cveId, UTF_8),
                PUBLISHER_NAME,
                addedAt.get(),
                cveId,
                null,
                null,
                true));
    }

    // 등재일(날짜만) > UTC 00:00
    private static Optional<Instant> toAddedAt(String dateAdded) {
        try {
            return Optional.of(LocalDate.parse(dateAdded).atStartOfDay(ZoneOffset.UTC).toInstant());
        } catch (DateTimeParseException exception) {
            return Optional.empty();
        }
    }

    private static boolean isAddedSince(CollectedArticle article, Instant since) {
        return since == null || !article.publishedAt().isBefore(since);
    }
}
