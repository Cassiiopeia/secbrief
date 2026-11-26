package kr.suhsaechan.secbrief.advisory.indicator;

import java.time.Instant;

/**
 * 해외 지표 1개를 마지막으로 읽은 값: 단계 값, 읽은 시각
 */
public record InternationalAlertIndicatorReading(InternationalAlertIndicator indicator, String levelKey, Instant checkedAt) {
}
