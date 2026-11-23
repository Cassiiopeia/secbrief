package kr.suhsaechan.secbrief.web.schema;

import static java.util.stream.Collectors.toMap;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Map;
import kr.suhsaechan.secbrief.advisory.constant.AiStatus;
import kr.suhsaechan.secbrief.advisory.constant.ArticleTopic;
import kr.suhsaechan.secbrief.advisory.constant.FeedFormat;
import kr.suhsaechan.secbrief.advisory.entity.Source;
import kr.suhsaechan.secbrief.advisory.repository.SourceRepository;
import kr.suhsaechan.secbrief.web.support.PostgresContainerTestSupport;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

/**
 * 스키마 검증: Flyway 적용 > JPA validate 통과, 출처 시드 7건, 기사 기본값·유일 제약
 */
@SpringBootTest
@ActiveProfiles("test")
class AdvisorySchemaTest extends PostgresContainerTestSupport {

    private static final String INSERT_ARTICLE_WITHOUT_AI_COLUMNS = """
            insert into article (source_id, kind, title, original_url, publisher_name, published_at,
                                 is_published_at_estimated, region, is_kev, is_urgent, collected_at, cve_id)
            values (?, 'NEWS', '테스트 제목', ?, '테스트 게시자', now(), false, 'DOMESTIC', false, false, now(), ?)
            """;

    @Autowired
    private SourceRepository sourceRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private Long kisaSourceId;

    @BeforeEach
    void cleanArticlesAndFindSource() {
        jdbcTemplate.update("delete from article");
        kisaSourceId = sourceRepository.findByCode("KISA").orElseThrow().getId();
    }

    @Test
    void flywayMigratesAndJpaValidatesSchema() {
        // 컨텍스트가 뜨면 ddl-auto=validate 통과
        assertThat(sourceRepository.count()).isEqualTo(7);
    }

    @Test
    void seedsSevenEnabledSourcesWithSpecIntervals() {
        Map<String, Integer> intervalByCode = sourceRepository.findAllByIsEnabledTrueOrderByIdAsc().stream()
                .collect(toMap(Source::getCode, Source::getIntervalMinutes));

        assertThat(intervalByCode).containsExactlyInAnyOrderEntriesOf(Map.of(
                "KISA", 30, "CISA_KEV", 360, "NVD", 120, "DAILYSECU", 30, "THE_HACKER_NEWS", 30,
                "BLEEPING_COMPUTER", 30, "GHSA", 120));
    }

    @Test
    void seedFeedFormatsMatchCollectorTypes() {
        assertThat(sourceRepository.findByCode("NVD").orElseThrow().getFeedFormat()).isEqualTo(FeedFormat.NVD_API);
        assertThat(sourceRepository.findByCode("CISA_KEV").orElseThrow().getFeedFormat())
                .isEqualTo(FeedFormat.CISA_KEV_JSON);
        assertThat(sourceRepository.findByCode("GHSA").orElseThrow().getFeedFormat()).isEqualTo(FeedFormat.GHSA_API);
    }

    @Test
    void newArticleDefaultsToPendingGeneral() {
        // topic·ai 컬럼 없이 insert > DB 기본값 적용
        jdbcTemplate.update(INSERT_ARTICLE_WITHOUT_AI_COLUMNS, kisaSourceId, "https://example.kr/default", null);

        Map<String, Object> row = jdbcTemplate.queryForMap(
                "select topic, ai_status, ai_attempt_count from article where original_url = ?",
                "https://example.kr/default");

        assertThat(row.get("topic")).isEqualTo(ArticleTopic.GENERAL.name());
        assertThat(row.get("ai_status")).isEqualTo(AiStatus.PENDING.name());
        assertThat(row.get("ai_attempt_count")).isEqualTo(0);
    }

    @Test
    void rejectsDuplicateOriginalUrl() {
        jdbcTemplate.update(INSERT_ARTICLE_WITHOUT_AI_COLUMNS, kisaSourceId, "https://example.kr/duplicate", null);

        assertThatThrownBy(() -> jdbcTemplate.update(
                INSERT_ARTICLE_WITHOUT_AI_COLUMNS, kisaSourceId, "https://example.kr/duplicate", null))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void allowsManyArticlesWithoutCveId() {
        jdbcTemplate.update(INSERT_ARTICLE_WITHOUT_AI_COLUMNS, kisaSourceId, "https://example.kr/no-cve-1", null);
        jdbcTemplate.update(INSERT_ARTICLE_WITHOUT_AI_COLUMNS, kisaSourceId, "https://example.kr/no-cve-2", null);

        Integer articleCount = jdbcTemplate.queryForObject("select count(*) from article", Integer.class);

        assertThat(articleCount).isEqualTo(2);
    }

    @Test
    void topicApiValuesRoundTrip() {
        assertThat(ArticleTopic.SUPPLY_CHAIN.getApiValue()).isEqualTo("supplyChain");
        assertThat(ArticleTopic.fromApiValue("remoteAccess")).contains(ArticleTopic.REMOTE_ACCESS);
        assertThat(ArticleTopic.fromApiValue("malware")).isEmpty();
    }
}
