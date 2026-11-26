package kr.suhsaechan.secbrief.web.advisory;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import kr.suhsaechan.secbrief.advisory.constant.AiStatus;
import kr.suhsaechan.secbrief.advisory.constant.ArticleKind;
import kr.suhsaechan.secbrief.advisory.constant.ArticleRegion;
import kr.suhsaechan.secbrief.advisory.constant.ArticleTopic;
import kr.suhsaechan.secbrief.advisory.dto.ArticlePageResponse;
import kr.suhsaechan.secbrief.advisory.dto.ArticleResponse;
import kr.suhsaechan.secbrief.advisory.dto.ArticleSearchCondition;
import kr.suhsaechan.secbrief.advisory.entity.Article;
import kr.suhsaechan.secbrief.advisory.repository.ArticleRepository;
import kr.suhsaechan.secbrief.advisory.service.ArticleQueryService;
import kr.suhsaechan.secbrief.common.exception.CustomException;
import kr.suhsaechan.secbrief.common.exception.constant.ErrorCode;
import kr.suhsaechan.secbrief.web.support.ArticleTestWriter;
import kr.suhsaechan.secbrief.web.support.PostgresContainerTestSupport;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

/**
 * 기사 목록 (DB): 최신순 커서 페이지(빠짐·중복 없음), 필터, AI 상태 무관, 크기 제한
 */
@SpringBootTest
@ActiveProfiles("test")
@Import(ArticleTestWriter.class)
class ArticleQueryServiceTest extends PostgresContainerTestSupport {

    private static final Instant BASE = Instant.parse("2026-11-20T00:00:00Z");

    @Autowired
    private ArticleQueryService articleQueryService;
    @Autowired
    private ArticleRepository articleRepository;
    @Autowired
    private ArticleTestWriter articleTestWriter;

    @BeforeEach
    void clearArticles() {
        articleRepository.deleteAll();
    }

    @Test
    void pagesNewestFirstWithoutGapsOrDuplicates() {
        List<Long> savedIds = new ArrayList<>();
        for (int index = 0; index < 22; index++) {
            savedIds.add(articleTestWriter.saveNews(BASE.plusSeconds(index * 60L), ArticleTopic.GENERAL).getId());
        }
        // 같은 게시일 3건 (id 로 순서 결정)
        for (int index = 0; index < 3; index++) {
            savedIds.add(articleTestWriter.saveNews(BASE.plusSeconds(600), ArticleTopic.GENERAL).getId());
        }

        List<Long> pagedIds = new ArrayList<>();
        String cursor = null;
        int pageCount = 0;
        do {
            ArticlePageResponse page = articleQueryService.findArticles(new ArticleSearchCondition(null, null, null, null, null, null, cursor, 10));
            page.items().forEach(item -> pagedIds.add(item.id()));
            cursor = page.nextCursor();
            pageCount++;
        } while (cursor != null);

        assertThat(pageCount).isEqualTo(3);
        assertThat(pagedIds).containsExactlyInAnyOrderElementsOf(savedIds).doesNotHaveDuplicates();
        List<ArticleResponse> firstPage = articleQueryService.findArticles(new ArticleSearchCondition(null, null, null, null, null, null, null, 25)).items();
        assertThat(firstPage).extracting(ArticleResponse::publishedAt).isSortedAccordingTo((first, second) -> second.compareTo(first));
    }

    @Test
    void filtersByKindAndRegion() {
        articleTestWriter.saveArticle(BASE, ArticleKind.NEWS, ArticleRegion.DOMESTIC, ArticleTopic.GENERAL, false);
        articleTestWriter.saveArticle(BASE, ArticleKind.NEWS, ArticleRegion.OVERSEAS, ArticleTopic.GENERAL, false);
        articleTestWriter.saveArticle(BASE, ArticleKind.ADVISORY, ArticleRegion.DOMESTIC, ArticleTopic.GENERAL, false);

        List<ArticleResponse> items = articleQueryService.findArticles(
                new ArticleSearchCondition(ArticleKind.NEWS, ArticleRegion.DOMESTIC, null, null, null, null, null, null)).items();

        assertThat(items).singleElement().satisfies(item -> {
            assertThat(item.kind()).isEqualTo(ArticleKind.NEWS);
            assertThat(item.region()).isEqualTo(ArticleRegion.DOMESTIC);
        });
    }

    @Test
    void filtersByTopic() {
        articleTestWriter.saveNews(BASE, ArticleTopic.SUPPLY_CHAIN);
        articleTestWriter.saveNews(BASE, ArticleTopic.RANSOMWARE);

        assertThat(articleQueryService.findArticles(new ArticleSearchCondition(null, null, ArticleTopic.SUPPLY_CHAIN, null, null, null, null, null)).items())
                .extracting(ArticleResponse::topic).containsExactly(ArticleTopic.SUPPLY_CHAIN);
    }

    @Test
    void includesPendingAndFailedAiArticles() {
        articleTestWriter.saveNews(BASE, ArticleTopic.GENERAL);
        Article failedArticle = articleTestWriter.saveNews(BASE.plusSeconds(1), ArticleTopic.GENERAL);
        for (int attempt = 0; attempt < 3; attempt++) {
            failedArticle.recordAiFailure();
        }
        articleRepository.save(failedArticle);

        List<ArticleResponse> items = articleQueryService.findArticles(new ArticleSearchCondition(null, null, null, null, null, null, null, null)).items();

        assertThat(articleRepository.findById(failedArticle.getId()).orElseThrow().getAiStatus()).isEqualTo(AiStatus.FAILED);
        assertThat(items).hasSize(2).allSatisfy(item -> {
            assertThat(item.title()).startsWith("테스트 기사");
            assertThat(item.translatedTitle()).isNull();
        });
    }

    @Test
    void rejectsSizeAboveFifty() {
        assertThatThrownBy(() -> articleQueryService.findArticles(new ArticleSearchCondition(null, null, null, null, null, null, null, 51)))
                .isInstanceOf(CustomException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.INVALID_REQUEST);
    }
}
