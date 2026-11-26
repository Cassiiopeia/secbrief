package kr.suhsaechan.secbrief.application.notification.notice;

import java.time.Instant;
import kr.suhsaechan.secbrief.member.entity.Notification;

/**
 * 관리자 공지 표 1행: 번호, 제목, 본문, 링크, 고정, 올린·고친 시각, 푸시 보낸 시각 (안 보냈으면 null)
 */
public record AdminNoticeRow(Long id, String title, String body, String linkUrl, boolean isPinned,
                             Instant createdAt, Instant updatedAt, Instant pushedAt) {

    public static AdminNoticeRow from(Notification notice) {
        return new AdminNoticeRow(notice.getId(), notice.getTitle(), notice.getBody(), notice.getLinkUrl(),
                notice.isPinned(), notice.getCreatedAt(), notice.getUpdatedAt(), notice.getPushedAt());
    }
}
