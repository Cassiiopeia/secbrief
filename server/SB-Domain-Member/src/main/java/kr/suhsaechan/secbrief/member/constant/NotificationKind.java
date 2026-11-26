package kr.suhsaechan.secbrief.member.constant;

/**
 * 알림함 종류 3개 (이름이 그대로 API 값·DB 값): ALERT 알림 발송 기록(30일 보관), NOTICE 관리자 공지사항,
 * DIGEST 기기 1대의 아침 요약 (알림함 목록에 보이지 않음, 푸시로만 엶, 30일 보관)
 */
public enum NotificationKind {
    ALERT,
    NOTICE,
    DIGEST
}
