package kr.suhsaechan.secbrief.web.notification;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import kr.suhsaechan.secbrief.advisory.classification.ArticleClassificationService;
import kr.suhsaechan.secbrief.advisory.constant.ArticleImportance;
import kr.suhsaechan.secbrief.advisory.constant.ArticleKind;
import kr.suhsaechan.secbrief.advisory.constant.ArticleRegion;
import kr.suhsaechan.secbrief.advisory.constant.ArticleTopic;
import kr.suhsaechan.secbrief.advisory.constant.SourceCode;
import kr.suhsaechan.secbrief.advisory.entity.Article;
import kr.suhsaechan.secbrief.advisory.entity.Source;
import kr.suhsaechan.secbrief.advisory.policy.ArticleDraft;
import kr.suhsaechan.secbrief.advisory.repository.ArticleRepository;
import kr.suhsaechan.secbrief.advisory.repository.SourceRepository;
import kr.suhsaechan.secbrief.application.notification.NotificationRetentionService;
import kr.suhsaechan.secbrief.application.notification.QuietHoursDigestService;
import kr.suhsaechan.secbrief.application.notification.UrgentAlertDispatchService;
import kr.suhsaechan.secbrief.member.constant.AlertCategory;
import kr.suhsaechan.secbrief.member.constant.NotificationKind;
import kr.suhsaechan.secbrief.member.constant.PushPlatform;
import kr.suhsaechan.secbrief.member.dto.PushDeviceRegisterRequest;
import kr.suhsaechan.secbrief.member.dto.QuietHoursRequest;
import kr.suhsaechan.secbrief.member.entity.Notification;
import kr.suhsaechan.secbrief.member.entity.NotificationArticle;
import kr.suhsaechan.secbrief.member.push.PushMessage;
import kr.suhsaechan.secbrief.member.push.PushSendOutcome;
import kr.suhsaechan.secbrief.member.push.PushSender;
import kr.suhsaechan.secbrief.member.repository.HeldPushRepository;
import kr.suhsaechan.secbrief.member.repository.NotificationRepository;
import kr.suhsaechan.secbrief.member.repository.PushDeviceRepository;
import kr.suhsaechan.secbrief.member.repository.PushTicketRepository;
import kr.suhsaechan.secbrief.member.service.PushDeviceService;
import kr.suhsaechan.secbrief.web.support.PostgresContainerTestSupport;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * 알림함 기록과 방해 금지 시간 (DB): 발송마다 기록(순서·종류), 푸시 data 키, 전부 실패 시 기록 취소,
 * 방해 금지 기기는 보류 > 끝나면 1건 그대로 또는 모음 1건, 실패하면 보류 유지, 30일 보관 정리 (발송기는 가짜)
 * 방해 금지 시간은 실제 지금 시각 기준 앞뒤 1시간으로 정함 (UTC 기기)
 */
@SpringBootTest
@ActiveProfiles("test")
class NotificationRecordDispatchTest extends PostgresContainerTestSupport {

    private static final String QUIET_TOKEN = "ExponentPushToken[quiet-device]";
    private static final String AWAKE_TOKEN = "ExponentPushToken[awake-device]";
    private static final String UTC_ZONE = "UTC";

    @Autowired
    private UrgentAlertDispatchService urgentAlertDispatchService;
    @Autowired
    private QuietHoursDigestService quietHoursDigestService;
    @Autowired
    private NotificationRetentionService notificationRetentionService;
    @Autowired
    private PushDeviceService pushDeviceService;
    @Autowired
    private PushDeviceRepository pushDeviceRepository;
    @Autowired
    private PushTicketRepository pushTicketRepository;
    @Autowired
    private HeldPushRepository heldPushRepository;
    @Autowired
    private NotificationRepository notificationRepository;
    @Autowired
    private ArticleRepository articleRepository;
    @Autowired
    private SourceRepository sourceRepository;
    @Autowired
    private TransactionTemplate transactionTemplate;
    @Autowired
    private ArticleClassificationService articleClassificationService;
    @MockitoBean
    private PushSender pushSender;

    private Source nvdSource;
    private Source kevSource;

    @BeforeEach
    void clearTablesAndEnableSender() {
        pushTicketRepository.deleteAll();
        pushDeviceRepository.deleteAll();
        notificationRepository.deleteAll();
        articleRepository.deleteAll();
        nvdSource = sourceRepository.findByCode(SourceCode.NVD).orElseThrow();
        kevSource = sourceRepository.findByCode(SourceCode.CISA_KEV).orElseThrow();
        given(pushSender.isAvailable()).willReturn(true);
        given(pushSender.send(anyList())).willAnswer(invocation -> {
            List<PushMessage> pushMessages = invocation.getArgument(0);
            return pushMessages.stream()
                    .map(pushMessage -> PushSendOutcome.accepted(pushMessage.pushToken(), "ticket-" + pushMessage.pushToken()))
                    .toList();
        });
    }

    @Test
    void recordsEveryDispatchWithOrderedArticlesAndSendsNotificationIdInPushData() {
        register(AWAKE_TOKEN, Set.of(AlertCategory.KEV, AlertCategory.CRITICAL_CVE), awakeWindow());
        Article criticalArticle = saveUrgentArticle(nvdSource, "CVE-2026-9101", false);
        Article kevArticle = saveUrgentArticle(kevSource, "CVE-2026-9102", true);

        urgentAlertDispatchService.dispatchPendingAlerts();

        Notification notification = findOnlyNotification();
        assertThat(notification.getKind()).isEqualTo(NotificationKind.ALERT);
        assertThat(notification.getAlertCategory()).isEqualTo(AlertCategory.KEV);
        assertThat(notification.getTitle()).isEqualTo("긴급 보안 소식 2건이 들어왔어요");
        assertThat(notification.getPushedAt()).isNotNull();
        // 알림 순서: 악용 중 먼저, 기사마다 종류
        assertThat(findArticles(notification)).extracting(NotificationArticle::getArticleId, NotificationArticle::getAlertCategory)
                .containsExactly(tuple(kevArticle.getId(), AlertCategory.KEV),
                        tuple(criticalArticle.getId(), AlertCategory.CRITICAL_CVE));

        PushMessage pushMessage = captureSentMessagesByToken().get(AWAKE_TOKEN);
        assertThat(pushMessage.data()).containsEntry("notificationId", String.valueOf(notification.getId()))
                .containsEntry("notificationKind", "ALERT").containsEntry("targetTab", "vulnerability")
                .doesNotContainKey("articleId");
    }

    @Test
    void keepsArticleIdForSingleArticleAlert() {
        register(AWAKE_TOKEN, Set.of(AlertCategory.KEV), awakeWindow());
        Article kevArticle = saveUrgentArticle(kevSource, "CVE-2026-9201", true);

        urgentAlertDispatchService.dispatchPendingAlerts();

        assertThat(captureSentMessagesByToken().get(AWAKE_TOKEN).data())
                .containsEntry("articleId", String.valueOf(kevArticle.getId()))
                .containsEntry("notificationId", String.valueOf(findOnlyNotification().getId()))
                .doesNotContainKey("targetScreen");
    }

    @Test
    void opensAlertLevelScreenForSingleKisaAlertLevelArticleAlsoAfterQuietHours() {
        // 푸시로 연 KISA 경보단계 글은 관제판으로 (#87): articleId 는 이전 앱용으로 그대로
        register(AWAKE_TOKEN, Set.of(AlertCategory.KISA_URGENT), awakeWindow());
        register(QUIET_TOKEN, Set.of(AlertCategory.KISA_URGENT), quietWindow());
        Article alertLevelArticle = saveUrgentArticle(sourceRepository.findByCode(SourceCode.KISA_ALERT_LEVEL).orElseThrow(),
                "CVE-2026-9250", false);

        urgentAlertDispatchService.dispatchPendingAlerts();

        assertThat(captureSentMessagesByToken().get(AWAKE_TOKEN).data())
                .containsEntry("articleId", String.valueOf(alertLevelArticle.getId()))
                .containsEntry("targetScreen", "alertLevel");
        // 보류분에는 출처가 없어도 보낼 때 다시 판정
        clearInvocations(pushSender);
        register(QUIET_TOKEN, Set.of(AlertCategory.KISA_URGENT), awakeWindow());
        assertThat(quietHoursDigestService.sendHeldPushesAfterQuietHours()).isEqualTo(1);
        assertThat(captureSentMessagesByToken().get(QUIET_TOKEN).data())
                .containsEntry("articleId", String.valueOf(alertLevelArticle.getId()))
                .containsEntry("targetScreen", "alertLevel");
    }

    @Test
    void recordsAlertForInboxEvenWithoutDevicesAndCancelsRecordWhenEverySendFails() {
        saveUrgentArticle(nvdSource, "CVE-2026-9301", false);
        urgentAlertDispatchService.dispatchPendingAlerts();
        // 기기가 없어도 알림함에는 남음 (푸시는 안 보냄)
        assertThat(findOnlyNotification().getPushedAt()).isNull();

        notificationRepository.deleteAll();
        register(AWAKE_TOKEN, Set.of(AlertCategory.CRITICAL_CVE), awakeWindow());
        Article pendingArticle = saveUrgentArticle(nvdSource, "CVE-2026-9302", false);
        given(pushSender.send(anyList())).willReturn(List.of(PushSendOutcome.failed(AWAKE_TOKEN)));

        assertThat(urgentAlertDispatchService.dispatchPendingAlerts().isRetryPending()).isTrue();
        // 다음 틱에 새 기록으로 다시 (기록이 두 번 쌓이지 않게)
        assertThat(notificationRepository.count()).isZero();
        assertThat(articleRepository.findById(pendingArticle.getId()).orElseThrow().getUrgentNotifiedAt()).isNull();
    }

    @Test
    void holdsPushDuringQuietHoursAndSendsHeldAlertAsIsWhenWindowEnds() {
        register(QUIET_TOKEN, Set.of(AlertCategory.KEV), quietWindow());
        register(AWAKE_TOKEN, Set.of(AlertCategory.KEV), awakeWindow());
        Article kevArticle = saveUrgentArticle(kevSource, "CVE-2026-9401", true);

        urgentAlertDispatchService.dispatchPendingAlerts();

        // KEV 도 예외 없이 보류, 깨어 있는 기기만 받음
        assertThat(captureSentMessagesByToken()).containsOnlyKeys(AWAKE_TOKEN);
        assertThat(heldPushRepository.findAll()).singleElement().satisfies(heldPush -> {
            assertThat(heldPush.getArticleId()).isEqualTo(kevArticle.getId());
            assertThat(heldPush.getTitle()).isEqualTo("악용 중인 취약점이 확인됐어요");
        });
        // 아직 방해 금지 시간: 보내지 않음
        clearInvocations(pushSender);
        assertThat(quietHoursDigestService.sendHeldPushesAfterQuietHours()).isZero();
        verify(pushSender, never()).send(anyList());

        // 시간이 끝남 (설정을 지금 밖의 구간으로) > 1건이라 그대로
        register(QUIET_TOKEN, Set.of(AlertCategory.KEV), awakeWindow());
        assertThat(quietHoursDigestService.sendHeldPushesAfterQuietHours()).isEqualTo(1);
        PushMessage heldMessage = captureSentMessagesByToken().get(QUIET_TOKEN);
        assertThat(heldMessage.title()).isEqualTo("악용 중인 취약점이 확인됐어요");
        assertThat(heldMessage.data()).containsEntry("articleId", String.valueOf(kevArticle.getId()));
        assertThat(heldPushRepository.count()).isZero();
    }

    @Test
    void sendsOneDigestForSeveralHeldAlertsAndKeepsThemWhenSendFails() {
        register(QUIET_TOKEN, Set.of(AlertCategory.KEV, AlertCategory.CRITICAL_CVE), quietWindow());
        saveUrgentArticle(kevSource, "CVE-2026-9501", true);
        urgentAlertDispatchService.dispatchPendingAlerts();
        saveUrgentArticle(nvdSource, "CVE-2026-9502", false);
        saveUrgentArticle(nvdSource, "CVE-2026-9503", false);
        urgentAlertDispatchService.dispatchPendingAlerts();
        assertThat(heldPushRepository.count()).isEqualTo(2);
        // 모두 보류라 알림함 기록의 발송 시각은 비어 있음
        assertThat(notificationRepository.findAll()).allSatisfy(notification -> assertThat(notification.getPushedAt()).isNull());

        register(QUIET_TOKEN, Set.of(AlertCategory.KEV, AlertCategory.CRITICAL_CVE), awakeWindow());
        given(pushSender.send(anyList())).willReturn(List.of(PushSendOutcome.failed(QUIET_TOKEN)));
        assertThat(quietHoursDigestService.sendHeldPushesAfterQuietHours()).isZero();
        assertThat(heldPushRepository.count()).isEqualTo(2);

        given(pushSender.send(anyList())).willAnswer(invocation -> List.of(PushSendOutcome.accepted(QUIET_TOKEN, "ticket-digest")));
        clearInvocations(pushSender);
        assertThat(quietHoursDigestService.sendHeldPushesAfterQuietHours()).isEqualTo(1);
        PushMessage digestMessage = captureSentMessagesByToken().get(QUIET_TOKEN);
        assertThat(digestMessage.title()).isEqualTo("방해 금지 시간에 긴급 소식 3건이 들어왔어요");
        assertThat(digestMessage.data()).containsEntry("notificationKind", "QUIET_HOURS_DIGEST");
        assertThat(heldPushRepository.count()).isZero();
        assertThat(notificationRepository.findAll()).allSatisfy(notification -> assertThat(notification.getPushedAt()).isNotNull());
    }

    @Test
    void deletesAlertsOlderThanThirtyDaysButKeepsNotices() {
        Article article = saveUrgentArticle(nvdSource, "CVE-2026-9601", false);
        Instant now = Instant.now();
        Notification expiredAlert = saveAlert(article, now.minus(Duration.ofDays(31)));
        Notification recentAlert = saveAlert(article, now.minus(Duration.ofDays(29)));
        Notification oldNotice = notificationRepository.save(
                Notification.createNotice("서비스 점검 안내", "본문", null, false, now.minus(Duration.ofDays(90))));

        assertThat(notificationRetentionService.deleteExpiredAlerts()).isEqualTo(1);

        assertThat(notificationRepository.findAll()).extracting(Notification::getId)
                .containsExactlyInAnyOrder(recentAlert.getId(), oldNotice.getId())
                .doesNotContain(expiredAlert.getId());
    }

    private Notification saveAlert(Article article, Instant createdAt) {
        return notificationRepository.save(Notification.createAlert(AlertCategory.CRITICAL_CVE, "고위험 취약점이 공개됐어요",
                article.getTitle(), List.of(new NotificationArticle(0, article.getId(), AlertCategory.CRITICAL_CVE)), createdAt));
    }

    private Notification findOnlyNotification() {
        List<Notification> notifications = notificationRepository.findAll();
        assertThat(notifications).hasSize(1);
        return notifications.get(0);
    }

    // 묶인 기사는 지연 로딩이라 트랜잭션 안에서 읽음
    private List<NotificationArticle> findArticles(Notification notification) {
        return transactionTemplate.execute(status ->
                List.copyOf(notificationRepository.findById(notification.getId()).orElseThrow().getArticles()));
    }

    private void register(String pushToken, Set<AlertCategory> enabledAlertCategories, QuietHoursRequest quietHours) {
        pushDeviceService.register(new PushDeviceRegisterRequest(pushToken, PushPlatform.ANDROID, enabledAlertCategories, quietHours));
    }

    // 지금(UTC) 앞뒤 1시간: 방해 금지 시간 안
    private static QuietHoursRequest quietWindow() {
        LocalTime nowTime = LocalTime.now(ZoneOffset.UTC);
        return new QuietHoursRequest(true, nowTime.minusHours(1), nowTime.plusHours(1), UTC_ZONE);
    }

    // 지금(UTC) 2~3시간 뒤: 방해 금지 시간 밖 (켜져 있음)
    private static QuietHoursRequest awakeWindow() {
        LocalTime nowTime = LocalTime.now(ZoneOffset.UTC);
        return new QuietHoursRequest(true, nowTime.plusHours(2), nowTime.plusHours(3), UTC_ZONE);
    }

    private Map<String, PushMessage> captureSentMessagesByToken() {
        ArgumentCaptor<List<PushMessage>> sentMessages = ArgumentCaptor.captor();
        verify(pushSender, atLeastOnce()).send(sentMessages.capture());
        return sentMessages.getAllValues().stream().flatMap(List::stream)
                .collect(Collectors.toMap(PushMessage::pushToken, pushMessage -> pushMessage, (first, last) -> last));
    }

    private Article saveUrgentArticle(Source source, String cveId, boolean isKev) {
        Instant collectedAt = Instant.now();
        Article article = Article.create(source, new ArticleDraft(ArticleKind.VULNERABILITY, ArticleRegion.OVERSEAS,
                cveId + ": Example flaw", "https://example.test/cve/" + cveId, source.getDisplayName(), collectedAt,
                false, cveId, isKev ? null : new BigDecimal("9.8"), isKev ? null : "3.1", isKev, true), collectedAt);
        article.applyAiAnalysis(ArticleTopic.VULNERABILITY, null, ArticleImportance.NOTABLE, null);
        articleClassificationService.classify(article);
        return articleRepository.save(article);
    }
}
