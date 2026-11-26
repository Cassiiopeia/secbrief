package kr.suhsaechan.secbrief.tool.summary;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.time.Instant;
import java.time.OffsetDateTime;
import kr.suhsaechan.secbrief.advisory.constant.ArticleKind;
import kr.suhsaechan.secbrief.advisory.constant.ArticleRegion;
import kr.suhsaechan.secbrief.advisory.constant.ArticleTopic;
import kr.suhsaechan.secbrief.advisory.constant.FeedFormat;
import kr.suhsaechan.secbrief.advisory.entity.Source;
import kr.suhsaechan.secbrief.advisory.summary.ArticleSummaryRequest;
import kr.suhsaechan.secbrief.advisory.summary.SummaryWritingKind;

/**
 * 적재 대상 기사 1건 (운영 DB 에서 내보낸 행): 기사 값 + 출처 값 + 요약 입력(없으면 null)
 * 내보내기 열 이름(snake_case)은 SB-Tool/run-summary-backfill.sh 의 내보내기 SQL 과 같아야 함
 */
public record BackfillTarget(long id, String title, ArticleRegion region, ArticleKind kind, ArticleTopic topic,
                             String sourceCode, String sourceDisplayName, FeedFormat feedFormat, String feedUrl,
                             String originalUrl, String cveId, Instant publishedAt, String summaryStatus,
                             String sourceText) {

    private static final String SOURCE_TEXT_COLUMN = "source_text";

    public static BackfillTarget fromJson(JsonNode row) {
        return new BackfillTarget(
                row.path("id").asLong(),
                row.path("title").asText(),
                ArticleRegion.valueOf(row.path("region").asText()),
                ArticleKind.valueOf(row.path("kind").asText()),
                ArticleTopic.valueOf(row.path("topic").asText()),
                row.path("source_code").asText(),
                row.path("source_display_name").asText(),
                FeedFormat.valueOf(row.path("feed_format").asText()),
                row.path("feed_url").asText(),
                row.path("original_url").asText(),
                toTextOrNull(row.path("cve_id")),
                OffsetDateTime.parse(row.path("published_at").asText()).toInstant(),
                row.path("summary_status").asText(),
                toTextOrNull(row.path(SOURCE_TEXT_COLUMN)));
    }

    /** 같은 행에 요약 입력만 바꾼 JSON (되찾은 입력을 임시 파일에 적을 때) */
    public static ObjectNode withSourceText(JsonNode row, String sourceText) {
        ObjectNode copiedRow = row.deepCopy();
        copiedRow.put(SOURCE_TEXT_COLUMN, sourceText);
        return copiedRow;
    }

    public boolean hasSourceText() {
        return sourceText != null && !sourceText.isBlank();
    }

    /** DB 에 저장하지 않는 출처 값 (수집기·정규화에 넘김) */
    public Source toSource() {
        return Source.ofFeed(sourceCode, sourceDisplayName, kind, region, feedFormat, feedUrl);
    }

    public SummaryWritingKind writingKind() {
        return SummaryWritingKind.of(sourceCode, kind, topic);
    }

    public ArticleSummaryRequest toSummaryRequest() {
        return new ArticleSummaryRequest(title, region, kind, writingKind(), sourceText);
    }

    private static String toTextOrNull(JsonNode textNode) {
        return textNode.isTextual() ? textNode.asText() : null;
    }
}
