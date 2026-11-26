package kr.suhsaechan.secbrief.application.notification.settings;

import java.time.LocalDate;

/**
 * 어제 기준 알림 추정: 날짜(기기 날짜), 바로 알림 횟수·기사 수, 아침 요약 기사 수·횟수(0 또는 1), 어제 실제 공통 알림 수
 */
public record AlertEstimateResponse(LocalDate date, int immediatePushCount, int immediateArticleCount,
                                    int digestArticleCount, int digestPushCount, long currentPushCount) {
}
