package kr.suhsaechan.secbrief.tool.summary;

import static kr.suhsaechan.secbrief.common.json.JsonSupport.OBJECT_MAPPER;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.ArrayList;
import java.util.List;
import kr.suhsaechan.secbrief.advisory.summary.ArticleSummary;

/**
 * 적재 결과 1건 (결과 파일의 한 줄): 기사 id, 출처 코드, 상태, 만든 요약(통과분만), 시도·호출 수, 실패 사유
 * 요약 입력(source_text)은 담지 않음 (출처 글을 남기지 않음: id + 만든 글만)
 */
public record BackfillResult(long id, String sourceCode, Status status, ArticleSummary acceptedSummary,
                             int attemptCount, int modelCallCount, boolean isRepaired, String failureReason) {

    public enum Status {
        // 검증을 통과한 요약 (적용 파일에 들어감)
        ACCEPTED,
        // 시도 3회 모두 검증 탈락
        REJECTED,
        // 요약할 내용이 없는 입력 (주소뿐, 너무 짧음)
        INPUT_TOO_THIN,
        // 공개 출처가 더는 주지 않아 입력을 되찾지 못함
        NOT_RECOVERABLE
    }

    public static BackfillResult withoutSummary(BackfillTarget target, Status status) {
        return new BackfillResult(target.id(), target.sourceCode(), status, null, 0, 0, false, null);
    }

    public ObjectNode toJson() {
        ObjectNode row = OBJECT_MAPPER.createObjectNode();
        row.put("id", id);
        row.put("sourceCode", sourceCode);
        row.put("status", status.name());
        if (acceptedSummary != null) {
            row.put("summary", acceptedSummary.summary());
            row.set("keyPoints", OBJECT_MAPPER.valueToTree(acceptedSummary.keyPoints()));
            row.put("actionGuide", acceptedSummary.actionGuide());
        }
        row.put("attemptCount", attemptCount);
        row.put("modelCallCount", modelCallCount);
        row.put("isRepaired", isRepaired);
        row.put("failureReason", failureReason);
        return row;
    }

    public static BackfillResult fromJson(JsonNode row) {
        ArticleSummary acceptedSummary = null;
        if (row.path("summary").isTextual()) {
            List<String> keyPoints = new ArrayList<>();
            row.path("keyPoints").forEach(keyPointNode -> keyPoints.add(keyPointNode.asText()));
            acceptedSummary = new ArticleSummary(row.path("summary").asText(), keyPoints,
                    row.path("actionGuide").isTextual() ? row.path("actionGuide").asText() : null);
        }
        return new BackfillResult(row.path("id").asLong(), row.path("sourceCode").asText(),
                Status.valueOf(row.path("status").asText()), acceptedSummary, row.path("attemptCount").asInt(),
                row.path("modelCallCount").asInt(), row.path("isRepaired").asBoolean(),
                row.path("failureReason").isTextual() ? row.path("failureReason").asText() : null);
    }
}
