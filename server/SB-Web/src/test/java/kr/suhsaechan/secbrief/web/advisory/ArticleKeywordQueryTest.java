package kr.suhsaechan.secbrief.web.advisory;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import jakarta.persistence.EntityManagerFactory;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import kr.suhsaechan.secbrief.advisory.ai.ArticleTitleAnalysis;
import kr.suhsaechan.secbrief.advisory.constant.ArticleImportance;
import kr.suhsaechan.secbrief.advisory.constant.ArticleKind;
import kr.suhsaechan.secbrief.advisory.constant.ArticleRegion;
import kr.suhsaechan.secbrief.advisory.constant.ArticleTopic;
import kr.suhsaechan.secbrief.advisory.dto.ArticlePageResponse;
import kr.suhsaechan.secbrief.advisory.dto.ArticleResponse;
import kr.suhsaechan.secbrief.advisory.dto.ArticleSearchCondition;
import kr.suhsaechan.secbrief.advisory.dto.TrendingKeywordResponse;
import kr.suhsaechan.secbrief.advisory.entity.Article;
import kr.suhsaechan.secbrief.advisory.repository.ArticleRepository;
import kr.suhsaechan.secbrief.advisory.service.ArticleQueryService;
import kr.suhsaechan.secbrief.advisory.service.KeywordTrendQueryService;
import kr.suhsaechan.secbrief.application.ai.ArticleAiResultRecorder;
import kr.suhsaechan.secbrief.common.exception.constant.ErrorCode;
import kr.suhsaechan.secbrief.web.support.ArticleTestWriter;
import kr.suhsaechan.secbrief.web.support.PostgresContainerTestSupport;
import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

/**
 * 키워드 (DB): AI 성공 기록 때 검증 통과분 저장·교체, 실패 때 비움, 응답의 keywords, 키워드 필터(정확히 일치·대소문자 무시·커서),
 * 인기 키워드 순위(기간·앞 기간·대소문자 합치기), 목록 조회 쿼리 수 (N+1 없음)
 */
@SpringBootTest
@ActiveProfiles("test")
@Import(ArticleTestWriter.class)
class ArticleKeywordQueryTest extends PostgresContainerTestSupport {

    @Autowired
    private ArticleAiResultRecorder articleAiResultRecorder;
    @Autowired
    private ArticleQueryService articleQueryService;
    @Autowired
    private KeywordTrendQueryService keywordTrendQueryService;
    @Autowired
    private ArticleRepository articleRepository;
    @Autowired
    private ArticleTestWriter articleTestWriter;
    @Autowired
    private JdbcTemplate jdbcTemplate;
    @Autowired
    private EntityManagerFactory entityManagerFactory;

    private Instant now;

    @BeforeEach
    void clearArticles() {
        articleRepository.deleteAll();
        now = Instant.now();
    }

    @Test
    void storesAcceptedKeywordsAndReturnsThemInResponse() {
        Article article = articleTestWriter.saveNews(now.minus(Duration.ofHours(1)), ArticleTopic.GENERAL);

        // 제목은 "테스트 기사 N": 제목에 없는 숫자열(2026)과 # 는 걸러짐, 대소문자만 다른 중복은 하나만
        articleAiResultRecorder.recordSuccess(article.getId(), new ArticleTitleAnalysis(ArticleTopic.RANSOMWARE, null,
                ArticleImportance.NOTABLE, null, List.of("#랜섬웨어", "Linux", "linux", "2026 보고서", " 개인정보 유출 ")));

        assertThat(findItems(null)).singleElement()
                .extracting(ArticleResponse::keywords).isEqualTo(List.of("Linux", "개인정보 유출", "랜섬웨어"));
        assertThat(countKeywordRows(article.getId())).isEqualTo(3);
    }

    @Test
    void replacesKeywordsOnNextSuccessAndClearsThemOnFailure() {
        Long articleId = saveNewsWithKeywords(Duration.ofHours(1), "랜섬웨어", "병원").getId();

        articleAiResultRecorder.recordSuccess(articleId, new ArticleTitleAnalysis(ArticleTopic.RANSOMWARE, null,
                ArticleImportance.ROUTINE, null, List.of("병원", "제로데이")));
        assertThat(findItems(null).get(0).keywords()).containsExactly("병원", "제로데이");

        articleAiResultRecorder.recordFailure(articleId);
        assertThat(findItems(null).get(0).keywords()).isEmpty();
        assertThat(countKeywordRows(articleId)).isZero();
    }

    @Test
    void articleWithoutAiResultHasEmptyKeywords() {
        articleTestWriter.saveNews(now.minus(Duration.ofHours(1)), ArticleTopic.GENERAL);

        assertThat(findItems(null).get(0).keywords()).isEmpty();
    }

    @Test
    void filtersByExactKeywordIgnoringCase() {
        Article linuxArticle = saveNewsWithKeywords(Duration.ofHours(1), "Linux", "제로데이");
        Article lowerCaseLinuxArticle = saveNewsWithKeywords(Duration.ofHours(2), "linux");
        saveNewsWithKeywords(Duration.ofHours(3), "Linux Kernel");
        saveNewsWithKeywords(Duration.ofHours(4), "랜섬웨어");

        assertThat(findItems(" LINUX ")).extracting(ArticleResponse::id)
                .containsExactly(linuxArticle.getId(), lowerCaseLinuxArticle.getId());
        assertThat(findItems("랜섬")).isEmpty();
        assertThat(findItems(null)).hasSize(4);
        assertThat(findItems("  ")).hasSize(4);
    }

    @Test
    void keywordFilterWorksWithOtherFiltersAndCursor() {
        for (int hoursAgo = 1; hoursAgo <= 3; hoursAgo++) {
            saveNewsWithKeywords(Duration.ofHours(hoursAgo), "랜섬웨어", "병원");
        }
        saveNewsWithKeywords(Duration.ofMinutes(30), "피싱");
        Article vulnerability = articleTestWriter.saveArticle(now.minus(Duration.ofMinutes(10)),
                ArticleKind.VULNERABILITY, ArticleRegion.OVERSEAS, ArticleTopic.GENERAL, false);
        articleAiResultRecorder.recordSuccess(vulnerability.getId(), new ArticleTitleAnalysis(
                ArticleTopic.VULNERABILITY, null, ArticleImportance.ROUTINE, null, List.of("랜섬웨어")));

        ArticlePageResponse firstPage = articleQueryService.findArticles(new ArticleSearchCondition(
                ArticleKind.NEWS, null, null, null, null, "랜섬웨어", null, 2));
        ArticlePageResponse secondPage = articleQueryService.findArticles(new ArticleSearchCondition(
                ArticleKind.NEWS, null, null, null, null, "랜섬웨어", firstPage.nextCursor(), 2));

        assertThat(firstPage.items()).hasSize(2);
        assertThat(secondPage.items()).hasSize(1);
        assertThat(secondPage.nextCursor()).isNull();
        assertThat(firstPage.items()).allSatisfy(item -> assertThat(item.kind()).isEqualTo(ArticleKind.NEWS));
    }

    @Test
    void ranksTrendingKeywordsWithPreviousPeriodAndCaseMerge() {
        // 이번 7일: 랜섬웨어 3건, Linux 2건 + linux 1건(합쳐 3건), 피싱 1건
        saveNewsWithKeywords(Duration.ofDays(1), "랜섬웨어", "Linux");
        saveNewsWithKeywords(Duration.ofDays(2), "랜섬웨어", "Linux");
        saveNewsWithKeywords(Duration.ofDays(3), "랜섬웨어", "linux");
        saveNewsWithKeywords(Duration.ofDays(6), "피싱");
        // 앞 7일: 피싱 2건, 랜섬웨어 1건
        saveNewsWithKeywords(Duration.ofDays(8), "피싱", "랜섬웨어");
        saveNewsWithKeywords(Duration.ofDays(13), "피싱");
        // 두 기간 밖
        saveNewsWithKeywords(Duration.ofDays(15), "제로데이");

        List<TrendingKeywordResponse> keywords = keywordTrendQueryService.findTrendingKeywords(null, null).keywords();

        // 같은 건수는 글자순: linux < 랜섬웨어. 앞 기간 순위: 피싱 1, 랜섬웨어 2
        assertThat(keywords).containsExactly(
                new TrendingKeywordResponse("Linux", 3, 0, 1, null),
                new TrendingKeywordResponse("랜섬웨어", 3, 1, 2, 0),
                new TrendingKeywordResponse("피싱", 1, 2, 3, -2));
    }

    @Test
    void trendingRespectsDaysAndSizeAndRejectsDaysOutOfRange() {
        saveNewsWithKeywords(Duration.ofHours(2), "랜섬웨어", "병원");
        saveNewsWithKeywords(Duration.ofHours(3), "랜섬웨어");
        saveNewsWithKeywords(Duration.ofDays(2), "피싱");

        assertThat(keywordTrendQueryService.findTrendingKeywords(1, null).keywords())
                .extracting(TrendingKeywordResponse::keyword).containsExactly("랜섬웨어", "병원");
        assertThat(keywordTrendQueryService.findTrendingKeywords(30, 1).keywords())
                .extracting(TrendingKeywordResponse::keyword).containsExactly("랜섬웨어");
        assertThatThrownBy(() -> keywordTrendQueryService.findTrendingKeywords(0, null))
                .extracting("errorCode").isEqualTo(ErrorCode.INVALID_REQUEST);
        assertThatThrownBy(() -> keywordTrendQueryService.findTrendingKeywords(31, null))
                .extracting("errorCode").isEqualTo(ErrorCode.INVALID_REQUEST);
        // 순위 크기는 거부하지 않고 조정 (0 > 기본 20, 51 > 50)
        assertThat(keywordTrendQueryService.findTrendingKeywords(1, 0).keywords()).hasSize(2);
        assertThat(keywordTrendQueryService.findTrendingKeywords(1, 51).keywords()).hasSize(2);
    }

    @Test
    void listReadsKeywordsAndSourcesInBatchesNotPerArticle() {
        // 출처 3곳의 기사 20건, 기사마다 키워드 2개
        for (int minutesAgo = 1; minutesAgo <= 20; minutesAgo++) {
            Article article = articleTestWriter.saveArticle(now.minus(Duration.ofMinutes(minutesAgo)),
                    ArticleKind.values()[minutesAgo % 3], ArticleRegion.OVERSEAS, ArticleTopic.GENERAL, false);
            articleAiResultRecorder.recordSuccess(article.getId(), new ArticleTitleAnalysis(
                    ArticleTopic.GENERAL, null, ArticleImportance.ROUTINE, null, List.of("랜섬웨어", "키워드" + (minutesAgo % 2 == 0 ? "가" : "나"))));
        }
        Statistics statistics = entityManagerFactory.unwrap(SessionFactory.class).getStatistics();
        statistics.setStatisticsEnabled(true);
        statistics.clear();
        try {
            List<ArticleResponse> items = findItems(null);

            assertThat(items).hasSize(20).allSatisfy(item -> assertThat(item.keywords()).hasSize(2));
            // 기사 1회 + 출처 묶음 1회 + 키워드 묶음 1회
            assertThat(statistics.getPrepareStatementCount()).isLessThanOrEqualTo(3);
        } finally {
            statistics.setStatisticsEnabled(false);
        }
    }

    private Article saveNewsWithKeywords(Duration publishedBefore, String... keywords) {
        Article article = articleTestWriter.saveNews(now.minus(publishedBefore), ArticleTopic.GENERAL);
        articleAiResultRecorder.recordSuccess(article.getId(), new ArticleTitleAnalysis(
                ArticleTopic.GENERAL, null, ArticleImportance.ROUTINE, null, List.of(keywords)));
        return article;
    }

    private List<ArticleResponse> findItems(String keyword) {
        return articleQueryService.findArticles(
                new ArticleSearchCondition(null, null, null, null, null, keyword, null, null)).items();
    }

    private int countKeywordRows(Long articleId) {
        return jdbcTemplate.queryForObject("select count(*) from article_keyword where article_id = ?", Integer.class, articleId);
    }
}
