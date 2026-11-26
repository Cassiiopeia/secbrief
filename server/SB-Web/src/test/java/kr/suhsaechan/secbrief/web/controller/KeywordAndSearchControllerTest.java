package kr.suhsaechan.secbrief.web.controller;

import static org.hamcrest.Matchers.nullValue;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.util.List;
import kr.suhsaechan.secbrief.advisory.constant.ArticleImportance;
import kr.suhsaechan.secbrief.advisory.constant.ArticleKind;
import kr.suhsaechan.secbrief.advisory.constant.ArticleRegion;
import kr.suhsaechan.secbrief.advisory.constant.ArticleSearchMode;
import kr.suhsaechan.secbrief.advisory.constant.ArticleTopic;
import kr.suhsaechan.secbrief.advisory.dto.ArticleResponse;
import kr.suhsaechan.secbrief.advisory.dto.ArticleSearchResponse;
import kr.suhsaechan.secbrief.advisory.dto.TrendingKeywordResponse;
import kr.suhsaechan.secbrief.advisory.dto.TrendingKeywordsResponse;
import kr.suhsaechan.secbrief.advisory.search.ArticleSearchService;
import kr.suhsaechan.secbrief.advisory.service.KeywordTrendQueryService;
import kr.suhsaechan.secbrief.common.exception.CustomException;
import kr.suhsaechan.secbrief.common.exception.constant.ErrorCode;
import kr.suhsaechan.secbrief.web.support.WebSecurityTestConfig;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

/**
 * 키워드·검색 API: {data} 감싸기, 인기 키워드 필드(새 키워드의 rankChange 는 null), 검색 방식(searchMode)·기사의 keywords,
 * 파라미터 전달, 범위 밖 400(INVALID_REQUEST)·빠진 질의(MISSING_PARAMETER)·숫자 아님(TYPE_MISMATCH)
 */
@WebMvcTest({KeywordController.class, ArticleSearchController.class})
@Import(WebSecurityTestConfig.class)
class KeywordAndSearchControllerTest {

    @Autowired
    private MockMvc mockMvc;
    @MockitoBean
    private KeywordTrendQueryService keywordTrendQueryService;
    @MockitoBean
    private ArticleSearchService articleSearchService;

    @Test
    void returnsTrendingKeywordsWrappedInData() throws Exception {
        given(keywordTrendQueryService.findTrendingKeywords(7, 20)).willReturn(new TrendingKeywordsResponse(List.of(
                new TrendingKeywordResponse("랜섬웨어", 12, 7, 1, 2),
                new TrendingKeywordResponse("Next.js", 5, 0, 2, null))));

        mockMvc.perform(get("/api/v1/keywords/trending").param("days", "7").param("size", "20"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.keywords[0].keyword").value("랜섬웨어"))
                .andExpect(jsonPath("$.data.keywords[0].articleCount").value(12))
                .andExpect(jsonPath("$.data.keywords[0].previousArticleCount").value(7))
                .andExpect(jsonPath("$.data.keywords[0].rank").value(1))
                .andExpect(jsonPath("$.data.keywords[0].rankChange").value(2))
                .andExpect(jsonPath("$.data.keywords[1].keyword").value("Next.js"))
                .andExpect(jsonPath("$.data.keywords[1].rankChange").value(nullValue()));
    }

    @Test
    void passesNullWhenTrendingParametersAreOmitted() throws Exception {
        given(keywordTrendQueryService.findTrendingKeywords(null, null)).willReturn(new TrendingKeywordsResponse(List.of()));

        mockMvc.perform(get("/api/v1/keywords/trending"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.keywords").isEmpty());
    }

    @Test
    void returnsInvalidRequestWhenTrendingRangeIsRejected() throws Exception {
        given(keywordTrendQueryService.findTrendingKeywords(31, null))
                .willThrow(new CustomException(ErrorCode.INVALID_REQUEST, "days out of range"));

        mockMvc.perform(get("/api/v1/keywords/trending").param("days", "31"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("INVALID_REQUEST"));
        mockMvc.perform(get("/api/v1/keywords/trending").param("days", "week"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("TYPE_MISMATCH"));
    }

    @Test
    void returnsSearchItemsWithSearchMode() throws Exception {
        given(articleSearchService.search("랜섬웨어", 5)).willReturn(new ArticleSearchResponse(
                List.of(createArticleResponse()), ArticleSearchMode.SEMANTIC));

        mockMvc.perform(get("/api/v1/articles/search").param("query", "랜섬웨어").param("size", "5"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.searchMode").value("SEMANTIC"))
                .andExpect(jsonPath("$.data.items[0].id").value(7))
                .andExpect(jsonPath("$.data.items[0].title").value("Ransomware gang hits hospital"))
                .andExpect(jsonPath("$.data.items[0].keywords[0]").value("랜섬웨어"))
                .andExpect(jsonPath("$.data.items[0].keywords[1]").value("병원"));
    }

    @Test
    void returnsKeywordModeWithEmptyItems() throws Exception {
        given(articleSearchService.search("양자 암호", null)).willReturn(new ArticleSearchResponse(List.of(), ArticleSearchMode.KEYWORD));

        mockMvc.perform(get("/api/v1/articles/search").param("query", "양자 암호"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.searchMode").value("KEYWORD"))
                .andExpect(jsonPath("$.data.items").isEmpty());
    }

    @Test
    void returnsInvalidRequestWhenQueryLengthIsRejected() throws Exception {
        given(articleSearchService.search("가", null))
                .willThrow(new CustomException(ErrorCode.INVALID_REQUEST, "query length out of range"));

        mockMvc.perform(get("/api/v1/articles/search").param("query", "가"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("INVALID_REQUEST"));
    }

    @Test
    void rejectsSearchWithoutQueryAsMissingParameter() throws Exception {
        mockMvc.perform(get("/api/v1/articles/search"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("MISSING_PARAMETER"));
        verifyNoInteractions(articleSearchService);
    }

    private static ArticleResponse createArticleResponse() {
        return new ArticleResponse(7L, ArticleKind.NEWS, ArticleRegion.OVERSEAS, ArticleTopic.RANSOMWARE,
                "Ransomware gang hits hospital", "랜섬웨어 조직, 병원 공격", "https://example.test/news/7", "The Hacker News",
                Instant.parse("2026-11-23T00:00:00Z"), false, null, null, null, false, false, null,
                ArticleImportance.NOTABLE, null, "THE_HACKER_NEWS", List.of("랜섬웨어", "병원"), null);
    }
}
