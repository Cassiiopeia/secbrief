package kr.suhsaechan.secbrief.advisory.image;

import java.time.Instant;
import java.time.ZonedDateTime;
import kr.suhsaechan.secbrief.advisory.policy.SeoulCalendar;

/**
 * 기사 대표 이미지 객체 키: articles/{yyyy}/{MM}/{articleId}.jpg (수집 시각의 서울 연·월)
 * 공개 콘텐츠라 추측 불가능할 필요 없음, 같은 기사는 항상 같은 키 (다시 저장하면 덮어씀)
 */
public final class ArticleImageObjectKey {

    private static final String OBJECT_KEY_FORMAT = "articles/%04d/%02d/%d.jpg";

    private ArticleImageObjectKey() {
    }

    public static String toObjectKey(Long articleId, Instant collectedAt) {
        ZonedDateTime seoulCollectedAt = collectedAt.atZone(SeoulCalendar.SEOUL);
        return OBJECT_KEY_FORMAT.formatted(seoulCollectedAt.getYear(), seoulCollectedAt.getMonthValue(), articleId);
    }
}
