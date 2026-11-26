package kr.suhsaechan.secbrief.web.notification;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import kr.suhsaechan.secbrief.advisory.constant.ArticleKind;
import kr.suhsaechan.secbrief.advisory.constant.ArticleRegion;
import kr.suhsaechan.secbrief.advisory.constant.ArticleTopic;
import kr.suhsaechan.secbrief.advisory.constant.ArticleImportance;
import kr.suhsaechan.secbrief.advisory.constant.SourceCode;
import kr.suhsaechan.secbrief.advisory.dto.ArticleResponse;
import kr.suhsaechan.secbrief.advisory.entity.Article;
import kr.suhsaechan.secbrief.advisory.entity.Source;
import kr.suhsaechan.secbrief.advisory.policy.ArticleDraft;
import kr.suhsaechan.secbrief.advisory.repository.ArticleRepository;
import kr.suhsaechan.secbrief.advisory.repository.SourceRepository;
import kr.suhsaechan.secbrief.advisory.summary.ArticleBrief;
import kr.suhsaechan.secbrief.application.notification.inbox.NotificationArticleGuideResponse;
import kr.suhsaechan.secbrief.application.notification.inbox.NotificationArticleSectionResponse;
import kr.suhsaechan.secbrief.application.notification.inbox.NotificationDetailResponse;
import kr.suhsaechan.secbrief.application.notification.inbox.NotificationPageResponse;
import kr.suhsaechan.secbrief.application.notification.inbox.NotificationQueryService;
import kr.suhsaechan.secbrief.application.notification.inbox.NotificationSummaryResponse;
import kr.suhsaechan.secbrief.common.exception.CustomException;
import kr.suhsaechan.secbrief.member.constant.AlertCategory;
import kr.suhsaechan.secbrief.member.constant.NotificationKind;
import kr.suhsaechan.secbrief.member.entity.Notification;
import kr.suhsaechan.secbrief.member.entity.NotificationArticle;
import kr.suhsaechan.secbrief.member.repository.NotificationRepository;
import kr.suhsaechan.secbrief.web.support.PostgresContainerTestSupport;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

/**
 * 알림함 조회 (DB): 커서로 끝까지(빠짐·겹침 없음), 공지는 고정 먼저, 대표 기사 제목, 상세의 종류별 묶음(지운 기사 뺌), 기사별 대응 안내 요약, 최근 번호
 */
@SpringBootTest
@ActiveProfiles("test")
class NotificationQueryTest extends PostgresContainerTestSupport {

    private static final Instant BASE_TIME = Instant.parse("2026-11-20T00:00:00Z");

    @Autowired
    private NotificationQueryService notificationQueryService;
    @Autowired
    private NotificationRepository notificationRepository;
    @Autowired
    private ArticleRepository articleRepository;
    @Autowired
    private SourceRepository sourceRepository;

    private Source nvdSource;

    @BeforeEach
    void clearTables() {
        notificationRepository.deleteAll();
        articleRepository.deleteAll();
        nvdSource = sourceRepository.findByCode(SourceCode.NVD).orElseThrow();
    }

    @Test
    void pagesAlertsNewestFirstWithoutGapsOrDuplicates() {
        Article article = saveArticle("CVE-2026-8101: Example flaw");
        // 같은 시각 2건 포함 (번호로 순서 정함)
        for (int index = 0; index < 5; index++) {
            saveAlert(article, BASE_TIME.plus(Duration.ofMinutes(index == 4 ? 3 : index)));
        }

        NotificationPageResponse firstPage = notificationQueryService.findNotifications(NotificationKind.ALERT, null, 2);
        NotificationPageResponse secondPage = notificationQueryService.findNotifications(NotificationKind.ALERT, firstPage.nextCursor(), 2);
        NotificationPageResponse lastPage = notificationQueryService.findNotifications(NotificationKind.ALERT, secondPage.nextCursor(), 2);

        List<Long> pagedIds = java.util.stream.Stream.of(firstPage, secondPage, lastPage)
                .flatMap(page -> page.items().stream()).map(NotificationSummaryResponse::id).toList();
        List<Long> expectedIds = notificationRepository.findAll().stream()
                .sorted(java.util.Comparator.comparing(Notification::getCreatedAt).thenComparing(Notification::getId).reversed())
                .map(Notification::getId).toList();
        assertThat(pagedIds).containsExactlyElementsOf(expectedIds);
        assertThat(lastPage.nextCursor()).isNull();
        assertThat(firstPage.items().get(0).leadArticleTitle()).isEqualTo("CVE-2026-8101: Example flaw");
        assertThat(firstPage.items().get(0).alertCategories()).containsExactly(AlertCategory.CRITICAL_CVE);
    }

    @Test
    void listsPinnedNoticesFirstAcrossPages() {
        Notification oldPinned = saveNotice("이용 안내", true, BASE_TIME);
        Notification newest = saveNotice("새 기능 소개", false, BASE_TIME.plus(Duration.ofDays(3)));
        Notification middle = saveNotice("점검 안내", false, BASE_TIME.plus(Duration.ofDays(2)));
        Notification newPinned = saveNotice("개인정보 처리방침 안내", true, BASE_TIME.plus(Duration.ofDays(1)));

        NotificationPageResponse firstPage = notificationQueryService.findNotifications(NotificationKind.NOTICE, null, 3);
        NotificationPageResponse secondPage = notificationQueryService.findNotifications(NotificationKind.NOTICE, firstPage.nextCursor(), 3);

        assertThat(firstPage.items()).extracting(NotificationSummaryResponse::id)
                .containsExactly(newPinned.getId(), oldPinned.getId(), newest.getId());
        assertThat(secondPage.items()).extracting(NotificationSummaryResponse::id).containsExactly(middle.getId());
        assertThat(firstPage.items().get(0).isPinned()).isTrue();
        assertThat(firstPage.items().get(0).alertCategories()).isEmpty();
    }

    @Test
    void groupsDetailArticlesByCategoryAndSkipsDeletedArticles() {
        Article kevArticle = saveArticle("CVE-2026-8201: Exploited flaw");
        Article criticalArticle = saveArticle("CVE-2026-8202: Critical flaw");
        Article deletedArticle = saveArticle("CVE-2026-8203: Removed flaw");
        Notification alert = notificationRepository.save(Notification.createAlert(AlertCategory.KEV,
                "긴급 보안 소식 3건이 들어왔어요", "CVE-2026-8201: Exploited flaw 외 2건", List.of(
                        new NotificationArticle(0, kevArticle.getId(), AlertCategory.KEV),
                        new NotificationArticle(1, criticalArticle.getId(), AlertCategory.CRITICAL_CVE),
                        new NotificationArticle(2, deletedArticle.getId(), AlertCategory.CRITICAL_CVE)), BASE_TIME));
        articleRepository.delete(deletedArticle);

        NotificationDetailResponse detail = notificationQueryService.findNotification(alert.getId());

        assertThat(detail.articleSections()).extracting(NotificationArticleSectionResponse::alertCategory)
                .containsExactly(AlertCategory.KEV, AlertCategory.CRITICAL_CVE);
        assertThat(detail.articleSections().get(1).articles()).extracting(ArticleResponse::id)
                .containsExactly(criticalArticle.getId());
        assertThat(detail.leadArticleId()).isEqualTo(kevArticle.getId());
        assertThatThrownBy(() -> notificationQueryService.findNotification(alert.getId() + 1000))
                .isInstanceOf(CustomException.class);
    }

    @Test
    void attachesResponseGuideSummaryWithBriefActionsPerAlertedArticle() {
        Article alertedArticle = saveArticle("CVE-2026-8401: Example Gateway flaw");
        alertedArticle.getSummaryState().applyBrief(new ArticleBrief("Example Gateway 취약점이 공개됐어요.",
                "Example Gateway에 원격 코드 실행 취약점이 있어요.", null, List.of(), List.of("3.2.1로 올리세요.")));
        articleRepository.save(alertedArticle);
        Article articleWithoutGuide = saveArticle("CVE-2026-8402: Unrelated flaw");
        Source kisaVulnerabilitySource = sourceRepository.findByCode(SourceCode.KISA_VULNERABILITY).orElseThrow();
        Article kisaNotice = articleRepository.save(Article.create(kisaVulnerabilitySource, new ArticleDraft(
                ArticleKind.ADVISORY, ArticleRegion.DOMESTIC, "CVE-2026-8401 | Example Gateway 원격 코드 실행",
                "https://example.test/kisa/8401", "KISA", Instant.now(), false, null, null, null, false, false), Instant.now()));
        Notification alert = notificationRepository.save(Notification.createAlert(AlertCategory.CRITICAL_CVE,
                "고위험 취약점 2건이 공개됐어요", "CVE-2026-8401: Example Gateway flaw 외 1건", List.of(
                        new NotificationArticle(0, alertedArticle.getId(), AlertCategory.CRITICAL_CVE),
                        new NotificationArticle(1, articleWithoutGuide.getId(), AlertCategory.CRITICAL_CVE)), BASE_TIME));

        NotificationDetailResponse detail = notificationQueryService.findNotification(alert.getId());

        assertThat(detail.articleGuides()).extracting(NotificationArticleGuideResponse::articleId)
                .containsExactly(alertedArticle.getId(), articleWithoutGuide.getId());
        NotificationArticleGuideResponse guide = detail.articleGuides().get(0);
        assertThat(guide.actions()).containsExactly("3.2.1로 올리세요.");
        assertThat(guide.officialCount()).isEqualTo(1);
        assertThat(guide.factCount()).isZero();
        assertThat(guide.leadOfficialItem().article().id()).isEqualTo(kisaNotice.getId());
        NotificationArticleGuideResponse emptyGuide = detail.articleGuides().get(1);
        assertThat(emptyGuide.actions()).isEmpty();
        assertThat(emptyGuide.officialCount() + emptyGuide.factCount() + emptyGuide.newsCount()).isZero();
        assertThat(emptyGuide.leadOfficialItem()).isNull();
    }

    @Test
    void returnsNoGuidesForNotice() {
        Notification notice = saveNotice("서비스 점검 안내", false, BASE_TIME);

        assertThat(notificationQueryService.findNotification(notice.getId()).articleGuides()).isEmpty();
    }

    @Test
    void returnsLatestIdsPerKind() {
        assertThat(notificationQueryService.findLatestIds().latestAlertId()).isNull();
        Article article = saveArticle("CVE-2026-8301: Example flaw");
        Notification alert = saveAlert(article, BASE_TIME);
        Notification notice = saveNotice("서비스 점검 안내", false, BASE_TIME);

        assertThat(notificationQueryService.findLatestIds().latestAlertId()).isEqualTo(alert.getId());
        assertThat(notificationQueryService.findLatestIds().latestNoticeId()).isEqualTo(notice.getId());
    }

    private Notification saveAlert(Article article, Instant createdAt) {
        return notificationRepository.save(Notification.createAlert(AlertCategory.CRITICAL_CVE, "고위험 취약점이 공개됐어요",
                article.getTitle(), List.of(new NotificationArticle(0, article.getId(), AlertCategory.CRITICAL_CVE)), createdAt));
    }

    private Notification saveNotice(String title, boolean isPinned, Instant createdAt) {
        return notificationRepository.save(Notification.createNotice(title, "본문\n둘째 줄", null, isPinned, createdAt));
    }

    private Article saveArticle(String title) {
        Instant collectedAt = Instant.now();
        String cveId = title.substring(0, title.indexOf(':'));
        Article article = Article.create(nvdSource, new ArticleDraft(ArticleKind.VULNERABILITY, ArticleRegion.OVERSEAS,
                title, "https://example.test/cve/" + cveId, "NVD", collectedAt, false, cveId, new BigDecimal("9.8"),
                "3.1", false, false), collectedAt);
        article.applyAiAnalysis(ArticleTopic.VULNERABILITY, null, ArticleImportance.NOTABLE, null);
        return articleRepository.save(article);
    }
}
