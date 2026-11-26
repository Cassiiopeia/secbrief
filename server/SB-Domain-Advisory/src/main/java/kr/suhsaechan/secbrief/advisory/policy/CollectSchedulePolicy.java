package kr.suhsaechan.secbrief.advisory.policy;

import java.time.Duration;
import java.time.Instant;
import kr.suhsaechan.secbrief.advisory.constant.FeedFormat;
import kr.suhsaechan.secbrief.advisory.constant.SourceCode;
import kr.suhsaechan.secbrief.advisory.entity.Source;

/**
 * 수집 주기 판정: 수집할 때가 됐는지, 수집 시작 시각(since), RSS 저장 범위
 * 주기는 마지막 시도 기준이라 계속 실패하는 출처를 매분 두드리지 않음
 */
public final class CollectSchedulePolicy {

    // 이전 수집 시각보다 이만큼 앞에서 다시 읽음 (늦게 공개된 항목 누락 방지, 중복은 저장 시 병합)
    public static final Duration COLLECT_OVERLAP = Duration.ofDays(1);
    // 첫 수집 범위 (일): 출처에 이전 수집 시각이 없을 때, 긴 중단 뒤 수집 범위 상한도 같은 값
    public static final int KEV_FIRST_COLLECT_DAYS = 30;
    public static final int NVD_FIRST_COLLECT_DAYS = 7;
    public static final int GHSA_FIRST_COLLECT_DAYS = 7;
    // RSS 저장 범위 (일): 게시일이 이보다 오래된 항목은 저장하지 않음
    // 과거 글을 한꺼번에 주는 피드(Red Hat 수백 건, Node.js 전체 이력)의 첫 수집이 AI·임베딩 대기열을 채우지 않게
    public static final int RSS_STORED_PUBLISHED_WITHIN_DAYS = 30;

    private CollectSchedulePolicy() {
    }

    /**
     * 수집 시점 여부: 마지막 시도 시작 + 주기가 지났거나 시도한 적 없음 (성공 여부와 무관)
     */
    public static boolean isCollectDue(Instant lastAttemptStartedAt, int intervalMinutes, Instant now) {
        if (lastAttemptStartedAt == null) {
            return true;
        }
        return !now.isBefore(lastAttemptStartedAt.plus(Duration.ofMinutes(intervalMinutes)));
    }

    /**
     * 수집 시작 시각: RSS 는 null(피드 전체), KEV·NVD·GHSA 는 max(마지막 수집 - 1일, 첫 수집 범위 시작)
     */
    public static Instant toCollectSince(Source source, Instant now) {
        return switch (source.getFeedFormat()) {
            case RSS -> null;
            case CISA_KEV_JSON -> toCollectSince(source, now, KEV_FIRST_COLLECT_DAYS);
            case NVD_API -> toCollectSince(source, now, NVD_FIRST_COLLECT_DAYS);
            case GHSA_API -> toCollectSince(source, now, GHSA_FIRST_COLLECT_DAYS);
        };
    }

    /**
     * 저장할 가장 이른 게시일: RSS 는 now - 30일, KEV·NVD·GHSA 는 null (수집기가 since 로 이미 거름), KISA 보호나라도 null
     * 게시일이 없어 수집 시각으로 대체한 항목은 걸리지 않음
     */
    public static Instant toEarliestStoredPublishedAt(Source source, Instant now) {
        // KISA 보호나라 제외: 피드가 10건뿐이고 경보단계·보고서는 드물게 올라와 30일로 자르면 현재 경보단계가 빠짐
        return source.getFeedFormat() == FeedFormat.RSS && !SourceCode.isKisa(source.getCode())
                ? now.minus(Duration.ofDays(RSS_STORED_PUBLISHED_WITHIN_DAYS))
                : null;
    }

    private static Instant toCollectSince(Source source, Instant now, int firstCollectDays) {
        Instant firstCollectStart = now.minus(Duration.ofDays(firstCollectDays));
        if (source.getLastCollectedAt() == null) {
            return firstCollectStart;
        }
        Instant overlappedStart = source.getLastCollectedAt().minus(COLLECT_OVERLAP);
        return overlappedStart.isAfter(firstCollectStart) ? overlappedStart : firstCollectStart;
    }
}
