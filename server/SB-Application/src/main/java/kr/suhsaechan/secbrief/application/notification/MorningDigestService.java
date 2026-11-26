package kr.suhsaechan.secbrief.application.notification;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;
import kr.suhsaechan.secbrief.advisory.catalog.TagCatalog;
import kr.suhsaechan.secbrief.advisory.catalog.TagCatalogProvider;
import kr.suhsaechan.secbrief.advisory.entity.Article;
import kr.suhsaechan.secbrief.advisory.repository.ArticleRepository;
import kr.suhsaechan.secbrief.member.constant.NotificationKind;
import kr.suhsaechan.secbrief.member.entity.PushDevice;
import kr.suhsaechan.secbrief.member.push.PushMessage;
import kr.suhsaechan.secbrief.member.push.PushSendOutcome;
import kr.suhsaechan.secbrief.member.push.PushSendStatus;
import kr.suhsaechan.secbrief.member.push.PushSender;
import kr.suhsaechan.secbrief.member.repository.DigestItemRepository;
import kr.suhsaechan.secbrief.member.repository.PushDeviceRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * 아침 요약 보내기 (알림 틱마다): 오래된 대기분 버림 > 요약 시각이 된 기기(방해 금지 아님, 오늘 아직 안 보냄)만 > 기기 전용 DIGEST 기록 > 발송 > 기록
 * 접수되면 대기분 삭제·오늘 보냄 표시, 실패하면 기록을 지우고 대기분을 남겨 다음 틱에 다시
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MorningDigestService {

    private final DigestItemRepository digestItemRepository;
    private final PushDeviceRepository pushDeviceRepository;
    private final ArticleRepository articleRepository;
    private final PushSender pushSender;
    private final MorningDigestRecorder morningDigestRecorder;
    private final TagCatalogProvider tagCatalogProvider;
    private final Clock clock;

    /**
     * @return 아침 요약을 보낸 기기 수 (접수 기준)
     */
    public int sendDueDigests() {
        if (!pushSender.isAvailable()) {
            return 0;
        }
        Instant now = clock.instant();
        morningDigestRecorder.deleteExpiredItems(now.minus(MorningDigestPolicy.QUEUE_RETENTION_DURATION));
        Map<Long, List<Long>> articleIdsByDeviceId = digestItemRepository.findAllInQueueOrder().stream()
                .collect(Collectors.groupingBy(item -> item.getId().getPushDeviceId(), LinkedHashMap::new,
                        Collectors.mapping(item -> item.getId().getArticleId(), Collectors.toList())));
        if (articleIdsByDeviceId.isEmpty()) {
            return 0;
        }
        List<PushDevice> dueDevices = pushDeviceRepository.findAllById(articleIdsByDeviceId.keySet()).stream()
                .filter(pushDevice -> pushDevice.isDigestDueAt(now))
                .toList();
        if (dueDevices.isEmpty()) {
            return 0;
        }
        Map<Long, Article> articleById = articleRepository.findAllById(articleIdsByDeviceId.values().stream()
                        .flatMap(List::stream).distinct().toList()).stream()
                .collect(Collectors.toMap(Article::getId, Function.identity()));
        TagCatalog catalog = tagCatalogProvider.getCatalog();

        // 기기마다 기록 1건 + 메시지 1건
        List<PushMessage> pushMessages = new ArrayList<>();
        Map<String, DigestDelivery> deliveryByPushToken = new HashMap<>();
        for (PushDevice pushDevice : dueDevices) {
            List<Article> articles = articleIdsByDeviceId.get(pushDevice.getId()).stream()
                    .map(articleById::get).filter(Objects::nonNull).toList();
            if (articles.isEmpty()) {
                continue;
            }
            UrgentAlertContent content = MorningDigestPolicy.toContent(articles, catalog);
            long notificationId = morningDigestRecorder.saveDigestNotification(pushDevice.getId(), content, articles,
                    now).getId();
            pushMessages.add(new PushContent(notificationId, NotificationKind.DIGEST, content.title(), content.body(),
                    null, articles.size()).toPushMessage(pushDevice.getPushToken()));
            deliveryByPushToken.put(pushDevice.getPushToken(), new DigestDelivery(pushDevice.getId(), notificationId,
                    articles.stream().map(Article::getId).toList()));
        }
        if (pushMessages.isEmpty()) {
            return 0;
        }
        List<PushSendOutcome> outcomes = pushSender.send(pushMessages);
        morningDigestRecorder.recordOutcomes(outcomes, deliveryByPushToken, now);
        int acceptedCount = PushDeliveryRecorder.countByStatus(outcomes, PushSendStatus.ACCEPTED);
        log.info("[morning digest] sent : devices={}, accepted={}, failed={}", pushMessages.size(), acceptedCount,
                PushDeliveryRecorder.countByStatus(outcomes, PushSendStatus.FAILED));
        return acceptedCount;
    }

    /** 기기 1대에 보낸 아침 요약: 기기, 기록 번호, 담긴 기사 */
    public record DigestDelivery(long pushDeviceId, long notificationId, List<Long> articleIds) {
    }
}
