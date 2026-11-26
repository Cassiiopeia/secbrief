package kr.suhsaechan.secbrief.web.notification;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalTime;
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
import kr.suhsaechan.secbrief.advisory.fact.SourceFact;
import kr.suhsaechan.secbrief.advisory.fact.SourceFactLabel;
import kr.suhsaechan.secbrief.advisory.policy.ArticleDraft;
import kr.suhsaechan.secbrief.advisory.repository.ArticleRepository;
import kr.suhsaechan.secbrief.advisory.repository.SourceRepository;
import kr.suhsaechan.secbrief.application.notification.MorningDigestService;
import kr.suhsaechan.secbrief.application.notification.UrgentAlertDispatchService;
import kr.suhsaechan.secbrief.application.notification.inbox.NotificationDetailResponse;
import kr.suhsaechan.secbrief.application.notification.inbox.NotificationQueryService;
import kr.suhsaechan.secbrief.member.constant.AlertCategory;
import kr.suhsaechan.secbrief.member.constant.AlertDeliveryMode;
import kr.suhsaechan.secbrief.member.constant.NotificationKind;
import kr.suhsaechan.secbrief.member.constant.PushPlatform;
import kr.suhsaechan.secbrief.member.dto.DigestSettingsRequest;
import kr.suhsaechan.secbrief.member.dto.InterestsRequest;
import kr.suhsaechan.secbrief.member.dto.PushDeviceRegisterRequest;
import kr.suhsaechan.secbrief.member.dto.QuietHoursRequest;
import kr.suhsaechan.secbrief.member.push.PushMessage;
import kr.suhsaechan.secbrief.member.push.PushSendOutcome;
import kr.suhsaechan.secbrief.member.push.PushSender;
import kr.suhsaechan.secbrief.member.repository.DigestItemRepository;
import kr.suhsaechan.secbrief.member.repository.NotificationRepository;
import kr.suhsaechan.secbrief.member.repository.PushDeviceRepository;
import kr.suhsaechan.secbrief.member.service.PushDeviceService;
import kr.suhsaechan.secbrief.web.support.PostgresContainerTestSupport;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.Limit;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

/**
 * 관심 기술 알림 흐름 (DB, 발송기는 가짜): 관심 제품은 긴급이면 바로·아니면 아침 요약, 관심 밖 고위험은 안 알림,
 * 아침 요약은 요약 시각에 기기 전용 DIGEST 1건 (하루 1번, 알림함 목록에 안 보임), AI 판단 전에는 보내지 않음 (#97)
 */
@SpringBootTest
@ActiveProfiles("test")
class InterestAlertFlowTest extends PostgresContainerTestSupport {

    private static final String DIGEST_TOKEN = "ExponentPushToken[fortinet-digest]";
    private static final String ESSENTIALS_TOKEN = "ExponentPushToken[essentials-only]";

    @Autowired
    private UrgentAlertDispatchService urgentAlertDispatchService;
    @Autowired
    private MorningDigestService morningDigestService;
    @Autowired
    private NotificationQueryService notificationQueryService;
    @Autowired
    private PushDeviceService pushDeviceService;
    @Autowired
    private ArticleClassificationService articleClassificationService;
    @Autowired
    private PushDeviceRepository pushDeviceRepository;
    @Autowired
    private DigestItemRepository digestItemRepository;
    @Autowired
    private NotificationRepository notificationRepository;
    @Autowired
    private ArticleRepository articleRepository;
    @Autowired
    private SourceRepository sourceRepository;
    @MockitoBean
    private PushSender pushSender;

    private Source nvdSource;
    private Source ghsaSource;

    @BeforeEach
    void clearTablesAndAcceptEverySend() {
        notificationRepository.deleteAll();
        pushDeviceRepository.deleteAll();
        articleRepository.deleteAll();
        nvdSource = sourceRepository.findByCode(SourceCode.NVD).orElseThrow();
        ghsaSource = sourceRepository.findByCode(SourceCode.GHSA_HIGH).orElseThrow();
        given(pushSender.isAvailable()).willReturn(true);
        given(pushSender.send(anyList())).willAnswer(invocation -> {
            List<PushMessage> pushMessages = invocation.getArgument(0);
            return pushMessages.stream()
                    .map(pushMessage -> PushSendOutcome.accepted(pushMessage.pushToken(), "ticket-" + System.nanoTime()))
                    .toList();
        });
    }

    @Test
    void routesInterestMatchesToImmediateOrMorningDigestAndSkipsUnrelatedCritical() {
        // 기기 2대: Fortinet 관심 + 아침 요약(요약 시각 00:00 이라 지금 보낼 때) / 관심 없음(꼭 필요한 것만)
        registerDevice(DIGEST_TOKEN, Set.of(AlertCategory.KEV, AlertCategory.KISA_URGENT),
                new InterestsRequest(Set.of("fortinet"), Set.of(), Set.of("maven")), AlertDeliveryMode.DIGEST);
        registerDevice(ESSENTIALS_TOKEN, Set.of(AlertCategory.KEV, AlertCategory.KISA_URGENT), null, AlertDeliveryMode.DIGEST);
        Article fortinetCritical = saveJudgedArticle(nvdSource, "CVE-2026-9101: Fortinet FortiOS SSL-VPN heap overflow", "9.8",
                ArticleImportance.NOTABLE, List.of());
        Article fortinetHigh = saveJudgedArticle(ghsaSource, "FortiClient EMS: SQL injection in device inventory", "8.1",
                ArticleImportance.ROUTINE, List.of());
        Article mavenNotable = saveJudgedArticle(ghsaSource, "Gradle: repository disable failure exposes build", "8.6",
                ArticleImportance.NOTABLE, List.of(new SourceFact(SourceFactLabel.AFFECTED_PACKAGE, "org.gradle:gradle (maven)")));
        Article unrelatedPlugin = saveJudgedArticle(nvdSource, "CVE-2026-9104: WordPress File Upload plugin arbitrary delete",
                "9.1", ArticleImportance.ROUTINE, List.of());

        urgentAlertDispatchService.dispatchPendingAlerts();

        // 관심 Fortinet 긴급 1건만 바로 (관심 밖 기기·관심 밖 플러그인은 알림 없음)
        Map<String, PushMessage> immediateByToken = captureSentMessagesByToken();
        assertThat(immediateByToken).containsOnlyKeys(DIGEST_TOKEN);
        assertThat(immediateByToken.get(DIGEST_TOKEN).title()).isEqualTo("관심 기술 소식이 들어왔어요");
        assertThat(immediateByToken.get(DIGEST_TOKEN).data()).containsEntry("articleId", String.valueOf(fortinetCritical.getId()));
        // 긴급 아닌 Fortinet 고위험·Maven 주요 패키지는 아침 요약 대기, 모든 후보는 처리됨
        assertThat(digestItemRepository.findAllInQueueOrder()).extracting(item -> item.getId().getArticleId())
                .containsExactlyInAnyOrder(fortinetHigh.getId(), mavenNotable.getId());
        assertThat(articleRepository.findAllById(List.of(unrelatedPlugin.getId(), fortinetHigh.getId())))
                .allSatisfy(article -> assertThat(article.getUrgentNotifiedAt()).isNotNull());

        // 아침 요약 1건: 기기 전용 DIGEST 기록, 알림함 목록에는 없음
        clearInvocations(pushSender);
        assertThat(morningDigestService.sendDueDigests()).isEqualTo(1);
        PushMessage digestMessage = captureSentMessagesByToken().get(DIGEST_TOKEN);
        assertThat(digestMessage.title()).isEqualTo("오늘 아침 요약: 관심 분야 소식 2건");
        assertThat(digestMessage.data()).containsEntry("notificationKind", "DIGEST").doesNotContainKey("articleId");
        long digestId = Long.parseLong(digestMessage.data().get("notificationId"));
        assertThat(digestItemRepository.count()).isZero();
        assertThat(notificationQueryService.findNotifications(NotificationKind.ALERT, null, null).items())
                .noneMatch(summary -> summary.id() == digestId);

        NotificationDetailResponse digestDetail = notificationQueryService.findNotification(digestId);
        assertThat(digestDetail.kind()).isEqualTo(NotificationKind.DIGEST);
        assertThat(digestDetail.digest().articleCount()).isEqualTo(2);
        // 꼭 볼 것: 널리 쓰는 제품(Fortinet, Gradle)이라 둘 다 주요
        assertThat(digestDetail.digest().notableArticles()).extracting(article -> article.id())
                .containsExactlyInAnyOrder(fortinetHigh.getId(), mavenNotable.getId());
        assertThat(digestDetail.digest().fieldSections()).extracting(section -> section.fieldCode())
                .containsExactlyInAnyOrder("network", "dev-tool");

        // 같은 날 다시 보내지 않음
        clearInvocations(pushSender);
        saveJudgedArticle(ghsaSource, "FortiWeb: path traversal in admin API", "7.5", ArticleImportance.ROUTINE, List.of());
        urgentAlertDispatchService.dispatchPendingAlerts();
        assertThat(morningDigestService.sendDueDigests()).isZero();
        verify(pushSender, never()).send(anyList());
    }

    @Test
    void sendsSeveralInterestOnlyArticlesAsOneDeviceRecordForImmediateMode() {
        registerDevice(DIGEST_TOKEN, Set.of(), new InterestsRequest(Set.of("fortinet", "chrome"), Set.of(), Set.of()),
                AlertDeliveryMode.IMMEDIATE);
        saveJudgedArticle(ghsaSource, "FortiClient EMS: SQL injection in device inventory", "8.1", ArticleImportance.ROUTINE,
                List.of());
        saveJudgedArticle(nvdSource, "CVE-2026-9201: Google Chrome V8 type confusion", "8.8", ArticleImportance.NOTABLE,
                List.of());

        urgentAlertDispatchService.dispatchPendingAlerts();

        PushMessage message = captureSentMessagesByToken().get(DIGEST_TOKEN);
        assertThat(message.title()).isEqualTo("관심 기술 소식 2건이 들어왔어요");
        long notificationId = Long.parseLong(message.data().get("notificationId"));
        assertThat(notificationRepository.findById(notificationId).orElseThrow().getPushDeviceId()).isNotNull();
        assertThat(notificationQueryService.findNotification(notificationId).alertCategories())
                .containsExactly(AlertCategory.INTEREST);
        // 켠 종류가 없어도 관심 기술이 있으면 기기를 지우지 않음
        assertThat(pushDeviceRepository.count()).isEqualTo(1);
    }

    @Test
    void holdsCandidatesUntilAiJudgesThenFallsBackToRulesAfterOneHour() {
        // #97: 방금 수집해 AI 판단 전인 고위험은 후보가 아님 > 60분 지나면 규칙만으로 (널리 쓰는 제품만 알림)
        registerDevice(ESSENTIALS_TOKEN, Set.of(AlertCategory.KEV, AlertCategory.CRITICAL_CVE), null, AlertDeliveryMode.DIGEST);
        Instant now = Instant.now();
        saveUnjudgedArticle("CVE-2026-9301: TOTOLINK N350RT command injection", now);

        assertThat(articleRepository.findAlertCandidates(now.minus(Article.AI_JUDGEMENT_HOLD_DURATION), Limit.of(10))).isEmpty();

        Article oldNiche = saveUnjudgedArticle("CVE-2026-9302: TOTOLINK A3002R stack overflow", now.minus(Duration.ofMinutes(61)));
        Article oldVmware = saveUnjudgedArticle("CVE-2026-9303: VMware vCenter Server authentication bypass",
                now.minus(Duration.ofMinutes(61)));
        articleClassificationService.classifyPendingBatch();

        urgentAlertDispatchService.dispatchPendingAlerts();

        Map<String, PushMessage> messageByToken = captureSentMessagesByToken();
        assertThat(messageByToken.get(ESSENTIALS_TOKEN).data()).containsEntry("articleId", String.valueOf(oldVmware.getId()));
        assertThat(articleRepository.findById(oldNiche.getId()).orElseThrow().getUrgentNotifiedAt()).isNotNull();
    }

    private void registerDevice(String pushToken, Set<AlertCategory> enabledAlertCategories, InterestsRequest interests,
                                AlertDeliveryMode deliveryMode) {
        pushDeviceService.register(new PushDeviceRegisterRequest(pushToken, PushPlatform.ANDROID, enabledAlertCategories,
                new QuietHoursRequest(false, LocalTime.of(22, 0), LocalTime.of(8, 0), "Asia/Seoul"), interests,
                new DigestSettingsRequest(deliveryMode, LocalTime.MIDNIGHT)));
    }

    // AI 판단 끝 + 분류된 기사 (CVSS 9.0 이상이면 긴급)
    private Article saveJudgedArticle(Source source, String title, String cvssScore, ArticleImportance importance,
                                      List<SourceFact> sourceFacts) {
        Instant now = Instant.now();
        BigDecimal score = new BigDecimal(cvssScore);
        boolean isUrgent = score.compareTo(new BigDecimal("9.0")) >= 0;
        Article article = Article.create(source, new ArticleDraft(ArticleKind.VULNERABILITY, ArticleRegion.OVERSEAS, title,
                "https://example.test/" + System.nanoTime(), source.getDisplayName(), now, false, null, score, "3.1", false,
                isUrgent, null, null, sourceFacts), now);
        article.applyAiAnalysis(ArticleTopic.VULNERABILITY, null, importance, null);
        articleClassificationService.classify(article);
        return articleRepository.save(article);
    }

    private Article saveUnjudgedArticle(String title, Instant collectedAt) {
        return articleRepository.save(Article.create(nvdSource, new ArticleDraft(ArticleKind.VULNERABILITY,
                ArticleRegion.OVERSEAS, title, "https://example.test/" + System.nanoTime(), "NVD", collectedAt, false, null,
                new BigDecimal("9.8"), "3.1", false, true), collectedAt));
    }

    private Map<String, PushMessage> captureSentMessagesByToken() {
        ArgumentCaptor<List<PushMessage>> sentMessages = ArgumentCaptor.captor();
        verify(pushSender).send(sentMessages.capture());
        return sentMessages.getValue().stream().collect(Collectors.toMap(PushMessage::pushToken, pushMessage -> pushMessage));
    }
}
