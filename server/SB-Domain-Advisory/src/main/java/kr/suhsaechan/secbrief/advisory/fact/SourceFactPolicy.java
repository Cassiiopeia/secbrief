package kr.suhsaechan.secbrief.advisory.fact;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * 사실 값 정리·병합 규칙 (값은 고치지 않고 다듬기만 함)
 * 병합 2가지: 기존 값 유지(새 라벨만 추가), NVD 링크 교체(NVD 가 준 라벨은 NVD 값으로)
 */
public final class SourceFactPolicy {

    // 값 1개 최대 길이 (KEV 조치 요구 문장이 가장 김, 넘으면 … 로 끝냄)
    public static final int MAXIMUM_VALUE_LENGTH = 500;
    // 기사 1건 최대 개수 (GHSA 패키지 5개 x 3줄 + 공통)
    public static final int MAXIMUM_FACT_COUNT = 24;
    private static final String TRUNCATION_MARK = "…";
    // 공백·줄바꿈·제어문자 묶음
    private static final Pattern WHITESPACE_OR_CONTROL_RUN = Pattern.compile("[\\s\\u00A0\\p{Cc}]+");

    private SourceFactPolicy() {
    }

    /**
     * 저장용 정리: 공백 정리 > 빈 라벨·값 버림 > 값 길이 제한 > 개수 제한
     */
    public static List<SourceFact> toStorableFacts(List<SourceFact> sourceFacts) {
        if (sourceFacts == null) {
            return List.of();
        }
        List<SourceFact> storableFacts = new ArrayList<>();
        for (SourceFact sourceFact : sourceFacts) {
            if (sourceFact == null || storableFacts.size() >= MAXIMUM_FACT_COUNT) {
                continue;
            }
            String singleLineLabel = toSingleLine(sourceFact.label());
            String singleLineValue = toSingleLine(sourceFact.value());
            if (!singleLineLabel.isEmpty() && !singleLineValue.isEmpty()) {
                storableFacts.add(new SourceFact(singleLineLabel, truncate(singleLineValue)));
            }
        }
        return storableFacts;
    }

    /**
     * 같은 CVE 병합 (KEV 등재 표시, GHSA·NVD 재수집): 기존 항목 그대로 + 기존에 없는 라벨의 새 항목만 뒤에 추가
     */
    public static List<SourceFact> mergeKeepingExisting(List<SourceFact> existingFacts, List<SourceFact> incomingFacts) {
        Set<String> existingLabels = toLabels(existingFacts);
        List<SourceFact> mergedFacts = new ArrayList<>(existingFacts);
        incomingFacts.stream()
                .filter(incomingFact -> !existingLabels.contains(incomingFact.label()))
                .forEach(mergedFacts::add);
        return toStorableFacts(mergedFacts);
    }

    /**
     * NVD 링크 교체: NVD 가 준 라벨의 기존 항목은 빼고 NVD 항목을 뒤에 추가 (점수·심각도는 NVD 값, KEV·GHSA 고유 값은 유지)
     */
    public static List<SourceFact> mergeReplacingLabels(List<SourceFact> existingFacts, List<SourceFact> replacingFacts) {
        Set<String> replacingLabels = toLabels(replacingFacts);
        List<SourceFact> mergedFacts = new ArrayList<>(existingFacts.stream()
                .filter(existingFact -> !replacingLabels.contains(existingFact.label()))
                .toList());
        mergedFacts.addAll(replacingFacts);
        return toStorableFacts(mergedFacts);
    }

    private static Set<String> toLabels(List<SourceFact> sourceFacts) {
        return sourceFacts.stream().map(SourceFact::label).collect(Collectors.toSet());
    }

    private static String toSingleLine(String text) {
        return text == null ? "" : WHITESPACE_OR_CONTROL_RUN.matcher(text).replaceAll(" ").strip();
    }

    // 길이 제한 (잘린 끝이 서로게이트 쌍 절반이면 그 글자는 뺌)
    private static String truncate(String value) {
        if (value.length() <= MAXIMUM_VALUE_LENGTH) {
            return value;
        }
        int endIndex = MAXIMUM_VALUE_LENGTH - TRUNCATION_MARK.length();
        if (Character.isHighSurrogate(value.charAt(endIndex - 1))) {
            endIndex--;
        }
        return value.substring(0, endIndex) + TRUNCATION_MARK;
    }
}
