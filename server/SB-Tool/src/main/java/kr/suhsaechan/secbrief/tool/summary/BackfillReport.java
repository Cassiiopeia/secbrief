package kr.suhsaechan.secbrief.tool.summary;

import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * 적재 결과 보고서 (마크다운 표): 출처별 대상, 입력 있음, 통과, 탈락, 내용 없는 입력, 되찾지 못함 + 합계·제공자 호출 수
 * 숫자만 담음 (제목·요약 입력은 넣지 않음)
 */
public final class BackfillReport {

    private static final String TABLE_HEADER = """
            | 출처 | 대상 | 입력 있음 | 통과 | 탈락 | 내용 없는 입력 | 되찾지 못함 |
            |---|---:|---:|---:|---:|---:|---:|
            """;
    private static final String TOTAL_LABEL = "합계";

    private BackfillReport() {
    }

    public static String toMarkdown(List<BackfillResult> results) {
        Map<String, SourceCount> countBySourceCode = new TreeMap<>();
        SourceCount totalCount = new SourceCount();
        int modelCallCount = 0;
        int repairedCount = 0;
        for (BackfillResult result : results) {
            countBySourceCode.computeIfAbsent(result.sourceCode(), sourceCode -> new SourceCount()).add(result.status());
            totalCount.add(result.status());
            modelCallCount += result.modelCallCount();
            repairedCount += result.isRepaired() ? 1 : 0;
        }
        StringBuilder markdown = new StringBuilder(TABLE_HEADER);
        countBySourceCode.forEach((sourceCode, count) -> markdown.append(count.toTableRow(sourceCode)));
        markdown.append(totalCount.toTableRow(TOTAL_LABEL));
        markdown.append("\n제공자 호출 ").append(modelCallCount).append("회, 고쳐 쓴 글로 통과 ").append(repairedCount).append("건\n");
        return markdown.toString();
    }

    /** 출처 1곳의 상태별 건수 */
    private static final class SourceCount {

        private int targetCount;
        private int acceptedCount;
        private int rejectedCount;
        private int inputTooThinCount;
        private int notRecoverableCount;

        private void add(BackfillResult.Status status) {
            targetCount++;
            switch (status) {
                case ACCEPTED -> acceptedCount++;
                case REJECTED -> rejectedCount++;
                case INPUT_TOO_THIN -> inputTooThinCount++;
                case NOT_RECOVERABLE -> notRecoverableCount++;
            }
        }

        private String toTableRow(String label) {
            return String.format("| %s | %d | %d | %d | %d | %d | %d |%n", label, targetCount,
                    targetCount - notRecoverableCount, acceptedCount, rejectedCount, inputTooThinCount, notRecoverableCount);
        }
    }
}
