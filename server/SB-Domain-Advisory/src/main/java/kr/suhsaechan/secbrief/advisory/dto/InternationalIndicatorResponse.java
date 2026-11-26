package kr.suhsaechan.secbrief.advisory.dto;

import java.time.Instant;
import kr.suhsaechan.secbrief.advisory.indicator.InternationalAlertIndicator;
import kr.suhsaechan.secbrief.advisory.indicator.InternationalAlertIndicatorReading;

/**
 * 해외 지표 1개: 지표 코드, 단계 값(지표 글자 그대로, 못 읽었으면 null), 단계 위치(1부터, 못 읽었으면 0)·단계 수, 읽은 시각, 설명 페이지
 */
public record InternationalIndicatorResponse(
        InternationalAlertIndicator code,
        String level,
        int levelStep,
        int levelCount,
        Instant checkedAt,
        String sourceUrl
) {

    public static InternationalIndicatorResponse from(InternationalAlertIndicatorReading reading) {
        InternationalAlertIndicator indicator = reading.indicator();
        return new InternationalIndicatorResponse(indicator, reading.levelKey(), indicator.toLevelStep(reading.levelKey()),
                indicator.getLevelKeys().size(), reading.checkedAt(), indicator.getSourceUrl());
    }

    /** 한 번도 못 읽은 지표 (화면은 확인하지 못했다는 문구 + 설명 페이지) */
    public static InternationalIndicatorResponse unavailable(InternationalAlertIndicator indicator) {
        return new InternationalIndicatorResponse(indicator, null, 0, indicator.getLevelKeys().size(), null,
                indicator.getSourceUrl());
    }
}
