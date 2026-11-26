package kr.suhsaechan.secbrief.web.search;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import kr.suhsaechan.secbrief.advisory.constant.ArticleImportance;
import kr.suhsaechan.secbrief.advisory.constant.ArticleKind;
import kr.suhsaechan.secbrief.advisory.constant.ArticleRegion;
import kr.suhsaechan.secbrief.advisory.constant.ArticleSearchMode;
import kr.suhsaechan.secbrief.advisory.constant.ArticleTopic;
import kr.suhsaechan.secbrief.advisory.constant.SourceCode;
import kr.suhsaechan.secbrief.advisory.dto.ArticleResponse;
import kr.suhsaechan.secbrief.advisory.dto.ArticleSearchResponse;
import kr.suhsaechan.secbrief.advisory.entity.Article;
import kr.suhsaechan.secbrief.advisory.entity.Source;
import kr.suhsaechan.secbrief.advisory.policy.ArticleDraft;
import kr.suhsaechan.secbrief.advisory.repository.ArticleRepository;
import kr.suhsaechan.secbrief.advisory.repository.SourceRepository;
import kr.suhsaechan.secbrief.advisory.search.ArticleSearchService;
import kr.suhsaechan.secbrief.web.support.PostgresContainerTestSupport;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

/**
 * 글자 일치 검색 (DB, 임베딩 꺼짐): 대상 5개(제목, 번역 제목, 회사·제품 이름, 키워드, CVE 번호)에 대소문자 무시 부분 일치,
 * 최신순·건수 상한, LIKE 특수문자(%, _, \)는 글자 그대로, 임베딩이 준비 안 된 환경에서는 항상 KEYWORD
 */
@SpringBootTest
@ActiveProfiles("test")
class ArticleKeywordSearchTest extends PostgresContainerTestSupport {

    @Autowired
    private ArticleSearchService articleSearchService;
    @Autowired
    private ArticleRepository articleRepository;
    @Autowired
    private SourceRepository sourceRepository;

    private Instant now;
    private int savedCount;

    @BeforeEach
    void clearArticles() {
        articleRepository.deleteAll();
        now = Instant.now();
    }

    @Test
    void matchesTitleTranslatedTitleSubjectNameKeywordAndCveIdIgnoringCase() {
        Article titleMatched = saveNews("Ivanti patches Connect Secure flaws", Duration.ofHours(1));
        Article translationMatched = saveNews("Hospital hit by extortion gang", Duration.ofHours(2));
        applyAiResult(translationMatched, "ivanti 장비를 쓰는 병원, 갈취 조직에 피해", null, List.of());
        Article subjectMatched = saveNews("보안 장비 업데이트 권고", Duration.ofHours(3));
        applyAiResult(subjectMatched, null, "IVANTI", List.of());
        Article keywordMatched = saveNews("원격 접속 장비 점검 안내", Duration.ofHours(4));
        applyAiResult(keywordMatched, null, null, List.of("VPN", "Ivanti"));
        saveNews("관련 없는 소식", Duration.ofHours(5));

        ArticleSearchResponse response = articleSearchService.search(" iVaNtI ", null);

        assertThat(response.searchMode()).isEqualTo(ArticleSearchMode.KEYWORD);
        assertThat(response.items()).extracting(ArticleResponse::id).containsExactly(
                titleMatched.getId(), translationMatched.getId(), subjectMatched.getId(), keywordMatched.getId());
    }

    @Test
    void matchesCveIdPartially() {
        Article cveArticle = saveVulnerability("CVE-2026-5555", Duration.ofHours(1));
        saveVulnerability("CVE-2026-7777", Duration.ofHours(2));

        assertThat(articleSearchService.search("cve-2026-55", null).items())
                .extracting(ArticleResponse::id).containsExactly(cveArticle.getId());
        assertThat(articleSearchService.search("CVE-2026", null).items()).hasSize(2);
    }

    @Test
    void returnsNewestFirstUpToSize() {
        for (int hoursAgo = 1; hoursAgo <= 5; hoursAgo++) {
            saveNews("랜섬웨어 피해 사례 " + hoursAgo, Duration.ofHours(hoursAgo));
        }

        List<ArticleResponse> items = articleSearchService.search("랜섬웨어", 3).items();

        assertThat(items).extracting(ArticleResponse::title)
                .containsExactly("랜섬웨어 피해 사례 1", "랜섬웨어 피해 사례 2", "랜섬웨어 피해 사례 3");
    }

    @Test
    void treatsLikeSpecialCharactersAsPlainText() {
        Article percentArticle = saveNews("피해 기업 50% 증가", Duration.ofHours(1));
        Article underscoreArticle = saveNews("악성 패키지 node_ipc 발견", Duration.ofHours(2));
        Article backslashArticle = saveNews("경로 C:\\Windows\\Temp 악용", Duration.ofHours(3));
        saveNews("피해 기업 500곳 증가", Duration.ofHours(4));
        saveNews("악성 패키지 nodeXipc 발견", Duration.ofHours(5));

        assertThat(articleSearchService.search("50%", null).items())
                .extracting(ArticleResponse::id).containsExactly(percentArticle.getId());
        assertThat(articleSearchService.search("node_ipc", null).items())
                .extracting(ArticleResponse::id).containsExactly(underscoreArticle.getId());
        assertThat(articleSearchService.search("C:\\Windows", null).items())
                .extracting(ArticleResponse::id).containsExactly(backslashArticle.getId());
        assertThat(articleSearchService.search("%%", null).items()).isEmpty();
        assertThat(articleSearchService.search("__", null).items()).isEmpty();
    }

    @Test
    void returnsEmptyKeywordResultWhenNothingMatches() {
        saveNews("관련 없는 소식", Duration.ofHours(1));

        ArticleSearchResponse response = articleSearchService.search("양자 암호", null);

        assertThat(response.items()).isEmpty();
        assertThat(response.searchMode()).isEqualTo(ArticleSearchMode.KEYWORD);
    }

    private void applyAiResult(Article article, String translatedTitle, String subjectName, List<String> keywords) {
        article.applyAiAnalysis(ArticleTopic.GENERAL, translatedTitle, ArticleImportance.ROUTINE, subjectName);
        article.replaceKeywords(keywords);
        articleRepository.save(article);
    }

    private Article saveNews(String title, Duration publishedBefore) {
        Source source = sourceRepository.findByCode(SourceCode.THE_HACKER_NEWS).orElseThrow();
        Instant publishedAt = now.minus(publishedBefore);
        return articleRepository.save(Article.create(source, new ArticleDraft(ArticleKind.NEWS, ArticleRegion.OVERSEAS, title,
                toUniqueUrl(), source.getDisplayName(), publishedAt, false, null, null, null, false, false), publishedAt));
    }

    private Article saveVulnerability(String cveId, Duration publishedBefore) {
        Source source = sourceRepository.findByCode(SourceCode.NVD).orElseThrow();
        Instant publishedAt = now.minus(publishedBefore);
        return articleRepository.save(Article.create(source, new ArticleDraft(ArticleKind.VULNERABILITY, ArticleRegion.OVERSEAS,
                "Example product: remote code execution", toUniqueUrl(), "NVD", publishedAt, false,
                cveId, new BigDecimal("7.5"), "3.1", false, false), publishedAt));
    }

    private String toUniqueUrl() {
        savedCount++;
        return "https://example.test/search/" + System.nanoTime() + "-" + savedCount;
    }
}
