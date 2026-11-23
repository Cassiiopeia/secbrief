package kr.suhsaechan.secbrief.web.controller;

import static org.hamcrest.Matchers.nullValue;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import kr.suhsaechan.secbrief.advisory.constant.ArticleKind;
import kr.suhsaechan.secbrief.advisory.constant.ArticleRegion;
import kr.suhsaechan.secbrief.advisory.constant.ArticleTopic;
import kr.suhsaechan.secbrief.advisory.constant.CollectStatus;
import kr.suhsaechan.secbrief.advisory.dto.ArticlePageResponse;
import kr.suhsaechan.secbrief.advisory.dto.ArticleResponse;
import kr.suhsaechan.secbrief.advisory.dto.ArticleSearchCondition;
import kr.suhsaechan.secbrief.advisory.dto.SourceStatusResponse;
import kr.suhsaechan.secbrief.advisory.dto.TodayResponse;
import kr.suhsaechan.secbrief.advisory.dto.TrendTopicResponse;
import kr.suhsaechan.secbrief.advisory.dto.WeeklyTrendResponse;
import kr.suhsaechan.secbrief.advisory.service.ArticleQueryService;
import kr.suhsaechan.secbrief.advisory.service.SourceQueryService;
import kr.suhsaechan.secbrief.advisory.service.TodayQueryService;
import kr.suhsaechan.secbrief.advisory.service.WeeklyTrendQueryService;
import kr.suhsaechan.secbrief.common.exception.CustomException;
import kr.suhsaechan.secbrief.common.exception.constant.ErrorCode;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

/**
 * 읽기 API 4개: {data} 감싸기, boolean 이름(is 접두사), 날짜 ISO, enum 이름, 오류 코드
 */
@WebMvcTest({TodayController.class, ArticleController.class, TrendController.class, SourceController.class})
class AdvisoryApiControllerTest {

    @Autowired
    private MockMvc mockMvc;
    @MockitoBean
    private TodayQueryService todayQueryService;
    @MockitoBean
    private ArticleQueryService articleQueryService;
    @MockitoBean
    private WeeklyTrendQueryService weeklyTrendQueryService;
    @MockitoBean
    private SourceQueryService sourceQueryService;

    @Test
    void returnsArticlesWrappedInDataWithBooleanFieldNames() throws Exception {
        given(articleQueryService.findArticles(any())).willReturn(new ArticlePageResponse(List.of(createNvdKevArticleResponse()), "c1"));

        mockMvc.perform(get("/api/v1/articles").param("kind", "VULNERABILITY"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items[0].publisherName").value("NVD"))
                .andExpect(jsonPath("$.data.items[0].title").value("CVE-2026-1111: Example remote code execution"))
                .andExpect(jsonPath("$.data.items[0].translatedTitle").value("CVE-2026-1111: 원격 코드 실행 예시"))
                .andExpect(jsonPath("$.data.items[0].isKev").value(true))
                .andExpect(jsonPath("$.data.items[0].isUrgent").value(true))
                .andExpect(jsonPath("$.data.items[0].isPublishedAtEstimated").value(false))
                .andExpect(jsonPath("$.data.items[0].publishedAt").value("2026-11-23T00:00:00Z"))
                .andExpect(jsonPath("$.data.items[0].cvssScore").value(9.8))
                .andExpect(jsonPath("$.data.items[0].aiStatus").doesNotExist())
                .andExpect(jsonPath("$.data.items[0].kev").doesNotExist())
                .andExpect(jsonPath("$.data.nextCursor").value("c1"));
    }

    @Test
    void returnsPendingArticleWithOriginalTitle() throws Exception {
        ArticleResponse pending = new ArticleResponse(2L, ArticleKind.NEWS, ArticleRegion.OVERSEAS, ArticleTopic.GENERAL,
                "Example breach report", null, "https://example.test/news/2", "The Hacker News",
                Instant.parse("2026-11-23T00:00:00Z"), false, null, null, null, false, false);
        given(articleQueryService.findArticles(any())).willReturn(new ArticlePageResponse(List.of(pending), null));

        mockMvc.perform(get("/api/v1/articles"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items[0].title").value("Example breach report"))
                .andExpect(jsonPath("$.data.items[0].translatedTitle").value(nullValue()))
                .andExpect(jsonPath("$.data.items[0].topic").value("GENERAL"))
                .andExpect(jsonPath("$.data.nextCursor").value(nullValue()));
    }

    @Test
    void passesFiltersToService() throws Exception {
        given(articleQueryService.findArticles(any())).willReturn(new ArticlePageResponse(List.of(), null));

        mockMvc.perform(get("/api/v1/articles")
                        .param("kind", "NEWS").param("region", "DOMESTIC").param("topic", "SUPPLY_CHAIN")
                        .param("cursor", "abc").param("size", "5"))
                .andExpect(status().isOk());

        ArgumentCaptor<ArticleSearchCondition> captor = ArgumentCaptor.forClass(ArticleSearchCondition.class);
        verify(articleQueryService).findArticles(captor.capture());
        assertThat(captor.getValue()).isEqualTo(new ArticleSearchCondition(
                ArticleKind.NEWS, ArticleRegion.DOMESTIC, ArticleTopic.SUPPLY_CHAIN, "abc", 5));
    }

    @Test
    void rejectsUnknownKindWithTypeMismatch() throws Exception {
        mockMvc.perform(get("/api/v1/articles").param("kind", "BLOG"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("TYPE_MISMATCH"));
    }

    @Test
    void returnsInvalidCursorErrorCode() throws Exception {
        given(articleQueryService.findArticles(any())).willThrow(new CustomException(ErrorCode.INVALID_CURSOR));

        mockMvc.perform(get("/api/v1/articles").param("cursor", "broken"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("INVALID_CURSOR"));
    }

    @Test
    void returnsTodayWithNullUrgentItem() throws Exception {
        given(todayQueryService.findToday()).willReturn(new TodayResponse(LocalDate.parse("2026-11-23"), null, 12, 40, null, List.of()));

        mockMvc.perform(get("/api/v1/today"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.date").value("2026-11-23"))
                .andExpect(jsonPath("$.data.urgentItem").value(nullValue()))
                .andExpect(jsonPath("$.data.todayCount").value(12))
                .andExpect(jsonPath("$.data.weeklyCount").value(40));
    }

    @Test
    void returnsWeeklyTrendTopicsAsEnumNames() throws Exception {
        given(weeklyTrendQueryService.findWeeklyTrend()).willReturn(new WeeklyTrendResponse(LocalDate.parse("2026-11-23"),
                List.of(new TrendTopicResponse(ArticleTopic.SUPPLY_CHAIN, 3, 0, null))));

        mockMvc.perform(get("/api/v1/trends/weekly"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.weekStartDate").value("2026-11-23"))
                .andExpect(jsonPath("$.data.topics[0].topic").value("SUPPLY_CHAIN"))
                .andExpect(jsonPath("$.data.topics[0].changePercent").value(nullValue()));
    }

    @Test
    void returnsSources() throws Exception {
        given(sourceQueryService.findEnabledSources()).willReturn(List.of(new SourceStatusResponse("KISA", "KISA 보호나라",
                ArticleKind.ADVISORY, ArticleRegion.DOMESTIC, Instant.parse("2026-11-23T00:00:00Z"), CollectStatus.SUCCESS)));

        mockMvc.perform(get("/api/v1/sources"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].code").value("KISA"))
                .andExpect(jsonPath("$.data[0].lastStatus").value("SUCCESS"));
    }

    private static ArticleResponse createNvdKevArticleResponse() {
        return new ArticleResponse(1L, ArticleKind.VULNERABILITY, ArticleRegion.OVERSEAS, ArticleTopic.REMOTE_ACCESS,
                "CVE-2026-1111: Example remote code execution", "CVE-2026-1111: 원격 코드 실행 예시",
                "https://nvd.nist.gov/vuln/detail/CVE-2026-1111", "NVD", Instant.parse("2026-11-23T00:00:00Z"), false,
                "CVE-2026-1111", new BigDecimal("9.8"), "3.1", true, true);
    }
}
