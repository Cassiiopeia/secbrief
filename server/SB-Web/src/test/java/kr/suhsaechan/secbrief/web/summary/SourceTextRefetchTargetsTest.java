package kr.suhsaechan.secbrief.web.summary;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.List;
import kr.suhsaechan.secbrief.advisory.constant.ArticleRegion;
import kr.suhsaechan.secbrief.advisory.constant.SourceCode;
import kr.suhsaechan.secbrief.advisory.entity.Article;
import kr.suhsaechan.secbrief.advisory.entity.Source;
import kr.suhsaechan.secbrief.advisory.policy.ArticleDraft;
import kr.suhsaechan.secbrief.advisory.refetch.SourceTextRefetchMethod;
import kr.suhsaechan.secbrief.advisory.repository.ArticleRepository;
import kr.suhsaechan.secbrief.advisory.repository.SourceRepository;
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
 * 다시 요약 대상 (DB): 브리핑·요약 입력 없는 DONE(이전 구조)·NONE 만, 다시 받는 출처만, beforeId 미만 id 내림차순
 * 다시 받은 브리핑 기록: 브리핑 열·이전 구조 열을 채우고 DONE, 그 사이 브리핑이 생겼거나 입력 대기 중이면 기록하지 않음
 */
@SpringBootTest
@ActiveProfiles("test")
class SourceTextRefetchTargetsTest extends PostgresContainerTestSupport {

    private static final Instant NOW = Instant.parse("2026-11-27T00:00:00Z");
    private static final String SOURCE_TEXT = "Attackers are exploiting a flaw in Example Router to install malware.";
    private static final ArticleBrief BRIEF = new ArticleBrief("Example Router 결함이 악용되고 있어요.",
            "공격자가 Example Router 결함을 악용해 악성코드를 심었어요.", null, List.of(), List.of("펌웨어를 올리세요."));

    @Autowired
    private ArticleRepository articleRepository;
    @Autowired
    private SourceRepository sourceRepository;
    @Autowired
    private ArticleSummaryResultRecorder recorder;
    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void clearArticles() {
        articleRepository.deleteAll();
    }

    @Test
    void findsArticlesWithoutBriefAndSourceTextFromRefetchedSourcesNewestFirst() {
        Long legacyDoneId = saveArticle(SourceCode.BLEEPING_COMPUTER, "legacy-done", null);
        jdbcTemplate.update("update article set summary_status = 'DONE', summary = '이전 구조 요약' where id = ?", legacyDoneId);
        Long noInputId = saveArticle(SourceCode.KISA, "no-input", null);
        Long pendingId = saveArticle(SourceCode.BLEEPING_COMPUTER, "pending", SOURCE_TEXT);
        Long failedId = saveArticle(SourceCode.BLEEPING_COMPUTER, "failed", null);
        jdbcTemplate.update("update article set summary_status = 'FAILED' where id = ?", failedId);
        Long briefedId = saveArticle(SourceCode.BLEEPING_COMPUTER, "briefed", SOURCE_TEXT);
        recorder.recordSuccess(briefedId, SOURCE_TEXT, BRIEF);
        Long notSupportedId = saveArticle(SourceCode.THE_HACKER_NEWS, "not-supported", null);

        List<Long> targetIds = findTargetIds(Long.MAX_VALUE, 10);

        assertThat(targetIds).containsExactly(noInputId, legacyDoneId)
                .doesNotContain(pendingId, failedId, briefedId, notSupportedId);
        assertThat(findTargetIds(noInputId, 10)).containsExactly(legacyDoneId);
        assertThat(findTargetIds(Long.MAX_VALUE, 1)).containsExactly(noInputId);
    }

    @Test
    void recordsRefetchedBriefOnlyWhileArticleStillHasNoBriefAndNoPendingInput() {
        Long legacyDoneId = saveArticle(SourceCode.BLEEPING_COMPUTER, "legacy-done", null);
        jdbcTemplate.update("update article set summary_status = 'DONE', summary = '이전 구조 요약' where id = ?", legacyDoneId);
        Long pendingId = saveArticle(SourceCode.BLEEPING_COMPUTER, "pending", SOURCE_TEXT);

        boolean isLegacyRecorded = recorder.recordRefetchedBrief(legacyDoneId, BRIEF);
        boolean isPendingRecorded = recorder.recordRefetchedBrief(pendingId, BRIEF);
        boolean isRecordedTwice = recorder.recordRefetchedBrief(legacyDoneId, BRIEF);

        assertThat(isLegacyRecorded).isTrue();
        assertThat(isPendingRecorded).isFalse();
        assertThat(isRecordedTwice).isFalse();
        assertThat(readColumn(legacyDoneId, "summary_one_line")).isEqualTo(BRIEF.oneLine());
        assertThat(readColumn(legacyDoneId, "summary")).startsWith(BRIEF.oneLine());
        assertThat(readColumn(legacyDoneId, "summary_status")).isEqualTo("DONE");
        assertThat(readColumn(pendingId, "summary_status")).isEqualTo("PENDING");
    }

    private List<Long> findTargetIds(long beforeId, int limit) {
        return articleRepository.findSourceTextRefetchTargets(beforeId, SourceTextRefetchMethod.toSupportedSourceCodes(),
                Limit.of(limit)).stream().map(Article::getId).toList();
    }

    private Long saveArticle(String sourceCode, String slug, String sourceText) {
        Source source = sourceRepository.findByCode(sourceCode).orElseThrow();
        ArticleDraft draft = new ArticleDraft(source.getKind(), ArticleRegion.OVERSEAS, "Example Router flaw exploited " + slug,
                "https://example.test/news/" + slug, "Example", NOW, false, null, null, null, false, false, null,
                sourceText, List.of());
        return articleRepository.save(Article.create(source, draft, NOW)).getId();
    }

    private String readColumn(Long articleId, String columnName) {
        return jdbcTemplate.queryForObject("select " + columnName + " from article where id = ?", String.class, articleId);
    }
}
