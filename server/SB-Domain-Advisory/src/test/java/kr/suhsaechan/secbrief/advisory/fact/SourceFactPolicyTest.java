package kr.suhsaechan.secbrief.advisory.fact;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * 사실 값 정리·병합: 빈 값 버림·길이·개수 제한, 기존 값 유지 병합(새 라벨만), NVD 교체 병합(NVD 라벨만 교체), JSON 왕복
 */
class SourceFactPolicyTest {

    private static final List<SourceFact> KEV_FACTS = List.of(
            new SourceFact(SourceFactLabel.VENDOR_PRODUCT, "ExampleVendor ExampleServer"),
            new SourceFact(SourceFactLabel.ACTION_DUE_DATE, "2026-11-23"),
            new SourceFact(SourceFactLabel.WEAKNESS, "CWE-502"));
    private static final List<SourceFact> NVD_FACTS = List.of(
            new SourceFact(SourceFactLabel.CVSS_SCORE, "9.8"),
            new SourceFact(SourceFactLabel.WEAKNESS, "CWE-94"));

    @Test
    void dropsBlankFactsAndCollapsesWhitespace() {
        List<SourceFact> sourceFacts = new ArrayList<>();
        sourceFacts.add(new SourceFact(" 조치 요구 ", "Apply\n  mitigations\tnow"));
        sourceFacts.add(new SourceFact("조치 기한", " "));
        sourceFacts.add(new SourceFact("", "값"));
        sourceFacts.add(null);

        assertThat(SourceFactPolicy.toStorableFacts(sourceFacts))
                .containsExactly(new SourceFact("조치 요구", "Apply mitigations now"));
        assertThat(SourceFactPolicy.toStorableFacts(null)).isEmpty();
    }

    @Test
    void truncatesLongValueAndLimitsFactCount() {
        List<SourceFact> manyFacts = new ArrayList<>();
        for (int factIndex = 0; factIndex < SourceFactPolicy.MAXIMUM_FACT_COUNT + 5; factIndex++) {
            manyFacts.add(new SourceFact("라벨" + factIndex, "가".repeat(SourceFactPolicy.MAXIMUM_VALUE_LENGTH + 50)));
        }

        List<SourceFact> storableFacts = SourceFactPolicy.toStorableFacts(manyFacts);

        assertThat(storableFacts).hasSize(SourceFactPolicy.MAXIMUM_FACT_COUNT);
        assertThat(storableFacts.get(0).value()).hasSize(SourceFactPolicy.MAXIMUM_VALUE_LENGTH).endsWith("…");
    }

    @Test
    void keepsExistingValuesAndAddsOnlyNewLabels() {
        // NVD 행에 KEV 등재분이 합쳐짐: 같은 라벨(CWE)은 기존 값, KEV 고유 라벨은 추가
        assertThat(SourceFactPolicy.mergeKeepingExisting(NVD_FACTS, KEV_FACTS)).containsExactly(
                new SourceFact(SourceFactLabel.CVSS_SCORE, "9.8"),
                new SourceFact(SourceFactLabel.WEAKNESS, "CWE-94"),
                new SourceFact(SourceFactLabel.VENDOR_PRODUCT, "ExampleVendor ExampleServer"),
                new SourceFact(SourceFactLabel.ACTION_DUE_DATE, "2026-11-23"));
        // 같은 값을 다시 합쳐도 그대로 (재수집)
        assertThat(SourceFactPolicy.mergeKeepingExisting(KEV_FACTS, KEV_FACTS)).isEqualTo(KEV_FACTS);
    }

    @Test
    void keepsRepeatedLabelsOfNewSourceTogether() {
        List<SourceFact> ghsaFacts = List.of(
                new SourceFact(SourceFactLabel.AFFECTED_PACKAGE, "a (npm)"), new SourceFact(SourceFactLabel.PATCHED_VERSION, "1.2.0"),
                new SourceFact(SourceFactLabel.AFFECTED_PACKAGE, "b (npm)"), new SourceFact(SourceFactLabel.PATCHED_VERSION, "3.0.1"));

        assertThat(SourceFactPolicy.mergeKeepingExisting(NVD_FACTS, ghsaFacts))
                .startsWith(NVD_FACTS.toArray(SourceFact[]::new))
                .endsWith(ghsaFacts.toArray(SourceFact[]::new));
    }

    @Test
    void replacesOnlyLabelsGivenByNvd() {
        // KEV 행을 NVD 가 교체: CWE 는 NVD 값으로, KEV 고유 값(제조사·제품, 조치 기한)은 유지
        assertThat(SourceFactPolicy.mergeReplacingLabels(KEV_FACTS, NVD_FACTS)).containsExactly(
                new SourceFact(SourceFactLabel.VENDOR_PRODUCT, "ExampleVendor ExampleServer"),
                new SourceFact(SourceFactLabel.ACTION_DUE_DATE, "2026-11-23"),
                new SourceFact(SourceFactLabel.CVSS_SCORE, "9.8"),
                new SourceFact(SourceFactLabel.WEAKNESS, "CWE-94"));
    }

    @Test
    void roundTripsThroughJsonAndTreatsBrokenJsonAsEmpty() {
        String sourceFactsJson = SourceFactJson.toJson(KEV_FACTS);

        assertThat(sourceFactsJson).startsWith("[{\"label\":\"제조사·제품\",\"value\":\"ExampleVendor ExampleServer\"}");
        assertThat(SourceFactJson.toSourceFacts(sourceFactsJson)).isEqualTo(KEV_FACTS);
        assertThat(SourceFactJson.toJson(List.of())).isNull();
        assertThat(SourceFactJson.toSourceFacts(null)).isEmpty();
        assertThat(SourceFactJson.toSourceFacts("{not json")).isEmpty();
    }
}
