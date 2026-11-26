package kr.suhsaechan.secbrief.tool.summary;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.ArrayList;
import java.util.List;
import kr.suhsaechan.secbrief.advisory.constant.FeedFormat;
import kr.suhsaechan.secbrief.advisory.entity.Source;
import kr.suhsaechan.secbrief.advisory.summary.ArticleBrief;
import kr.suhsaechan.secbrief.advisory.summary.SummaryWritingKind;
import org.junit.jupiter.api.Test;

/**
 * 도구 보조 값: 내보낸 행 읽기(출처 값·글 종류), 평가 표본 고르기(묶음별 고르게, 항상 같은 결과), 적재 보고서 건수
 */
class BackfillSupportTest {

    @Test
    void readsExportedRowIntoTargetSourceAndWritingKind() {
        BackfillTarget target = BackfillTarget.fromJson(BackfillTestData.row(5L, "CISA_KEV", "VULNERABILITY", "REMOTE_ACCESS",
                "CISA_KEV_JSON", "https://feed.example.test/kev.json", "https://catalog.example.test/?q=CVE-0000-00001",
                "CVE-0000-00001", null));

        assertThat(target.hasSourceText()).isFalse();
        assertThat(target.cveId()).isEqualTo("CVE-0000-00001");
        assertThat(target.writingKind()).isEqualTo(SummaryWritingKind.CVE_ENTRY);
        Source source = target.toSource();
        assertThat(source.getCode()).isEqualTo("CISA_KEV");
        assertThat(source.getFeedFormat()).isEqualTo(FeedFormat.CISA_KEV_JSON);
        assertThat(source.getFeedUrl()).isEqualTo("https://feed.example.test/kev.json");
        // 되찾은 입력을 넣은 행은 요약 요청으로 바뀜
        BackfillTarget recoveredTarget = BackfillTarget.fromJson(
                BackfillTarget.withSourceText(BackfillTestData.newsRow(1L, null), BackfillTestData.SOURCE_TEXT));
        assertThat(recoveredTarget.toSummaryRequest().sourceText()).isEqualTo(BackfillTestData.SOURCE_TEXT);
        assertThat(recoveredTarget.toSummaryRequest().writingKind()).isEqualTo(SummaryWritingKind.NEWS_ARTICLE);
    }

    @Test
    void samplesEvenlyPerSourceAndWritingKindAndSkipsThinInput() {
        List<JsonNode> inputRows = new ArrayList<>();
        for (long id = 1; id <= 10; id++) {
            inputRows.add(BackfillTestData.newsRow(id, BackfillTestData.SOURCE_TEXT));
        }
        inputRows.add(BackfillTestData.row(20L, "THE_HACKER_NEWS", "NEWS", "EVENT", "RSS", "https://feed.example.test/news",
                "https://news.example.test/articles/20", null, BackfillTestData.SOURCE_TEXT));
        inputRows.add(BackfillTestData.newsRow(30L, null));
        inputRows.add(BackfillTestData.newsRow(31L, "https://tracker.example.test/tracker/EXSA-0000-1"));

        List<JsonNode> sampleRows = EvaluationSampler.sample(inputRows, 3);

        // 행사 묶음 1건 + 기사 묶음 10건 중 고르게 3건 (입력 없는 행·주소뿐인 행 제외)
        assertThat(sampleRows).extracting(row -> row.path("id").asLong()).containsExactly(20L, 1L, 4L, 7L);
        assertThat(EvaluationSampler.sample(inputRows, 3)).isEqualTo(sampleRows);
    }

    @Test
    void reportCountsStatusesPerSourceWithTotals() {
        ArticleBrief brief = new ArticleBrief("한 줄이에요.", "무슨 일이에요.", null, List.of(), List.of());
        String report = BackfillReport.toMarkdown(List.of(
                new BackfillResult(1L, "NVD", BackfillResult.Status.ACCEPTED, brief, 1, 1, false, null),
                new BackfillResult(2L, "NVD", BackfillResult.Status.ACCEPTED, brief, 1, 2, true, null),
                new BackfillResult(3L, "NVD", BackfillResult.Status.REJECTED, null, 3, 6, false, "whatHappened copies source text"),
                BackfillResult.withoutBrief(BackfillTestData.newsTarget(4L, null), BackfillResult.Status.NOT_RECOVERABLE),
                BackfillResult.withoutBrief(BackfillTestData.newsTarget(5L, null), BackfillResult.Status.INPUT_TOO_THIN)));

        assertThat(report).contains("| NVD | 3 | 3 | 2 | 1 | 0 | 0 |");
        assertThat(report).contains("| THE_HACKER_NEWS | 2 | 1 | 0 | 0 | 1 | 1 |");
        assertThat(report).contains("| 합계 | 5 | 4 | 2 | 1 | 1 | 1 |");
        assertThat(report).contains("제공자 호출 9회, 고쳐 쓴 글로 통과 1건");
    }
}
