package kr.suhsaechan.secbrief.web.notification;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
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
import kr.suhsaechan.secbrief.application.notification.PushReceiptCheckService;
import kr.suhsaechan.secbrief.application.notification.UrgentAlertDispatchResult;
import kr.suhsaechan.secbrief.application.notification.UrgentAlertDispatchService;
import kr.suhsaechan.secbrief.member.constant.AlertCategory;
import kr.suhsaechan.secbrief.member.constant.PushPlatform;
import kr.suhsaechan.secbrief.member.dto.PushDeviceRegisterRequest;
import kr.suhsaechan.secbrief.member.entity.PushDevice;
import kr.suhsaechan.secbrief.member.entity.PushTicket;
import kr.suhsaechan.secbrief.member.push.PushMessage;
import kr.suhsaechan.secbrief.member.push.PushReceipt;
import kr.suhsaechan.secbrief.member.push.PushSendOutcome;
import kr.suhsaechan.secbrief.member.push.PushSendStatus;
import kr.suhsaechan.secbrief.member.push.PushSender;
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

/**
 * 긴급 알림 (DB): 기기 등록·교체·삭제, 켠 종류에 맞는 기사만 묶어 발송, 발송 처리 표시, 전부 실패 시 대기 유지,
 * 무효 토큰 기기 삭제, 접수증 확인, KEV 등재 시 다시 알림 (발송기는 가짜)
 */
@SpringBootTest
@ActiveProfiles("test")
class UrgentAlertDispatchTest extends PostgresContainerTestSupport {

    private static final String KEV_ONLY_TOKEN = "ExponentPushToken[kev-only]";
    private static final String ALL_CATEGORIES_TOKEN = "ExponentPushToken[all-categories]";
    private static final String KISA_ONLY_TOKEN = "ExponentPushToken[kisa-only]";

    @Autowired
    private UrgentAlertDispatchService urgentAlertDispatchService;
    @Autowired
    private PushReceiptCheckService pushReceiptCheckService;
    @Autowired
    private PushDeviceService pushDeviceService;
    @Autowired
    private PushDeviceRepository pushDeviceRepository;
    @Autowired
    private PushTicketRepository pushTicketRepository;
    @Autowired
    private ArticleRepository articleRepository;
    @Autowired
    private SourceRepository sourceRepository;
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
        articleRepository.deleteAll();
        nvdSource = sourceRepository.findByCode(SourceCode.NVD).orElseThrow();
        kevSource = sourceRepository.findByCode(SourceCode.CISA_KEV).orElseThrow();
        given(pushSender.isAvailable()).willReturn(true);
        // 기본: 받은 메시지 전부 접수
        given(pushSender.send(anyList())).willAnswer(invocation -> {
            List<PushMessage> pushMessages = invocation.getArgument(0);
            return pushMessages.stream()
                    .map(pushMessage -> PushSendOutcome.accepted(pushMessage.pushToken(), "ticket-" + pushMessage.pushToken()))
                    .toList();
        });
    }

    @Test
    void registersUpdatesAndDeletesDeviceByPushToken() {
        register(KEV_ONLY_TOKEN, Set.of(AlertCategory.KEV, AlertCategory.KISA_URGENT));
        assertThat(pushDeviceService.register(new PushDeviceRegisterRequest(KEV_ONLY_TOKEN, PushPlatform.IOS,
                Set.of(AlertCategory.CRITICAL_CVE))).enabledAlertCategories()).containsExactly(AlertCategory.CRITICAL_CVE);

        List<PushDevice> pushDevices = pushDeviceRepository.findAllWithAlertSettings();
        assertThat(pushDevices).hasSize(1);
        assertThat(pushDevices.get(0).getPlatform()).isEqualTo(PushPlatform.IOS);
        assertThat(pushDevices.get(0).getEnabledAlertCategories()).containsExactly(AlertCategory.CRITICAL_CVE);

        // 켠 종류가 없으면 기기 삭제
        assertThat(register(KEV_ONLY_TOKEN, Set.of())).isEmpty();
        assertThat(pushDeviceRepository.count()).isZero();
    }

    @Test
    void sendsOneDigestPerDeviceWithOnlyEnabledCategories() {
        register(KEV_ONLY_TOKEN, Set.of(AlertCategory.KEV));
        register(ALL_CATEGORIES_TOKEN, Set.of(AlertCategory.KEV, AlertCategory.KISA_URGENT, AlertCategory.CRITICAL_CVE));
        register(KISA_ONLY_TOKEN, Set.of(AlertCategory.KISA_URGENT));
        Article kevArticle = saveUrgentArticle(kevSource, "CVE-2026-1001", true, true);
        Article criticalArticle = saveUrgentArticle(nvdSource, "CVE-2026-1002", false, true);

        UrgentAlertDispatchResult dispatchResult = urgentAlertDispatchService.dispatchPendingAlerts();

        Map<String, PushMessage> messageByToken = captureSentMessagesByToken();
        // KISA 만 켠 기기는 맞는 기사가 없어 받지 않음
        assertThat(messageByToken).containsOnlyKeys(KEV_ONLY_TOKEN, ALL_CATEGORIES_TOKEN);
        assertThat(messageByToken.get(KEV_ONLY_TOKEN).title()).isEqualTo("악용 중인 취약점이 확인됐어요");
        assertThat(messageByToken.get(KEV_ONLY_TOKEN).body()).isEqualTo(kevArticle.getTitle());
        // 1건 알림은 기사 번호를 실어 앱이 상세를 엶, 여러 건 알림은 탭만
        assertThat(messageByToken.get(KEV_ONLY_TOKEN).data()).containsEntry("articleId", String.valueOf(kevArticle.getId()));
        assertThat(messageByToken.get(ALL_CATEGORIES_TOKEN).data()).doesNotContainKey("articleId");
        assertThat(messageByToken.get(ALL_CATEGORIES_TOKEN).title()).isEqualTo("긴급 보안 소식 2건이 들어왔어요");
        assertThat(messageByToken.get(ALL_CATEGORIES_TOKEN).data()).containsEntry("targetTab", "vulnerability");
        assertThat(messageByToken.get(ALL_CATEGORIES_TOKEN).channelId()).isEqualTo("urgent-alerts");

        assertThat(dispatchResult).isEqualTo(new UrgentAlertDispatchResult(2, 2, 0, 0, false));
        assertThat(findArticle(kevArticle).getUrgentNotifiedAt()).isNotNull();
        assertThat(findArticle(criticalArticle).getUrgentNotifiedAt()).isNotNull();
        assertThat(pushTicketRepository.findAll()).extracting(PushTicket::getTicketId)
                .containsExactlyInAnyOrder("ticket-" + KEV_ONLY_TOKEN, "ticket-" + ALL_CATEGORIES_TOKEN);

        // 같은 기사를 두 번 알리지 않음
        assertThat(urgentAlertDispatchService.dispatchPendingAlerts()).isEqualTo(UrgentAlertDispatchResult.nothingToSend());
    }

    @Test
    void waitsForAiTranslationThenSendsAfterDeadline() {
        register(ALL_CATEGORIES_TOKEN, Set.of(AlertCategory.CRITICAL_CVE));
        // AI 대기 중이고 방금 수집: 아직 알리지 않음
        Article waitingArticle = saveUrgentArticle(nvdSource, "CVE-2026-2001", false, false, Instant.now());
        assertThat(urgentAlertDispatchService.dispatchPendingAlerts()).isEqualTo(UrgentAlertDispatchResult.nothingToSend());
        assertThat(findArticle(waitingArticle).getUrgentNotifiedAt()).isNull();

        // AI 대기 기한(60분)을 넘기면 규칙만으로 판정: 처리 표시 (널리 쓰는 제품이 아니라 보내지는 않음, #97)
        saveUrgentArticle(nvdSource, "CVE-2026-2002", false, false, Instant.now().minus(Duration.ofMinutes(61)));
        assertThat(urgentAlertDispatchService.dispatchPendingAlerts().articleCount()).isEqualTo(1);
    }

    @Test
    void skipsCriticalCveThatAiJudgedRoutineButStillAlertsKev() {
        register(ALL_CATEGORIES_TOKEN, Set.of(AlertCategory.KEV, AlertCategory.CRITICAL_CVE));
        // AI 가 일반으로 본 고위험 CVE: 알리지 않고 처리 표시만
        Article routineArticle = saveUrgentArticle(nvdSource, "CVE-2026-7001", false, false);
        routineArticle.applyAiAnalysis(ArticleTopic.VULNERABILITY, null, ArticleImportance.ROUTINE, null);
        articleClassificationService.classify(routineArticle);
        articleRepository.save(routineArticle);

        assertThat(urgentAlertDispatchService.dispatchPendingAlerts())
                .isEqualTo(new UrgentAlertDispatchResult(1, 0, 0, 0, false));
        assertThat(findArticle(routineArticle).getUrgentNotifiedAt()).isNotNull();
        verify(pushSender, never()).send(anyList());

        // KEV 는 AI 판단과 무관하게 알림 (등재되면 중요도 하한이 NOTABLE)
        Article kevArticle = saveUrgentArticle(kevSource, "CVE-2026-7002", true, false);
        kevArticle.applyAiAnalysis(ArticleTopic.VULNERABILITY, null, ArticleImportance.ROUTINE, null);
        articleClassificationService.classify(kevArticle);
        articleRepository.save(kevArticle);

        assertThat(urgentAlertDispatchService.dispatchPendingAlerts().acceptedCount()).isEqualTo(1);
        assertThat(findArticle(kevArticle).getImportance()).isEqualTo(ArticleImportance.NOTABLE);
    }

    @Test
    void doesNotAlertStaleUrgentArticleButAlertsWhenItIsListedInKevLater() {
        register(ALL_CATEGORIES_TOKEN, Set.of(AlertCategory.KEV, AlertCategory.CRITICAL_CVE));
        // 게시된 지 5일 된 고위험 CVE 를 지금 처음 수집: 알리지 않음
        Instant collectedAt = Instant.now();
        Article staleArticle = articleRepository.save(Article.create(nvdSource, new ArticleDraft(ArticleKind.VULNERABILITY,
                ArticleRegion.OVERSEAS, "CVE-2026-8001: Example flaw", "https://example.test/cve/CVE-2026-8001", "NVD",
                collectedAt.minus(Duration.ofDays(5)), false, "CVE-2026-8001", new BigDecimal("9.8"), "3.1", false, true),
                collectedAt));

        assertThat(staleArticle.getUrgentNotifiedAt()).isEqualTo(collectedAt);
        assertThat(urgentAlertDispatchService.dispatchPendingAlerts()).isEqualTo(UrgentAlertDispatchResult.nothingToSend());

        // 나중에 KEV 에 오르면 새 소식이라 알림 (AI 처리는 끝난 상태)
        staleArticle.applyAiAnalysis(ArticleTopic.VULNERABILITY, null, ArticleImportance.ROUTINE, null);
        articleClassificationService.classify(staleArticle);
        staleArticle.markKev();
        articleRepository.save(staleArticle);
        assertThat(urgentAlertDispatchService.dispatchPendingAlerts().acceptedCount()).isEqualTo(1);
    }

    @Test
    void marksArticlesNotifiedEvenWithoutDevices() {
        Article article = saveUrgentArticle(nvdSource, "CVE-2026-3001", false, true);

        UrgentAlertDispatchResult dispatchResult = urgentAlertDispatchService.dispatchPendingAlerts();

        // 나중에 기기가 등록돼도 지난 소식을 몰아서 보내지 않음
        assertThat(dispatchResult).isEqualTo(new UrgentAlertDispatchResult(1, 0, 0, 0, false));
        assertThat(findArticle(article).getUrgentNotifiedAt()).isNotNull();
        verify(pushSender, never()).send(anyList());
    }

    @Test
    void keepsArticlesPendingWhenEverySendFails() {
        register(ALL_CATEGORIES_TOKEN, Set.of(AlertCategory.CRITICAL_CVE));
        Article article = saveUrgentArticle(nvdSource, "CVE-2026-4001", false, true);
        given(pushSender.send(anyList())).willReturn(List.of(PushSendOutcome.failed(ALL_CATEGORIES_TOKEN)));

        UrgentAlertDispatchResult dispatchResult = urgentAlertDispatchService.dispatchPendingAlerts();

        assertThat(dispatchResult.isRetryPending()).isTrue();
        assertThat(findArticle(article).getUrgentNotifiedAt()).isNull();
        assertThat(pushTicketRepository.count()).isZero();
    }

    @Test
    void deletesDeviceWhenTokenIsNotRegistered() {
        register(KEV_ONLY_TOKEN, Set.of(AlertCategory.CRITICAL_CVE));
        register(ALL_CATEGORIES_TOKEN, Set.of(AlertCategory.CRITICAL_CVE));
        saveUrgentArticle(nvdSource, "CVE-2026-5001", false, true);
        given(pushSender.send(anyList())).willAnswer(invocation -> {
            List<PushMessage> pushMessages = invocation.getArgument(0);
            return pushMessages.stream()
                    .map(pushMessage -> KEV_ONLY_TOKEN.equals(pushMessage.pushToken())
                            ? PushSendOutcome.deviceNotRegistered(pushMessage.pushToken())
                            : PushSendOutcome.accepted(pushMessage.pushToken(), "ticket-1"))
                    .toList();
        });

        urgentAlertDispatchService.dispatchPendingAlerts();

        assertThat(pushDeviceRepository.findAll()).extracting(PushDevice::getPushToken).containsExactly(ALL_CATEGORIES_TOKEN);
    }

    @Test
    void alertsAgainWhenNotifiedArticleIsListedInKev() {
        register(ALL_CATEGORIES_TOKEN, Set.of(AlertCategory.KEV));
        Article article = saveUrgentArticle(nvdSource, "CVE-2026-6001", false, true);
        urgentAlertDispatchService.dispatchPendingAlerts();
        assertThat(findArticle(article).getUrgentNotifiedAt()).isNotNull();

        Article notifiedArticle = findArticle(article);
        notifiedArticle.markKev();
        articleRepository.save(notifiedArticle);

        assertThat(urgentAlertDispatchService.dispatchPendingAlerts().acceptedCount()).isEqualTo(1);
    }

    @Test
    void checksReceiptsAndCleansUpTicketsAndInvalidDevices() {
        PushDevice goneDevice = registerAndFind(KEV_ONLY_TOKEN);
        PushDevice aliveDevice = registerAndFind(ALL_CATEGORIES_TOKEN);
        Instant sentLongEnoughAgo = Instant.now().minus(Duration.ofMinutes(20));
        pushTicketRepository.saveAll(List.of(
                PushTicket.create(goneDevice, "ticket-gone", sentLongEnoughAgo),
                PushTicket.create(aliveDevice, "ticket-delivered", sentLongEnoughAgo),
                PushTicket.create(aliveDevice, "ticket-waiting", sentLongEnoughAgo),
                PushTicket.create(aliveDevice, "ticket-too-recent", Instant.now()),
                PushTicket.create(aliveDevice, "ticket-expired", Instant.now().minus(Duration.ofHours(25)))));
        given(pushSender.fetchReceipts(anyList())).willReturn(Map.of(
                "ticket-gone", new PushReceipt(PushSendStatus.DEVICE_NOT_REGISTERED, "DeviceNotRegistered"),
                "ticket-delivered", new PushReceipt(PushSendStatus.ACCEPTED, null)));

        pushReceiptCheckService.checkReceipts();

        // 무효 기기 삭제, 확인한 티켓·보관 기간 지난 티켓 삭제, 접수증이 아직 없는 티켓은 유지
        assertThat(pushDeviceRepository.findAll()).extracting(PushDevice::getPushToken).containsExactly(ALL_CATEGORIES_TOKEN);
        assertThat(pushTicketRepository.findAll()).extracting(PushTicket::getTicketId)
                .containsExactlyInAnyOrder("ticket-waiting", "ticket-too-recent");
    }

    private List<AlertCategory> register(String pushToken, Set<AlertCategory> enabledAlertCategories) {
        return pushDeviceService.register(new PushDeviceRegisterRequest(pushToken, PushPlatform.ANDROID, enabledAlertCategories))
                .enabledAlertCategories();
    }

    private PushDevice registerAndFind(String pushToken) {
        register(pushToken, Set.of(AlertCategory.KEV));
        return pushDeviceRepository.findByPushToken(pushToken).orElseThrow();
    }

    private Map<String, PushMessage> captureSentMessagesByToken() {
        ArgumentCaptor<List<PushMessage>> sentMessages = ArgumentCaptor.captor();
        verify(pushSender).send(sentMessages.capture());
        return sentMessages.getValue().stream()
                .collect(java.util.stream.Collectors.toMap(PushMessage::pushToken, pushMessage -> pushMessage));
    }

    private Article saveUrgentArticle(Source source, String cveId, boolean isKev, boolean isAiDone) {
        return saveUrgentArticle(source, cveId, isKev, isAiDone, Instant.now());
    }

    private Article saveUrgentArticle(Source source, String cveId, boolean isKev, boolean isAiDone, Instant collectedAt) {
        Article article = Article.create(source, new ArticleDraft(ArticleKind.VULNERABILITY, ArticleRegion.OVERSEAS,
                cveId + ": Example flaw", "https://example.test/cve/" + cveId, source.getDisplayName(), collectedAt,
                false, cveId, isKev ? null : new BigDecimal("9.8"), isKev ? null : "3.1", isKev, true), collectedAt);
        if (isAiDone) {
            article.applyAiAnalysis(ArticleTopic.VULNERABILITY, null, ArticleImportance.NOTABLE, null);
            articleClassificationService.classify(article);
        }
        return articleRepository.save(article);
    }

    private Article findArticle(Article article) {
        return articleRepository.findById(article.getId()).orElseThrow();
    }
}
