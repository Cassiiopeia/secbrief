package kr.suhsaechan.secbrief.tool.summary;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.stream.Collectors;
import kr.suhsaechan.secbrief.advisory.policy.SourceTextPolicy;

/**
 * 평가 표본 고르기: 요약할 내용이 있는 대상 > (출처 코드 + 글 종류)로 묶음 > 묶음마다 id 순으로 고르게 perGroupCount 건
 * 같은 입력이면 항상 같은 표본 (무작위 없음: 기준선과 하네스를 같은 표본으로 비교)
 */
public final class EvaluationSampler {

    private EvaluationSampler() {
    }

    public static List<JsonNode> sample(List<JsonNode> inputRows, int perGroupCount) {
        Map<String, List<JsonNode>> rowsByGroup = inputRows.stream()
                .filter(EvaluationSampler::isWorthSummarizing)
                .sorted(Comparator.comparingLong(row -> row.path("id").asLong()))
                .collect(Collectors.groupingBy(EvaluationSampler::toGroupKey, TreeMap::new, Collectors.toList()));
        List<JsonNode> sampleRows = new ArrayList<>();
        rowsByGroup.values().forEach(groupRows -> sampleRows.addAll(pickEvenly(groupRows, perGroupCount)));
        return sampleRows;
    }

    private static boolean isWorthSummarizing(JsonNode inputRow) {
        BackfillTarget target = BackfillTarget.fromJson(inputRow);
        return SourceTextPolicy.isWorthSummarizing(target.sourceText(), target.title());
    }

    private static String toGroupKey(JsonNode inputRow) {
        BackfillTarget target = BackfillTarget.fromJson(inputRow);
        return target.sourceCode() + "/" + target.writingKind().name();
    }

    // 묶음 전체에서 같은 간격으로 고름 (앞쪽 id 에 몰리지 않게)
    private static List<JsonNode> pickEvenly(List<JsonNode> groupRows, int pickCount) {
        if (groupRows.size() <= pickCount) {
            return groupRows;
        }
        List<JsonNode> pickedRows = new ArrayList<>();
        for (int pickIndex = 0; pickIndex < pickCount; pickIndex++) {
            pickedRows.add(groupRows.get((int) ((long) pickIndex * groupRows.size() / pickCount)));
        }
        return pickedRows;
    }
}
