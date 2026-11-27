package kr.suhsaechan.secbrief.advisory.refetch;

import static kr.suhsaechan.secbrief.common.json.JsonSupport.OBJECT_MAPPER;

import com.fasterxml.jackson.databind.JsonNode;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import kr.suhsaechan.secbrief.advisory.collector.NvdResponseParser;
import kr.suhsaechan.secbrief.common.exception.CustomException;
import kr.suhsaechan.secbrief.common.exception.constant.ErrorCode;

/**
 * 공개 권고 JSON > 요약 입력 재료 글 (정리 전): NVD CVE 1건(영어 설명, 점수 무관), Ubuntu 권고(요약 + 설명), Red Hat CSAF(주제 + 상세)
 * 글 칸이 비었으면 빈 값 (호출부가 NOT_FOUND 로 셈)
 */
final class SourceJsonTextExtractor {

    // Ubuntu 권고 JSON 칸: 한 줄 요약, 설명 (피드 description 과 같은 글)
    private static final String UBUNTU_SUMMARY_FIELD = "summary";
    private static final String UBUNTU_DESCRIPTION_FIELD = "description";
    // Red Hat CSAF document.notes 종류: summary 주제(Topic, 피드 description 과 같은 글), general 상세(Details)
    private static final List<String> REDHAT_NOTE_CATEGORIES = List.of("summary", "general");
    private static final String PART_SEPARATOR = "\n\n";

    private SourceJsonTextExtractor() {
    }

    // 요청한 CVE ID 와 같은 항목의 설명
    static Optional<String> extractNvdDescription(byte[] cveJson, String cveId) {
        for (JsonNode vulnerability : readJson(cveJson).path("vulnerabilities")) {
            JsonNode cve = vulnerability.path("cve");
            if (cveId.equalsIgnoreCase(cve.path("id").asText())) {
                return Optional.of(NvdResponseParser.toEnglishDescription(cve.path("descriptions")))
                        .filter(description -> !description.isBlank());
            }
        }
        return Optional.empty();
    }

    static Optional<String> extractUbuntuNoticeText(byte[] noticeJson) {
        JsonNode notice = readJson(noticeJson);
        List<String> parts = new ArrayList<>();
        addIfPresent(parts, notice.path(UBUNTU_SUMMARY_FIELD));
        addIfPresent(parts, notice.path(UBUNTU_DESCRIPTION_FIELD));
        return toJoinedText(parts);
    }

    // 종류 순서대로 (주제 > 상세), 법적 고지 같은 다른 종류는 뺌
    static Optional<String> extractRedHatCsafText(byte[] csafJson) {
        JsonNode notes = readJson(csafJson).path("document").path("notes");
        List<String> parts = new ArrayList<>();
        for (String category : REDHAT_NOTE_CATEGORIES) {
            for (JsonNode note : notes) {
                if (category.equals(note.path("category").asText())) {
                    addIfPresent(parts, note.path("text"));
                }
            }
        }
        return toJoinedText(parts);
    }

    private static void addIfPresent(List<String> parts, JsonNode textNode) {
        if (textNode.isTextual() && !textNode.asText().isBlank()) {
            parts.add(textNode.asText().strip());
        }
    }

    private static Optional<String> toJoinedText(List<String> parts) {
        return parts.isEmpty() ? Optional.empty() : Optional.of(String.join(PART_SEPARATOR, parts));
    }

    // 형식 오류는 FEED_PARSE_ERROR
    private static JsonNode readJson(byte[] jsonBytes) {
        try {
            return OBJECT_MAPPER.readTree(jsonBytes);
        } catch (IOException exception) {
            throw new CustomException(ErrorCode.FEED_PARSE_ERROR, exception);
        }
    }
}
