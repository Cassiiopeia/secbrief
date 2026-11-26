package kr.suhsaechan.secbrief.web.advisory;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import kr.suhsaechan.secbrief.advisory.collector.FeedHttpClient;
import kr.suhsaechan.secbrief.advisory.constant.AlertLevelChangeDirection;
import kr.suhsaechan.secbrief.advisory.constant.CyberCrisisLevel;
import kr.suhsaechan.secbrief.advisory.constant.SourceCode;
import kr.suhsaechan.secbrief.advisory.dto.AlertLevelChangeResponse;
import kr.suhsaechan.secbrief.advisory.dto.AlertLevelDashboardResponse;
import kr.suhsaechan.secbrief.advisory.dto.ArticleResponse;
import kr.suhsaechan.secbrief.advisory.dto.ArticleSearchCondition;
import kr.suhsaechan.secbrief.advisory.dto.InternationalIndicatorResponse;
import kr.suhsaechan.secbrief.advisory.entity.Article;
import kr.suhsaechan.secbrief.advisory.indicator.InternationalAlertIndicator;
import kr.suhsaechan.secbrief.advisory.indicator.InternationalAlertIndicatorStore;
import kr.suhsaechan.secbrief.advisory.repository.ArticleRepository;
import kr.suhsaechan.secbrief.advisory.service.AlertLevelQueryService;
import kr.suhsaechan.secbrief.advisory.service.AlertLevelUrgencyExpiryService;
import kr.suhsaechan.secbrief.advisory.service.ArticleQueryService;
import kr.suhsaechan.secbrief.common.exception.CustomException;
import kr.suhsaechan.secbrief.common.exception.constant.ErrorCode;
import kr.suhsaechan.secbrief.web.support.ArticleTestWriter;
import kr.suhsaechan.secbrief.web.support.PostgresContainerTestSupport;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

/**
 * 경보단계 화면 (DB, 고정 시계 2026-11-27 금 09:00 KST): 지금 단계·시작일, 변경 이력의 바뀌기 전 단계(제목 > 앞 글), 모르는 제목,
 * 이번 주 건수 3개, 해외 지표(읽은 값·못 읽은 지표), 지난 경보단계 글 긴급 해제, 긴급만 목록 필터
 */
@SpringBootTest
@ActiveProfiles("test")
@Import({ArticleTestWriter.class, AlertLevelDashboardQueryTest.FixedClockConfig.class})
class AlertLevelDashboardQueryTest extends PostgresContainerTestSupport {

    private static final Instant NOW = Instant.parse("2026-11-27T00:00:00Z");
    // 서울 2026-10-22 00:00 (운영 DB 기사 794 의 게시일)
    private static final Instant RAISED_AT = Instant.parse("2026-10-21T15:00:00Z");

    @Autowired
    private AlertLevelQueryService alertLevelQueryService;
    @Autowired
    private AlertLevelUrgencyExpiryService alertLevelUrgencyExpiryService;
    @Autowired
    private ArticleQueryService articleQueryService;
    @Autowired
    private InternationalAlertIndicatorStore internationalAlertIndicatorStore;
    @Autowired
    private ArticleRepository articleRepository;
    @Autowired
    private ArticleTestWriter articleTestWriter;
    @MockitoBean
    private FeedHttpClient feedHttpClient;

    @BeforeEach
    void clearArticles() {
        articleRepository.deleteAll();
    }

    @Test
    void derivesCurrentLevelSinceDateAndChangeHistoryFromTitles() {
        saveAlertLevel("국내·외 사이버 위협 우려가 고조됨에 따라 '주의' 경보 발령", Instant.parse("2022-03-20T15:00:00Z"));
        saveAlertLevel("사이버위기 경보 단계 '주의' → '관심'으로 하향 조정", Instant.parse("2024-09-22T15:00:00Z"));
        saveAlertLevel("사이버위기 경보 단계 '관심' → '주의' 상향 조정", RAISED_AT);

        AlertLevelDashboardResponse dashboard = alertLevelQueryService.findDashboard();

        assertThat(dashboard.current().level()).isEqualTo(CyberCrisisLevel.CAUTION);
        assertThat(dashboard.current().since()).isEqualTo(RAISED_AT);
        assertThat(dashboard.changes()).extracting(AlertLevelChangeResponse::toLevel)
                .containsExactly(CyberCrisisLevel.CAUTION, CyberCrisisLevel.ATTENTION, CyberCrisisLevel.CAUTION);
        assertThat(dashboard.changes().getFirst().direction()).isEqualTo(AlertLevelChangeDirection.RAISED);
        assertThat(dashboard.changes().get(1).direction()).isEqualTo(AlertLevelChangeDirection.LOWERED);
        // 제목에 바뀌기 전 단계가 없고 더 오래된 글도 없음: 모름
        assertThat(dashboard.changes().getLast().fromLevel()).isNull();
        assertThat(dashboard.sourceUrl()).contains("bbsId=B0000342");
    }

    @Test
    void fillsFromLevelWithPreviousArticleAndKeepsUnknownTitle() {
        saveAlertLevel("사이버공격 위험성 증가에 따른 '관심' 경보 발령", Instant.parse("2011-08-23T15:00:00Z"));
        saveAlertLevel("사이버공격 위험성 증가에 따른 주의 경보 발령", Instant.parse("2011-12-18T15:00:00Z"));
        Article unknown = saveAlertLevel("국가ㆍ공공기관 해킹 위협 증대에 따라 '경고' 단계 발령", Instant.parse("2012-01-01T00:00:00Z"));

        AlertLevelDashboardResponse dashboard = alertLevelQueryService.findDashboard();

        // 가장 새 글의 단계를 못 읽음: 단계 null + 제목 그대로
        assertThat(dashboard.current().level()).isNull();
        assertThat(dashboard.current().title()).isEqualTo(unknown.getTitle());
        assertThat(dashboard.changes().getFirst().fromLevel()).isEqualTo(CyberCrisisLevel.CAUTION);
        assertThat(dashboard.changes().getFirst().direction()).isNull();
        // '주의' 발령의 바뀌기 전 단계는 앞 글('관심')에서
        assertThat(dashboard.changes().get(1).fromLevel()).isEqualTo(CyberCrisisLevel.ATTENTION);
        assertThat(dashboard.changes().get(1).direction()).isEqualTo(AlertLevelChangeDirection.RAISED);
    }

    @Test
    void returnsEmptyStatusWithoutAlertLevelArticles() {
        AlertLevelDashboardResponse dashboard = alertLevelQueryService.findDashboard();

        assertThat(dashboard.current()).isNull();
        assertThat(dashboard.changes()).isEmpty();
    }

    @Test
    void countsThisWeekFromSeoulMonday() {
        // 이번 주 시작 = 서울 11-23(월) 00:00 = 11-22T15:00Z
        Instant thisWeek = Instant.parse("2026-11-24T01:00:00Z");
        Instant lastWeek = Instant.parse("2026-11-22T14:00:00Z");
        articleTestWriter.saveSourceArticle(SourceCode.CISA_KEV, "CVE-2026-1 악용", thisWeek, true);
        articleTestWriter.saveSourceArticle(SourceCode.CISA_KEV, "CVE-2026-2 악용", lastWeek, true);
        articleTestWriter.saveSourceArticle(SourceCode.KISA, "Linux 제품 보안 업데이트 권고", thisWeek, false);

        AlertLevelDashboardResponse dashboard = alertLevelQueryService.findDashboard();

        assertThat(dashboard.weeklyCounts().weekStart()).isEqualTo(Instant.parse("2026-11-22T15:00:00Z"));
        assertThat(dashboard.weeklyCounts().exploitedVulnerabilityCount()).isEqualTo(1);
        assertThat(dashboard.weeklyCounts().urgentArticleCount()).isEqualTo(1);
        assertThat(dashboard.weeklyCounts().kisaNoticeCount()).isEqualTo(1);
    }

    @Test
    void returnsReadIndicatorAndUnavailableIndicator() {
        when(feedHttpClient.fetch(InternationalAlertIndicator.CIS_MS_ISAC.getFeedUrl())).thenReturn("elevated\nyellow\n".getBytes());
        when(feedHttpClient.fetch(InternationalAlertIndicator.SANS_ISC_INFOCON.getFeedUrl()))
                .thenThrow(new CustomException(ErrorCode.EXTERNAL_API_ERROR, "status=503"));
        internationalAlertIndicatorStore.refreshAll();

        AlertLevelDashboardResponse dashboard = alertLevelQueryService.findDashboard();

        assertThat(dashboard.internationalIndicators()).extracting(InternationalIndicatorResponse::code)
                .containsExactly(InternationalAlertIndicator.CIS_MS_ISAC, InternationalAlertIndicator.SANS_ISC_INFOCON);
        InternationalIndicatorResponse cis = dashboard.internationalIndicators().getFirst();
        assertThat(cis.level()).isEqualTo("elevated");
        assertThat(cis.levelStep()).isEqualTo(3);
        assertThat(cis.levelCount()).isEqualTo(5);
        assertThat(cis.checkedAt()).isEqualTo(NOW);
        InternationalIndicatorResponse sans = dashboard.internationalIndicators().get(1);
        assertThat(sans.level()).isNull();
        assertThat(sans.levelCount()).isEqualTo(4);
        assertThat(sans.sourceUrl()).isEqualTo("https://isc.sans.edu/infocon.html");
    }

    @Test
    void clearsUrgencyOfAlertLevelArticlesOlderThanSevenDays() {
        Article oldRaised = saveUrgentAlertLevel(RAISED_AT);
        Article freshRaised = saveUrgentAlertLevel(NOW.minus(Duration.ofDays(2)));
        Article oldKev = articleTestWriter.saveSourceArticle(SourceCode.CISA_KEV, "CVE-2026-3 악용", RAISED_AT, true);

        assertThat(alertLevelUrgencyExpiryService.expireAlertLevelUrgency()).isEqualTo(1);

        assertThat(articleRepository.findById(oldRaised.getId()).orElseThrow().isUrgent()).isFalse();
        assertThat(articleRepository.findById(freshRaised.getId()).orElseThrow().isUrgent()).isTrue();
        // 다른 출처의 긴급은 그대로
        assertThat(articleRepository.findById(oldKev.getId()).orElseThrow().isUrgent()).isTrue();
    }

    @Test
    void filtersUrgentArticlesOnly() {
        Article urgent = articleTestWriter.saveSourceArticle(SourceCode.CISA_KEV, "CVE-2026-4 악용", NOW, true);
        articleTestWriter.saveSourceArticle(SourceCode.KISA, "정기 보안 업데이트", NOW, false);

        assertThat(articleQueryService.findArticles(
                new ArticleSearchCondition(null, null, null, null, null, null, true, null, null)).items())
                .extracting(ArticleResponse::id).containsExactly(urgent.getId());
        assertThat(articleQueryService.findArticles(
                new ArticleSearchCondition(null, null, null, null, null, null, null, null)).items()).hasSize(2);
    }

    private Article saveAlertLevel(String title, Instant publishedAt) {
        return articleTestWriter.saveSourceArticle(SourceCode.KISA_ALERT_LEVEL, title, publishedAt, false);
    }

    private Article saveUrgentAlertLevel(Instant publishedAt) {
        return articleTestWriter.saveSourceArticle(SourceCode.KISA_ALERT_LEVEL, "사이버위기 경보 단계 '관심' → '주의' 상향 조정",
                publishedAt, true);
    }

    @TestConfiguration
    static class FixedClockConfig {

        @Bean
        @Primary
        Clock testClock() {
            return Clock.fixed(NOW, ZoneOffset.UTC);
        }
    }
}
