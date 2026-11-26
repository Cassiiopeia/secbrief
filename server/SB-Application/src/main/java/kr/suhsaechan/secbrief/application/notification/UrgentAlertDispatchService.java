package kr.suhsaechan.secbrief.application.notification;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import kr.suhsaechan.secbrief.advisory.entity.Article;
import kr.suhsaechan.secbrief.advisory.repository.ArticleRepository;
import kr.suhsaechan.secbrief.member.constant.AlertCategory;
import kr.suhsaechan.secbrief.member.entity.PushDevice;
import kr.suhsaechan.secbrief.member.push.PushMessage;
import kr.suhsaechan.secbrief.member.push.PushSendOutcome;
import kr.suhsaechan.secbrief.member.push.PushSendStatus;
import kr.suhsaechan.secbrief.member.push.PushSender;
import kr.suhsaechan.secbrief.member.repository.PushDeviceRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Limit;
import org.springframework.stereotype.Service;

/**
 * 긴급 알림 유스케이스: 알림 대기 기사 > 기기별로 켠 종류에 맞는 기사만 묶어 1건 > 발송 > 기록
 * 기사 제목·푸시 토큰은 로그에 남기지 않음
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class UrgentAlertDispatchService {

    // AI 번역을 기다리는 시간 (넘으면 원문 제목으로 알림, AI 틱 5분보다 길게)
    static final Duration AI_WAIT_DURATION = Duration.ofMinutes(10);
    // 한 틱에 묶는 기사 수 상한 (넘는 분량은 다음 틱)
    static final int MAXIMUM_ARTICLES_PER_TICK = 200;

    private final ArticleRepository articleRepository;
    private final PushDeviceRepository pushDeviceRepository;
    private final PushSender pushSender;
    private final UrgentAlertRecorder urgentAlertRecorder;
    private final Clock clock;

    /**
     * 틱 1회: 대기 기사 조회 > 기기별 메시지 > 발송 > 기록
     * 발송이 전부 실패하면 기사를 대기로 남김 (다음 틱에 다시), 받을 기기가 없거나 알리지 않기로 한 기사도 처리한 것으로 표시
     */
    public UrgentAlertDispatchResult dispatchPendingAlerts() {
        if (!pushSender.isAvailable()) {
            return UrgentAlertDispatchResult.nothingToSend();
        }
        Instant now = clock.instant();
        List<Article> pendingArticles = articleRepository.findUrgentAlertTargets(
                now.minus(AI_WAIT_DURATION), Limit.of(MAXIMUM_ARTICLES_PER_TICK));
        if (pendingArticles.isEmpty()) {
            return UrgentAlertDispatchResult.nothingToSend();
        }

        // 기기별 메시지 (알릴 만한 기사만, 켠 종류에 맞는 기사가 없는 기기는 보내지 않음)
        List<Article> alertWorthyArticles = pendingArticles.stream().filter(UrgentAlertPolicy::isAlertWorthy).toList();
        List<PushDevice> pushDevices = alertWorthyArticles.isEmpty()
                ? List.of()
                : pushDeviceRepository.findAllWithEnabledAlertCategories();
        Map<String, Long> deviceIdByPushToken = new HashMap<>();
        List<PushMessage> pushMessages = toPushMessages(alertWorthyArticles, pushDevices, deviceIdByPushToken);

        List<PushSendOutcome> outcomes = pushMessages.isEmpty() ? List.of() : pushSender.send(pushMessages);
        int acceptedCount = countByStatus(outcomes, PushSendStatus.ACCEPTED);
        int failedCount = countByStatus(outcomes, PushSendStatus.FAILED);
        int unregisteredCount = countByStatus(outcomes, PushSendStatus.DEVICE_NOT_REGISTERED);

        // 전부 실패 (제공자 장애 등): 기사를 대기로 남기고 무효 기기만 정리
        if (acceptedCount == 0 && failedCount > 0) {
            urgentAlertRecorder.deleteUnregisteredDevices(outcomes);
            log.warn("[urgent alert] failed 502 : all sends failed : articles={}, devices={}",
                    pendingArticles.size(), pushMessages.size());
            return new UrgentAlertDispatchResult(pendingArticles.size(), 0, failedCount, unregisteredCount, true);
        }

        urgentAlertRecorder.recordDispatch(pendingArticles.stream().map(Article::getId).toList(), now,
                outcomes, deviceIdByPushToken);
        log.info("[urgent alert] dispatched : articles={}, accepted={}, failed={}, unregistered={}",
                pendingArticles.size(), acceptedCount, failedCount, unregisteredCount);
        return new UrgentAlertDispatchResult(pendingArticles.size(), acceptedCount, failedCount, unregisteredCount, false);
    }

    // 같은 종류 조합을 켠 기기는 같은 글을 받음 (조합마다 글을 한 번만 만듦)
    private static List<PushMessage> toPushMessages(List<Article> pendingArticles, List<PushDevice> pushDevices,
                                                    Map<String, Long> deviceIdByPushToken) {
        Map<Set<AlertCategory>, Optional<UrgentAlertContent>> contentByCategories = new HashMap<>();
        List<PushMessage> pushMessages = new ArrayList<>();
        for (PushDevice pushDevice : pushDevices) {
            Optional<UrgentAlertContent> content = contentByCategories.computeIfAbsent(
                    Set.copyOf(pushDevice.getEnabledAlertCategories()),
                    enabledAlertCategories -> toContent(pendingArticles, enabledAlertCategories));
            if (content.isEmpty()) {
                continue;
            }
            deviceIdByPushToken.put(pushDevice.getPushToken(), pushDevice.getId());
            pushMessages.add(new PushMessage(pushDevice.getPushToken(), content.get().title(), content.get().body(),
                    UrgentAlertPolicy.toPushData(content.get()),
                    UrgentAlertPolicy.ANDROID_CHANNEL_ID));
        }
        return pushMessages;
    }

    private static Optional<UrgentAlertContent> toContent(List<Article> pendingArticles,
                                                         Set<AlertCategory> enabledAlertCategories) {
        List<Article> matchedArticles = pendingArticles.stream()
                .filter(article -> enabledAlertCategories.contains(UrgentAlertPolicy.toAlertCategory(article)))
                .toList();
        return matchedArticles.isEmpty() ? Optional.empty() : Optional.of(UrgentAlertPolicy.toContent(matchedArticles));
    }

    private static int countByStatus(List<PushSendOutcome> outcomes, PushSendStatus status) {
        return (int) outcomes.stream().filter(outcome -> outcome.status() == status).count();
    }
}
