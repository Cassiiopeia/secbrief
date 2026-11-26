package kr.suhsaechan.secbrief.web.controller;

import static kr.suhsaechan.secbrief.advisory.support.SourceFixtures.createSource;
import static org.hamcrest.Matchers.hasKey;
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
import kr.suhsaechan.secbrief.advisory.constant.ResponseGuideMatchReason;
import kr.suhsaechan.secbrief.advisory.constant.ResponseGuideSection;
import kr.suhsaechan.secbrief.advisory.constant.SourceCode;
import kr.suhsaechan.secbrief.advisory.dto.ArticleDetailResponse;
import kr.suhsaechan.secbrief.advisory.dto.ResponseGuideItemResponse;
import kr.suhsaechan.secbrief.advisory.dto.ResponseGuideSectionResponse;
import kr.suhsaechan.secbrief.advisory.entity.Article;
import kr.suhsaechan.secbrief.advisory.fact.SourceFact;
import kr.suhsaechan.secbrief.advisory.policy.ArticleDraft;
import kr.suhsaechan.secbrief.advisory.service.ArticleDetailQueryService;
import kr.suhsaechan.secbrief.advisory.summary.AffectedTarget;
import kr.suhsaechan.secbrief.advisory.summary.ArticleBrief;
import kr.suhsaechan.secbrief.advisory.support.LegacySummaryFixtures;
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
 * 기사 상세 API: {data} 감싸기, 브리핑 있음(brief + 브리핑에서 옮긴 이전 구조 3개)·이전 요약만 있음(brief null)·요약 없음 응답 모양,
 * 사실 값·관련 기사·대응 모아보기, 없는 id 404, 숫자 아닌 id 400
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
    void returnsBriefLegacyFieldsFactsAndRelatedItemsWrappedInData() throws Exception {
        Article article = createArticle(1L, "CVE-2026-1111: Example VPN authentication bypass",
                List.of(new SourceFact("조치 기한", "2026-12-11")));
        article.getSummaryState().applyBrief(new ArticleBrief("Example VPN에서 인증 없이 관리자 콘솔에 들어갈 수 있어요.",
                "Example VPN에 인증을 건너뛰는 취약점이 있어요.", "원격 공격자가 관리자 콘솔에 접근할 수 있어요.",
                List.of(new AffectedTarget("Example VPN", "2.4.0 이전 버전"), new AffectedTarget("Example Hub", null)),
                List.of("2.4.1로 올리세요.", "관리 포트를 닫으세요.")));
        Article relatedArticle = createArticle(2L, "Example VPN ships emergency update", List.of());
        ResponseGuideSectionResponse officialSection = new ResponseGuideSectionResponse(ResponseGuideSection.OFFICIAL, 1,
                List.of(ResponseGuideItemResponse.of(relatedArticle, ResponseGuideMatchReason.SAME_CVE, "CVE-2026-1111")));
        given(articleDetailQueryService.findArticleDetail(1L))
                .willReturn(ArticleDetailResponse.of(article, List.of(relatedArticle), List.of(officialSection)));

        mockMvc.perform(get("/api/v1/articles/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.article.id").value(1))
                .andExpect(jsonPath("$.data.article.title").value("CVE-2026-1111: Example VPN authentication bypass"))
                .andExpect(jsonPath("$.data.article.originalUrl").value("https://example.test/articles/1"))
                // 목록 항목(기사)에도 한 줄 결론
                .andExpect(jsonPath("$.data.article.oneLine").value("Example VPN에서 인증 없이 관리자 콘솔에 들어갈 수 있어요."))
                // 브리핑: 앱과 약속한 이름 5개
                .andExpect(jsonPath("$.data.brief.*", hasSize(5)))
                .andExpect(jsonPath("$.data.brief.oneLine").value("Example VPN에서 인증 없이 관리자 콘솔에 들어갈 수 있어요."))
                .andExpect(jsonPath("$.data.brief.whatHappened").value("Example VPN에 인증을 건너뛰는 취약점이 있어요."))
                .andExpect(jsonPath("$.data.brief.whyItMatters").value("원격 공격자가 관리자 콘솔에 접근할 수 있어요."))
                .andExpect(jsonPath("$.data.brief.affected", hasSize(2)))
                .andExpect(jsonPath("$.data.brief.affected[0].*", hasSize(2)))
                .andExpect(jsonPath("$.data.brief.affected[0].target").value("Example VPN"))
                .andExpect(jsonPath("$.data.brief.affected[0].detail").value("2.4.0 이전 버전"))
                .andExpect(jsonPath("$.data.brief.affected[1].target").value("Example Hub"))
                .andExpect(jsonPath("$.data.brief.affected[1].detail").value(nullValue()))
                .andExpect(jsonPath("$.data.brief.actions", hasSize(2)))
                .andExpect(jsonPath("$.data.brief.actions[1]").value("관리 포트를 닫으세요."))
                // 이전 앱 빌드가 읽는 3개: 브리핑에서 옮긴 값
                .andExpect(jsonPath("$.data.summary")
                        .value("Example VPN에서 인증 없이 관리자 콘솔에 들어갈 수 있어요. Example VPN에 인증을 건너뛰는 취약점이 있어요. "
                                + "원격 공격자가 관리자 콘솔에 접근할 수 있어요."))
                .andExpect(jsonPath("$.data.keyPoints", hasSize(2)))
                .andExpect(jsonPath("$.data.keyPoints[0]").value("Example VPN: 2.4.0 이전 버전"))
                .andExpect(jsonPath("$.data.keyPoints[1]").value("Example Hub"))
                .andExpect(jsonPath("$.data.actionGuide").value("2.4.1로 올리세요. 관리 포트를 닫으세요."))
                .andExpect(jsonPath("$.data.isAiSummarized").value(true))
                .andExpect(jsonPath("$.data.aiSummarized").doesNotExist())
                .andExpect(jsonPath("$.data.facts[0].label").value("조치 기한"))
                .andExpect(jsonPath("$.data.facts[0].value").value("2026-12-11"))
                .andExpect(jsonPath("$.data.relatedItems", hasSize(1)))
                .andExpect(jsonPath("$.data.relatedItems[0].id").value(2))
                // 대응 모아보기 (#71): 구역 + 전체 건수 + 항목 (기사 + 출처 이름·묶인 이유·겹친 CVE·조치 기한)
                .andExpect(jsonPath("$.data.responseGuideSections", hasSize(1)))
                .andExpect(jsonPath("$.data.responseGuideSections[0].section").value("OFFICIAL"))
                .andExpect(jsonPath("$.data.responseGuideSections[0].totalCount").value(1))
                .andExpect(jsonPath("$.data.responseGuideSections[0].items[0].*", hasSize(5)))
                .andExpect(jsonPath("$.data.responseGuideSections[0].items[0].article.id").value(2))
                .andExpect(jsonPath("$.data.responseGuideSections[0].items[0].article.originalUrl").value("https://example.test/articles/2"))
                .andExpect(jsonPath("$.data.responseGuideSections[0].items[0].article.sourceCode").value(SourceCode.THE_HACKER_NEWS))
                .andExpect(jsonPath("$.data.responseGuideSections[0].items[0].sourceName").isNotEmpty())
                .andExpect(jsonPath("$.data.responseGuideSections[0].items[0].matchReason").value("SAME_CVE"))
                .andExpect(jsonPath("$.data.responseGuideSections[0].items[0].matchedCveId").value("CVE-2026-1111"))
                .andExpect(jsonPath("$.data.responseGuideSections[0].items[0].actionDueDate").value(nullValue()))
                // 요약 입력·상태는 내보내지 않음
                .andExpect(jsonPath("$.data.sourceText").doesNotExist())
                .andExpect(jsonPath("$.data.article.summaryState").doesNotExist());
    }

    @Test
    void returnsBriefWithNullWhyItMattersAndEmptyLists() throws Exception {
        Article article = createArticle(5L, "Example VPN flaw", List.of());
        article.getSummaryState().applyBrief(new ArticleBrief("Example VPN 결함이 공개됐어요.", "Example VPN에 결함이 있어요.",
                null, List.of(), List.of()));
        given(articleDetailQueryService.findArticleDetail(5L)).willReturn(ArticleDetailResponse.of(article, List.of(), List.of()));

        mockMvc.perform(get("/api/v1/articles/5"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.brief.oneLine").value("Example VPN 결함이 공개됐어요."))
                // 없는 값: whyItMatters 는 null, 목록은 빈 배열 (이름은 항상 있음)
                .andExpect(jsonPath("$.data.brief", hasKey("whyItMatters")))
                .andExpect(jsonPath("$.data.brief.whyItMatters").value(nullValue()))
                .andExpect(jsonPath("$.data.brief.affected", hasSize(0)))
                .andExpect(jsonPath("$.data.brief.actions", hasSize(0)))
                .andExpect(jsonPath("$.data.summary").value("Example VPN 결함이 공개됐어요. Example VPN에 결함이 있어요."))
                .andExpect(jsonPath("$.data.keyPoints", hasSize(0)))
                .andExpect(jsonPath("$.data.actionGuide").value(nullValue()))
                .andExpect(jsonPath("$.data.isAiSummarized").value(true));
    }

    @Test
    void returnsLegacySummaryWithNullBriefForArticleSummarizedBeforeBriefStructure() throws Exception {
        Article article = createArticle(4L, "Example VPN flaw exploited", List.of());
        LegacySummaryFixtures.applyLegacySummary(article.getSummaryState(), "Example VPN 에서 인증을 건너뛸 수 있어요.",
                List.of("첫째예요.", "둘째예요."), "패치를 적용하세요.");
        given(articleDetailQueryService.findArticleDetail(4L)).willReturn(ArticleDetailResponse.of(article, List.of(), List.of()));

        mockMvc.perform(get("/api/v1/articles/4"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data", hasKey("brief")))
                .andExpect(jsonPath("$.data.brief").value(nullValue()))
                .andExpect(jsonPath("$.data.article.oneLine").value(nullValue()))
                .andExpect(jsonPath("$.data.summary").value("Example VPN 에서 인증을 건너뛸 수 있어요."))
                .andExpect(jsonPath("$.data.keyPoints", hasSize(2)))
                .andExpect(jsonPath("$.data.keyPoints[1]").value("둘째예요."))
                .andExpect(jsonPath("$.data.actionGuide").value("패치를 적용하세요."))
                .andExpect(jsonPath("$.data.isAiSummarized").value(true));
    }

    @Test
    void returnsNullBriefWhenStoredOneLineIsBlank() throws Exception {
        // 앱은 한 줄 결론이 없는 브리핑을 통째로 무시하므로 서버가 brief 를 null 로 내보냄 (이전 구조 3개는 그대로)
        Article article = createArticle(6L, "Example VPN flaw", List.of());
        article.getSummaryState().applyBrief(new ArticleBrief("", "Example VPN에 결함이 있어요.", null, List.of(), List.of()));
        given(articleDetailQueryService.findArticleDetail(6L)).willReturn(ArticleDetailResponse.of(article, List.of(), List.of()));

        mockMvc.perform(get("/api/v1/articles/6"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.brief").value(nullValue()))
                .andExpect(jsonPath("$.data.article.oneLine").value(nullValue()))
                .andExpect(jsonPath("$.data.summary").value("Example VPN에 결함이 있어요."));
    }

    @Test
    void returnsNullSummaryAndEmptyListsWhenNotSummarized() throws Exception {
        given(articleDetailQueryService.findArticleDetail(3L))
                .willReturn(ArticleDetailResponse.of(createArticle(3L, "Pending article", List.of()), List.of(), List.of()));

        mockMvc.perform(get("/api/v1/articles/3"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.brief").value(nullValue()))
                .andExpect(jsonPath("$.data.summary").value(nullValue()))
                .andExpect(jsonPath("$.data.keyPoints", hasSize(0)))
                .andExpect(jsonPath("$.data.actionGuide").value(nullValue()))
                .andExpect(jsonPath("$.data.isAiSummarized").value(false))
                .andExpect(jsonPath("$.data.facts", hasSize(0)))
                .andExpect(jsonPath("$.data.relatedItems", hasSize(0)))
                .andExpect(jsonPath("$.data.responseGuideSections", hasSize(0)));
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
