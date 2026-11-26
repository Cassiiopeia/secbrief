package kr.suhsaechan.secbrief.application.notification;

/**
 * 긴급 알림 1건의 글: 제목, 본문, 기사 번호 (기사 1건을 알릴 때만 값 있음: 앱이 그 상세를 엶), 알린 기사 수
 */
public record UrgentAlertContent(String title, String body, Long articleId, int articleCount) {
}
