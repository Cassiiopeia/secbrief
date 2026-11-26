package kr.suhsaechan.secbrief.web.search;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.verify;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import kr.suhsaechan.secbrief.advisory.ai.ArticleTitleAnalysis;
import kr.suhsaechan.secbrief.advisory.constant.ArticleImportance;
import kr.suhsaechan.secbrief.advisory.constant.ArticleKind;
import kr.suhsaechan.secbrief.advisory.constant.ArticleRegion;
import kr.suhsaechan.secbrief.advisory.constant.ArticleSearchMode;
import kr.suhsaechan.secbrief.advisory.constant.ArticleTopic;
import kr.suhsaechan.secbrief.advisory.constant.SourceCode;
import kr.suhsaechan.secbrief.advisory.dto.ArticleResponse;
import kr.suhsaechan.secbrief.advisory.dto.ArticleSearchResponse;
import kr.suhsaechan.secbrief.advisory.embedding.TextEmbeddingProvider;
import kr.suhsaechan.secbrief.advisory.entity.Article;
import kr.suhsaechan.secbrief.advisory.entity.Source;
import kr.suhsaechan.secbrief.advisory.policy.ArticleDraft;
import kr.suhsaechan.secbrief.advisory.repository.ArticleRepository;
import kr.suhsaechan.secbrief.advisory.repository.SourceRepository;
import kr.suhsaechan.secbrief.advisory.search.ArticleSearchService;
import kr.suhsaechan.secbrief.application.ai.ArticleAiResultRecorder;
import kr.suhsaechan.secbrief.application.embedding.ArticleEmbeddingProcessService;
import kr.suhsaechan.secbrief.application.embedding.EmbeddingProcessResult;
import kr.suhsaechan.secbrief.common.exception.CustomException;
import kr.suhsaechan.secbrief.common.exception.constant.ErrorCode;
import kr.suhsaechan.secbrief.web.support.PostgresContainerTestSupport;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

/**
 * 임베딩·의미 검색 (DB, 가짜 제공자·고정 시계): 임베딩 대기열 조건(AI 대기, 검색 범위, 모델 변경), real[] 저장,
 * AI 결과가 나중에 기록되면 다시 임베딩, 유사도 순 SEMANTIC 결과, 결과 없음·제공자 실패 시 KEYWORD 대체
 */
@SpringBootTest
@ActiveProfiles("test")
@Import(ArticleSemanticSearchTest.AdjustableClockConfig.class)
class ArticleSemanticSearchTest extends PostgresContainerTestSupport {

    private static final String MODEL = "test-embedding-model";
    // 테스트마다 7시간씩 넘겨 임베딩 캐시가 전체를 다시 읽게 함 (전체 읽기 간격 6시간)
    private static final AtomicReference<Instant> NOW = new AtomicReference<>(Instant.parse("2026-11-23T00:00:00Z"));
    private static final Duration AFTER_AI_WAIT = ArticleEmbeddingProcessService.AI_WAIT_DURATION.plusMinutes(1);

    @MockitoBean
    private TextEmbeddingProvider textEmbeddingProvider;
    @Autowired
    private ArticleEmbeddingProcessService articleEmbeddingProcessService;
    @Autowired
    private ArticleSearchService articleSearchService;
    @Autowired
    private ArticleAiResultRecorder articleAiResultRecorder;
    @Autowired
    private ArticleRepository articleRepository;
    @Autowired
    private SourceRepository sourceRepository;
    @Autowired
    private JdbcTemplate jdbcTemplate;

    private int savedCount;

    @BeforeEach
    void clearArticlesAndPrepareProvider() {
        articleRepository.deleteAll();
        NOW.set(NOW.get().plus(Duration.ofHours(7)));
        given(textEmbeddingProvider.isAvailable()).willReturn(true);
        given(textEmbeddingProvider.getModelName()).willReturn(MODEL);
        given(textEmbeddingProvider.embed(anyList())).willAnswer(invocation -> {
            List<String> texts = invocation.getArgument(0);
            List<float[]> embeddings = new ArrayList<>();
            texts.forEach(text -> embeddings.add(toFakeEmbedding(text)));
            return embeddings;
        });
    }

    @Test
    void storesEmbeddingsAsRealArrayAndSearchesBySimilarity() {
        Article ransomwareArticle = saveAnalyzedNews("Ransomware gang hits hospital", "랜섬웨어 조직, 병원 공격");
        Article mixedArticle = saveAnalyzedNews("Ransomware and phishing campaign grows", "랜섬웨어·피싱 공격 증가");
        Article phishingArticle = saveAnalyzedNews("Phishing kit targets banks", "피싱 도구, 은행 노려");
        saveAnalyzedNews("Quarterly patch summary", "분기 패치 요약");

        assertThat(articleEmbeddingProcessService.processMissingEmbeddings()).isEqualTo(new EmbeddingProcessResult(4, false));
        // 다시 돌리면 대상 없음
        assertThat(articleEmbeddingProcessService.processMissingEmbeddings()).isEqualTo(new EmbeddingProcessResult(0, false));

        Map<String, Object> storedRow = jdbcTemplate.queryForMap(
                "select embedding_model, array_length(embedding, 1) as dimension_count, embedding[1] as first_value "
                        + "from article_embedding where article_id = ?", ransomwareArticle.getId());
        assertThat(storedRow.get("embedding_model")).isEqualTo(MODEL);
        assertThat(storedRow.get("dimension_count")).isEqualTo(3);
        assertThat(storedRow.get("first_value")).isEqualTo(1f);

        // 질의 벡터 (1, 0, 0): 랜섬웨어 1.0 > 섞인 기사 0.707 > 피싱·그 밖 0 (최소 유사도 0.5 미만 제외)
        ArticleSearchResponse response = articleSearchService.search("랜섬웨어 피해", null);

        assertThat(response.searchMode()).isEqualTo(ArticleSearchMode.SEMANTIC);
        assertThat(response.items()).extracting(ArticleResponse::id)
                .containsExactly(ransomwareArticle.getId(), mixedArticle.getId());
        assertThat(articleSearchService.search("피싱 메일", 1).items())
                .extracting(ArticleResponse::id).containsExactly(phishingArticle.getId());
    }

    @Test
    void waitsForAiResultBeforeEmbeddingThenUsesOriginalTitleAfterDeadline() {
        Article pendingArticle = saveNews("Ransomware gang hits hospital", Duration.ZERO);

        assertThat(articleEmbeddingProcessService.processMissingEmbeddings().embeddedCount()).isZero();

        NOW.set(NOW.get().plus(AFTER_AI_WAIT));
        assertThat(articleEmbeddingProcessService.processMissingEmbeddings().embeddedCount()).isEqualTo(1);
        assertThat(findEmbeddedTexts()).containsExactly("Ransomware gang hits hospital");
        assertThat(countEmbeddingRows(pendingArticle.getId())).isEqualTo(1);
    }

    @Test
    void reEmbedsWithTranslationAndKeywordsWhenAiResultIsRecordedLater() {
        Article article = saveNews("Phishing kit targets banks", Duration.ZERO);
        NOW.set(NOW.get().plus(AFTER_AI_WAIT));
        articleEmbeddingProcessService.processMissingEmbeddings();

        articleAiResultRecorder.recordSuccess(article.getId(), new ArticleTitleAnalysis(ArticleTopic.GENERAL,
                "피싱 도구, 은행 노려", ArticleImportance.ROUTINE, null, List.of("피싱", "은행")));
        assertThat(countEmbeddingRows(article.getId())).isZero();

        assertThat(articleEmbeddingProcessService.processMissingEmbeddings().embeddedCount()).isEqualTo(1);
        assertThat(findEmbeddedTexts()).last().isEqualTo("피싱 도구, 은행 노려\nPhishing kit targets banks\n은행, 피싱");
        assertThat(countEmbeddingRows(article.getId())).isEqualTo(1);
    }

    @Test
    void reEmbedsEveryArticleWhenModelChanges() {
        Article article = saveAnalyzedNews("Ransomware gang hits hospital", "랜섬웨어 조직, 병원 공격");
        articleEmbeddingProcessService.processMissingEmbeddings();

        given(textEmbeddingProvider.getModelName()).willReturn("other-embedding-model");

        assertThat(articleEmbeddingProcessService.processMissingEmbeddings().embeddedCount()).isEqualTo(1);
        assertThat(countEmbeddingRows(article.getId())).isEqualTo(1);
        assertThat(jdbcTemplate.queryForObject("select embedding_model from article_embedding where article_id = ?",
                String.class, article.getId())).isEqualTo("other-embedding-model");
        // 새 모델 기준으로 캐시를 다시 읽어 검색
        assertThat(articleSearchService.search("랜섬웨어 피해", null).searchMode()).isEqualTo(ArticleSearchMode.SEMANTIC);
    }

    @Test
    void skipsArticlesPublishedBeforeSearchableRange() {
        Article oldArticle = saveNews("Ransomware gang hits hospital", Duration.ofDays(181));
        oldArticle.applyAiAnalysis(ArticleTopic.RANSOMWARE, null, ArticleImportance.ROUTINE, null);
        articleRepository.save(oldArticle);

        assertThat(articleEmbeddingProcessService.processMissingEmbeddings().embeddedCount()).isZero();
    }

    @Test
    void fallsBackToKeywordWhenNoArticleIsSimilarEnough() {
        Article phishingArticle = saveAnalyzedNews("Phishing kit targets banks", "피싱 도구, 은행 노려");
        articleEmbeddingProcessService.processMissingEmbeddings();

        // 질의 "은행" 의 벡터 (0, 0, 1) 과 유사한 기사가 없음 > 글자 일치로 대체 (번역 제목의 "은행")
        ArticleSearchResponse response = articleSearchService.search("은행", null);

        assertThat(response.searchMode()).isEqualTo(ArticleSearchMode.KEYWORD);
        assertThat(response.items()).extracting(ArticleResponse::id).containsExactly(phishingArticle.getId());
    }

    @Test
    void fallsBackToKeywordWhenProviderFailsOnQuery() {
        Article ransomwareArticle = saveAnalyzedNews("Ransomware gang hits hospital", "랜섬웨어 조직, 병원 공격");
        articleEmbeddingProcessService.processMissingEmbeddings();
        given(textEmbeddingProvider.embed(anyList())).willThrow(new CustomException(ErrorCode.AI_API_ERROR, "status=503"));

        ArticleSearchResponse response = articleSearchService.search("랜섬웨어", null);

        assertThat(response.searchMode()).isEqualTo(ArticleSearchMode.KEYWORD);
        assertThat(response.items()).extracting(ArticleResponse::id).containsExactly(ransomwareArticle.getId());
    }

    @Test
    void keepsQueueUntouchedWhenProviderFailsOnBatch() {
        Article article = saveAnalyzedNews("Ransomware gang hits hospital", "랜섬웨어 조직, 병원 공격");
        given(textEmbeddingProvider.embed(anyList())).willThrow(new CustomException(ErrorCode.AI_API_ERROR, "status=503"));

        assertThat(articleEmbeddingProcessService.processMissingEmbeddings()).isEqualTo(new EmbeddingProcessResult(0, false));
        assertThat(countEmbeddingRows(article.getId())).isZero();
    }

    // 가짜 제공자: 문장 > 3차원 벡터 (랜섬웨어·ransomware x축, 피싱·phishing y축, 아무 단어도 없으면 z축)
    private static float[] toFakeEmbedding(String text) {
        String lowerCaseText = text.toLowerCase();
        float ransomwareAxis = lowerCaseText.contains("랜섬웨어") || lowerCaseText.contains("ransomware") ? 1f : 0f;
        float phishingAxis = lowerCaseText.contains("피싱") || lowerCaseText.contains("phishing") ? 1f : 0f;
        float otherAxis = ransomwareAxis + phishingAxis == 0f ? 1f : 0f;
        return new float[] {ransomwareAxis, phishingAxis, otherAxis};
    }

    @SuppressWarnings("unchecked")
    private List<String> findEmbeddedTexts() {
        ArgumentCaptor<List<String>> textsCaptor = ArgumentCaptor.forClass(List.class);
        verify(textEmbeddingProvider, atLeastOnce()).embed(textsCaptor.capture());
        return textsCaptor.getAllValues().stream().flatMap(List::stream).toList();
    }

    // AI 처리가 끝난 기사 (바로 임베딩 대상)
    private Article saveAnalyzedNews(String title, String translatedTitle) {
        Article article = saveNews(title, Duration.ofHours(1));
        article.applyAiAnalysis(ArticleTopic.GENERAL, translatedTitle, ArticleImportance.ROUTINE, null);
        return articleRepository.save(article);
    }

    private Article saveNews(String title, Duration publishedBefore) {
        savedCount++;
        Source source = sourceRepository.findByCode(SourceCode.THE_HACKER_NEWS).orElseThrow();
        Instant publishedAt = NOW.get().minus(publishedBefore);
        return articleRepository.save(Article.create(source, new ArticleDraft(ArticleKind.NEWS, ArticleRegion.OVERSEAS, title,
                "https://example.test/semantic/" + System.nanoTime() + "-" + savedCount, source.getDisplayName(),
                publishedAt, false, null, null, null, false, false), NOW.get()));
    }

    private int countEmbeddingRows(Long articleId) {
        return jdbcTemplate.queryForObject("select count(*) from article_embedding where article_id = ?", Integer.class, articleId);
    }

    @TestConfiguration
    static class AdjustableClockConfig {

        // NOW 를 읽는 시계 (테스트마다 시각 변경)
        @Bean
        @Primary
        Clock testClock() {
            return new Clock() {
                @Override
                public ZoneId getZone() {
                    return ZoneOffset.UTC;
                }

                @Override
                public Clock withZone(ZoneId zone) {
                    return this;
                }

                @Override
                public Instant instant() {
                    return NOW.get();
                }
            };
        }
    }
}
