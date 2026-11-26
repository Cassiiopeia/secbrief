package kr.suhsaechan.secbrief.application.notification.notice;

/**
 * 공지 올리기 결과: 공지 번호, 바로 보낸 기기 수(접수), 방해 금지 시간이라 보류한 기기 수, 실패한 기기 수
 */
public record NoticePublishResult(long noticeId, int sentDeviceCount, int heldDeviceCount, int failedDeviceCount) {

    public static NoticePublishResult withoutPush(long noticeId) {
        return new NoticePublishResult(noticeId, 0, 0, 0);
    }
}
