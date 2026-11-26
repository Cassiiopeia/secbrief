package kr.suhsaechan.secbrief.application.admin.stats;

import java.util.List;

/**
 * 추이 그래프 한 줄: key(색·범례 고정용), 이름, 칸별 값 (기록 없는 칸은 0)
 */
public record AdminTrendSeries(String key, String label, List<Number> values) {
}
