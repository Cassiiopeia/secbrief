package kr.suhsaechan.secbrief.advisory.summary;

import static org.assertj.core.api.Assertions.assertThat;

import kr.suhsaechan.secbrief.advisory.constant.ArticleKind;
import kr.suhsaechan.secbrief.advisory.constant.ArticleTopic;
import kr.suhsaechan.secbrief.advisory.constant.SourceCode;
import org.junit.jupiter.api.Test;

/**
 * 글 종류 판정: 행사 주제 > KISA 출처 > GitHub 권고 출처 > 기사 종류, 종류마다 작성 안내 파일이 있음
 */
class SummaryWritingKindTest {

    @Test
    void resolvesWritingKindFromSourceCodeArticleKindAndTopic() {
        assertThat(SummaryWritingKind.of(SourceCode.NVD, ArticleKind.VULNERABILITY, ArticleTopic.VULNERABILITY))
                .isEqualTo(SummaryWritingKind.CVE_ENTRY);
        assertThat(SummaryWritingKind.of(SourceCode.CISA_KEV, ArticleKind.VULNERABILITY, ArticleTopic.REMOTE_ACCESS))
                .isEqualTo(SummaryWritingKind.CVE_ENTRY);
        assertThat(SummaryWritingKind.of(SourceCode.GHSA, ArticleKind.VULNERABILITY, ArticleTopic.SUPPLY_CHAIN))
                .isEqualTo(SummaryWritingKind.PACKAGE_ADVISORY);
        assertThat(SummaryWritingKind.of(SourceCode.GHSA_HIGH, ArticleKind.VULNERABILITY, ArticleTopic.VULNERABILITY))
                .isEqualTo(SummaryWritingKind.PACKAGE_ADVISORY);
        assertThat(SummaryWritingKind.of(SourceCode.REDHAT_RHSA, ArticleKind.ADVISORY, ArticleTopic.VULNERABILITY))
                .isEqualTo(SummaryWritingKind.VENDOR_ADVISORY);
        assertThat(SummaryWritingKind.of(SourceCode.UBUNTU_USN, ArticleKind.ADVISORY, ArticleTopic.GENERAL))
                .isEqualTo(SummaryWritingKind.VENDOR_ADVISORY);
        assertThat(SummaryWritingKind.of(SourceCode.KISA_ALERT_LEVEL, ArticleKind.ADVISORY, ArticleTopic.POLICY))
                .isEqualTo(SummaryWritingKind.KISA_NOTICE);
        assertThat(SummaryWritingKind.of(SourceCode.KISA_VULNERABILITY, ArticleKind.ADVISORY, ArticleTopic.VULNERABILITY))
                .isEqualTo(SummaryWritingKind.KISA_NOTICE);
        assertThat(SummaryWritingKind.of(SourceCode.DAILYSECU, ArticleKind.NEWS, ArticleTopic.RANSOMWARE))
                .isEqualTo(SummaryWritingKind.NEWS_ARTICLE);
        assertThat(SummaryWritingKind.of(SourceCode.THE_HACKER_NEWS, ArticleKind.NEWS, ArticleTopic.GENERAL))
                .isEqualTo(SummaryWritingKind.NEWS_ARTICLE);
    }

    @Test
    void eventTopicWinsOverSourceAndArticleKind() {
        assertThat(SummaryWritingKind.of(SourceCode.THE_HACKER_NEWS, ArticleKind.NEWS, ArticleTopic.EVENT))
                .isEqualTo(SummaryWritingKind.EVENT_NOTICE);
        assertThat(SummaryWritingKind.of(SourceCode.KISA_REPORT, ArticleKind.ADVISORY, ArticleTopic.EVENT))
                .isEqualTo(SummaryWritingKind.EVENT_NOTICE);
    }

    @Test
    void everyWritingKindHasItsOwnGuideFile() {
        for (SummaryWritingKind writingKind : SummaryWritingKind.values()) {
            assertThat(SummaryHarnessGuide.writingKindGuide(writingKind)).startsWith("# This item:");
        }
        assertThat(SummaryHarnessGuide.commonRules()).isNotBlank();
        assertThat(SummaryHarnessGuide.repairRules()).startsWith("# Rewrite request");
    }
}
