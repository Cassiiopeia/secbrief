package kr.suhsaechan.secbrief.tool.summary;

import static kr.suhsaechan.secbrief.common.json.JsonSupport.OBJECT_MAPPER;

import com.fasterxml.jackson.databind.node.ObjectNode;

/**
 * 도구 테스트용 대상 행 (내보내기 SQL 의 열 이름 그대로): 지어낸 값만 씀 (실제 기사·운영 값 없음)
 */
final class BackfillTestData {

    static final String SOURCE_TEXT = "Attackers are exploiting a flaw in Example VPN 2.4.0 to steal session tokens. "
            + "Example Corp released version 2.4.1.";

    private BackfillTestData() {
    }

    /** 해외 기사 출처(RSS)의 대상 행 (sourceText 가 null 이면 입력 없는 행) */
    static ObjectNode newsRow(long id, String sourceText) {
        return row(id, "THE_HACKER_NEWS", "NEWS", "GENERAL", "RSS", "https://feed.example.test/news",
                "https://news.example.test/articles/" + id, null, sourceText);
    }

    static ObjectNode row(long id, String sourceCode, String kind, String topic, String feedFormat, String feedUrl,
                          String originalUrl, String cveId, String sourceText) {
        ObjectNode row = OBJECT_MAPPER.createObjectNode();
        row.put("id", id);
        row.put("title", "Example headline " + id);
        row.put("region", "OVERSEAS");
        row.put("kind", kind);
        row.put("topic", topic);
        row.put("source_code", sourceCode);
        row.put("source_display_name", "표시명 " + sourceCode);
        row.put("feed_format", feedFormat);
        row.put("feed_url", feedUrl);
        row.put("original_url", originalUrl);
        row.put("cve_id", cveId);
        row.put("published_at", "2026-11-20T03:00:00+00:00");
        row.put("summary_status", sourceText == null ? "NONE" : "PENDING");
        row.put("source_text", sourceText);
        return row;
    }

    static BackfillTarget newsTarget(long id, String sourceText) {
        return BackfillTarget.fromJson(newsRow(id, sourceText));
    }
}
