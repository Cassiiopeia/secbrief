package kr.suhsaechan.secbrief.web.support;

import java.math.BigDecimal;
import java.time.Instant;
import kr.suhsaechan.secbrief.advisory.constant.ArticleKind;
import kr.suhsaechan.secbrief.advisory.constant.ArticleRegion;
import kr.suhsaechan.secbrief.advisory.constant.ArticleTopic;
import kr.suhsaechan.secbrief.advisory.constant.SourceCode;
import kr.suhsaechan.secbrief.advisory.entity.Article;
import kr.suhsaechan.secbrief.advisory.entity.Source;
import kr.suhsaechan.secbrief.advisory.policy.ArticleDraft;
import kr.suhsaechan.secbrief.advisory.repository.ArticleRepository;
import kr.suhsaechan.secbrief.advisory.repository.SourceRepository;
import org.springframework.boot.test.context.TestComponent;

/**
 * 조회 테스트용 기사 저장: 게시일·종류·지역·주제·긴급을 정해 1건 저장 (링크는 매번 다름)
 */
@TestComponent
public class ArticleTestWriter {

    private final ArticleRepository articleRepository;
    private final SourceRepository sourceRepository;
    private int savedCount;

    public ArticleTestWriter(ArticleRepository articleRepository, SourceRepository sourceRepository) {
        this.articleRepository = articleRepository;
        this.sourceRepository = sourceRepository;
    }

    public Article saveArticle(Instant publishedAt, ArticleKind kind, ArticleRegion region, ArticleTopic topic, boolean isUrgent) {
        savedCount++;
        Source source = sourceRepository.findByCode(toSourceCode(kind, region)).orElseThrow();
        Article article = Article.create(source, new ArticleDraft(kind, region, "테스트 기사 " + savedCount,
                "https://example.test/articles/" + System.nanoTime() + "-" + savedCount, source.getDisplayName(),
                publishedAt, false, null, isUrgent ? new BigDecimal("9.8") : null, isUrgent ? "3.1" : null,
                false, isUrgent), publishedAt);
        if (topic != ArticleTopic.GENERAL) {
            article.applyAiAnalysis(topic, null);
        }
        return articleRepository.save(article);
    }

    public Article saveNews(Instant publishedAt, ArticleTopic topic) {
        return saveArticle(publishedAt, ArticleKind.NEWS, ArticleRegion.DOMESTIC, topic, false);
    }

    private static String toSourceCode(ArticleKind kind, ArticleRegion region) {
        return switch (kind) {
            case NEWS -> region == ArticleRegion.DOMESTIC ? SourceCode.DAILYSECU : SourceCode.THE_HACKER_NEWS;
            case ADVISORY -> SourceCode.KISA;
            case VULNERABILITY -> SourceCode.NVD;
        };
    }
}
