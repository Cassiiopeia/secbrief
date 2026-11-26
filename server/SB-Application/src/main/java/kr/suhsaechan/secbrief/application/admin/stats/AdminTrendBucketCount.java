package kr.suhsaechan.secbrief.application.admin.stats;

/**
 * 집계 1행: 칸 첫날(yyyy-MM-dd), 줄 key(종류·묶음 이름), 건수
 */
public record AdminTrendBucketCount(String bucketStart, String seriesKey, long totalCount) {
}
