package kr.suhsaechan.secbrief.tool.summary;

import static kr.suhsaechan.secbrief.common.json.JsonSupport.OBJECT_MAPPER;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import kr.suhsaechan.secbrief.advisory.summary.ArticleBrief;
import kr.suhsaechan.secbrief.advisory.summary.ArticleSummaryPrompt;

/**
 * 적재 결과 1건 (결과 파일의 한 줄): 기사 id, 출처 코드, 상태, 만든 브리핑(통과분만), 시도·호출 수, 실패 사유
 * 요약 입력(source_text)은 담지 않음 (출처 글을 남기지 않음: id + 만든 글만)
 */
public record BackfillResult(long id, String sourceCode, Status status, ArticleBrief acceptedBrief,
                             int attemptCount, int modelCallCount, boolean isRepaired, String failureReason) {

    public enum Status {
        // 검증을 통과한 브리핑 (적용 파일에 들어감)
        ACCEPTED,
        // 시도 3회 모두 검증 탈락
        REJECTED,
        // 요약할 내용이 없는 입력 (주소뿐, 너무 짧음)
        INPUT_TOO_THIN,
        // 공개 출처가 더는 주지 않아 입력을 되찾지 못함
        NOT_RECOVERABLE
    }

    // 결과 줄에서 브리핑을 담는 이름 (값의 모양은 출력 스키마와 같음: ArticleSummaryPrompt.toBriefFields)
    private static final String BRIEF_FIELD = "brief";

    /** 제공자를 부르지 않은 결과 (failureReason: 되찾지 못한 사유, 없으면 null) */
    public static BackfillResult withoutBrief(BackfillTarget target, Status status, String failureReason) {
        return new BackfillResult(target.id(), target.sourceCode(), status, null, 0, 0, false, failureReason);
    }

    public ObjectNode toJson() {
        ObjectNode row = OBJECT_MAPPER.createObjectNode();
        row.put("id", id);
        row.put("sourceCode", sourceCode);
        row.put("status", status.name());
        if (acceptedBrief != null) {
            row.set(BRIEF_FIELD, OBJECT_MAPPER.valueToTree(ArticleSummaryPrompt.toBriefFields(acceptedBrief)));
        }
        row.put("attemptCount", attemptCount);
        row.put("modelCallCount", modelCallCount);
        row.put("isRepaired", isRepaired);
        row.put("failureReason", failureReason);
        return row;
    }

    public static BackfillResult fromJson(JsonNode row) {
        ArticleBrief acceptedBrief = row.path(BRIEF_FIELD).isObject()
                ? ArticleSummaryPrompt.toOutcome(row.path(BRIEF_FIELD).toString()).brief()
                : null;
        return new BackfillResult(row.path("id").asLong(), row.path("sourceCode").asText(),
                Status.valueOf(row.path("status").asText()), acceptedBrief, row.path("attemptCount").asInt(),
                row.path("modelCallCount").asInt(), row.path("isRepaired").asBoolean(),
                row.path("failureReason").isTextual() ? row.path("failureReason").asText() : null);
    }
}
