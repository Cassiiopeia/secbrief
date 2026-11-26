package kr.suhsaechan.secbrief.application.notification;

/**
 * 긴급 알림 1건의 글: 제목, 본문, 눌렀을 때 열 탭, 기사 번호 (기사 1건을 알릴 때만 값 있음: 앱이 그 상세를 엶)
 */
public record UrgentAlertContent(String title, String body, String targetTab, Long articleId) {
}
