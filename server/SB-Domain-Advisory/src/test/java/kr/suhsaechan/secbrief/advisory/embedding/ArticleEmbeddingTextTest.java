package kr.suhsaechan.secbrief.advisory.embedding;

import static kr.suhsaechan.secbrief.advisory.support.SourceFixtures.createSource;
import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.List;
import kr.suhsaechan.secbrief.advisory.constant.ArticleImportance;
import kr.suhsaechan.secbrief.advisory.constant.ArticleKind;
import kr.suhsaechan.secbrief.advisory.constant.ArticleRegion;
import kr.suhsaechan.secbrief.advisory.constant.ArticleTopic;
import kr.suhsaechan.secbrief.advisory.constant.FeedFormat;
import kr.suhsaechan.secbrief.advisory.constant.SourceCode;
import kr.suhsaechan.secbrief.advisory.entity.Article;
import kr.suhsaechan.secbrief.advisory.policy.ArticleDraft;
import org.junit.jupiter.api.Test;

/**
 * 임베딩 입력 문장: 번역 제목(있으면) + 원문 제목 + 키워드, AI 결과가 없으면 원문 제목만
 */
class ArticleEmbeddingTextTest {

    private static final Instant NOW = Instant.parse("2026-11-23T00:00:00Z");

    private final Article article = Article.create(
            createSource(SourceCode.THE_HACKER_NEWS, ArticleKind.NEWS, ArticleRegion.OVERSEAS, FeedFormat.RSS, null),
            new ArticleDraft(ArticleKind.NEWS, ArticleRegion.OVERSEAS, "Ransomware gang hits hospital",
                    "https://thehackernews.com/2026/11/example.html", "The Hacker News", NOW, false,
                    null, null, null, false, false),
            NOW);

    @Test
    void usesOnlyOriginalTitleBeforeAiAnalysis() {
        assertThat(ArticleEmbeddingText.from(article)).isEqualTo("Ransomware gang hits hospital");
    }

    @Test
    void joinsTranslatedTitleOriginalTitleAndKeywords() {
        article.applyAiAnalysis(ArticleTopic.RANSOMWARE, "랜섬웨어 조직, 병원 공격", ArticleImportance.NOTABLE, null);
        article.replaceKeywords(List.of("병원", "랜섬웨어"));

        assertThat(ArticleEmbeddingText.from(article))
                .isEqualTo("랜섬웨어 조직, 병원 공격\nRansomware gang hits hospital\n랜섬웨어, 병원");
    }
}
