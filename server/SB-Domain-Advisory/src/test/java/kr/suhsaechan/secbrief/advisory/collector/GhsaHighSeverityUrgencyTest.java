package kr.suhsaechan.secbrief.advisory.collector;

import static java.nio.charset.StandardCharsets.UTF_8;
import static kr.suhsaechan.secbrief.advisory.support.SourceFixtures.createSource;
import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayInputStream;
import java.time.Instant;
import java.util.List;
import kr.suhsaechan.secbrief.advisory.constant.ArticleImportance;
import kr.suhsaechan.secbrief.advisory.constant.ArticleKind;
import kr.suhsaechan.secbrief.advisory.constant.ArticleRegion;
import kr.suhsaechan.secbrief.advisory.constant.FeedFormat;
import kr.suhsaechan.secbrief.advisory.constant.SourceCode;
import kr.suhsaechan.secbrief.advisory.entity.Article;
import kr.suhsaechan.secbrief.advisory.entity.Source;
import kr.suhsaechan.secbrief.advisory.policy.ArticleDraft;
import kr.suhsaechan.secbrief.advisory.policy.ArticleNormalizer;
import org.junit.jupiter.api.Test;

/**
 * GitHub 보안 권고 high 등급 (GHSA_HIGH 출처): 파싱 > 정규화까지 긴급으로 표시하지 않음 (긴급은 등급이 아니라 CVSS 9.0 이상·KEV 규칙)
 * 같은 응답 모양의 critical 권고는 점수로 긴급 (직접 작성한 최소 예시)
 */
class GhsaHighSeverityUrgencyTest {

    private static final Instant NOW = Instant.parse("2026-11-23T00:00:00Z");
    private static final String ADVISORIES_JSON = """
            [
              {
                "ghsa_id": "GHSA-hhhh-iiii-jjjj",
                "cve_id": "CVE-2026-8801",
                "html_url": "https://github.com/advisories/GHSA-hhhh-iiii-jjjj",
                "summary": "Example Parser: denial of service in nested input",
                "severity": "high",
                "published_at": "2026-11-22T10:00:00Z",
                "cvss_severities": {
                  "cvss_v3": {"vector_string": "CVSS:3.1/AV:N/AC:L/PR:N/UI:N/S:U/C:N/I:N/A:H", "score": 7.5},
                  "cvss_v4": {"vector_string": null, "score": 0.0}
                }
              },
              {
                "ghsa_id": "GHSA-kkkk-llll-mmmm",
                "cve_id": null,
                "html_url": "https://github.com/advisories/GHSA-kkkk-llll-mmmm",
                "summary": "Example Uploader: path traversal in archive extraction",
                "severity": "high",
                "published_at": "2026-11-22T09:00:00Z",
                "cvss_severities": {
                  "cvss_v3": {"vector_string": null, "score": 0.0},
                  "cvss_v4": {"vector_string": "CVSS:4.0/AV:N/AC:L/AT:N/PR:L/UI:N/VC:H/VI:H/VA:N/SC:N/SI:N/SA:N", "score": 8.7}
                }
              },
              {
                "ghsa_id": "GHSA-nnnn-oooo-pppp",
                "cve_id": "CVE-2026-8803",
                "html_url": "https://github.com/advisories/GHSA-nnnn-oooo-pppp",
                "summary": "Example Gateway: remote code execution in admin API",
                "severity": "critical",
                "published_at": "2026-11-22T08:00:00Z",
                "cvss_severities": {
                  "cvss_v3": {"vector_string": "CVSS:3.1/AV:N/AC:L/PR:N/UI:N/S:U/C:H/I:H/A:H", "score": 9.8},
                  "cvss_v4": {"vector_string": null, "score": 0.0}
                }
              }
            ]
            """;

    private final Source ghsaHighSource =
            createSource(SourceCode.GHSA_HIGH, ArticleKind.VULNERABILITY, ArticleRegion.OVERSEAS, FeedFormat.GHSA_API, null);

    @Test
    void highSeverityAdvisoriesAreNotUrgent() {
        List<ArticleDraft> drafts = toDrafts();

        assertThat(drafts.get(0).cvssScore()).isEqualByComparingTo("7.5");
        assertThat(drafts.get(0).isUrgent()).isFalse();
        assertThat(drafts.get(1).cvssScore()).isEqualByComparingTo("8.7");
        assertThat(drafts.get(1).isUrgent()).isFalse();
    }

    @Test
    void scoreNineOrMoreIsUrgentRegardlessOfSourceCode() {
        assertThat(toDrafts().get(2).isUrgent()).isTrue();
    }

    @Test
    void highSeverityArticleStartsRoutineWithoutAlertWaiting() {
        Article article = Article.create(ghsaHighSource, toDrafts().get(0), NOW);

        // 긴급이 아니면 알림 대기열(긴급 + 미발송)에 들어가지 않음
        assertThat(article.isUrgent()).isFalse();
        assertThat(article.getImportance()).isEqualTo(ArticleImportance.ROUTINE);
        assertThat(article.getKind()).isEqualTo(ArticleKind.VULNERABILITY);
    }

    private List<ArticleDraft> toDrafts() {
        return GhsaAdvisoryParser.parse(new ByteArrayInputStream(ADVISORIES_JSON.getBytes(UTF_8)), null).stream()
                .map(collected -> ArticleNormalizer.normalize(collected, ghsaHighSource, NOW).orElseThrow())
                .toList();
    }
}
