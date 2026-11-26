package kr.suhsaechan.secbrief.advisory.collector;

import static kr.suhsaechan.secbrief.common.json.JsonSupport.OBJECT_MAPPER;

import static java.nio.charset.StandardCharsets.UTF_8;

import com.fasterxml.jackson.databind.JsonNode;
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
import kr.suhsaechan.secbrief.advisory.fact.SourceFact;
import kr.suhsaechan.secbrief.advisory.fact.SourceFactLabel;
import kr.suhsaechan.secbrief.common.exception.CustomException;
import kr.suhsaechan.secbrief.common.exception.constant.ErrorCode;
import lombok.extern.slf4j.Slf4j;

/**
 * CISA KEV 카탈로그(JSON) 파싱: 등재 항목 > 수집 원본 (제목, 링크, CVE ID, 등재일, 요약 입력, 사실 값), since 이전 등재분은 버림
 * 요약 입력: shortDescription + requiredAction, 사실 값 5개: 제조사·제품, 조치 요구, 조치 기한, 랜섬웨어 악용, CWE (CVSS 점수는 KEV 가 안 줌 > null)
 */
@Slf4j
public final class KevCatalogParser {

    public static final String PUBLISHER_NAME = "CISA KEV";

    // 카탈로그 검색 화면에서 해당 CVE 만 보이는 링크
    private static final String ORIGINAL_URL_PREFIX = "https://www.cisa.gov/known-exploited-vulnerabilities-catalog?search_api_fulltext=";
    private static final String TITLE_SEPARATOR = ": ";
    // 요약 입력에서 조치 문구 앞에 붙이는 표시 (AI 가 대응 안내의 근거로 씀)
    private static final String REQUIRED_ACTION_PREFIX = " Required action: ";
    private static final String WEAKNESS_SEPARATOR = ", ";

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
        return Optional.of(CollectedArticle.ofVulnerability(
                cveId + TITLE_SEPARATOR + vulnerabilityName,
                ORIGINAL_URL_PREFIX + URLEncoder.encode(cveId, UTF_8),
                PUBLISHER_NAME,
                addedAt.get(),
                cveId,
                null,
                null,
                true,
                toSourceText(entry),
                toSourceFacts(entry)));
    }

    // 요약 입력: 설명 (+ 조치 문구), 설명이 없으면 null
    private static String toSourceText(JsonNode entry) {
        String shortDescription = toText(entry, "shortDescription");
        if (shortDescription.isEmpty()) {
            return null;
        }
        String requiredAction = toText(entry, "requiredAction");
        return requiredAction.isEmpty() ? shortDescription : shortDescription + REQUIRED_ACTION_PREFIX + requiredAction;
    }

    // 카탈로그 값 그대로 (없는 필드는 싣지 않음)
    private static List<SourceFact> toSourceFacts(JsonNode entry) {
        List<SourceFact> sourceFacts = new ArrayList<>();
        addFact(sourceFacts, SourceFactLabel.VENDOR_PRODUCT,
                (toText(entry, "vendorProject") + " " + toText(entry, "product")).strip());
        addFact(sourceFacts, SourceFactLabel.REQUIRED_ACTION, toText(entry, "requiredAction"));
        addFact(sourceFacts, SourceFactLabel.ACTION_DUE_DATE, toText(entry, "dueDate"));
        addFact(sourceFacts, SourceFactLabel.RANSOMWARE_USE,
                toRansomwareUseLabel(toText(entry, "knownRansomwareCampaignUse")));
        List<String> weaknesses = new ArrayList<>();
        entry.path("cwes").forEach(weakness -> weaknesses.add(weakness.asText("").strip()));
        weaknesses.removeIf(String::isEmpty);
        addFact(sourceFacts, SourceFactLabel.WEAKNESS, String.join(WEAKNESS_SEPARATOR, weaknesses));
        return sourceFacts;
    }

    // knownRansomwareCampaignUse: Known 확인됨, Unknown 확인 안 됨 (그 밖의 값은 원문 그대로)
    private static String toRansomwareUseLabel(String knownRansomwareCampaignUse) {
        return switch (knownRansomwareCampaignUse) {
            case "Known" -> "확인됨";
            case "Unknown" -> "확인 안 됨";
            default -> knownRansomwareCampaignUse;
        };
    }

    private static void addFact(List<SourceFact> sourceFacts, String label, String value) {
        if (!value.isEmpty()) {
            sourceFacts.add(new SourceFact(label, value));
        }
    }

    private static String toText(JsonNode entry, String fieldName) {
        return entry.path(fieldName).asText("").strip();
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
