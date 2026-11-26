package kr.suhsaechan.secbrief.member.constant;

/**
 * 알림함 종류 2개 (이름이 그대로 API 값·DB 값): ALERT 긴급 알림 발송 기록(30일 보관), NOTICE 관리자 공지사항
 */
public enum NotificationKind {
    ALERT,
    NOTICE
}
