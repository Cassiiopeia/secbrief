package kr.suhsaechan.secbrief.application.notification.notice;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import kr.suhsaechan.secbrief.application.notification.PushContent;
import kr.suhsaechan.secbrief.application.notification.PushDelivery;
import kr.suhsaechan.secbrief.application.notification.PushDeliveryPlan;
import kr.suhsaechan.secbrief.application.notification.PushDeliveryRecorder;
import kr.suhsaechan.secbrief.common.exception.CustomException;
import kr.suhsaechan.secbrief.common.exception.constant.ErrorCode;
import kr.suhsaechan.secbrief.common.text.TextTruncation;
import kr.suhsaechan.secbrief.member.constant.NotificationKind;
import kr.suhsaechan.secbrief.member.entity.Notification;
import kr.suhsaechan.secbrief.member.push.PushSendOutcome;
import kr.suhsaechan.secbrief.member.push.PushSendStatus;
import kr.suhsaechan.secbrief.member.push.PushSender;
import kr.suhsaechan.secbrief.member.repository.NotificationRepository;
import kr.suhsaechan.secbrief.member.repository.PushDeviceRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 관리자 공지사항: 표 조회, 올리기(선택: 알림 켠 기기 전체에 푸시, 방해 금지 시간이면 보류), 고치기, 고정, 지우기
 * 고칠 때는 푸시를 다시 보내지 않음
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AdminNoticeService {

    // 푸시 본문에 싣는 공지 본문 앞부분 길이
    static final int PUSH_BODY_LENGTH = 120;

    private final NotificationRepository notificationRepository;
    private final PushDeviceRepository pushDeviceRepository;
    private final PushSender pushSender;
    private final PushDeliveryRecorder pushDeliveryRecorder;
    private final Clock clock;

    @Transactional(readOnly = true)
    public List<AdminNoticeRow> findAllNotices() {
        return notificationRepository.findAllByKindOrderByIsPinnedDescCreatedAtDescIdDesc(NotificationKind.NOTICE)
                .stream().map(AdminNoticeRow::from).toList();
    }

    @Transactional(readOnly = true)
    public AdminNoticeRow findNotice(long noticeId) {
        return AdminNoticeRow.from(getNotice(noticeId));
    }

    /**
     * 푸시를 받을 기기 수 (알림을 켠 기기 전체: 종류를 모두 끈 기기는 행이 없음)
     */
    public long countPushRecipients() {
        return pushDeviceRepository.count();
    }

    /**
     * 올리기: 검증 > 저장 > (선택) 푸시 발송
     */
    public NoticePublishResult publish(NoticeForm form, boolean shouldSendPush) {
        NoticeForm validatedForm = form.toValidated();
        Notification notice = notificationRepository.save(Notification.createNotice(validatedForm.title(),
                validatedForm.body(), validatedForm.linkUrl(), validatedForm.isPinned(), clock.instant()));
        if (!shouldSendPush || !pushSender.isAvailable()) {
            return NoticePublishResult.withoutPush(notice.getId());
        }
        return sendNoticePush(notice);
    }

    @Transactional
    public void update(long noticeId, NoticeForm form) {
        NoticeForm validatedForm = form.toValidated();
        getNotice(noticeId).updateNotice(validatedForm.title(), validatedForm.body(), validatedForm.linkUrl(),
                validatedForm.isPinned(), clock.instant());
    }

    @Transactional
    public void changePinned(long noticeId, boolean isPinned) {
        getNotice(noticeId).changePinned(isPinned, clock.instant());
    }

    /**
     * 지우기 (보류 중인 푸시도 DB 가 함께 지움)
     */
    @Transactional
    public AdminNoticeRow delete(long noticeId) {
        Notification notice = getNotice(noticeId);
        notificationRepository.delete(notice);
        return AdminNoticeRow.from(notice);
    }

    // 기기 전체 > 방해 금지 시간 나눔 > 발송 > 티켓·보류·발송 시각 기록
    private NoticePublishResult sendNoticePush(Notification notice) {
        Instant now = clock.instant();
        PushContent pushContent = new PushContent(notice.getId(), NotificationKind.NOTICE, notice.getTitle(),
                toPushBody(notice.getBody()), null, 0);
        PushDeliveryPlan deliveryPlan = PushDeliveryPlan.of(pushDeviceRepository.findAll().stream()
                .map(pushDevice -> new PushDelivery(pushDevice, pushContent)).toList(), now);
        List<PushSendOutcome> outcomes = deliveryPlan.pushMessages().isEmpty()
                ? List.of()
                : pushSender.send(deliveryPlan.pushMessages());
        pushDeliveryRecorder.recordOutcomes(outcomes, deliveryPlan.deviceIdByPushToken(), now);
        pushDeliveryRecorder.holdDeliveries(deliveryPlan.heldDeliveries(), now);
        int acceptedCount = PushDeliveryRecorder.countByStatus(outcomes, PushSendStatus.ACCEPTED);
        if (acceptedCount > 0) {
            pushDeliveryRecorder.markPushed(List.of(notice.getId()), now);
        }
        int failedCount = PushDeliveryRecorder.countByStatus(outcomes, PushSendStatus.FAILED);
        log.info("[admin notice] pushed : noticeId={}, accepted={}, held={}, failed={}", notice.getId(), acceptedCount,
                deliveryPlan.heldDeliveries().size(), failedCount);
        return new NoticePublishResult(notice.getId(), acceptedCount, deliveryPlan.heldDeliveries().size(), failedCount);
    }

    private Notification getNotice(long noticeId) {
        return notificationRepository.findById(noticeId)
                .filter(Notification::isNotice)
                .orElseThrow(() -> new CustomException(ErrorCode.RESOURCE_NOT_FOUND));
    }

    // 줄바꿈은 공백으로 (알림 본문은 한 덩어리), 길면 자름
    static String toPushBody(String noticeBody) {
        String flattenedBody = noticeBody.replaceAll("\\s+", " ").strip();
        return flattenedBody.length() <= PUSH_BODY_LENGTH
                ? flattenedBody
                : TextTruncation.truncateKeepingSurrogatePairs(flattenedBody, PUSH_BODY_LENGTH - 1) + "…";
    }
}
