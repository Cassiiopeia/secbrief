package kr.suhsaechan.secbrief.web.controller;

import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.util.List;
import kr.suhsaechan.secbrief.advisory.constant.AlertLevelChangeDirection;
import kr.suhsaechan.secbrief.advisory.constant.CyberCrisisLevel;
import kr.suhsaechan.secbrief.advisory.dto.AlertLevelChangeResponse;
import kr.suhsaechan.secbrief.advisory.dto.AlertLevelDashboardResponse;
import kr.suhsaechan.secbrief.advisory.dto.AlertLevelStatusResponse;
import kr.suhsaechan.secbrief.advisory.dto.InternationalIndicatorResponse;
import kr.suhsaechan.secbrief.advisory.dto.WeeklyThreatCountsResponse;
import kr.suhsaechan.secbrief.advisory.indicator.InternationalAlertIndicator;
import kr.suhsaechan.secbrief.advisory.service.AlertLevelQueryService;
import kr.suhsaechan.secbrief.web.support.WebSecurityTestConfig;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

/**
 * 경보단계 API JSON: {data} 감싸기, 단계 enum 이름, 시각 ISO, 못 읽은 해외 지표는 level null (인증 없음)
 */
@WebMvcTest(AlertLevelController.class)
@Import(WebSecurityTestConfig.class)
class AlertLevelControllerTest {

    private static final Instant RAISED_AT = Instant.parse("2026-10-21T15:00:00Z");
    private static final String ARTICLE_URL = "https://www.boho.or.kr/kr/bbs/view.do?nttId=72233";

    @Autowired
    private MockMvc mockMvc;
    @MockitoBean
    private AlertLevelQueryService alertLevelQueryService;

    @Test
    void returnsDashboardJson() throws Exception {
        given(alertLevelQueryService.findDashboard()).willReturn(new AlertLevelDashboardResponse(
                new AlertLevelStatusResponse(CyberCrisisLevel.CAUTION, RAISED_AT, 794L, "사이버위기 경보 단계 '관심' → '주의' 상향 조정", ARTICLE_URL),
                List.of(new AlertLevelChangeResponse(794L, CyberCrisisLevel.ATTENTION, CyberCrisisLevel.CAUTION,
                        AlertLevelChangeDirection.RAISED, RAISED_AT, "사이버위기 경보 단계 '관심' → '주의' 상향 조정", ARTICLE_URL)),
                new WeeklyThreatCountsResponse(Instant.parse("2026-11-22T15:00:00Z"), 4, 12, 9),
                List.of(new InternationalIndicatorResponse(InternationalAlertIndicator.CIS_MS_ISAC, "elevated", 3, 5,
                                Instant.parse("2026-11-27T00:00:00Z"), "https://www.cisecurity.org/cybersecurity-threats/alert-level"),
                        InternationalIndicatorResponse.unavailable(InternationalAlertIndicator.SANS_ISC_INFOCON)),
                "https://www.boho.or.kr/kr/bbs/list.do?menuNo=205024&bbsId=B0000342"));

        mockMvc.perform(get("/api/v1/alert-level"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.current.level").value("CAUTION"))
                .andExpect(jsonPath("$.data.current.since").value("2026-10-21T15:00:00Z"))
                .andExpect(jsonPath("$.data.changes[0].fromLevel").value("ATTENTION"))
                .andExpect(jsonPath("$.data.changes[0].direction").value("RAISED"))
                .andExpect(jsonPath("$.data.weeklyCounts.exploitedVulnerabilityCount").value(4))
                .andExpect(jsonPath("$.data.weeklyCounts.urgentArticleCount").value(12))
                .andExpect(jsonPath("$.data.weeklyCounts.kisaNoticeCount").value(9))
                .andExpect(jsonPath("$.data.internationalIndicators[0].code").value("CIS_MS_ISAC"))
                .andExpect(jsonPath("$.data.internationalIndicators[0].levelStep").value(3))
                .andExpect(jsonPath("$.data.internationalIndicators[1].level").doesNotExist())
                .andExpect(jsonPath("$.data.internationalIndicators[1].levelCount").value(4))
                .andExpect(jsonPath("$.data.sourceUrl").value("https://www.boho.or.kr/kr/bbs/list.do?menuNo=205024&bbsId=B0000342"));
    }
}
