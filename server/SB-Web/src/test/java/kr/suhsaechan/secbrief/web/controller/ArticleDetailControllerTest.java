package kr.suhsaechan.secbrief.web.controller;

import static kr.suhsaechan.secbrief.advisory.support.SourceFixtures.createSource;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.nullValue;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.util.List;
import kr.suhsaechan.secbrief.advisory.constant.ArticleKind;
import kr.suhsaechan.secbrief.advisory.constant.ArticleRegion;
import kr.suhsaechan.secbrief.advisory.constant.FeedFormat;
import kr.suhsaechan.secbrief.advisory.constant.SourceCode;
import kr.suhsaechan.secbrief.advisory.dto.ArticleDetailResponse;
import kr.suhsaechan.secbrief.advisory.entity.Article;
import kr.suhsaechan.secbrief.advisory.fact.SourceFact;
import kr.suhsaechan.secbrief.advisory.policy.ArticleDraft;
import kr.suhsaechan.secbrief.advisory.service.ArticleDetailQueryService;
import kr.suhsaechan.secbrief.common.exception.CustomException;
import kr.suhsaechan.secbrief.common.exception.constant.ErrorCode;
import kr.suhsaechan.secbrief.web.support.WebSecurityTestConfig;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;

/**
 * 기사 상세 API: {data} 감싸기, 요약 있음·없음 응답 모양(isAiSummarized, 빈 keyPoints), 사실 값·관련 기사, 없는 id 404, 숫자 아닌 id 400
 */
@WebMvcTest(ArticleDetailController.class)
@Import(WebSecurityTestConfig.class)
class ArticleDetailControllerTest {

    private static final Instant NOW = Instant.parse("2026-11-23T00:00:00Z");

    @Autowired
    private MockMvc mockMvc;
    @MockitoBean
    private ArticleDetailQueryService articleDetailQueryService;

    @Test
    void returnsSummaryFactsAndRelatedItemsWrappedInData() throws Exception {
        Article article = createArticle(1L, "CVE-2026-1111: Example VPN authentication bypass",
                List.of(new SourceFact("조치 기한", "2026-12-11")));
        article.getSummaryState().applySummary("Example VPN 에서 인증을 건너뛸 수 있어요.", List.of("첫째예요.", "둘째예요."), "패치를 적용하세요.");
        Article relatedArticle = createArticle(2L, "Example VPN ships emergency update", List.of());
        given(articleDetailQueryService.findArticleDetail(1L))
                .willReturn(ArticleDetailResponse.of(article, List.of(relatedArticle)));

        mockMvc.perform(get("/api/v1/articles/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.article.id").value(1))
                .andExpect(jsonPath("$.data.article.title").value("CVE-2026-1111: Example VPN authentication bypass"))
                .andExpect(jsonPath("$.data.article.originalUrl").value("https://example.test/articles/1"))
                .andExpect(jsonPath("$.data.summary").value("Example VPN 에서 인증을 건너뛸 수 있어요."))
                .andExpect(jsonPath("$.data.keyPoints", hasSize(2)))
                .andExpect(jsonPath("$.data.keyPoints[1]").value("둘째예요."))
                .andExpect(jsonPath("$.data.actionGuide").value("패치를 적용하세요."))
                .andExpect(jsonPath("$.data.isAiSummarized").value(true))
                .andExpect(jsonPath("$.data.aiSummarized").doesNotExist())
                .andExpect(jsonPath("$.data.facts[0].label").value("조치 기한"))
                .andExpect(jsonPath("$.data.facts[0].value").value("2026-12-11"))
                .andExpect(jsonPath("$.data.relatedItems", hasSize(1)))
                .andExpect(jsonPath("$.data.relatedItems[0].id").value(2))
                // 요약 입력·상태는 내보내지 않음
                .andExpect(jsonPath("$.data.sourceText").doesNotExist())
                .andExpect(jsonPath("$.data.article.summaryState").doesNotExist());
    }

    @Test
    void returnsNullSummaryAndEmptyListsWhenNotSummarized() throws Exception {
        given(articleDetailQueryService.findArticleDetail(3L))
                .willReturn(ArticleDetailResponse.of(createArticle(3L, "Pending article", List.of()), List.of()));

        mockMvc.perform(get("/api/v1/articles/3"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.summary").value(nullValue()))
                .andExpect(jsonPath("$.data.keyPoints", hasSize(0)))
                .andExpect(jsonPath("$.data.actionGuide").value(nullValue()))
                .andExpect(jsonPath("$.data.isAiSummarized").value(false))
                .andExpect(jsonPath("$.data.facts", hasSize(0)))
                .andExpect(jsonPath("$.data.relatedItems", hasSize(0)));
    }

    @Test
    void returnsResourceNotFoundForUnknownId() throws Exception {
        given(articleDetailQueryService.findArticleDetail(404L)).willThrow(new CustomException(ErrorCode.RESOURCE_NOT_FOUND));

        mockMvc.perform(get("/api/v1/articles/404"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorCode").value("RESOURCE_NOT_FOUND"));
    }

    @Test
    void rejectsNonNumericIdWithTypeMismatch() throws Exception {
        mockMvc.perform(get("/api/v1/articles/abc"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("TYPE_MISMATCH"));
    }

    private static Article createArticle(Long id, String title, List<SourceFact> sourceFacts) {
        Article article = Article.create(
                createSource(SourceCode.THE_HACKER_NEWS, ArticleKind.NEWS, ArticleRegion.OVERSEAS, FeedFormat.RSS, null),
                new ArticleDraft(ArticleKind.NEWS, ArticleRegion.OVERSEAS, title, "https://example.test/articles/" + id,
                        "The Hacker News", NOW, false, null, null, null, false, false, null,
                        "Example VPN contains an authentication bypass reachable by remote attackers.", sourceFacts),
                NOW);
        ReflectionTestUtils.setField(article, "id", id);
        return article;
    }
}
