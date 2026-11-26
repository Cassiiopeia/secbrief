package kr.suhsaechan.secbrief.web.summary;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.List;
import kr.suhsaechan.secbrief.advisory.constant.ArticleKind;
import kr.suhsaechan.secbrief.advisory.constant.ArticleRegion;
import kr.suhsaechan.secbrief.advisory.constant.SourceCode;
import kr.suhsaechan.secbrief.advisory.constant.SummaryStatus;
import kr.suhsaechan.secbrief.advisory.entity.Article;
import kr.suhsaechan.secbrief.advisory.entity.ArticleSummaryState;
import kr.suhsaechan.secbrief.advisory.entity.Source;
import kr.suhsaechan.secbrief.advisory.policy.ArticleDraft;
import kr.suhsaechan.secbrief.advisory.repository.ArticleRepository;
import kr.suhsaechan.secbrief.advisory.repository.SourceRepository;
import kr.suhsaechan.secbrief.advisory.summary.AffectedTarget;
import kr.suhsaechan.secbrief.advisory.summary.ArticleBrief;
import kr.suhsaechan.secbrief.application.summary.ArticleSummaryResultRecorder;
import kr.suhsaechan.secbrief.web.support.PostgresContainerTestSupport;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.Limit;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

/**
 * 요약 결과 기록 (DB): 성공이면 브리핑 열 5개 + 이전 구조 열 3개를 채움, 성공·최종 실패 때 요약 입력(source_text)을 비움, 3회 실패면 대기열에서 빠짐
 * 요약하는 사이 입력이 바뀐 건은 기록하지 않음, 대기열은 중요도 높은 순 > 최신순
 */
@SpringBootTest
@ActiveProfiles("test")
class ArticleSummaryResultRecorderTest extends PostgresContainerTestSupport {

    private static final Instant NOW = Instant.parse("2026-11-23T00:00:00Z");
    private static final String SOURCE_TEXT = "Attackers are exploiting a flaw in Example VPN to steal session tokens.";
    private static final ArticleBrief BRIEF = new ArticleBrief("Example VPN 결함으로 세션 토큰이 털리고 있어요.",
            "공격자가 Example VPN의 결함을 악용하고 있어요.", "세션 토큰을 빼앗길 수 있어요.",
            List.of(new AffectedTarget("Example VPN", "2.4.0 이전 버전"), new AffectedTarget("Example Hub", null)),
            List.of("패치를 적용하세요.", "관리 포트를 닫으세요."));

    @Autowired
    private ArticleSummaryResultRecorder recorder;
    @Autowired
    private ArticleRepository articleRepository;
    @Autowired
    private SourceRepository sourceRepository;
    @Autowired
    private JdbcTemplate jdbcTemplate;

    private Source source;

    @BeforeEach
    void clearArticles() {
        articleRepository.deleteAll();
        source = sourceRepository.findByCode(SourceCode.THE_HACKER_NEWS).orElseThrow();
    }

    @Test
    void savesPendingArticleWithSourceText() {
        Long articleId = saveArticle("pending", SOURCE_TEXT, NOW);

        assertThat(readColumn(articleId, "summary_status")).isEqualTo("PENDING");
        assertThat(readColumn(articleId, "source_text")).isEqualTo(SOURCE_TEXT);
        assertThat(readColumn(saveArticle("none", null, NOW), "summary_status")).isEqualTo("NONE");
    }

    @Test
    void recordsBriefWithLegacyColumnsAndClearsSourceText() {
        Long articleId = saveArticle("done", SOURCE_TEXT, NOW);

        boolean isRecorded = recorder.recordSuccess(articleId, SOURCE_TEXT, BRIEF);

        ArticleSummaryState summaryState = articleRepository.findById(articleId).orElseThrow().getSummaryState();
        assertThat(isRecorded).isTrue();
        assertThat(summaryState.getStatus()).isEqualTo(SummaryStatus.DONE);
        assertThat(summaryState.hasBrief()).isTrue();
        assertThat(summaryState.getBrief()).isEqualTo(BRIEF);
        // 브리핑 열 5개
        assertThat(readColumn(articleId, "summary_one_line")).isEqualTo(BRIEF.oneLine());
        assertThat(readColumn(articleId, "summary_what_happened")).isEqualTo(BRIEF.whatHappened());
        assertThat(readColumn(articleId, "summary_why_it_matters")).isEqualTo(BRIEF.whyItMatters());
        assertThat(readColumn(articleId, "summary_affected")).isEqualTo(
                "[{\"target\":\"Example VPN\",\"detail\":\"2.4.0 이전 버전\"},{\"target\":\"Example Hub\",\"detail\":null}]");
        assertThat(readColumn(articleId, "summary_actions")).isEqualTo("[\"패치를 적용하세요.\",\"관리 포트를 닫으세요.\"]");
        // 이전 구조 열 3개: 이전 서버 버전·이전 앱 빌드가 읽는 값
        assertThat(readColumn(articleId, "summary"))
                .isEqualTo("Example VPN 결함으로 세션 토큰이 털리고 있어요. 공격자가 Example VPN의 결함을 악용하고 있어요. 세션 토큰을 빼앗길 수 있어요.");
        assertThat(readColumn(articleId, "summary_key_points")).isEqualTo("[\"Example VPN: 2.4.0 이전 버전\",\"Example Hub\"]");
        assertThat(readColumn(articleId, "summary_action_guide")).isEqualTo("패치를 적용하세요. 관리 포트를 닫으세요.");
        // 본문을 보관하지 않음: DB 열이 실제로 비었는지 확인
        assertThat(readColumn(articleId, "source_text")).isNull();
    }

    @Test
    void briefWithoutOptionalFieldsStoresNullColumns() {
        Long articleId = saveArticle("minimal", SOURCE_TEXT, NOW);

        recorder.recordSuccess(articleId, SOURCE_TEXT,
                new ArticleBrief("Example VPN 결함이 공개됐어요.", "Example VPN에 결함이 있어요.", null, List.of(), List.of()));

        assertThat(readColumn(articleId, "summary_why_it_matters")).isNull();
        assertThat(readColumn(articleId, "summary_affected")).isNull();
        assertThat(readColumn(articleId, "summary_actions")).isNull();
        assertThat(readColumn(articleId, "summary_key_points")).isNull();
        assertThat(readColumn(articleId, "summary_action_guide")).isNull();
        assertThat(articleRepository.findById(articleId).orElseThrow().getSummaryState().getBrief())
                .isEqualTo(new ArticleBrief("Example VPN 결함이 공개됐어요.", "Example VPN에 결함이 있어요.", null, List.of(), List.of()));
    }

    @Test
    void marksFailedAndClearsSourceTextAfterThirdFailure() {
        Long articleId = saveArticle("failed", SOURCE_TEXT, NOW);

        recorder.recordFailure(articleId, SOURCE_TEXT);
        recorder.recordFailure(articleId, SOURCE_TEXT);
        assertThat(readColumn(articleId, "summary_status")).isEqualTo("PENDING");
        assertThat(readColumn(articleId, "source_text")).isEqualTo(SOURCE_TEXT);

        recorder.recordFailure(articleId, SOURCE_TEXT);
        assertThat(readColumn(articleId, "summary_status")).isEqualTo("FAILED");
        assertThat(readColumn(articleId, "source_text")).isNull();
        assertThat(articleRepository.findSummaryTargets(Limit.of(10))).isEmpty();
    }

    @Test
    void doesNotRecordWhenSourceTextChangedWhileSummarizing() {
        Long articleId = saveArticle("changed", SOURCE_TEXT, NOW);
        String staleSourceText = "Older description that was replaced while the provider was answering.";

        boolean isRecorded = recorder.recordSuccess(articleId, staleSourceText, BRIEF);
        recorder.recordFailure(articleId, staleSourceText);

        ArticleSummaryState summaryState = articleRepository.findById(articleId).orElseThrow().getSummaryState();
        assertThat(isRecorded).isFalse();
        assertThat(summaryState.getStatus()).isEqualTo(SummaryStatus.PENDING);
        assertThat(summaryState.getSourceText()).isEqualTo(SOURCE_TEXT);
        assertThat(summaryState.getAttemptCount()).isZero();
    }

    @Test
    void queuesPendingArticlesByImportanceThenNewest() {
        Long olderRoutineId = saveArticle("older-routine", SOURCE_TEXT, NOW.minusSeconds(3600));
        Long newerRoutineId = saveArticle("newer-routine", SOURCE_TEXT, NOW);
        Long notableId = saveArticle("notable", SOURCE_TEXT, NOW.minusSeconds(7200));
        Long headlineId = saveArticle("headline", SOURCE_TEXT, NOW.minusSeconds(10800));
        saveArticle("no-source-text", null, NOW);
        jdbcTemplate.update("update article set importance = 'NOTABLE' where id = ?", notableId);
        jdbcTemplate.update("update article set importance = 'HEADLINE' where id = ?", headlineId);

        assertThat(articleRepository.findSummaryTargets(Limit.of(10))).extracting(Article::getId)
                .containsExactly(headlineId, notableId, newerRoutineId, olderRoutineId);
        assertThat(articleRepository.findSummaryTargets(Limit.of(2))).extracting(Article::getId)
                .containsExactly(headlineId, notableId);
    }

    private Long saveArticle(String slug, String sourceText, Instant publishedAt) {
        ArticleDraft draft = new ArticleDraft(ArticleKind.NEWS, ArticleRegion.OVERSEAS, "Example VPN flaw exploited",
                "https://thehackernews.com/2026/11/" + slug + ".html", "The Hacker News", publishedAt, false,
                null, null, null, false, false, null, sourceText, List.of());
        return articleRepository.save(Article.create(source, draft, NOW)).getId();
    }

    private String readColumn(Long articleId, String columnName) {
        return jdbcTemplate.queryForObject("select " + columnName + " from article where id = ?", String.class, articleId);
    }
}
